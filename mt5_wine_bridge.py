# ==============================================================================
# FOREX INTELLIGENCE SUITE — NATIVE WINE METATRADER 5 HTTP JSON BRIDGE
# Runs directly under Windows Python inside Wine alongside terminal64.exe.
# Replaces brittle RPyC/Pickling bridges with the proven single-lock in-process
# MetaTrader5 pattern (no proxy dicts, automatic filling-mode fallback).
# ==============================================================================

import ctypes
import datetime
import json
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

try:
    import MetaTrader5 as mt5
except ImportError as exc:
    print(f"[FATAL] MetaTrader5 package missing in Wine Python: {exc}", flush=True)
    sys.exit(1)

PORT = int(os.environ.get("MT5_BRIDGE_PORT", "18815"))
HOST = os.environ.get("MT5_BRIDGE_HOST", "127.0.0.1")

_mt5_lock = threading.Lock()
_last_toggle_ts = 0.0

STANDARD_28_PAIRS = [
    "EURUSD", "GBPUSD", "AUDUSD", "NZDUSD", "USDJPY", "USDCHF", "USDCAD",
    "EURGBP", "EURJPY", "EURCHF", "EURAUD", "EURNZD", "EURCAD",
    "GBPJPY", "GBPCHF", "GBPAUD", "GBPNZD", "GBPCAD",
    "AUDJPY", "AUDNZD", "AUDCAD", "AUDCHF",
    "NZDJPY", "NZDCAD", "NZDCHF", "CADJPY", "CADCHF", "CHFJPY",
]

TIMEFRAME_MAP = {
    "M1": getattr(mt5, "TIMEFRAME_M1", 1),
    "M5": getattr(mt5, "TIMEFRAME_M5", 5),
    "M15": getattr(mt5, "TIMEFRAME_M15", 15),
    "M30": getattr(mt5, "TIMEFRAME_M30", 30),
    "H1": getattr(mt5, "TIMEFRAME_H1", 16385),
    "H4": getattr(mt5, "TIMEFRAME_H4", 16388),
    "D1": getattr(mt5, "TIMEFRAME_D1", 16408),
    "W1": getattr(mt5, "TIMEFRAME_W1", 32769),
    "MN1": getattr(mt5, "TIMEFRAME_MN1", 49153),
}

RETCODE_DONE = getattr(mt5, "TRADE_RETCODE_DONE", 10009)
RETCODE_PLACED = getattr(mt5, "TRADE_RETCODE_PLACED", 10008)
RETCODE_INVALID_FILL = getattr(mt5, "TRADE_RETCODE_INVALID_FILL", 10030)


def _to_json_safe(obj):
    if obj is None:
        return None
    if hasattr(obj, "_asdict"):
        return {k: _to_json_safe(v) for k, v in obj._asdict().items()}
    if isinstance(obj, dict):
        return {str(k): _to_json_safe(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [_to_json_safe(x) for x in obj]
    if isinstance(obj, (int, float, bool, str)):
        return obj
    return str(obj)


def _ensure_connected_locked(login=None, password=None, server=None):
    """Attach to the already-running MT5 terminal under _mt5_lock."""
    ti = mt5.terminal_info()
    acc = mt5.account_info()
    if ti is not None and acc is not None and getattr(acc, "login", 0):
        return True, ti, acc

    # Attach without credentials first (terminal64 launches with /config:start.ini)
    ok = bool(mt5.initialize())
    if not ok:
        mt5.shutdown()
        time.sleep(0.2)
        ok = bool(mt5.initialize())

    ti = mt5.terminal_info() if ok else None
    acc = mt5.account_info() if ok else None

    env_login = login or os.environ.get("MT5_LOGIN", "")
    env_password = password if password is not None else os.environ.get("MT5_PASSWORD", "")
    env_server = server or os.environ.get("MT5_SERVER", "GTCGlobalTrade-Demo")

    if ok and (acc is None or not getattr(acc, "login", 0)) and str(env_login).isdigit() and env_password:
        mt5.login(login=int(env_login), password=str(env_password), server=str(env_server))
        ti = mt5.terminal_info()
        acc = mt5.account_info()

    connected = bool(ok and ti is not None and acc is not None and getattr(acc, "login", 0))
    return connected, ti, acc


def _toggle_algotrading_hotkey_locked():
    """Press Ctrl+E on the MT5 main window via Win32 user32 when trade_allowed is False."""
    global _last_toggle_ts
    now = time.time()
    if now - _last_toggle_ts < 5.0:
        return False
    _last_toggle_ts = now
    try:
        user32 = ctypes.windll.user32
        WNDENUMPROC = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
        wins = []

        def _cb(hwnd, lparam):
            if user32.IsWindowVisible(hwnd):
                buf = ctypes.create_unicode_buffer(256)
                user32.GetWindowTextW(hwnd, buf, 256)
                if buf.value:
                    wins.append((hwnd, buf.value))
            return True

        user32.EnumWindows(WNDENUMPROC(_cb), 0)
        target = None
        for hwnd, title in wins:
            if "57452535486" in title or "GTC" in title or "MetaTrader" in title:
                target = hwnd
                break
        if target:
            user32.ShowWindow(target, 9)
            user32.SetForegroundWindow(target)
            time.sleep(0.15)
            user32.keybd_event(0x11, 0, 0, 0)  # VK_CONTROL down
            user32.keybd_event(0x45, 0, 0, 0)  # 'E' down
            time.sleep(0.05)
            user32.keybd_event(0x45, 0, 2, 0)  # 'E' up
            user32.keybd_event(0x11, 0, 2, 0)  # VK_CONTROL up
            time.sleep(0.4)
            return True
    except Exception as ex:
        print(f"[WineBridge] Ctrl+E hotkey warning: {ex}", flush=True)
    return False


def _filling_candidates(symbol_info_obj):
    raw_mask = int(getattr(symbol_info_obj, "filling_mode", 0) or 0) if symbol_info_obj else 0
    order = []
    if raw_mask & 2:
        order.append(int(mt5.ORDER_FILLING_IOC))
    if raw_mask & 1:
        order.append(int(mt5.ORDER_FILLING_FOK))
    for fallback in (mt5.ORDER_FILLING_IOC, mt5.ORDER_FILLING_FOK, mt5.ORDER_FILLING_RETURN):
        val = int(fallback)
        if val not in order:
            order.append(val)
    return order


def _send_with_filling_fallback_locked(base_request: dict, s_info):
    """Send trade order trying supported filling modes.

    Under Wine or certain brokers an unsupported filling mode can return either
    TRADE_RETCODE_INVALID_FILL (10030) or None.
    """
    candidates = _filling_candidates(s_info)
    last_result = None
    last_err = None
    used_filling = candidates[0]

    for attempt_cycle in range(2):
        for filling in candidates:
            req = dict(base_request)
            req["type_filling"] = int(filling)
            res = mt5.order_send(req)
            if res is None:
                last_err = list(mt5.last_error())
                continue
            last_result = res
            used_filling = filling
            ret = int(getattr(res, "retcode", 0))
            if ret == RETCODE_INVALID_FILL:
                continue
            return res, used_filling, last_err

        # If every filling mode returned None (IPC drop), re-initialize once and retry
        if last_result is None and attempt_cycle == 0:
            mt5.shutdown()
            time.sleep(0.25)
            _ensure_connected_locked()
            continue
        break

    return last_result, used_filling, last_err


def _connection_supervisor():
    while True:
        try:
            with _mt5_lock:
                ok, ti, acc = _ensure_connected_locked()
                if ok and ti is not None and not getattr(ti, "trade_allowed", False):
                    _toggle_algotrading_hotkey_locked()
        except Exception as ex:
            print(f"[WineBridge Supervisor] {ex}", flush=True)
        time.sleep(5)


def handle_health():
    with _mt5_lock:
        ok, ti, acc = _ensure_connected_locked()
        err = list(mt5.last_error()) if not ok else None
    return {
        "ok": True,
        "connected": ok,
        "terminal_info": _to_json_safe(ti),
        "account_info": _to_json_safe(acc),
        "last_error": err,
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    }


def handle_initialize(payload: dict):
    with _mt5_lock:
        ok, ti, acc = _ensure_connected_locked(
            login=payload.get("login"),
            password=payload.get("password"),
            server=payload.get("server"),
        )
        err = list(mt5.last_error()) if not ok else None
    return {
        "success": ok,
        "connected": ok,
        "terminal_info": _to_json_safe(ti),
        "account_info": _to_json_safe(acc),
        "last_error": err,
    }


def handle_account():
    with _mt5_lock:
        ok, ti, acc = _ensure_connected_locked()
        if not ok or acc is None:
            return {"connected": False, "last_error": list(mt5.last_error())}
        positions = mt5.positions_get() or ()
        pos_dicts = [p._asdict() for p in positions]
    floating = sum(float(p.get("profit", 0.0)) for p in pos_dicts)
    acc_d = _to_json_safe(acc)
    acc_d["connected"] = True
    acc_d["open_positions_count"] = len(pos_dicts)
    acc_d["floating_pnl"] = round(floating, 2)
    acc_d["terminal_trade_allowed"] = bool(getattr(ti, "trade_allowed", False))
    acc_d["terminal_tradeapi_disabled"] = bool(getattr(ti, "tradeapi_disabled", True))
    return acc_d


def handle_positions(symbol: str = None):
    with _mt5_lock:
        ok, _, _ = _ensure_connected_locked()
        if not ok:
            return {"connected": False, "positions": []}
        positions = mt5.positions_get(symbol=symbol) if symbol else mt5.positions_get()
        rows = []
        for p in (positions or ()):
            d = p._asdict()
            s_info = mt5.symbol_info(d["symbol"])
            d["digits"] = int(getattr(s_info, "digits", 3 if "JPY" in d["symbol"] else 5))
            d["point"] = float(getattr(s_info, "point", 0.001 if "JPY" in d["symbol"] else 0.00001))
            rows.append(_to_json_safe(d))
    return {"connected": True, "positions": rows}


def handle_history_deals(days: int = 90, position_ticket: int = None):
    with _mt5_lock:
        ok, _, _ = _ensure_connected_locked()
        if not ok:
            return {"connected": False, "deals": []}
        if position_ticket:
            deals = mt5.history_deals_get(position=int(position_ticket))
        else:
            now_utc = datetime.datetime.now(datetime.timezone.utc)
            from_dt = now_utc - datetime.timedelta(days=int(days))
            # Broker server time can be several hours ahead of UTC; pad +1d so latest deals are never skipped
            to_dt = now_utc + datetime.timedelta(days=1)
            deals = mt5.history_deals_get(from_dt.replace(tzinfo=None), to_dt.replace(tzinfo=None))
        rows = [_to_json_safe(d) for d in (deals or ())]
    return {"connected": True, "deals": rows}


def handle_symbol_info(symbol: str):
    sym = symbol.upper()
    with _mt5_lock:
        ok, _, _ = _ensure_connected_locked()
        if not ok:
            return {"connected": False, "symbol_info": None, "tick": None}
        mt5.symbol_select(sym, True)
        s_info = mt5.symbol_info(sym)
        tick = mt5.symbol_info_tick(sym)
    info_dict = _to_json_safe(s_info)
    tick_dict = _to_json_safe(tick)
    spread_pips = None
    if tick_dict and info_dict:
        digits = int(info_dict.get("digits") or (3 if "JPY" in sym else 5))
        point = float(info_dict.get("point") or (0.001 if "JPY" in sym else 0.00001))
        pip_mult = 10.0 if digits in (3, 5) else 1.0
        bid = float(tick_dict.get("bid") or 0.0)
        ask = float(tick_dict.get("ask") or 0.0)
        if bid > 0 and ask >= bid and point > 0:
            spread_pips = round((ask - bid) / (point * pip_mult), 2)
    return {
        "connected": True,
        "symbol_info": info_dict,
        "tick": tick_dict,
        "spread_pips": spread_pips,
    }


def handle_candles(symbol: str, timeframe: str = "D1", count: int = 60):
    sym = symbol.upper()
    tf_const = TIMEFRAME_MAP.get(timeframe.upper(), mt5.TIMEFRAME_D1)
    with _mt5_lock:
        ok, _, _ = _ensure_connected_locked()
        if not ok:
            return {"connected": False, "bars": []}
        mt5.symbol_select(sym, True)
        s_info = mt5.symbol_info(sym)
        rates = mt5.copy_rates_from_pos(sym, tf_const, 0, int(count))
    bars = []
    if rates is not None and len(rates) > 0:
        for r in rates:
            bars.append({
                "time": int(r["time"]),
                "open": float(r["open"]),
                "high": float(r["high"]),
                "low": float(r["low"]),
                "close": float(r["close"]),
                "tick_volume": int(r["tick_volume"]),
            })
    return {
        "connected": True,
        "digits": int(getattr(s_info, "digits", 3 if "JPY" in sym else 5)),
        "bars": bars,
    }


def handle_all_candles_28(count: int = 60):
    rates_out = {}
    candles_out = {}
    with _mt5_lock:
        ok, _, acc = _ensure_connected_locked()
        if not ok:
            return {"connected": False, "rates": {}, "candles": {}}

    for sym in STANDARD_28_PAIRS:
        try:
            with _mt5_lock:
                mt5.symbol_select(sym, True)
                s_info = mt5.symbol_info(sym)
                dec = int(getattr(s_info, "digits", 3 if "JPY" in sym else 5))
                tick = mt5.symbol_info_tick(sym)
                if tick:
                    rates_out[sym] = round(float(tick.bid), dec)
                arr = mt5.copy_rates_from_pos(sym, mt5.TIMEFRAME_D1, 0, int(count))
            if arr is not None and len(arr) > 0:
                bars = []
                ema20 = float(arr[0]["close"])
                ema50 = float(arr[0]["close"])
                k20, k50 = 2.0 / 21.0, 2.0 / 51.0
                total_n = len(arr)
                for idx, r in enumerate(arr):
                    cl = float(r["close"])
                    ema20 = cl * k20 + ema20 * (1.0 - k20)
                    ema50 = cl * k50 + ema50 * (1.0 - k50)
                    dt = datetime.datetime.fromtimestamp(int(r["time"]), datetime.timezone.utc).strftime("%Y-%m-%d")
                    bars.append({
                        "daysAgo": total_n - 1 - idx,
                        "date": dt,
                        "open": round(float(r["open"]), dec),
                        "high": round(float(r["high"]), dec),
                        "low": round(float(r["low"]), dec),
                        "close": round(cl, dec),
                        "ema20": round(ema20, dec),
                        "ema50": round(ema50, dec),
                        "volume": int(r["tick_volume"]),
                    })
                candles_out[sym] = bars
        except Exception:
            pass
        time.sleep(0.005)  # Yield between pairs so manual order requests never wait long

    return {"connected": True, "rates": rates_out, "candles": candles_out}


def handle_order(payload: dict):
    symbol = str(payload.get("symbol", "EURUSD")).upper()
    action = str(payload.get("action", "BUY")).upper()
    is_buy = action == "BUY"
    volume = float(payload.get("volume", 0.01))
    deviation = int(payload.get("deviation", 20))
    magic = int(payload.get("magic", 786609))
    comment = str(payload.get("comment", "QuantFX-AntiHunt v7.0"))[:31]

    with _mt5_lock:
        ok, ti, acc = _ensure_connected_locked()
        if not ok:
            return {"success": False, "error": "MT5 terminal not connected in Wine", "last_error": list(mt5.last_error())}

        if ti is not None and not getattr(ti, "trade_allowed", False):
            _toggle_algotrading_hotkey_locked()
            ti = mt5.terminal_info()

        mt5.symbol_select(symbol, True)
        s_info = mt5.symbol_info(symbol)
        if s_info is None:
            return {"success": False, "error": f"Symbol {symbol} unavailable in GTC terminal", "last_error": list(mt5.last_error())}

        tick = mt5.symbol_info_tick(symbol)
        if tick is None:
            time.sleep(0.2)
            tick = mt5.symbol_info_tick(symbol)
        if tick is None:
            return {"success": False, "error": f"No live quote tick for {symbol}", "last_error": list(mt5.last_error())}

        digits = int(getattr(s_info, "digits", 3 if "JPY" in symbol else 5))
        point = float(getattr(s_info, "point", 0.001 if "JPY" in symbol else 0.00001))
        stops_level_pts = int(getattr(s_info, "trade_stops_level", 0) or 0)
        min_stop_dist = max((stops_level_pts + 5) * point, 10 * point)

        price = float(tick.ask if is_buy else tick.bid)
        sl_dist = payload.get("sl_dist")
        tp_dist = payload.get("tp_dist")

        if sl_dist is not None and float(sl_dist) > 0:
            d = max(float(sl_dist), min_stop_dist)
            sl = round(price - d if is_buy else price + d, digits)
        elif payload.get("sl") is not None and float(payload["sl"]) > 0:
            sl = round(float(payload["sl"]), digits)
        else:
            sl = 0.0

        if tp_dist is not None and float(tp_dist) > 0:
            d = max(float(tp_dist), min_stop_dist)
            tp = round(price + d if is_buy else price - d, digits)
        elif payload.get("tp") is not None and float(payload["tp"]) > 0:
            tp = round(float(payload["tp"]), digits)
        else:
            tp = 0.0

        req = {
            "action": int(mt5.TRADE_ACTION_DEAL),
            "symbol": symbol,
            "volume": float(round(volume, 2)),
            "type": int(mt5.ORDER_TYPE_BUY if is_buy else mt5.ORDER_TYPE_SELL),
            "price": float(round(price, digits)),
            "deviation": deviation,
            "magic": magic,
            "comment": comment,
            "type_time": int(mt5.ORDER_TIME_GTC),
        }
        if sl > 0:
            req["sl"] = float(sl)
        if tp > 0:
            req["tp"] = float(tp)

        res, used_filling, last_err = _send_with_filling_fallback_locked(req, s_info)
        ti_after = mt5.terminal_info()

    if res is None:
        return {
            "success": False,
            "retcode": None,
            "comment": "mt5.order_send returned None",
            "last_error": last_err,
            "used_filling": used_filling,
            "terminal_trade_allowed": bool(getattr(ti_after, "trade_allowed", False)) if ti_after else None,
            "terminal_tradeapi_disabled": bool(getattr(ti_after, "tradeapi_disabled", True)) if ti_after else None,
        }

    ret = int(getattr(res, "retcode", 0))
    fill_price = float(getattr(res, "price", 0.0) or price)
    return {
        "success": ret in (RETCODE_DONE, RETCODE_PLACED),
        "retcode": ret,
        "order": int(getattr(res, "order", 0)),
        "deal": int(getattr(res, "deal", 0)),
        "price": round(fill_price, digits),
        "sl": sl,
        "tp": tp,
        "digits": digits,
        "comment": str(getattr(res, "comment", "")),
        "used_filling": used_filling,
        "last_error": last_err,
        "terminal_trade_allowed": bool(getattr(ti_after, "trade_allowed", False)) if ti_after else None,
        "terminal_tradeapi_disabled": bool(getattr(ti_after, "tradeapi_disabled", True)) if ti_after else None,
    }


def handle_close(payload: dict):
    ticket = int(payload.get("ticket", 0))
    fraction = float(payload.get("fraction", 1.0))
    comment = str(payload.get("comment", "QuantFX-Close"))[:31]
    magic = int(payload.get("magic", 786609))

    with _mt5_lock:
        ok, ti, _ = _ensure_connected_locked()
        if not ok:
            return {"success": False, "error": "MT5 terminal not connected in Wine"}

        positions = mt5.positions_get(ticket=ticket) if ticket > 0 else mt5.positions_get()
        if not positions:
            return {"success": False, "error": f"Position #{ticket} not found on broker"}

        pos = positions[0]._asdict()
        pos_ticket = int(pos["ticket"])
        symbol = pos["symbol"]
        is_buy = int(pos["type"]) == 0
        vol = round(float(pos["volume"]) * fraction, 2)
        if vol < 0.01:
            return {"success": False, "error": f"Close volume {vol} below minimum 0.01"}

        mt5.symbol_select(symbol, True)
        s_info = mt5.symbol_info(symbol)
        tick = mt5.symbol_info_tick(symbol)
        if tick is None:
            return {"success": False, "error": f"No live quote tick for {symbol}"}
        close_price = float(tick.bid if is_buy else tick.ask)

        req = {
            "action": int(mt5.TRADE_ACTION_DEAL),
            "position": pos_ticket,
            "symbol": symbol,
            "volume": float(vol),
            "type": int(mt5.ORDER_TYPE_SELL if is_buy else mt5.ORDER_TYPE_BUY),
            "price": close_price,
            "deviation": 20,
            "magic": magic,
            "comment": comment,
            "type_time": int(mt5.ORDER_TIME_GTC),
        }
        res, used_filling, last_err = _send_with_filling_fallback_locked(req, s_info)

    if res is None:
        return {"success": False, "error": f"Close order returned None (last_error={last_err})"}
    ret = int(getattr(res, "retcode", 0))
    return {
        "success": ret in (RETCODE_DONE, RETCODE_PLACED),
        "retcode": ret,
        "ticket": pos_ticket,
        "close_price": float(getattr(res, "price", 0.0) or close_price),
        "comment": str(getattr(res, "comment", "")),
        "position": _to_json_safe(pos),
    }


def handle_modify_sltp(payload: dict):
    ticket = int(payload.get("ticket", 0))
    symbol = str(payload.get("symbol", "")).upper()
    sl = float(payload.get("sl", 0.0))
    tp = float(payload.get("tp", 0.0))

    with _mt5_lock:
        ok, _, _ = _ensure_connected_locked()
        if not ok:
            return {"success": False, "error": "MT5 terminal not connected in Wine"}
        req = {
            "action": int(mt5.TRADE_ACTION_SLTP),
            "position": ticket,
            "symbol": symbol,
            "sl": sl,
            "tp": tp,
        }
        res = mt5.order_send(req)
        err = list(mt5.last_error()) if res is None else None
    if res is None:
        return {"success": False, "error": f"SLTP modify returned None (last_error={err})"}
    ret = int(getattr(res, "retcode", 0))
    return {"success": ret in (RETCODE_DONE, RETCODE_PLACED), "retcode": ret, "comment": str(getattr(res, "comment", ""))}


class BridgeHandler(BaseHTTPRequestHandler):
    def _send_json(self, data: dict, status: int = 200):
        try:
            body = json.dumps(data, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (ConnectionResetError, BrokenPipeError, ConnectionAbortedError, OSError):
            pass

    def finish(self):
        try:
            super().finish()
        except (ConnectionResetError, BrokenPipeError, ConnectionAbortedError, OSError):
            pass

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        q = parse_qs(parsed.query)
        try:
            if path == "/health":
                self._send_json(handle_health())
            elif path == "/account":
                self._send_json(handle_account())
            elif path == "/positions":
                sym = q.get("symbol", [None])[0]
                self._send_json(handle_positions(sym))
            elif path == "/history_deals":
                days = int(q.get("days", [90])[0])
                pos_t = q.get("position", [None])[0]
                self._send_json(handle_history_deals(days=days, position_ticket=int(pos_t) if pos_t else None))
            elif path == "/symbol_info":
                sym = q.get("symbol", ["EURUSD"])[0]
                self._send_json(handle_symbol_info(sym))
            elif path == "/candles":
                sym = q.get("symbol", ["EURUSD"])[0]
                tf = q.get("timeframe", ["D1"])[0]
                cnt = int(q.get("count", [60])[0])
                self._send_json(handle_candles(sym, tf, cnt))
            elif path == "/all_candles_28":
                cnt = int(q.get("count", [60])[0])
                self._send_json(handle_all_candles_28(cnt))
            else:
                self._send_json({"error": "Not found"}, status=404)
        except Exception as ex:
            self._send_json({"success": False, "error": str(ex)}, status=500)

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            payload = json.loads(raw) if raw.strip() else {}
        except Exception:
            payload = {}

        try:
            if path == "/initialize":
                self._send_json(handle_initialize(payload))
            elif path == "/order":
                self._send_json(handle_order(payload))
            elif path == "/close":
                self._send_json(handle_close(payload))
            elif path == "/modify_sltp":
                self._send_json(handle_modify_sltp(payload))
            else:
                self._send_json({"error": "Not found"}, status=404)
        except Exception as ex:
            self._send_json({"success": False, "error": str(ex)}, status=500)

    def log_message(self, format, *args):
        return


def main():
    global PORT
    if "--port" in sys.argv:
        idx = sys.argv.index("--port")
        if idx + 1 < len(sys.argv):
            PORT = int(sys.argv[idx + 1])

    with _mt5_lock:
        ok, ti, acc = _ensure_connected_locked()
        print(
            f"[WineBridge] Initial attach ok={ok} login={getattr(acc, 'login', None)} "
            f"trade_allowed={getattr(ti, 'trade_allowed', None)} "
            f"tradeapi_disabled={getattr(ti, 'tradeapi_disabled', None)}",
            flush=True,
        )

    threading.Thread(target=_connection_supervisor, daemon=True).start()

    class QuietThreadingHTTPServer(ThreadingHTTPServer):
        def handle_error(self, request, client_address):
            ex_type, _, _ = sys.exc_info()
            if ex_type in (ConnectionResetError, BrokenPipeError, ConnectionAbortedError):
                return
            super().handle_error(request, client_address)

    server = QuietThreadingHTTPServer((HOST, PORT), BridgeHandler)
    server.daemon_threads = True
    print(f"[WineBridge] Native Wine MT5 HTTP JSON Bridge listening on http://{HOST}:{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

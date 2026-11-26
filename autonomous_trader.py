#!/usr/bin/env python3
"""
Autonomous 7-Gate Institutional Auto-Trader & Market Structure Engine
=====================================================================
Runs unattended inside the Railway Cloud MT5 Daemon, executing the exact
walk-forward verified Dual Institutional Strategy (2020-2026 OOS PF=1.66):
  - Edge A: Shock-Safe Overextension Exhaustion Reversal (RSI <= 30 / >= 70 + >1.4 ATR stretch + CB shock guard)
  - Edge B: Macro Carry & 6-Pillar Divergence Value Pullback (Live Java Macro Delta + Live CB Rates + D1 EMA200 Pullback)
And maintains live Daily/H4 Market Structure (BOS/CHoCH) inside Java H2 for Gate 5.
"""

import os
import json
import math
import time
import datetime
import requests
from typing import Dict, List, Optional, Tuple

PAIRS_28 = [
    "EURUSD", "GBPUSD", "AUDUSD", "NZDUSD", "USDCAD", "USDCHF", "USDJPY",
    "EURGBP", "EURAUD", "EURNZD", "EURCAD", "EURCHF", "EURJPY",
    "GBPAUD", "GBPNZD", "GBPCAD", "GBPCHF", "GBPJPY",
    "AUDNZD", "AUDCAD", "AUDCHF", "AUDJPY",
    "NZDCAD", "NZDCHF", "NZDJPY",
    "CADCHF", "CADJPY", "CHFJPY",
]

CURRENCIES = ["USD", "EUR", "GBP", "AUD", "NZD", "CAD", "CHF", "JPY"]

TYPICAL_SPREAD_PIPS = {
    "EURUSD": 1.1, "USDJPY": 1.3, "GBPUSD": 1.4, "USDCHF": 1.5,
    "USDCAD": 1.6, "AUDUSD": 1.3, "NZDUSD": 1.7, "EURGBP": 1.5,
    "EURJPY": 1.8, "EURCHF": 1.8, "EURCAD": 2.2, "EURAUD": 2.3,
    "EURNZD": 2.8, "GBPJPY": 2.4, "GBPCHF": 2.5, "GBPCAD": 2.6,
    "GBPAUD": 2.8, "GBPNZD": 3.4, "AUDJPY": 1.9, "AUDCAD": 2.2,
    "AUDCHF": 2.3, "AUDNZD": 2.3, "NZDJPY": 2.4, "NZDCAD": 2.6,
    "NZDCHF": 2.5, "CADJPY": 2.1, "CADCHF": 2.2, "CHFJPY": 2.3,
}

# Fallback historical 6-month policy baseline (used to measure recent CB rate cycle changes)
CB_RATE_6M_BASELINE = {
    "USD": 3.75, "EUR": 2.00, "GBP": 4.00, "JPY": 0.75,
    "AUD": 3.60, "CAD": 2.25, "CHF": 0.00, "NZD": 2.25,
}


def pip_size(pair: str) -> float:
    return 0.01 if "JPY" in pair.upper() else 0.0001


def compute_pure_indicators(bars: List[dict]) -> Optional[dict]:
    """Pure Python EMA20/50/200, ATR14, Wilder RSI14, Z20 & 20-bar swings (no NumPy required)."""
    if not bars or len(bars) < 55:
        return None
    n = len(bars)
    closes = [float(b["close"]) for b in bars]
    opens = [float(b["open"]) for b in bars]
    highs = [float(b["high"]) for b in bars]
    lows = [float(b["low"]) for b in bars]

    def ema_series(vals: List[float], period: int) -> List[float]:
        k = 2.0 / (period + 1.0)
        out = [vals[0]]
        for v in vals[1:]:
            out.append(v * k + out[-1] * (1.0 - k))
        return out

    ema20 = ema_series(closes, 20)
    ema50 = ema_series(closes, 50)
    ema200 = ema_series(closes, 200) if n >= 200 else ema_series(closes, min(n, 100))

    # True Range & ATR(14) SMA
    trs = [highs[0] - lows[0]]
    for i in range(1, n):
        trs.append(max(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1])))
    atr14 = []
    for i in range(n):
        start = max(0, i - 13)
        window = trs[start:i + 1]
        atr14.append(sum(window) / len(window))

    # Wilder RSI(14)
    rsi14 = [50.0] * n
    if n >= 15:
        alpha = 1.0 / 14.0
        avg_g, avg_l = 0.0, 0.0
        for i in range(1, n):
            chg = closes[i] - closes[i - 1]
            g = chg if chg > 0 else 0.0
            l = -chg if chg < 0 else 0.0
            avg_g = (1.0 - alpha) * avg_g + alpha * g
            avg_l = (1.0 - alpha) * avg_l + alpha * l
            if avg_l <= 1e-12:
                rsi14[i] = 100.0 if avg_g > 0 else 50.0
            else:
                rs = avg_g / avg_l
                rsi14[i] = 100.0 - (100.0 / (1.0 + rs))

    # Z20 against EMA20 (sample std ddof=1 matching pandas rolling.std)
    z20 = [0.0] * n
    for i in range(19, n):
        win = closes[i - 19:i + 1]
        mean = sum(win) / 20.0
        var = sum((x - mean) ** 2 for x in win) / 19.0
        std = math.sqrt(var)
        z20[i] = (closes[i] - ema20[i]) / std if std > 1e-12 else 0.0

    return {
        "bars": bars,
        "opens": opens,
        "highs": highs,
        "lows": lows,
        "closes": closes,
        "ema20": ema20,
        "ema50": ema50,
        "ema200": ema200,
        "atr14": atr14,
        "rsi14": rsi14,
        "z20": z20,
    }


def compute_structure_record(pair: str, ind: dict, timeframe: str = "DAILY") -> dict:
    """Computes institutional trend & BOS/CHoCH structure record for Java Gate 5."""
    closes = ind["closes"]
    highs = ind["highs"]
    lows = ind["lows"]
    ema20 = ind["ema20"]
    ema50 = ind["ema50"]

    idx = len(closes) - 2 if len(closes) >= 25 else len(closes) - 1
    spot = closes[-1]
    prev_hi20 = max(highs[max(0, idx - 20):idx]) if idx > 1 else highs[idx]
    prev_lo20 = min(lows[max(0, idx - 20):idx]) if idx > 1 else lows[idx]
    sw_hi = max(highs[max(0, idx - 20):idx + 1])
    sw_lo = min(lows[max(0, idx - 20):idx + 1])

    c = closes[idx]
    e20 = ema20[idx]
    e50 = ema50[idx]

    if e20 > e50 and spot >= e50:
        trend = "BULLISH"
    elif e20 < e50 and spot <= e50:
        trend = "BEARISH"
    else:
        trend = "RANGE"

    if c > prev_hi20 or spot > prev_hi20:
        last_ev = "BOS" if trend == "BULLISH" else "CHoCH"
    elif c < prev_lo20 or spot < prev_lo20:
        last_ev = "BOS" if trend == "BEARISH" else "CHoCH"
    else:
        last_ev = "SWING_HIGH" if spot >= e20 else "SWING_LOW"

    dec = 3 if "JPY" in pair else 5
    return {
        "pair": pair,
        "timeframe": timeframe,
        "trend": trend,
        "lastStructureEvent": last_ev,
        "keySupportLevel": round(sw_lo, dec),
        "keyResistanceLevel": round(sw_hi, dec),
        "currentPrice": round(spot, dec),
    }


def compute_live_cs_flow_scores(candles_map: Dict[str, List[dict]]) -> Dict[str, float]:
    """Cross-sectional 20D/60D/120D currency factor scores [-5, +5] from closed MT5 D1 bars."""
    raw = {c: [] for c in CURRENCIES}
    valid_pairs = 0
    for pair, bars in candles_map.items():
        if len(bars) < 65:
            continue
        # Use closed bar [-2] cleanly
        idx = len(bars) - 2
        c_now = float(bars[idx]["close"])
        c20 = float(bars[max(0, idx - 20)]["close"])
        c60 = float(bars[max(0, idx - 60)]["close"])
        c120 = float(bars[max(0, idx - 120)]["close"])
        if min(c_now, c20, c60, c120) <= 0:
            continue
        blended = (
            0.35 * math.log(c_now / c20)
            + 0.40 * math.log(c_now / c60)
            + 0.25 * math.log(c_now / c120)
        )
        base, quote = pair[:3], pair[3:]
        if base in raw:
            raw[base].append(blended)
        if quote in raw:
            raw[quote].append(-blended)
        valid_pairs += 1

    if valid_pairs < 14:
        return {c: 0.0 for c in CURRENCIES}

    ccy_means = {c: (sum(v) / len(v) if v else 0.0) for c, v in raw.items()}
    vals = list(ccy_means.values())
    mean = sum(vals) / len(vals)
    var = sum((v - mean) ** 2 for v in vals) / max(len(vals), 1)
    std = math.sqrt(var)
    if std <= 1e-9:
        return {c: 0.0 for c in CURRENCIES}
    return {
        c: max(-5.0, min(5.0, ((ccy_means[c] - mean) / std) * 2.0))
        for c in CURRENCIES
    }


def is_fx_trading_window_open(now_utc: Optional[datetime.datetime] = None) -> Tuple[bool, str]:
    """Guards against weekend market closed (10018) and daily GTC rollover spread spikes."""
    dt = now_utc or datetime.datetime.now(datetime.timezone.utc)
    wd = dt.weekday()  # 0=Mon .. 4=Fri, 5=Sat, 6=Sun
    hm = dt.hour * 60 + dt.minute

    if wd == 5:
        return False, "Weekend (Saturday Market Closed)"
    if wd == 4 and hm >= 20 * 60 + 55:
        return False, "Friday Late Close Window (>= 20:55 UTC)"
    if wd == 6 and hm < 22 * 60 + 15:
        return False, "Sunday Pre-Open Window (< 22:15 UTC)"
    if hm >= 21 * 60 + 50 or hm < 0 * 60 + 25:
        return False, "Daily Rollover Spread Widening Window (21:50-00:25 UTC)"
    return True, "OK"


class AutonomousTrader:
    def __init__(self, engine, logs_dir: str, java_base_url: str = "http://127.0.0.1:8080"):
        self.engine = engine
        self.logs_dir = logs_dir
        self.java_base_url = java_base_url.rstrip("/")
        self.state_file = os.path.join(logs_dir, "autopilot_state.json")
        self.state = self._load_state()
        self.last_eval_ts = 0.0
        self.last_structure_sync_ts = 0.0
        self.last_status = {
            "enabled": True,
            "last_scan_utc": None,
            "market_window": "INIT",
            "active_blocks": [],
            "last_candidates": [],
            "last_action": "Initialized",
            "trades_opened_total": int(self.state.get("trades_opened_total", 0)),
        }

    def _load_state(self) -> dict:
        default = {
            "traded_bars": {},
            "cooldown_until": {},
            "position_meta": {},
            "daily_anchor": {"date": None, "start_equity": None},
            "high_water_equity": None,
            "trades_opened_total": 0,
            "recent_decisions": [],
        }
        if os.path.exists(self.state_file):
            try:
                with open(self.state_file, "r", encoding="utf-8") as f:
                    loaded = json.load(f)
                if isinstance(loaded, dict):
                    default.update(loaded)
            except Exception:
                pass
        return default

    def _save_state(self):
        try:
            os.makedirs(self.logs_dir, exist_ok=True)
            with open(self.state_file, "w", encoding="utf-8") as f:
                json.dump(self.state, f, indent=2)
        except Exception as ex:
            print(f"[Autopilot State Save Error] {ex}", flush=True)

    def _record_decision(self, text: str):
        ts = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
        hist = self.state.setdefault("recent_decisions", [])
        hist.append({"time": ts, "note": text})
        self.state["recent_decisions"] = hist[-40:]
        self.last_status["last_action"] = text

    def sync_market_structure(self, candles_map: Dict[str, List[dict]]) -> int:
        """Computes live D1 Market Structure (BOS/CHoCH/Swings) and batch-updates Java H2."""
        payload = []
        for pair, bars in candles_map.items():
            ind = compute_pure_indicators(bars)
            if ind:
                payload.append(compute_structure_record(pair, ind, "DAILY"))
        if not payload:
            return 0
        try:
            r = requests.post(
                f"{self.java_base_url}/api/v1/market-structure/batch",
                json=payload,
                timeout=8.0,
            )
            if r.status_code == 200:
                self.last_structure_sync_ts = time.time()
                return len(payload)
        except Exception as ex:
            print(f"[MarketStructure Sync Notice] {ex}", flush=True)
        return 0

    def _fetch_java_context(self) -> Tuple[dict, dict, set, dict]:
        """Returns (pair_deltas, cb_rates, news_locked_ccys, calendar_locks)."""
        pair_deltas = {}
        cb_rates = dict(CB_RATE_6M_BASELINE)
        news_locked_ccys = set()
        signal_map = {}

        try:
            r_sc = requests.get(f"{self.java_base_url}/api/v1/quant/scores", timeout=5.0)
            if r_sc.status_code == 200:
                for row in (r_sc.json() or []):
                    ccy = str(row.get("currency", "")).upper()
                    if ccy in CURRENCIES and row.get("actualInterestRate") is not None:
                        cb_rates[ccy] = float(row["actualInterestRate"])
        except Exception:
            pass

        try:
            r_bs = requests.get(f"{self.java_base_url}/api/v1/bootstrap", timeout=6.0)
            if r_bs.status_code == 200:
                bs = r_bs.json() or {}
                for sig in (bs.get("pairSignals") or []):
                    pair = str(sig.get("pair", "")).upper()
                    if pair:
                        pair_deltas[pair] = float(sig.get("deltaScore", 0.0))
                        signal_map[pair] = sig
                        gates = sig.get("gates") or {}
                        if sig.get("isNewsLocked") or str(gates.get("gate4NewsStatus", "")).upper() == "NEWS_LOCKED":
                            news_locked_ccys.add(pair[:3])
                            news_locked_ccys.add(pair[3:])

                now_utc = datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
                for ev in (bs.get("economicCalendar") or []):
                    if str(ev.get("impact", "")).upper() != "HIGH":
                        continue
                    ccy = str(ev.get("currency", "")).upper()
                    ts_str = ev.get("exactEventTimestampUtc")
                    if ccy in CURRENCIES and ts_str:
                        try:
                            ev_dt = datetime.datetime.fromisoformat(str(ts_str).replace("Z", "")[:19])
                            diff_min = (ev_dt - now_utc).total_seconds() / 60.0
                            if -45.0 <= diff_min <= 45.0:
                                news_locked_ccys.add(ccy)
                        except Exception:
                            pass
        except Exception:
            pass

        return pair_deltas, cb_rates, news_locked_ccys, signal_map

    def _compute_setup_signal(
        self,
        pair: str,
        ind: dict,
        java_delta: Optional[float],
        flow_scores: Dict[str, float],
        cb_rates: Dict[str, float],
    ) -> Optional[dict]:
        """Evaluates exact verified Dual Institutional Strategy on last completed D1 bar ([-2])."""
        bars = ind["bars"]
        if len(bars) < 60:
            return None
        i = len(bars) - 2  # Strictly closed prior Daily bar (non-repainting)
        bar_date = str(bars[i].get("date") or bars[i].get("time") or "")[:10]

        # Freshness check: completed bar must be recent (within 5 days for weekends/holidays)
        try:
            bar_dt = datetime.datetime.strptime(bar_date, "%Y-%m-%d").date()
            if (datetime.datetime.now(datetime.timezone.utc).date() - bar_dt).days > 5:
                return None
        except Exception:
            pass

        c = ind["closes"][i]
        o = ind["opens"][i]
        pc = ind["closes"][i - 1]
        e50 = ind["ema50"][i]
        e200 = ind["ema200"][i]
        atr = ind["atr14"][i]
        rsi = ind["rsi14"][i]
        prsi = ind["rsi14"][i - 1]
        z = ind["z20"][i]
        spot = ind["closes"][-1]
        if atr <= 0:
            return None

        b, q = pair[:3], pair[3:]
        flow_delta = (flow_scores.get(b, 0.0) - flow_scores.get(q, 0.0))
        delta = (0.5 * java_delta + 0.5 * flow_delta) if java_delta is not None else flow_delta
        carry = cb_rates.get(b, 0.0) - cb_rates.get(q, 0.0)
        chg_diff = (cb_rates.get(b, 0.0) - CB_RATE_6M_BASELINE.get(b, cb_rates.get(b, 0.0))) - (
            cb_rates.get(q, 0.0) - CB_RATE_6M_BASELINE.get(q, cb_rates.get(q, 0.0))
        )

        action, setup, conviction = None, None, 0.0

        # Edge A: Shock-Safe Exhaustion Fade
        if rsi <= 30.0 and c > o and (e50 - c) > 1.4 * atr and chg_diff > -1.0 and delta > -3.0:
            action, setup, conviction = "BUY", "ShockSafeMeanRev", 2.0 + (e50 - c) / atr
        elif rsi >= 70.0 and c < o and (c - e50) > 1.4 * atr and chg_diff < 1.0 and delta < 3.0:
            action, setup, conviction = "SELL", "ShockSafeMeanRev", 2.0 + (c - e50) / atr

        # Edge B: Macro Carry & 6-Pillar Divergence Value Pullback
        elif (carry >= 1.25 and delta >= 1.5 and chg_diff >= -0.25) and c > e200 and z <= -0.75 and rsi <= 48.0 and c > o and c > pc and rsi > prsi:
            action, setup, conviction = "BUY", "MacroCarryPullback", 1.5 + abs(delta) * 0.4 + carry * 0.3
        elif (carry <= -1.25 and delta <= -1.5 and chg_diff <= 0.25) and c < e200 and z >= 0.75 and rsi >= 52.0 and c < o and c < pc and rsi < prsi:
            action, setup, conviction = "SELL", "MacroCarryPullback", 1.5 + abs(delta) * 0.4 - carry * 0.3

        if not action:
            return None

        # Anti-Chase Guard: reject if spot already ran away > 0.55 * ATR past completed bar close
        drift = (spot - c) if action == "BUY" else (c - spot)
        if drift > 0.55 * atr:
            return None

        return {
            "pair": pair,
            "action": action,
            "setup": setup,
            "conviction": round(conviction, 3),
            "bar_date": bar_date,
            "atr14": atr,
            "delta": round(delta, 2),
            "carry": round(carry, 2),
            "rsi14": round(rsi, 1),
            "z20": round(z, 2),
        }

    def _sync_closed_cooldowns(self, open_positions: List[dict]):
        """Tracks closed broker deals so losing trades enforce a 5-day per-pair cooldown."""
        if not self.engine.use_wine_bridge:
            return
        from mt5_cockpit_daemon import wine_bridge_get
        resp = wine_bridge_get("/history_deals", {"days": 7}, timeout=4.0)
        deals = (resp or {}).get("deals") or []
        open_tickets = {int(p["ticket"]) for p in open_positions}
        today_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
        today_date = datetime.datetime.now(datetime.timezone.utc).date()

        # Check closed deals for net loss & mark cooldown
        by_pos = {}
        for d in deals:
            pid = int(d.get("position_id") or 0)
            if pid <= 0 or pid in open_tickets:
                continue
            if int(d.get("entry", 0)) != 1:  # 1 == DEAL_ENTRY_OUT
                continue
            sym = str(d.get("symbol", "")).upper()
            net = float(d.get("profit", 0.0)) + float(d.get("commission", 0.0)) + float(d.get("swap", 0.0))
            t_sec = int(d.get("time", 0))
            by_pos.setdefault(pid, {"sym": sym, "net": 0.0, "time": t_sec})
            by_pos[pid]["net"] += net
            by_pos[pid]["time"] = max(by_pos[pid]["time"], t_sec)

        for pid, info in by_pos.items():
            sym = info["sym"]
            if sym in PAIRS_28 and info["net"] < -0.10 and info["time"] > 0:
                deal_dt = datetime.datetime.fromtimestamp(info["time"], datetime.timezone.utc).date()
                until_dt = deal_dt + datetime.timedelta(days=5)
                if until_dt >= today_date:
                    cur_until = self.state["cooldown_until"].get(sym, "")
                    if until_dt.strftime("%Y-%m-%d") > cur_until:
                        self.state["cooldown_until"][sym] = until_dt.strftime("%Y-%m-%d")

        # Prune closed position_meta
        for tk in list(self.state.get("position_meta", {}).keys()):
            try:
                if int(tk) not in open_tickets:
                    self.state["position_meta"].pop(tk, None)
            except Exception:
                pass

    def evaluate_and_trade_once(self, force: bool = False) -> dict:
        """Main single-pass autonomous evaluation & execution step (called every ~60s)."""
        now_utc = datetime.datetime.now(datetime.timezone.utc)
        now_str = now_utc.strftime("%Y-%m-%d %H:%M:%S UTC")
        today_str = now_utc.strftime("%Y-%m-%d")
        self.last_status["last_scan_utc"] = now_str
        self.last_status["enabled"] = bool(self.engine.autopilot_enabled)

        if not self.engine.autopilot_enabled:
            self.last_status["market_window"] = "PAUSED_BY_USER"
            return self.last_status

        if not force and (time.time() - self.last_eval_ts) < 55.0:
            return self.last_status
        self.last_eval_ts = time.time()

        blocks = []
        win_ok, win_reason = is_fx_trading_window_open(now_utc)
        self.last_status["market_window"] = win_reason
        if not win_ok:
            blocks.append(win_reason)
            self.last_status["active_blocks"] = blocks
            return self.last_status

        # Gate 1: Terminal & Trade Permission check
        acc = self.engine.get_account_summary()
        if not acc.get("connected") or not acc.get("terminal_trade_allowed", True) or acc.get("terminal_tradeapi_disabled", False):
            blocks.append(f"Terminal not ready (connected={acc.get('connected')}, trade_allowed={acc.get('terminal_trade_allowed')})")
            self.last_status["active_blocks"] = blocks
            return self.last_status

        balance = float(acc.get("balance", 0.0))
        equity = float(acc.get("equity", balance))
        margin = float(acc.get("margin", 0.0))
        margin_free = float(acc.get("margin_free", equity))
        margin_level = float(acc.get("margin_level", 9999.0)) if margin > 0 else 9999.0

        # Gate 4 (Account Vault): Daily Loss Kill-Switch & Max Drawdown Floor
        anchor = self.state.setdefault("daily_anchor", {})
        if anchor.get("date") != today_str or not anchor.get("start_equity"):
            anchor["date"] = today_str
            anchor["start_equity"] = max(balance, equity, 1.0)
        start_eq = float(anchor["start_equity"])
        hw = float(self.state.get("high_water_equity") or max(balance, equity, 1.0))
        if equity > hw:
            hw = equity
        self.state["high_water_equity"] = hw

        daily_loss_limit_pct = 5.0 if start_eq < 500.0 else 2.5
        daily_dd_pct = ((start_eq - equity) / start_eq) * 100.0 if start_eq > 0 else 0.0
        total_dd_limit_pct = 18.0 if hw < 500.0 else 8.0
        total_dd_pct = ((hw - equity) / hw) * 100.0 if hw > 0 else 0.0

        if daily_dd_pct >= daily_loss_limit_pct:
            blocks.append(f"Daily Loss Kill-Switch Active ({daily_dd_pct:.2f}% >= {daily_loss_limit_pct:.1f}%)")
        if total_dd_pct >= total_dd_limit_pct:
            blocks.append(f"Max Drawdown Vault Active ({total_dd_pct:.2f}% >= {total_dd_limit_pct:.1f}%)")
        if margin_free < max(15.0, balance * 0.25) or (margin > 0 and margin_level < 250.0):
            blocks.append(f"Insufficient Free Margin (${margin_free:.2f}, Level={margin_level:.0f}%)")

        positions = self.engine.get_live_positions()
        self._sync_closed_cooldowns(positions)

        max_concurrent = 2 if balance < 500.0 else 4
        if len(positions) >= max_concurrent:
            blocks.append(f"Max Concurrent Positions Reached ({len(positions)}/{max_concurrent})")

        if blocks:
            self.last_status["active_blocks"] = blocks
            self._save_state()
            return self.last_status

        # Fetch live 250-bar D1 candles + live Java Macro Context
        candles_map = {}
        try:
            with open(os.path.join(self.logs_dir, "mt5_candles.json"), "r", encoding="utf-8") as f:
                cjson = json.load(f)
            candles_map = cjson.get("candles") or {}
        except Exception:
            pass
        if len(candles_map) < 20 or len(next(iter(candles_map.values()), [])) < 210:
            synced = self.engine.sync_all_28_pairs_candles(250)
            scandles = (synced or {}).get("candles") or {}
            if len(scandles) >= len(candles_map) and scandles:
                candles_map = scandles

        java_deltas, cb_rates, news_locked_ccys, _ = self._fetch_java_context()
        flow_scores = compute_live_cs_flow_scores(candles_map)

        # Correlation / Single-Currency Net Exposure Check
        open_pairs = {str(p.get("symbol", "")).upper() for p in positions}
        ccy_exp = {}
        for p in positions:
            sym = str(p.get("symbol", "")).upper()
            if len(sym) == 6:
                d = 1 if int(p.get("type", 0)) == 0 else -1
                ccy_exp[sym[:3]] = ccy_exp.get(sym[:3], 0) + d
                ccy_exp[sym[3:]] = ccy_exp.get(sym[3:], 0) - d

        candidates = []
        for pair in PAIRS_28:
            bars = candles_map.get(pair)
            if not bars:
                continue
            ind = compute_pure_indicators(bars)
            if not ind:
                continue
            sig = self._compute_setup_signal(pair, ind, java_deltas.get(pair), flow_scores, cb_rates)
            if sig:
                candidates.append(sig)

        candidates.sort(key=lambda x: x["conviction"], reverse=True)
        self.last_status["last_candidates"] = candidates[:6]

        from mt5_cockpit_daemon import wine_bridge_get
        for cand in candidates:
            if len(positions) >= max_concurrent:
                break
            pair = cand["pair"]
            action = cand["action"]
            bar_date = cand["bar_date"]
            b, q = pair[:3], pair[3:]

            if pair in open_pairs:
                continue
            if self.state["traded_bars"].get(pair) == bar_date:
                continue
            if self.state["cooldown_until"].get(pair, "") > today_str:
                continue
            if b in news_locked_ccys or q in news_locked_ccys:
                self._record_decision(f"Skipped {pair} {action}: High-Impact News Lock on {b}/{q}")
                continue
            d_sign = 1 if action == "BUY" else -1
            if abs(ccy_exp.get(b, 0) + d_sign) > 1 or abs(ccy_exp.get(q, 0) - d_sign) > 1:
                self._record_decision(f"Skipped {pair} {action}: Correlation Cap on {b}/{q}")
                continue

            # Live Broker Symbol Check (Spread & Exact Tick Value)
            ps = pip_size(pair)
            spread_pips = TYPICAL_SPREAD_PIPS.get(pair, 2.0)
            pip_val_per_lot = 10.0 if q == "USD" else 7.5
            vol_min, vol_max, vol_step = 0.01, 10.0, 0.01
            min_stop_pips = 5.0

            if self.engine.use_wine_bridge and self.engine.wine_bridge_ok:
                si = wine_bridge_get("/symbol_info", {"symbol": pair}, timeout=4.0)
                if not si or not si.get("connected") or not si.get("symbol_info"):
                    continue
                info = si["symbol_info"]
                tick = si.get("tick") or {}
                digits = int(info.get("digits") or (3 if "JPY" in pair else 5))
                point = float(info.get("point") or (0.001 if "JPY" in pair else 0.00001))
                pip_unit = point * (10 if digits in (3, 5) else 1)
                ps = pip_unit
                if si.get("spread_pips") is not None:
                    spread_pips = float(si["spread_pips"])
                tick_val = float(info.get("trade_tick_value") or 0.0)
                tick_sz = float(info.get("trade_tick_size") or point or 1e-5)
                if tick_val > 0 and tick_sz > 0:
                    pip_val_per_lot = tick_val * (pip_unit / tick_sz)
                vol_min = float(info.get("volume_min") or 0.01)
                vol_max = float(info.get("volume_max") or 10.0)
                vol_step = float(info.get("volume_step") or 0.01)
                min_stop_pips = (float(info.get("trade_stops_level") or 0.0) * point) / pip_unit
                if float(tick.get("bid") or 0.0) <= 0:
                    continue

            max_allowed_spread = max(TYPICAL_SPREAD_PIPS.get(pair, 2.0) * 2.2, 3.8)
            if spread_pips > max_allowed_spread:
                self._record_decision(f"Skipped {pair} {action}: Wide Spread {spread_pips:.1f}p > {max_allowed_spread:.1f}p")
                continue

            atr_pips = cand["atr14"] / ps
            sl_pips = round(max(atr_pips * 1.8, 25.0, min_stop_pips + 3.0), 1)
            tp_pips = round(sl_pips * (2.8 / 1.8), 1)

            if balance < 500.0:
                lots = vol_min
                risk_usd = sl_pips * pip_val_per_lot * lots
                budget_cap = max(13.8, equity * 0.14)
                if risk_usd > budget_cap:
                    self._record_decision(
                        f"Skipped {pair} {action} ({cand['setup']}): 0.01L risk ${risk_usd:.2f} > ${budget_cap:.2f} safety cap"
                    )
                    continue
            else:
                target_risk = equity * 0.015
                raw_lots = target_risk / max(sl_pips * pip_val_per_lot, 1e-6)
                steps = max(1, int(round(raw_lots / vol_step)))
                lots = max(vol_min, min(vol_max, round(steps * vol_step, 2)))

            res = self.engine.execute_order(pair, action, volume=lots, sl_pips=sl_pips, tp_pips=tp_pips)
            if res and res.get("success"):
                ticket = int(res.get("ticket") or 0)
                self.state["traded_bars"][pair] = bar_date
                self.state["trades_opened_total"] = int(self.state.get("trades_opened_total", 0)) + 1
                self.last_status["trades_opened_total"] = self.state["trades_opened_total"]
                if ticket > 0:
                    self.state["position_meta"][str(ticket)] = {
                        "pair": pair,
                        "direction": action,
                        "setup": cand["setup"],
                        "entry_price": float(res.get("price") or 0.0),
                        "sl_pips": sl_pips,
                        "tp_pips": tp_pips,
                        "atr_pips": round(atr_pips, 1),
                        "be_locked": False,
                        "opened_at": now_str,
                    }
                msg = (
                    f"AUTOPILOT ENTERED {action} {lots} {pair} #{ticket} @ {res.get('price')} "
                    f"[{cand['setup']} | SL={sl_pips}p TP={tp_pips}p | d={cand['delta']}]"
                )
                self.engine.log_event(f"🤖 {msg}")
                self._record_decision(msg)
                self._save_state()

                # Sync to Java ActiveTradeEntity cleanly
                try:
                    requests.post(
                        f"{self.java_base_url}/api/v1/active-trades",
                        json={
                            "pair": pair,
                            "direction": "LONG" if action == "BUY" else "SHORT",
                            "entryPrice": float(res.get("price") or 0.0),
                            "stopLoss": float(res.get("sl") or 0.0),
                            "takeProfit": float(res.get("tp") or 0.0),
                            "positionSizeLots": lots,
                            "status": "OPEN",
                            "notes": f"Autopilot #{ticket} ({cand['setup']})",
                        },
                        timeout=4.0,
                    )
                except Exception:
                    pass

                open_pairs.add(pair)
                ccy_exp[b] = ccy_exp.get(b, 0) + d_sign
                ccy_exp[q] = ccy_exp.get(q, 0) - d_sign
                positions.append({"symbol": pair, "type": 0 if action == "BUY" else 1, "ticket": ticket})
            else:
                err_detail = (res or {}).get("detail") or (res or {}).get("error") or "Unknown order failure"
                self._record_decision(f"Order Failed {pair} {action}: {err_detail}")

        self.last_status["active_blocks"] = blocks
        self._save_state()
        return self.last_status

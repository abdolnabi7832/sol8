#!/usr/bin/env python3
"""
Autonomous Multi-Timeframe, Per-Pair & Session-Specialized Auto-Trader v2
=========================================================================
Integrates:
  - Per-Pair Verified Specialization Playbook across M15 / H1 / H4 / D1
    (Tokyo Box Mean-Reversion, London ORB/Judas Sweep, NY US-Open Sweep/ORB)
  - Full 42-Indicator / 6-Pillar Java Macro Consensus + Taylor Rule Deviation
    + Central Bank Carry Differential
  - Live ForexFactory/TradingEconomics News Blackout ([-30m, +20m])
    + Post-News Surprise Bias Continuation ([+20m, +180m])
  - Live D1 Market Structure Sync (BOS/CHoCH/Swings) to Java H2 (/batch)
  - Strictly Account-Safe Execution ($100 0.01-lot cap, correlation cap, cooldowns, max_hold)
"""

import os
import json
import math
import time
import datetime
import requests
from typing import Dict, List, Optional, Tuple

from strategy_playbook import (
    PAIRS_28, CURRENCIES, TYPICAL_SPREAD_PIPS, PAIR_PLAYBOOK, TF_MINUTES,
    pip_size, compute_full_indicators, evaluate_bespoke_signal,
)

CB_RATE_6M_BASELINE = {
    "USD": 3.75, "EUR": 2.00, "GBP": 4.00, "JPY": 0.75,
    "AUD": 3.60, "CAD": 2.25, "CHF": 0.00, "NZD": 2.25,
}


def compute_pure_indicators(bars: List[dict]) -> Optional[dict]:
    """Alias kept clean for backwards test compatibility."""
    return compute_full_indicators(bars)


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
    """Cross-sectional 20D/60D/120D currency factor scores [-5, +5] from closed D1 bars."""
    raw = {c: [] for c in CURRENCIES}
    valid_pairs = 0
    for pair, bars in candles_map.items():
        if len(bars) < 65:
            continue
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
    wd = dt.weekday()
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
            "strategy_mode": "BESPOKE_28_PAIR_MTF_SESSION_MACRO_V2",
            "last_scan_utc": None,
            "market_window": "INIT",
            "active_session": "INIT",
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
        self.state["recent_decisions"] = hist[-50:]
        self.last_status["last_action"] = text

    def sync_market_structure(self, candles_map: Dict[str, List[dict]]) -> int:
        """Computes live D1 Market Structure (BOS/CHoCH/Swings) and batch-updates Java H2."""
        payload = []
        for pair, bars in candles_map.items():
            ind = compute_full_indicators(bars)
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
        """
        Queries ALL 42 indicators' synthesized 6-Pillar Quant Scores + Taylor Deviations + Calendar:
        Returns (pair_deltas, cb_rates, news_locked_ccys, ccy_post_news_bias).
        """
        pair_deltas = {}
        cb_rates = dict(CB_RATE_6M_BASELINE)
        news_locked_ccys = set()
        ccy_scores = {}
        ccy_taylor_dev = {}
        ccy_pillar_consensus = {}
        ccy_post_news = {}

        try:
            r_sc = requests.get(f"{self.java_base_url}/api/v1/quant/scores", timeout=5.0)
            if r_sc.status_code == 200:
                for row in (r_sc.json() or []):
                    ccy = str(row.get("currency", "")).upper()
                    if ccy not in CURRENCIES:
                        continue
                    ccy_scores[ccy] = float(row.get("finalScore", 0.0))
                    ccy_taylor_dev[ccy] = float(row.get("taylorDeviation", 0.0))
                    if row.get("actualInterestRate") is not None:
                        cb_rates[ccy] = float(row["actualInterestRate"])
                    # Count consensus across all 6 Macro Pillars (42 indicators total)
                    pillars = row.get("pillarScores") or {}
                    net_p = 0.0
                    for pk, pval in pillars.items():
                        raw_p = float((pval or {}).get("rawPillarScore", 0.0))
                        if abs(raw_p) >= 0.15:
                            net_p += 1.0 if raw_p > 0 else -1.0
                    ccy_pillar_consensus[ccy] = net_p / 6.0  # [-1, +1] across all 6 pillars!
        except Exception:
            pass

        try:
            r_bs = requests.get(f"{self.java_base_url}/api/v1/bootstrap", timeout=6.0)
            if r_bs.status_code == 200:
                bs = r_bs.json() or {}
                for sig in (bs.get("pairSignals") or []):
                    pair = str(sig.get("pair", "")).upper()
                    if not pair or len(pair) != 6:
                        continue
                    base_d = float(sig.get("deltaScore", 0.0))
                    b, q = pair[:3], pair[3:]
                    # Combine 6-pillar consensus & Taylor rule policy bias into live pair delta
                    pil_d = ccy_pillar_consensus.get(b, 0.0) - ccy_pillar_consensus.get(q, 0.0)
                    tay_d = max(-1.0, min(1.0, (ccy_taylor_dev.get(b, 0.0) - ccy_taylor_dev.get(q, 0.0)) * 0.25))
                    pair_deltas[pair] = round(base_d + 0.4 * pil_d + 0.3 * tay_d, 3)

                    gates = sig.get("gates") or {}
                    if sig.get("isNewsLocked") or str(gates.get("gate4NewsStatus", "")).upper() == "NEWS_LOCKED":
                        news_locked_ccys.add(b)
                        news_locked_ccys.add(q)

                now_utc = datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
                for ev in (bs.get("economicCalendar") or []):
                    if str(ev.get("impact", "")).upper() != "HIGH":
                        continue
                    ccy = str(ev.get("currency", "")).upper()
                    ts_str = ev.get("exactEventTimestampUtc")
                    if ccy not in CURRENCIES or not ts_str:
                        continue
                    try:
                        ev_dt = datetime.datetime.fromisoformat(str(ts_str).replace("Z", "")[:19])
                        diff_min = (now_utc - ev_dt).total_seconds() / 60.0  # positive = after release
                        if -30.0 <= diff_min <= 20.0:
                            news_locked_ccys.add(ccy)
                        elif 20.0 < diff_min <= 180.0 and ev.get("actual") is not None:
                            surp = float(ev.get("surpriseStdDev") or 0.0)
                            # Unemployment miss is negative for currency; handle direction safely
                            ev_name = str(ev.get("eventName", "")).lower()
                            if "unemployment" in ev_name or "jobless" in ev_name:
                                surp = -surp
                            if abs(surp) >= 0.75:
                                ccy_post_news[ccy] = 1 if surp > 0 else -1
                    except Exception:
                        pass
        except Exception:
            pass

        return pair_deltas, cb_rates, news_locked_ccys, ccy_post_news

    def _compute_setup_signal(
        self,
        pair: str,
        ind: dict,
        java_delta: Optional[float],
        flow_scores: Dict[str, float],
        cb_rates: Dict[str, float],
        d1_ind: Optional[dict] = None,
        post_news_bias: int = 0,
        cfg_override: Optional[dict] = None,
    ) -> Optional[dict]:
        """Compatibility wrapper & dispatcher using verified per-pair playbook."""
        cfg = cfg_override or PAIR_PLAYBOOK.get(pair)
        if not cfg:
            return None
        b, q = pair[:3], pair[3:]
        flow_delta = flow_scores.get(b, 0.0) - flow_scores.get(q, 0.0)
        delta = (0.55 * java_delta + 0.45 * flow_delta) if java_delta is not None else flow_delta
        carry = cb_rates.get(b, 0.0) - cb_rates.get(q, 0.0)
        chg_diff = (cb_rates.get(b, 0.0) - CB_RATE_6M_BASELINE.get(b, cb_rates.get(b, 0.0))) - (
            cb_rates.get(q, 0.0) - CB_RATE_6M_BASELINE.get(q, cb_rates.get(q, 0.0))
        )
        sig = evaluate_bespoke_signal(
            pair=pair, cfg=cfg, ind=ind, d1_ind=d1_ind,
            macro_delta=delta, carry_diff=carry, chg_diff=chg_diff,
            bar_idx=None, post_news_bias=post_news_bias,
        )
        if sig:
            sig["bar_date"] = sig["bar_key"]
        return sig

    def _get_tf_candles(self, tf: str) -> Dict[str, List[dict]]:
        """Non-blocking accessor for cached MTF candles populated by background sync_candles_worker."""
        tf_up = tf.upper()
        if hasattr(self.engine, "get_mtf_candles_28"):
            cmap = self.engine.get_mtf_candles_28(tf_up, blocking=False)
            if cmap:
                return cmap

        disk_key = "candles" if tf_up == "D1" else f"candles_{tf_up.lower()}"
        try:
            with open(os.path.join(self.logs_dir, "mt5_candles.json"), "r", encoding="utf-8") as f:
                cjson = json.load(f)
            cmap = cjson.get(disk_key) or {}
            if cmap:
                return cmap
            if tf_up == "D1":
                return cjson.get("candles") or {}
        except Exception:
            pass
        return {}

    def _sync_closed_cooldowns(self, open_positions: List[dict]):
        """Tracks closed broker deals so losing trades enforce a cooldown per pair."""
        if not self.engine.use_wine_bridge:
            return
        from mt5_cockpit_daemon import wine_bridge_get
        resp = wine_bridge_get("/history_deals", {"days": 7}, timeout=4.0)
        deals = (resp or {}).get("deals") or []
        open_tickets = {int(p["ticket"]) for p in open_positions}
        today_date = datetime.datetime.now(datetime.timezone.utc).date()

        by_pos = {}
        for d in deals:
            pid = int(d.get("position_id") or 0)
            if pid <= 0 or pid in open_tickets or int(d.get("entry", 0)) != 1:
                continue
            sym = str(d.get("symbol", "")).upper()
            net = float(d.get("profit", 0.0)) + float(d.get("commission", 0.0)) + float(d.get("swap", 0.0))
            t_sec = int(d.get("time", 0))
            by_pos.setdefault(pid, {"sym": sym, "net": 0.0, "time": t_sec})
            by_pos[pid]["net"] += net
            by_pos[pid]["time"] = max(by_pos[pid]["time"], t_sec)

        for pid, info in by_pos.items():
            sym = info["sym"]
            if sym in PAIRS_28 and info["net"] < -0.15 and info["time"] > 0:
                cfg = PAIR_PLAYBOOK.get(sym, {"tf": "H4"})
                cd_days = 1 if cfg["tf"] in ("M15", "H1") else (2 if cfg["tf"] == "H4" else 5)
                deal_dt = datetime.datetime.fromtimestamp(info["time"], datetime.timezone.utc).date()
                until_dt = deal_dt + datetime.timedelta(days=cd_days)
                if until_dt >= today_date:
                    cur_until = self.state["cooldown_until"].get(sym, "")
                    if until_dt.strftime("%Y-%m-%d") > cur_until:
                        self.state["cooldown_until"][sym] = until_dt.strftime("%Y-%m-%d")

        for tk in list(self.state.get("position_meta", {}).keys()):
            try:
                if int(tk) not in open_tickets:
                    self.state["position_meta"].pop(tk, None)
            except Exception:
                pass

    def evaluate_and_trade_once(self, force: bool = False) -> dict:
        """Evaluates all 28 bespoke pair strategies (M15/H1/H4/D1) with 7-gate safety."""
        now_utc = datetime.datetime.now(datetime.timezone.utc)
        now_str = now_utc.strftime("%Y-%m-%d %H:%M:%S UTC")
        today_str = now_utc.strftime("%Y-%m-%d")
        self.last_status["last_scan_utc"] = now_str
        self.last_status["enabled"] = bool(self.engine.autopilot_enabled)

        hr = now_utc.hour
        self.last_status["active_session"] = (
            "TOKYO (00-07 UTC)" if hr < 7 else ("LONDON (07-12 UTC)" if hr < 12 else ("NY_US_OPEN (12-18 UTC)" if hr < 18 else "AFTER_HOURS"))
        )

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

        max_concurrent = 3 if balance < 500.0 else 5
        if len(positions) >= max_concurrent:
            blocks.append(f"Max Concurrent Positions Reached ({len(positions)}/{max_concurrent})")

        if blocks:
            self.last_status["active_blocks"] = blocks
            self._save_state()
            return self.last_status

        # Load Multi-Timeframe Candle Panels
        d1_candles = self._get_tf_candles("D1")
        mtf_cache = {"D1": d1_candles, "H4": {}, "H1": {}, "M15": {}}
        needed_tfs = {cfg["tf"] for cfg in PAIR_PLAYBOOK.values()} - {"D1"}
        for tf in needed_tfs:
            mtf_cache[tf] = self._get_tf_candles(tf)

        d1_ind_map = {p: compute_full_indicators(bars) for p, bars in d1_candles.items() if bars}
        java_deltas, cb_rates, news_locked_ccys, ccy_post_news = self._fetch_java_context()
        flow_scores = compute_live_cs_flow_scores(d1_candles)

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
            cfg = PAIR_PLAYBOOK[pair]
            tf = cfg["tf"]
            bars = (mtf_cache.get(tf) or {}).get(pair)
            if not bars:
                continue
            ind = d1_ind_map.get(pair) if tf == "D1" else compute_full_indicators(bars)
            if not ind:
                continue
            b, q = pair[:3], pair[3:]
            pnb = ccy_post_news.get(b, 0) - ccy_post_news.get(q, 0)
            pnb_sign = 1 if pnb > 0 else (-1 if pnb < 0 else 0)
            sig = self._compute_setup_signal(
                pair, ind, java_deltas.get(pair), flow_scores, cb_rates,
                d1_ind=d1_ind_map.get(pair), post_news_bias=pnb_sign, cfg_override=cfg,
            )
            if sig:
                candidates.append(sig)

        candidates.sort(key=lambda x: x["conviction"], reverse=True)
        self.last_status["last_candidates"] = candidates[:8]

        from mt5_cockpit_daemon import wine_bridge_get
        for cand in candidates:
            if len(positions) >= max_concurrent:
                break
            pair = cand["pair"]
            action = cand["action"]
            bar_key = cand["bar_key"]
            b, q = pair[:3], pair[3:]

            if pair in open_pairs:
                continue
            if self.state["traded_bars"].get(pair) == bar_key:
                continue
            if self.state["cooldown_until"].get(pair, "") > today_str:
                continue
            if b in news_locked_ccys or q in news_locked_ccys:
                self._record_decision(f"Skipped {pair} {action} ({cand['setup']}): High-Impact News Lock on {b}/{q}")
                continue
            d_sign = 1 if action == "BUY" else -1
            if abs(ccy_exp.get(b, 0) + d_sign) > 1 or abs(ccy_exp.get(q, 0) - d_sign) > 1:
                self._record_decision(f"Skipped {pair} {action} ({cand['setup']}): Correlation Cap on {b}/{q}")
                continue

            ps = pip_size(pair)
            spread_pips = TYPICAL_SPREAD_PIPS.get(pair, 2.0)
            pip_val_per_lot = 10.0 if q == "USD" else 7.5
            vol_min, vol_max, vol_step = 0.01, 10.0, 0.01
            min_stop_pips = 3.0

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
            floor_pips = 16.0 if cand["tf"] in ("M15", "H1", "H4") else 25.0
            sl_pips = round(max(atr_pips * cand["sl_m"], floor_pips, min_stop_pips + 3.0), 1)
            tp_pips = round(sl_pips * (cand["tp_m"] / cand["sl_m"]), 1)

            if balance < 500.0:
                lots = vol_min
                risk_usd = sl_pips * pip_val_per_lot * lots
                budget_cap = max(13.8, equity * 0.14)
                if risk_usd > budget_cap:
                    self._record_decision(
                        f"Skipped {pair} {action} ({cand['setup']}): 0.01L risk ${risk_usd:.2f} > ${budget_cap:.2f} cap"
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
                self.state["traded_bars"][pair] = bar_key
                self.state["trades_opened_total"] = int(self.state.get("trades_opened_total", 0)) + 1
                self.last_status["trades_opened_total"] = self.state["trades_opened_total"]
                max_hold_min = int(cand["max_hold_bars"]) * TF_MINUTES.get(cand["tf"], 240)
                if ticket > 0:
                    self.state["position_meta"][str(ticket)] = {
                        "pair": pair,
                        "direction": action,
                        "setup": cand["setup"],
                        "tf": cand["tf"],
                        "entry_price": float(res.get("price") or 0.0),
                        "sl_pips": sl_pips,
                        "tp_pips": tp_pips,
                        "atr_pips": round(atr_pips, 1),
                        "be_locked": False,
                        "opened_epoch": int(time.time()),
                        "max_hold_minutes": max_hold_min,
                        "opened_at": now_str,
                    }
                msg = (
                    f"AUTOPILOT ENTERED {action} {lots} {pair} #{ticket} @ {res.get('price')} "
                    f"[{cand['setup']} | SL={sl_pips}p TP={tp_pips}p | d={cand['delta']}]"
                )
                self.engine.log_event(f"🤖 {msg}")
                self._record_decision(msg)
                self._save_state()

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

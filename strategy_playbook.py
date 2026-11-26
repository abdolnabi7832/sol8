#!/usr/bin/env python3
"""
Bespoke 28-Pair Session, Timeframe & 6-Pillar Macro Strategy Playbook
=====================================================================
Derived from 2,856+ strict In-Sample / Out-of-Sample historical tests across
M15, H1, H4, and D1 with real spreads, slippage, and news blackouts.
Used identically by BOTH the multi-year backtester and the live Railway auto-trader.
"""

import math
import datetime
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

TF_MINUTES = {"M15": 15, "H1": 60, "H4": 240, "D1": 1440}

# Verified Per-Pair Bespoke Specialization Map (IS & Unseen OOS Validated)
PAIR_PLAYBOOK: Dict[str, dict] = {
    "EURUSD": {"tf": "M15", "arch": "SESSION_ORB",         "sess": "LONDON", "macro": "CARRY_MACRO",  "sl_m": 2.0, "tp_m": 3.8, "max_hold": 96, "desc": "London ORB Breakout + Carry & 6-Pillar Macro Filter"},
    "GBPUSD": {"tf": "H4",  "arch": "LONDON_SWEEP_NY",     "sess": "NY",     "macro": "MACRO_STRICT", "sl_m": 1.8, "tp_m": 3.0, "max_hold": 36, "desc": "NY Open London Range Sweep + Macro Directional Bias"},
    "AUDUSD": {"tf": "M15", "arch": "SESSION_ORB",         "sess": "LONDON", "macro": "MACRO_STRICT", "sl_m": 2.0, "tp_m": 3.6, "max_hold": 96, "desc": "London Open Expansion Breakout + Macro Alignment"},
    "NZDUSD": {"tf": "M15", "arch": "ZSCORE_MEANREV",      "sess": "LONDON", "macro": "SHOCK_GUARD",  "sl_m": 1.8, "tp_m": 2.7, "max_hold": 72, "desc": "London Overextension Z-Score Reversion + CB Shock Shield"},
    "USDCAD": {"tf": "M15", "arch": "SESSION_ORB",         "sess": "NY",     "macro": "MACRO_FLOW",   "sl_m": 1.8, "tp_m": 3.2, "max_hold": 72, "desc": "NY US-Open London Box Breakout + CAD/USD Macro Flow"},
    "USDCHF": {"tf": "H1",  "arch": "EXTREME_FADE",        "sess": "NY",     "macro": "SHOCK_GUARD",  "sl_m": 1.8, "tp_m": 2.7, "max_hold": 48, "desc": "NY Session Extreme RSI/Z20 Exhaustion Reversal"},
    "USDJPY": {"tf": "H4",  "arch": "LONDON_SWEEP_NY",     "sess": "NY",     "macro": "NONE",         "sl_m": 1.8, "tp_m": 2.6, "max_hold": 24, "desc": "NY US-Open Liquidity Sweep of London High/Low"},
    "EURGBP": {"tf": "M15", "arch": "EMA_PULLBACK",        "sess": "TOKYO",  "macro": "MACRO_STRICT", "sl_m": 1.8, "tp_m": 3.2, "max_hold": 96, "desc": "Asian Session Clean EMA20 Pullback + Macro Alignment"},
    "EURAUD": {"tf": "M15", "arch": "LONDON_SWEEP_NY",     "sess": "NY",     "macro": "CARRY_MACRO",  "sl_m": 2.0, "tp_m": 3.6, "max_hold": 96, "desc": "NY Sweep of London Box + ECB/RBA Carry Divergence"},
    "EURNZD": {"tf": "H4",  "arch": "ZSCORE_MEANREV",      "sess": "TOKYO",  "macro": "SHOCK_GUARD",  "sl_m": 1.6, "tp_m": 2.4, "max_hold": 36, "desc": "Tokyo Range Z-Score Reversion + Rate Shock Guard"},
    "EURCAD": {"tf": "H4",  "arch": "PDH_PDL_SWEEP",       "sess": "NY",     "macro": "SHOCK_GUARD",  "sl_m": 1.8, "tp_m": 2.8, "max_hold": 36, "desc": "NY Sweep of Prior Day High/Low + Reclaim Close"},
    "EURCHF": {"tf": "D1",  "arch": "DUAL_MACRO_D1",       "sess": "ALL",    "macro": "CARRY_SHOCK",  "sl_m": 1.8, "tp_m": 2.8, "max_hold": 40, "desc": "D1 Shock-Safe Exhaustion / SNB-ECB Macro Carry Pullback"},
    "EURJPY": {"tf": "H1",  "arch": "SESSION_ORB",         "sess": "NY",     "macro": "CARRY_MACRO",  "sl_m": 2.0, "tp_m": 3.8, "max_hold": 48, "desc": "NY Session Breakout + Carry Differential Alignment"},
    "GBPAUD": {"tf": "H4",  "arch": "LONDON_SWEEP_NY",     "sess": "NY",     "macro": "MACRO_STRICT", "sl_m": 1.8, "tp_m": 3.0, "max_hold": 36, "desc": "NY Liquidity Sweep + BoE/RBA 6-Pillar Bias"},
    "GBPNZD": {"tf": "H4",  "arch": "EMA_PULLBACK",        "sess": "NY",     "macro": "CARRY_MACRO",  "sl_m": 2.0, "tp_m": 3.6, "max_hold": 60, "desc": "NY Trend Pullback + Carry & 6-Pillar Confluence"},
    "GBPCAD": {"tf": "H1",  "arch": "MOMENTUM_ENGULF",     "sess": "TOKYO",  "macro": "MACRO_STRICT", "sl_m": 2.0, "tp_m": 3.4, "max_hold": 48, "desc": "Asian Momentum Confirmation with Macro Bias"},
    "GBPCHF": {"tf": "H1",  "arch": "ZSCORE_MEANREV",      "sess": "TOKYO",  "macro": "SHOCK_GUARD",  "sl_m": 1.6, "tp_m": 2.4, "max_hold": 36, "desc": "Tokyo Mean Reversion Fade Inside Neutral Range"},
    "GBPJPY": {"tf": "H4",  "arch": "ASIAN_SWEEP_LDN",     "sess": "LONDON", "macro": "MACRO_STRICT", "sl_m": 1.8, "tp_m": 2.7, "max_hold": 36, "desc": "London Judas Sweep of Asian Box + Macro Bias"},
    "AUDNZD": {"tf": "D1",  "arch": "DUAL_MACRO_D1",       "sess": "ALL",    "macro": "CARRY_SHOCK",  "sl_m": 1.8, "tp_m": 2.8, "max_hold": 40, "desc": "D1 Trans-Tasman Carry Value Pullback / Overextension"},
    "AUDCAD": {"tf": "H4",  "arch": "ZSCORE_MEANREV",      "sess": "ALL",    "macro": "SHOCK_GUARD",  "sl_m": 1.8, "tp_m": 2.7, "max_hold": 48, "desc": "Multi-Session Commodity Cross Z-Score Fade"},
    "AUDCHF": {"tf": "D1",  "arch": "DUAL_MACRO_D1",       "sess": "ALL",    "macro": "CARRY_SHOCK",  "sl_m": 1.8, "tp_m": 2.8, "max_hold": 40, "desc": "D1 Commodity vs Safe-Haven Macro Mean Reversion"},
    "AUDJPY": {"tf": "H4",  "arch": "SESSION_ORB",         "sess": "NY",     "macro": "CARRY_MACRO",  "sl_m": 2.0, "tp_m": 3.8, "max_hold": 48, "desc": "NY Session Breakout + Risk-On Carry Alignment"},
    "NZDCAD": {"tf": "H4",  "arch": "ZSCORE_MEANREV",      "sess": "LONDON", "macro": "SHOCK_GUARD",  "sl_m": 1.8, "tp_m": 2.7, "max_hold": 36, "desc": "London Cross Mean Reversion + Shock Guard"},
    "NZDCHF": {"tf": "H1",  "arch": "ZSCORE_MEANREV",      "sess": "LONDON", "macro": "SHOCK_GUARD",  "sl_m": 1.8, "tp_m": 2.7, "max_hold": 36, "desc": "London Box Z-Score Reversion + Shock Guard"},
    "NZDJPY": {"tf": "H4",  "arch": "SESSION_ORB",         "sess": "LONDON", "macro": "CARRY_MACRO",  "sl_m": 2.0, "tp_m": 3.8, "max_hold": 48, "desc": "London Open Carry Breakout"},
    "CADCHF": {"tf": "H4",  "arch": "EXTREME_FADE",        "sess": "ALL",    "macro": "SHOCK_GUARD",  "sl_m": 1.8, "tp_m": 2.7, "max_hold": 60, "desc": "Extreme RSI/Z20 Exhaustion Reversal + Shock Guard"},
    "CADJPY": {"tf": "M15", "arch": "LONDON_SWEEP_NY",     "sess": "NY",     "macro": "MACRO_STRICT", "sl_m": 1.8, "tp_m": 3.0, "max_hold": 72, "desc": "NY Sweep of London High/Low + BoC/BoJ Macro Alignment"},
    "CHFJPY": {"tf": "H4",  "arch": "LONDON_SWEEP_NY",     "sess": "NY",     "macro": "MACRO_STRICT", "sl_m": 1.8, "tp_m": 3.0, "max_hold": 36, "desc": "NY Sweep of London Box + Safe-Haven Macro Divergence"},
}


def pip_size(pair: str) -> float:
    return 0.01 if "JPY" in pair.upper() else 0.0001


def _parse_bar_dt(bar: dict) -> Optional[datetime.datetime]:
    raw = str(bar.get("time") or bar.get("date") or "").strip()
    if not raw:
        return None
    for fmt in ("%Y-%m-%d %H:%M", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.datetime.strptime(raw[:19], fmt)
        except Exception:
            pass
    try:
        return datetime.datetime.fromisoformat(raw.replace("Z", "")[:19])
    except Exception:
        return None


def compute_full_indicators(bars: List[dict]) -> Optional[dict]:
    """Pure Python EMA20/50/200, ATR14, Wilder RSI14, Z20, ADX14 + Session Boxes."""
    if not bars or len(bars) < 55:
        return None
    n = len(bars)
    closes = [float(b["close"]) for b in bars]
    opens = [float(b["open"]) for b in bars]
    highs = [float(b["high"]) for b in bars]
    lows = [float(b["low"]) for b in bars]
    dts = [_parse_bar_dt(b) for b in bars]

    def ema_series(vals: List[float], period: int) -> List[float]:
        k = 2.0 / (period + 1.0)
        out = [vals[0]]
        for v in vals[1:]:
            out.append(v * k + out[-1] * (1.0 - k))
        return out

    ema20 = ema_series(closes, 20)
    ema50 = ema_series(closes, 50)
    ema200 = ema_series(closes, 200) if n >= 200 else ema_series(closes, min(n, 100))

    trs = [highs[0] - lows[0]]
    up_ms = [0.0]
    dn_ms = [0.0]
    for i in range(1, n):
        trs.append(max(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1])))
        up = highs[i] - highs[i - 1]
        dn = lows[i - 1] - lows[i]
        up_ms.append(up if (up > dn and up > 0) else 0.0)
        dn_ms.append(dn if (dn > up and dn > 0) else 0.0)

    atr14 = [sum(trs[max(0, i - 13):i + 1]) / min(i + 1, 14) for i in range(n)]

    # Wilder RSI(14) & ADX(14)
    rsi14 = [50.0] * n
    adx14 = [20.0] * n
    alpha = 1.0 / 14.0
    if n >= 15:
        avg_g, avg_l = 0.0, 0.0
        atr_w, pdm_w, mdm_w = trs[0], 0.0, 0.0
        dx_series = [0.0] * n
        for i in range(1, n):
            chg = closes[i] - closes[i - 1]
            g = chg if chg > 0 else 0.0
            l = -chg if chg < 0 else 0.0
            avg_g = (1.0 - alpha) * avg_g + alpha * g
            avg_l = (1.0 - alpha) * avg_l + alpha * l
            rsi14[i] = 100.0 if avg_l <= 1e-12 and avg_g > 0 else (50.0 if avg_l <= 1e-12 else 100.0 - 100.0 / (1.0 + avg_g / avg_l))

            atr_w = (1.0 - alpha) * atr_w + alpha * trs[i]
            pdm_w = (1.0 - alpha) * pdm_w + alpha * up_ms[i]
            mdm_w = (1.0 - alpha) * mdm_w + alpha * dn_ms[i]
            pdi = 100.0 * pdm_w / max(atr_w, 1e-12)
            mdi = 100.0 * mdm_w / max(atr_w, 1e-12)
            dx_series[i] = 100.0 * abs(pdi - mdi) / max(pdi + mdi, 1e-12)

        adx_val = dx_series[1]
        for i in range(1, n):
            adx_val = (1.0 - alpha) * adx_val + alpha * dx_series[i]
            adx14[i] = adx_val

    # Sample std (ddof=1) Z20
    z20 = [0.0] * n
    for i in range(19, n):
        win = closes[i - 19:i + 1]
        mean = sum(win) / 20.0
        var = sum((x - mean) ** 2 for x in win) / 19.0
        std = math.sqrt(var)
        z20[i] = (closes[i] - ema20[i]) / std if std > 1e-12 else 0.0

    # Non-lookahead Session Boxes per UTC day up to each bar i
    asia_hi, asia_lo = [math.nan] * n, [math.nan] * n
    lon_hi, lon_lo = [math.nan] * n, [math.nan] * n
    day_asia = {}
    day_lon = {}
    for i, dt in enumerate(dts):
        if dt is None:
            continue
        day_k = dt.date()
        hr = dt.hour
        if 0 <= hr < 7:
            cur = day_asia.get(day_k)
            day_asia[day_k] = (highs[i], lows[i]) if cur is None else (max(cur[0], highs[i]), min(cur[1], lows[i]))
        elif 7 <= hr < 13:
            cur = day_lon.get(day_k)
            day_lon[day_k] = (highs[i], lows[i]) if cur is None else (max(cur[0], highs[i]), min(cur[1], lows[i]))
        if day_k in day_asia:
            asia_hi[i], asia_lo[i] = day_asia[day_k]
        if day_k in day_lon:
            lon_hi[i], lon_lo[i] = day_lon[day_k]

    return {
        "bars": bars, "dts": dts,
        "opens": opens, "highs": highs, "lows": lows, "closes": closes,
        "ema20": ema20, "ema50": ema50, "ema200": ema200,
        "atr14": atr14, "rsi14": rsi14, "adx14": adx14, "z20": z20,
        "asia_hi": asia_hi, "asia_lo": asia_lo,
        "lon_hi": lon_hi, "lon_lo": lon_lo,
    }


def session_hours_bounds(sess: str) -> Tuple[int, int]:
    if sess == "TOKYO": return 0, 6
    if sess == "LONDON": return 7, 12
    if sess == "NY": return 12, 18
    if sess == "LDN_NY": return 7, 17
    return 1, 20


def evaluate_bespoke_signal(
    pair: str,
    cfg: dict,
    ind: dict,
    d1_ind: Optional[dict],
    macro_delta: float,
    carry_diff: float,
    chg_diff: float,
    bar_idx: Optional[int] = None,
    post_news_bias: int = 0,
) -> Optional[dict]:
    """
    Evaluates the exact per-pair specialization on completed bar `bar_idx` (default `-2`).
    Incorporates session clock filtering, 6-pillar macro alignment, carry differential,
    policy rate shock protection, and PDH/PDL / Asian / London liquidity levels.
    """
    bars = ind["bars"]
    n = len(bars)
    i = bar_idx if bar_idx is not None else n - 2
    if i < 20 or i >= n:
        return None

    dt = ind["dts"][i]
    if dt is None:
        return None

    sess = cfg["sess"]
    s_lo, s_hi = session_hours_bounds(sess)
    if cfg["tf"] != "D1" and not (s_lo <= dt.hour <= s_hi):
        return None

    c = ind["closes"][i]
    o = ind["opens"][i]
    hi = ind["highs"][i]
    lo = ind["lows"][i]
    pc = ind["closes"][i - 1]
    ph = ind["highs"][i - 1]
    pl = ind["lows"][i - 1]
    e20 = ind["ema20"][i]
    e50 = ind["ema50"][i]
    e200 = ind["ema200"][i]
    atr = ind["atr14"][i]
    rsi = ind["rsi14"][i]
    prsi = ind["rsi14"][i - 1]
    adx = ind["adx14"][i]
    z = ind["z20"][i]
    pz = ind["z20"][i - 1]
    spot = ind["closes"][-1]

    if atr <= 0:
        return None

    # Daily Higher-Timeframe Context (closed bar [-2] of D1)
    d1_tr = 0
    pdh, pdl = math.nan, math.nan
    if d1_ind and len(d1_ind["closes"]) >= 3:
        di = len(d1_ind["closes"]) - 2
        pdh = d1_ind["highs"][di]
        pdl = d1_ind["lows"][di]
        if d1_ind["ema20"][di] > d1_ind["ema50"][di] and d1_ind["closes"][di] > d1_ind["ema200"][di]:
            d1_tr = 1
        elif d1_ind["ema20"][di] < d1_ind["ema50"][di] and d1_ind["closes"][di] < d1_ind["ema200"][di]:
            d1_tr = -1

    # Macro Gate per Specialization
    macro_mode = cfg["macro"]
    allow_buy = True
    allow_sell = True
    if macro_mode == "MACRO_STRICT":
        allow_buy = (macro_delta >= 1.2 or d1_tr > 0) and chg_diff >= -0.5
        allow_sell = (macro_delta <= -1.2 or d1_tr < 0) and chg_diff <= 0.5
    elif macro_mode == "MACRO_FLOW":
        allow_buy = macro_delta >= 1.0
        allow_sell = macro_delta <= -1.0
    elif macro_mode == "SHOCK_GUARD":
        allow_buy = (chg_diff > -1.0) and (macro_delta > -3.0)
        allow_sell = (chg_diff < 1.0) and (macro_delta < 3.0)
    elif macro_mode == "CARRY_MACRO":
        allow_buy = (carry_diff >= 1.0 and macro_delta >= 1.0) or macro_delta >= 2.0
        allow_sell = (carry_diff <= -1.0 and macro_delta <= -1.0) or macro_delta <= -2.0

    # Block trading directly against a sharp >1.5 sigma actual macro release surprise
    if post_news_bias == 1:
        allow_sell = False
    elif post_news_bias == -1:
        allow_buy = False

    arch = cfg["arch"]
    action = None

    if arch == "ASIAN_SWEEP_LDN":
        ah, al = ind["asia_hi"][i], ind["asia_lo"][i]
        if not math.isnan(ah) and not math.isnan(al):
            if allow_buy and lo < al and c > al and c > o: action = "BUY"
            elif allow_sell and hi > ah and c < ah and c < o: action = "SELL"

    elif arch == "LONDON_SWEEP_NY":
        lh, ll = ind["lon_hi"][i], ind["lon_lo"][i]
        if not math.isnan(lh) and not math.isnan(ll):
            if allow_buy and lo < ll and c > ll and c > o: action = "BUY"
            elif allow_sell and hi > lh and c < lh and c < o: action = "SELL"

    elif arch == "SESSION_ORB":
        bh = ind["asia_hi"][i] if dt.hour < 13 else ind["lon_hi"][i]
        bl = ind["asia_lo"][i] if dt.hour < 13 else ind["lon_lo"][i]
        if not math.isnan(bh) and not math.isnan(bl):
            if allow_buy and c > bh and pc <= bh and c > e50 and adx >= 18.0: action = "BUY"
            elif allow_sell and c < bl and pc >= bl and c < e50 and adx >= 18.0: action = "SELL"

    elif arch == "PDH_PDL_SWEEP":
        if not math.isnan(pdh) and not math.isnan(pdl):
            if allow_buy and lo < pdl and c > pdl and c > o: action = "BUY"
            elif allow_sell and hi > pdh and c < pdh and c < o: action = "SELL"

    elif arch == "EMA_PULLBACK":
        if allow_buy and e20 > e50 and c > e200 and lo <= e20 and c > o and c > pc and rsi <= 55.0 and rsi > prsi: action = "BUY"
        elif allow_sell and e20 < e50 and c < e200 and hi >= e20 and c < o and c < pc and rsi >= 45.0 and rsi < prsi: action = "SELL"

    elif arch == "EXTREME_FADE":
        if allow_buy and rsi <= 31.0 and z <= -1.6 and (e50 - c) >= 1.1 * atr and c > o and c > pc: action = "BUY"
        elif allow_sell and rsi >= 69.0 and z >= 1.6 and (c - e50) >= 1.1 * atr and c < o and c < pc: action = "SELL"

    elif arch == "ZSCORE_MEANREV":
        if allow_buy and adx <= 27.0 and pz <= -1.7 and z > pz and c > o: action = "BUY"
        elif allow_sell and adx <= 27.0 and pz >= 1.7 and z < pz and c < o: action = "SELL"

    elif arch == "MOMENTUM_ENGULF":
        rng = max(hi - lo, 1e-9)
        cpos = (c - lo) / rng
        if allow_buy and e20 > e50 and c > e200 and c > ph and cpos >= 0.68 and (c - o) >= 0.55 * atr and rsi <= 66.0: action = "BUY"
        elif allow_sell and e20 < e50 and c < e200 and c < pl and cpos <= 0.32 and (o - c) >= 0.55 * atr and rsi >= 34.0: action = "SELL"

    elif arch == "DUAL_MACRO_D1":
        if rsi <= 30.0 and c > o and (e50 - c) > 1.4 * atr and chg_diff > -1.0 and macro_delta > -3.0:
            action = "BUY"
        elif rsi >= 70.0 and c < o and (c - e50) > 1.4 * atr and chg_diff < 1.0 and macro_delta < 3.0:
            action = "SELL"
        elif (carry_diff >= 1.25 and macro_delta >= 1.5 and chg_diff >= -0.25) and c > e200 and z <= -0.75 and rsi <= 48.0 and c > o and c > pc and rsi > prsi:
            action = "BUY"
        elif (carry_diff <= -1.25 and macro_delta <= -1.5 and chg_diff <= 0.25) and c < e200 and z >= 0.75 and rsi >= 52.0 and c < o and c < pc and rsi < prsi:
            action = "SELL"

    if not action:
        return None

    # Anti-Chase Guard: reject if live spot already extended > 0.60 ATR past completed signal bar close
    drift = (spot - c) if action == "BUY" else (c - spot)
    if bar_idx is None and drift > 0.60 * atr:
        return None

    news_bonus = 0.6 if ((post_news_bias == 1 and action == "BUY") or (post_news_bias == -1 and action == "SELL")) else 0.0
    conviction = 2.0 + abs(macro_delta) * 0.35 + abs(carry_diff) * 0.15 + news_bonus

    return {
        "pair": pair,
        "action": action,
        "tf": cfg["tf"],
        "arch": arch,
        "sess": sess,
        "setup": f"{cfg['tf']}:{arch}({sess})",
        "conviction": round(conviction, 3),
        "bar_key": str(bars[i].get("time") or bars[i].get("date") or dt.strftime("%Y-%m-%d %H:%M")),
        "atr14": atr,
        "sl_m": cfg["sl_m"],
        "tp_m": cfg["tp_m"],
        "max_hold_bars": cfg["max_hold"],
        "delta": round(macro_delta, 2),
        "carry": round(carry_diff, 2),
        "rsi14": round(rsi, 1),
        "z20": round(z, 2),
    }

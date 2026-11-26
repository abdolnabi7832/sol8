#!/usr/bin/env python3
"""Comprehensive Unit & Integration Tests for Bespoke 28-Pair MTF Playbook & Auto-Trader."""

import os
import json
import shutil
import tempfile
import datetime
import unittest
from unittest.mock import patch

from backtest_engine import compute_indicators, load_historical_data
from strategy_playbook import PAIRS_28, PAIR_PLAYBOOK, compute_full_indicators
from autonomous_trader import AutonomousTrader, compute_structure_record, is_fx_trading_window_open


class MockEngine:
    def __init__(self, logs_dir):
        self.autopilot_enabled = True
        self.use_wine_bridge = False
        self.wine_bridge_ok = False
        self.logs = []
        self.orders = []
        self.mtf = {"D1": {}, "H4": {}, "H1": {}, "M15": {}}
        self.account = {
            "connected": True,
            "terminal_trade_allowed": True,
            "terminal_tradeapi_disabled": False,
            "balance": 100.0,
            "equity": 100.0,
            "margin": 0.0,
            "margin_free": 100.0,
            "margin_level": 0.0,
        }
        self.positions = []

    def get_account_summary(self):
        return dict(self.account)

    def get_live_positions(self):
        return list(self.positions)

    def get_mtf_candles_28(self, tf="D1", count=260, force=False):
        return self.mtf.get(tf.upper(), {})

    def sync_all_28_pairs_candles(self, count=250):
        return {"candles": self.mtf.get("D1", {}), "rates": {}}

    def execute_order(self, symbol, action, volume=0.01, sl_pips=None, tp_pips=None):
        ticket = 910000 + len(self.orders) + 1
        rec = {
            "success": True, "ticket": ticket, "symbol": symbol, "action": action,
            "volume": volume, "price": 1.1000, "sl": 1.0975, "tp": 1.1045,
            "sl_pips": sl_pips, "tp_pips": tp_pips,
        }
        self.orders.append(rec)
        return rec

    def log_event(self, msg):
        self.logs.append(msg)


def make_d1_bars(n=240, base=0.9200, step=0.0001, oversold_end=False, move_step=0.0035):
    today = datetime.datetime.now(datetime.timezone.utc).date()
    start = today - datetime.timedelta(days=n + 5)
    bars = []
    p = base
    for i in range(n):
        d = start + datetime.timedelta(days=i)
        p += step
        o, h, l, c = p - 0.0006, p + 0.0012, p - 0.0012, p
        if oversold_end:
            if n - 14 <= i <= n - 3:
                p -= move_step
                o, h, l, c = p + move_step * 0.8, p + move_step * 0.9, p - 0.0005, p
            elif i == n - 2:
                o, h, l, c = p - 0.0005, p + 0.0022, p - 0.0008, p + 0.0018
            elif i == n - 1:
                o, h, l, c = c, c + 0.0005, c - 0.0005, c + 0.0002
        bars.append({"date": d.strftime("%Y-%m-%d"), "time": d.strftime("%Y-%m-%d"), "open": round(o, 5), "high": round(h, 5), "low": round(l, 5), "close": round(c, 5), "volume": 10000})
    bars[-2]["date"] = bars[-2]["time"] = (today - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
    bars[-1]["date"] = bars[-1]["time"] = today.strftime("%Y-%m-%d")
    return bars


def make_intraday_m15_london_breakout():
    """240 M15 bars in an uptrend where London 08:30 bar breaks above Asian box High."""
    today = datetime.datetime.now(datetime.timezone.utc).date()
    t0 = datetime.datetime.combine(today - datetime.timedelta(days=3), datetime.time(0, 0))
    bars = []
    p = 1.0900
    for i in range(250):
        dt = t0 + datetime.timedelta(minutes=15 * i)
        # Keep steady uptrend so EMA20>EMA50>EMA200 and ADX >= 18
        p += 0.00004
        o, h, l, c = p - 0.0002, p + 0.0003, p - 0.0003, p
        bars.append({"time": dt.strftime("%Y-%m-%d %H:%M"), "date": dt.strftime("%Y-%m-%d %H:%M"), "open": round(o, 5), "high": round(h, 5), "low": round(l, 5), "close": round(c, 5), "volume": 1500})

    # Make final day: Asian 00:00..06:45 range around 1.1000..1.1010, then bar[-2] at 08:30 breaks 1.1025!
    day_final = today
    asian_start = datetime.datetime.combine(day_final, datetime.time(0, 0))
    # Replace last 36 bars chronologically up to 08:45 UTC
    bars = bars[:-36]
    base_px = p
    for k in range(36):
        dt = asian_start + datetime.timedelta(minutes=15 * k)
        hr = dt.hour
        if hr < 7:
            o, h, l, c = base_px, base_px + 0.0008, base_px - 0.0005, base_px + 0.0002
        elif k < 34:
            # 07:00..08:15 inside box
            o, h, l, c = base_px + 0.0003, base_px + 0.0007, base_px - 0.0002, base_px + 0.0005
        elif k == 34:
            # bar[-2] = 08:30 London Breakout close ABOVE asian_hi (base_px + 0.0008)
            o, h, l, c = base_px + 0.0005, base_px + 0.0022, base_px + 0.0004, base_px + 0.0020
        else:
            # bar[-1] forming near close
            o, h, l, c = base_px + 0.0020, base_px + 0.0023, base_px + 0.0018, base_px + 0.0021
        bars.append({"time": dt.strftime("%Y-%m-%d %H:%M"), "date": dt.strftime("%Y-%m-%d %H:%M"), "open": round(o, 5), "high": round(h, 5), "low": round(l, 5), "close": round(c, 5), "volume": 3500})
    return bars


def make_h4_ny_sweep_sell():
    """Creates 220 H4 bars ending in NY 16:00 UTC sweeping London High & closing bearish."""
    today = datetime.datetime.now(datetime.timezone.utc).date()
    t0 = datetime.datetime.combine(today - datetime.timedelta(days=40), datetime.time(0, 0))
    bars = []
    p = 190.000
    for i in range(215):
        dt = t0 + datetime.timedelta(hours=4 * i)
        o, h, l, c = p, p + 0.15, p - 0.15, p + (0.04 if i % 2 == 0 else -0.04)
        bars.append({"time": dt.strftime("%Y-%m-%d %H:%M"), "date": dt.strftime("%Y-%m-%d %H:%M"), "open": o, "high": h, "low": l, "close": c, "volume": 5000})
    d_fin = today
    seq = [
        (4, 190.1, 190.2, 189.95, 190.15),
        (8, 190.15, 190.50, 190.05, 190.35),   # London 08:00-12:00 box: hi=190.50
        (12, 190.35, 190.48, 190.10, 190.30),  # London 12:00
        (16, 190.30, 190.75, 190.20, 190.25),  # bar[-2] 16:00 NY sweeps > 190.50 and closes < 190.50 bearish!
        (20, 190.25, 190.30, 190.15, 190.20),  # bar[-1] forming
    ]
    for hr, o, h, l, c in seq:
        dt = datetime.datetime.combine(d_fin, datetime.time(hr, 0))
        bars.append({"time": dt.strftime("%Y-%m-%d %H:%M"), "date": dt.strftime("%Y-%m-%d %H:%M"), "open": o, "high": h, "low": l, "close": c, "volume": 8000})
    return bars


class TestAutonomousBespokeSystem(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.engine = MockEngine(self.tmp)
        self.trader = AutonomousTrader(self.engine, self.tmp)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_all_28_pairs_present_in_playbook(self):
        self.assertEqual(len(PAIRS_28), 28)
        for p in PAIRS_28:
            self.assertIn(p, PAIR_PLAYBOOK)
            self.assertIn(PAIR_PLAYBOOK[p]["tf"], ("M15", "H1", "H4", "D1"))

    def test_pure_indicators_match_backtester_on_real_eurusd(self):
        data = load_historical_data(download_if_missing=False)
        df = data["d1"]["EURUSD"].tail(250).copy()
        bt_ind = compute_indicators(df)
        bars = [
            {"date": str(idx.date()), "time": str(idx.date()), "open": float(r["Open"]), "high": float(r["High"]), "low": float(r["Low"]), "close": float(r["Close"]), "volume": int(r["Volume"])}
            for idx, r in df.iterrows()
        ]
        pure = compute_full_indicators(bars)
        i = -2
        self.assertAlmostEqual(pure["ema20"][i], float(bt_ind["ema20"].iloc[i]), places=5)
        self.assertAlmostEqual(pure["ema50"][i], float(bt_ind["ema50"].iloc[i]), places=5)
        self.assertAlmostEqual(pure["atr14"][i], float(bt_ind["atr14"].iloc[i]), places=5)
        self.assertAlmostEqual(pure["rsi14"][i], float(bt_ind["rsi14"].iloc[i]), places=3)
        self.assertAlmostEqual(pure["z20"][i], float(bt_ind["z20"].iloc[i]), places=3)
        self.assertAlmostEqual(pure["adx14"][i], float(bt_ind["adx14"].iloc[i]), places=2)

    def test_fx_trading_window_and_structure(self):
        self.assertFalse(is_fx_trading_window_open(datetime.datetime(2026, 11, 28, 12, 0, tzinfo=datetime.timezone.utc))[0])
        self.assertFalse(is_fx_trading_window_open(datetime.datetime(2026, 11, 26, 22, 30, tzinfo=datetime.timezone.utc))[0])
        self.assertTrue(is_fx_trading_window_open(datetime.datetime(2026, 11, 26, 13, 30, tzinfo=datetime.timezone.utc))[0])
        rec = compute_structure_record("EURCHF", compute_full_indicators(make_d1_bars(220, step=0.0004)), "DAILY")
        self.assertEqual(rec["trend"], "BULLISH")

    def test_m15_london_orb_eurusd_and_news_lock_and_dedup(self):
        self.engine.mtf["D1"] = {"EURUSD": make_d1_bars(240, base=1.0800, step=0.0001)}
        self.engine.mtf["M15"] = {"EURUSD": make_intraday_m15_london_breakout()}

        # News lock blocks execution
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")), \
             patch.object(self.trader, "_fetch_java_context", return_value=({"EURUSD": 2.5}, {"EUR": 3.5, "USD": 2.0}, {"USD"}, {})):
            self.trader.evaluate_and_trade_once(force=True)
            self.assertEqual(len(self.engine.orders), 0)

        # Without News Lock -> opens 0.01 BUY on M15 London ORB, then blocks duplicate!
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")), \
             patch.object(self.trader, "_fetch_java_context", return_value=({"EURUSD": 2.5}, {"EUR": 3.5, "USD": 2.0}, set(), {})):
            self.trader.evaluate_and_trade_once(force=True)
            self.assertEqual(len(self.engine.orders), 1)
            self.assertEqual(self.engine.orders[0]["symbol"], "EURUSD")
            self.assertEqual(self.engine.orders[0]["action"], "BUY")
            self.engine.positions = []
            self.trader.evaluate_and_trade_once(force=True)
            self.assertEqual(len(self.engine.orders), 1)

    def test_h4_ny_london_sweep_chfjpy_and_d1_budget_cap(self):
        self.engine.mtf["D1"] = {
            "EURCHF": make_d1_bars(240, base=0.9500, step=0.0001, oversold_end=True, move_step=0.0150),
            "AUDNZD": make_d1_bars(240, base=1.0800, step=0.0001, oversold_end=True, move_step=0.0150),
        }
        self.engine.mtf["H4"] = {"CHFJPY": make_h4_ny_sweep_sell()}
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")), \
             patch.object(self.trader, "_fetch_java_context", return_value=({"CHFJPY": -2.5, "EURCHF": 0.5, "AUDNZD": 0.5}, {"CHF": 0.0, "JPY": 0.75, "EUR": 2.0, "AUD": 3.6, "NZD": 2.25}, set(), {})):
            self.trader.evaluate_and_trade_once(force=True)
        # CHFJPY H4 NY Sweep fires SELL; EURCHF blocked by CHF Correlation Cap; AUDNZD oversized SL ($20+ > $14 cap) safely skipped!
        syms = [o["symbol"] for o in self.engine.orders]
        self.assertIn("CHFJPY", syms)
        self.assertEqual(self.engine.orders[0]["action"], "SELL")
        self.assertNotIn("EURCHF", syms)
        self.assertNotIn("AUDNZD", syms)
        notes = " | ".join(d.get("note", "") for d in self.trader.state.get("recent_decisions", []))
        self.assertIn("Correlation Cap", notes)
        self.assertIn("cap", notes)

    def test_daily_loss_killswitch(self):
        today_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
        self.trader.state["daily_anchor"] = {"date": today_str, "start_equity": 100.0}
        self.engine.account["equity"] = 94.0
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")):
            st = self.trader.evaluate_and_trade_once(force=True)
        self.assertTrue(any("Daily Loss Kill-Switch" in b for b in st["active_blocks"]))
        self.assertEqual(len(self.engine.orders), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)

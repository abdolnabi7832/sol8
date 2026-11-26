#!/usr/bin/env python3
"""Comprehensive Unit & Integration Tests for AutonomousTrader + Backtest Parity."""

import os
import json
import shutil
import tempfile
import datetime
import unittest
from unittest.mock import patch, MagicMock

import pandas as pd
import numpy as np

from backtest_engine import compute_indicators, load_historical_data, PAIRS_28
from autonomous_trader import (
    AutonomousTrader,
    compute_pure_indicators,
    compute_structure_record,
    compute_live_cs_flow_scores,
    is_fx_trading_window_open,
)


class MockEngine:
    def __init__(self, logs_dir):
        self.autopilot_enabled = True
        self.use_wine_bridge = False
        self.wine_bridge_ok = False
        self.logs = []
        self.orders = []
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
        self.candles = {}

    def get_account_summary(self):
        return dict(self.account)

    def get_live_positions(self):
        return list(self.positions)

    def sync_all_28_pairs_candles(self, count=250):
        return {"candles": self.candles, "rates": {}}

    def execute_order(self, symbol, action, volume=0.01, sl_pips=None, tp_pips=None):
        ticket = 900000 + len(self.orders) + 1
        rec = {
            "success": True, "ticket": ticket, "symbol": symbol, "action": action,
            "volume": volume, "price": 1.1000, "sl": 1.0940, "tp": 1.1095,
            "sl_pips": sl_pips, "tp_pips": tp_pips,
        }
        self.orders.append(rec)
        return rec

    def log_event(self, msg):
        self.logs.append(msg)


def make_synthetic_bars(n=240, base=1.1000, step=0.0002, oversold_end=False, overbought_end=False, move_step=0.0040):
    bars = []
    today_utc = datetime.datetime.now(datetime.timezone.utc).date()
    start = today_utc - datetime.timedelta(days=n + 5)
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
                o, h, l, c = c, c + 0.0006, c - 0.0006, c + 0.0002
                bars[-1]["date"] = (today_utc - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
        if overbought_end:
            if n - 14 <= i <= n - 3:
                p += move_step
                o, h, l, c = p - move_step * 0.8, p + 0.0005, p - move_step * 0.9, p
            elif i == n - 2:
                o, h, l, c = p + 0.0005, p + 0.0008, p - 0.0022, p - 0.0018
            elif i == n - 1:
                o, h, l, c = c, c + 0.0006, c - 0.0006, c - 0.0002
                bars[-1]["date"] = (today_utc - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
        bars.append({
            "date": d.strftime("%Y-%m-%d"),
            "open": round(o, 5), "high": round(h, 5),
            "low": round(l, 5), "close": round(c, 5), "volume": 10000,
        })
    if not oversold_end and not overbought_end:
        bars[-2]["date"] = (today_utc - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
        bars[-1]["date"] = today_utc.strftime("%Y-%m-%d")
    else:
        bars[-1]["date"] = today_utc.strftime("%Y-%m-%d")
    return bars


class TestAutonomousTrader(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.engine = MockEngine(self.tmp)
        self.trader = AutonomousTrader(self.engine, self.tmp)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_pure_indicators_match_backtester_on_real_eurusd(self):
        data = load_historical_data(download_if_missing=False)
        df = data["d1"]["EURUSD"].tail(250).copy()
        bt_ind = compute_indicators(df)
        bars = [
            {
                "date": str(idx.date()),
                "open": float(r["Open"]), "high": float(r["High"]),
                "low": float(r["Low"]), "close": float(r["Close"]),
                "volume": int(r["Volume"]),
            }
            for idx, r in df.iterrows()
        ]
        pure = compute_pure_indicators(bars)
        self.assertIsNotNone(pure)
        i = -2
        self.assertAlmostEqual(pure["ema20"][i], float(bt_ind["ema20"].iloc[i]), places=5)
        self.assertAlmostEqual(pure["ema50"][i], float(bt_ind["ema50"].iloc[i]), places=5)
        self.assertAlmostEqual(pure["atr14"][i], float(bt_ind["atr14"].iloc[i]), places=5)
        self.assertAlmostEqual(pure["rsi14"][i], float(bt_ind["rsi14"].iloc[i]), places=3)
        self.assertAlmostEqual(pure["z20"][i], float(bt_ind["z20"].iloc[i]), places=3)

    def test_fx_trading_window_guards(self):
        # Saturday closed
        self.assertFalse(is_fx_trading_window_open(datetime.datetime(2026, 11, 28, 12, 0, tzinfo=datetime.timezone.utc))[0])
        # Friday late closed
        self.assertFalse(is_fx_trading_window_open(datetime.datetime(2026, 11, 27, 21, 5, tzinfo=datetime.timezone.utc))[0])
        # Daily rollover blackout 22:30 & 00:10 UTC
        self.assertFalse(is_fx_trading_window_open(datetime.datetime(2026, 11, 26, 22, 30, tzinfo=datetime.timezone.utc))[0])
        self.assertFalse(is_fx_trading_window_open(datetime.datetime(2026, 11, 26, 0, 10, tzinfo=datetime.timezone.utc))[0])
        # Regular London/NY session open
        self.assertTrue(is_fx_trading_window_open(datetime.datetime(2026, 11, 26, 13, 30, tzinfo=datetime.timezone.utc))[0])

    def test_structure_record_detection(self):
        bars = make_synthetic_bars(220, base=1.1000, step=0.0005)
        ind = compute_pure_indicators(bars)
        rec = compute_structure_record("EURUSD", ind, "DAILY")
        self.assertEqual(rec["pair"], "EURUSD")
        self.assertEqual(rec["trend"], "BULLISH")
        self.assertIn(rec["lastStructureEvent"], ("BOS", "SWING_HIGH", "CHoCH", "SWING_LOW"))

    def test_setup_trigger_and_anti_duplicate_and_news_shield(self):
        bars_buy = make_synthetic_bars(240, base=1.2000, step=0.0001, oversold_end=True)
        ind = compute_pure_indicators(bars_buy)
        self.assertIsNotNone(ind)
        sig = self.trader._compute_setup_signal(
            "EURUSD", ind, 0.5, {c: 0.0 for c in ["EUR", "USD"]}, {"EUR": 2.0, "USD": 3.75}
        )
        self.assertIsNotNone(sig)
        self.assertEqual(sig["action"], "BUY")
        self.assertEqual(sig["setup"], "ShockSafeMeanRev")

        # Put candles on disk for evaluate_and_trade_once
        candles_payload = {"candles": {"EURUSD": bars_buy, "AUDCAD": make_synthetic_bars(240, 0.90, 0.0001)}}
        with open(os.path.join(self.tmp, "mt5_candles.json"), "w") as f:
            json.dump(candles_payload, f)

        # News Lock blocks USD!
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")), \
             patch.object(self.trader, "_fetch_java_context", return_value=({"EURUSD": 0.5}, {"EUR": 2.0, "USD": 3.75}, {"USD"}, {})):
            self.trader.evaluate_and_trade_once(force=True)
            self.assertEqual(len(self.engine.orders), 0)

        # Without News Lock, executes exactly once and avoids duplicate on repeat call
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")), \
             patch.object(self.trader, "_fetch_java_context", return_value=({"EURUSD": 0.5}, {"EUR": 2.0, "USD": 3.75}, set(), {})):
            self.trader.evaluate_and_trade_once(force=True)
            self.assertEqual(len(self.engine.orders), 1)
            self.assertEqual(self.engine.orders[0]["symbol"], "EURUSD")
            self.assertEqual(self.engine.orders[0]["volume"], 0.01)

            # Second scan on same D1 bar must not open duplicate even if position closed
            self.engine.positions = []
            self.trader.evaluate_and_trade_once(force=True)
            self.assertEqual(len(self.engine.orders), 1)

    def test_budget_cap_skips_oversized_atr_sl_on_100_account(self):
        bars_volatile = make_synthetic_bars(240, base=1.2000, step=0.0001, oversold_end=True, move_step=0.0150)
        with open(os.path.join(self.tmp, "mt5_candles.json"), "w") as f:
            json.dump({"candles": {"EURUSD": bars_volatile}}, f)
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")), \
             patch.object(self.trader, "_fetch_java_context", return_value=({"EURUSD": 0.5}, {"EUR": 2.0, "USD": 3.75}, set(), {})):
            self.trader.evaluate_and_trade_once(force=True)
        self.assertEqual(len(self.engine.orders), 0)
        self.assertTrue(any("safety cap" in d.get("note", "") for d in self.trader.state.get("recent_decisions", [])))

    def test_daily_loss_killswitch_blocks_new_entries(self):
        today_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
        self.trader.state["daily_anchor"] = {"date": today_str, "start_equity": 100.0}
        self.engine.account["equity"] = 94.0  # -6% >= 5% limit
        with patch("autonomous_trader.is_fx_trading_window_open", return_value=(True, "OK")):
            st = self.trader.evaluate_and_trade_once(force=True)
        self.assertTrue(any("Daily Loss Kill-Switch" in b for b in st["active_blocks"]))
        self.assertEqual(len(self.engine.orders), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)

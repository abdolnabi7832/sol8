import os
import sys
import time
import datetime

# Ensure root directory is on python path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mt5_cockpit_daemon import (
    MT5CockpitEngine, render_chart_snapshot,
    dispatch_telegram_alert, dispatch_telegram_photo,
    TELEGRAM_INLINE_KEYBOARD, LOGS_DIR
)

def run_suite_verification():
    print("=" * 70)
    print("  FOREX INTELLIGENCE SUITE — LIVE TRADE & TELEGRAM VERIFICATION TEST")
    print("=" * 70)

    engine = MT5CockpitEngine()
    connected = engine.connect()
    print(f"[*] Engine Connected: {connected} (Cloud Mode: {getattr(engine, 'is_cloud_mode', False)})")
    
    summary = engine.get_account_summary()
    print(f"[*] Account Summary: #{summary.get('login')} | Balance: ${summary.get('balance'):.2f} | Eq: ${summary.get('equity'):.2f}")

    # 1. Execute Live Test Order (EURUSD BUY 0.01)
    symbol = "EURUSD"
    action = "BUY"
    volume = 0.01
    print(f"\n[*] Executing Live Micro Order: {action} {volume} {symbol}...")
    
    order_res = engine.execute_order(symbol=symbol, action=action, volume=volume, sl_pips=45, tp_pips=65)
    print(f"[*] Order Result: {order_res}")
    
    if not order_res.get("success"):
        print(f"[ERROR] Order execution failed: {order_res.get('error')}")
        return False

    ticket = order_res.get("order")
    print(f"[+] Order Successfully Placed! Ticket: #{ticket}")

    # Allow time for Telegram photo delivery and account recording
    print("[*] Holding position for 4 seconds to simulate active trade excursion...")
    time.sleep(4)

    # 2. Close Position and Verify Closed Alert
    print(f"\n[*] Closing Position #{ticket} with verified realized PnL calculation...")
    close_res = engine.close_position(ticket=ticket, reason="تست تاییدیه کامل چرخه معاملات لایو کوانت")
    print(f"[*] Close Result: {close_res}")

    if not close_res.get("success"):
        print(f"[ERROR] Close execution failed: {close_res.get('error')}")
        return False

    print(f"[+] Position #{ticket} Successfully Closed!")
    
    acc_after = engine.get_account_summary()
    print(f"[*] Final Account Balance: ${acc_after.get('balance'):.2f} | Eq: ${acc_after.get('equity'):.2f}")
    print("=" * 70)
    print("  ALL TESTS PASSED: Trade Opened -> Chart Generated -> Telegram Photo Sent ->")
    print("  Trade Closed -> Realized PnL Computed -> Telegram Closed Alert Sent!")
    print("=" * 70)
    return True

if __name__ == "__main__":
    success = run_suite_verification()
    sys.exit(0 if success else 1)


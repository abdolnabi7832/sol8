import requests
import json

PROXY = {"http": "http://127.0.0.1:10808", "https": "http://127.0.0.1:10808"}

def test_backend_telegram_config():
    url = "http://localhost:8080/api/v1/telegram/config"
    payload = {
        "botToken": "9255473348:AAFiBI_x37LLxw-QWP8Abpsact2I6iuAoBU",
        "chatId": "5792130899",
        "enabled": True
    }
    r = requests.post(url, json=payload, timeout=5)
    print("1. Backend Telegram Config Save:", r.status_code, r.text)

    r2 = requests.get(url, timeout=5)
    print("   Backend Telegram Config GET:", r2.status_code, r2.text)

def test_mt5_status():
    url = "http://127.0.0.1:8085/api/mt5/status"
    r = requests.get(url, timeout=5)
    print("2. MT5 Cockpit Daemon Status:", r.status_code, r.json().get("login"), r.json().get("company"), "Balance:", r.json().get("balance"))

def test_send_balance_to_telegram():
    url = "http://127.0.0.1:8085/api/mt5/send_balance_to_telegram"
    r = requests.post(url, json={}, timeout=10)
    print("3. MT5 Send Balance to Telegram:", r.status_code, r.text)

def test_send_closed_trade_alert():
    url = "http://127.0.0.1:8085/api/mt5/send_closed_alert_test"
    payload = {
        "symbol": "EURUSD",
        "profit": 42.50,
        "pips": 85.0
    }
    r = requests.post(url, json=payload, timeout=10)
    print("4. MT5 Send Closed Trade Alert ($42.50):", r.status_code, r.text)

def test_send_chart_to_telegram():
    url = "http://127.0.0.1:8085/api/mt5/send_chart_to_telegram"
    payload = {
        "symbol": "EURUSD",
        "action": "BUY"
    }
    r = requests.post(url, json=payload, timeout=15)
    print("5. MT5 Send Chart Snapshot to Telegram:", r.status_code, r.text)

if __name__ == "__main__":
    print("=== STARTING FULL VERIFICATION SUITE ===")
    test_backend_telegram_config()
    test_mt5_status()
    test_send_balance_to_telegram()
    test_send_closed_trade_alert()
    test_send_chart_to_telegram()
    print("=== ALL TESTS COMPLETED ===")


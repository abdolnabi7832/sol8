import os
import sys
import json
import socket
import datetime
import requests
from PIL import Image, ImageDraw, ImageFont

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
LOGS_DIR = os.path.join(BASE_DIR, "data", "Excel_Logs")

BOT_TOKEN = "9255473348:AAFiBI_x37LLxw-QWP8Abpsact2I6iuAoBU"
CHAT_ID = "5792130899"

PROXIES = {
    "http": "http://127.0.0.1:10808",
    "https": "http://127.0.0.1:10808"
}

def get_font(size=14, bold=False):
    # Try common Windows fonts, fall back to default
    candidates = [
        r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\consola.ttf"
    ]
    for c in candidates:
        if os.path.isfile(c):
            try:
                return ImageFont.truetype(c, size)
            except Exception:
                pass
    return ImageFont.load_default()

def render_sample_chart(symbol="EURUSD", action="BUY", entry=1.05420, sl=1.04880, tp1=1.06200, tp2=1.06950, out_path="sample_chart.png"):
    width, height = 860, 480
    im = Image.new("RGBA", (width, height), (15, 23, 42, 255)) # Dark slate bg #0f172a
    draw = ImageDraw.Draw(im)

    font_title = get_font(20, bold=True)
    font_bold = get_font(13, bold=True)
    font_small = get_font(11, bold=False)
    font_mono = get_font(12, bold=True)

    # Top header banner
    draw.rectangle([(0, 0), (width, 56)], fill=(30, 41, 59, 255))
    draw.line([(0, 56), (width, 56)], fill=(51, 65, 85, 255), width=1)

    action_color = (16, 185, 129, 255) if action == "BUY" else (239, 68, 68, 255)
    draw.text((20, 14), f"FOREX INTELLIGENCE QUANT V6.0 — {symbol} ({action})", font=font_title, fill=(255, 255, 255, 255))
    draw.text((width - 240, 18), "🛡️ DYNAMIC ANTI-HUNT ENGINE", font=font_bold, fill=(56, 189, 248, 255))

    # Sub banner info
    draw.rectangle([(0, 57), (width, 86)], fill=(20, 29, 47, 255))
    info_text = f"H4 Timeframe | Daily ATR-14: 64 pips | Anti-Hunt Buffer: +20 pips | Risk:Reward 1:2.4 | Account: $100.00"
    draw.text((20, 65), info_text, font=font_small, fill=(148, 163, 184, 255))

    # Chart Area
    c_left = 60
    c_right = width - 110
    c_top = 100
    c_bottom = height - 45

    # Price range
    min_p = min(sl, entry, 1.04700)
    max_p = max(tp2, entry, 1.07100)
    p_span = max_p - min_p if max_p > min_p else 0.01

    def y_from_price(p):
        norm = (p - min_p) / p_span
        return int(c_bottom - norm * (c_bottom - c_top))

    # Grid lines
    for i in range(6):
        gp = min_p + (p_span / 5.0) * i
        gy = y_from_price(gp)
        draw.line([(c_left, gy), (c_right, gy)], fill=(30, 41, 59, 200), width=1)
        draw.text((c_right + 8, gy - 7), f"{gp:.4f}", font=font_small, fill=(100, 116, 139, 255))

    # Sample Candlestick data simulating H4 price action
    import random
    random.seed(42)
    n_candles = 36
    candle_w = (c_right - c_left) / n_candles

    cur_p = 1.05000
    c_data = []
    for i in range(n_candles):
        delta = (random.random() - 0.46) * 0.0020
        o = cur_p
        c = cur_p + delta
        h = max(o, c) + random.random() * 0.0010
        l = min(o, c) - random.random() * 0.0010
        cur_p = c
        c_data.append((o, h, l, c))

    # Draw EMA lines
    ema20_pts = []
    ema50_pts = []
    e20 = c_data[0][3]
    e50 = c_data[0][3]
    for idx, (o, h, l, c) in enumerate(c_data):
        e20 = c * (2/21) + e20 * (1 - 2/21)
        e50 = c * (2/51) + e50 * (1 - 2/51)
        cx = int(c_left + idx * candle_w + candle_w / 2)
        ema20_pts.append((cx, y_from_price(e20)))
        ema50_pts.append((cx, y_from_price(e50)))

    # Draw EMA curves
    for i in range(len(ema20_pts) - 1):
        draw.line([ema20_pts[i], ema20_pts[i+1]], fill=(56, 189, 248, 220), width=2) # Cyan EMA20
        draw.line([ema50_pts[i], ema50_pts[i+1]], fill=(168, 85, 247, 220), width=2) # Purple EMA50

    # Draw candles
    for idx, (o, h, l, c) in enumerate(c_data):
        cx = int(c_left + idx * candle_w + candle_w / 2)
        yo = y_from_price(o)
        yc = y_from_price(c)
        yh = y_from_price(h)
        yl = y_from_price(l)

        is_bull = c >= o
        bar_col = (16, 185, 129, 255) if is_bull else (244, 63, 94, 255)

        # High-Low Wick
        draw.line([(cx, yh), (cx, yl)], fill=bar_col, width=1)
        # Body
        b_top = min(yo, yc)
        b_bot = max(yo, yc)
        if b_bot - b_top < 2:
            b_bot = b_top + 2
        half_w = max(2, int(candle_w * 0.35))
        draw.rectangle([(cx - half_w, b_top), (cx + half_w, b_bot)], fill=bar_col)

    # 1. Draw Entry Line (Cyan Solid)
    y_entry = y_from_price(entry)
    draw.line([(c_left, y_entry), (c_right, y_entry)], fill=(6, 182, 212, 255), width=2)
    draw.rectangle([(c_right + 4, y_entry - 11), (c_right + 100, y_entry + 11)], fill=(6, 182, 212, 255))
    draw.text((c_right + 8, y_entry - 8), f"ENTRY {entry:.5f}", font=font_mono, fill=(15, 23, 42, 255))

    # 2. Draw Dynamic Anti-Hunt SL Line (Red Dashed)
    y_sl = y_from_price(sl)
    for x in range(c_left, c_right, 10):
        draw.line([(x, y_sl), (min(x + 6, c_right), y_sl)], fill=(239, 68, 68, 255), width=2)
    draw.rectangle([(c_right + 4, y_sl - 11), (c_right + 104, y_sl + 11)], fill=(239, 68, 68, 255))
    draw.text((c_right + 8, y_sl - 8), f"🛡️ SL {sl:.5f}", font=font_mono, fill=(255, 255, 255, 255))

    # 3. Draw Anti-Hunt Liquidity Sweep Buffer Zone (Transparent Red Tint)
    y_retail_sl = y_sl - 25 # retail stops were here
    draw.rectangle([(c_left, min(y_sl, y_retail_sl)), (c_right, max(y_sl, y_retail_sl))], fill=(239, 68, 68, 40))
    draw.text((c_left + 15, max(y_sl, y_retail_sl) - 16), "⚠️ RETAIL LIQUIDITY SWEEP ZONE (Protected by Anti-Hunt Buffer)", font=font_small, fill=(248, 113, 113, 200))

    # 4. Draw TP1 Line (Green Dashed)
    y_tp1 = y_from_price(tp1)
    for x in range(c_left, c_right, 10):
        draw.line([(x, y_tp1), (min(x + 6, c_right), y_tp1)], fill=(34, 197, 94, 255), width=2)
    draw.rectangle([(c_right + 4, y_tp1 - 11), (c_right + 104, y_tp1 + 11)], fill=(34, 197, 94, 255))
    draw.text((c_right + 8, y_tp1 - 8), f"🎯 TP1 {tp1:.5f}", font=font_mono, fill=(15, 23, 42, 255))

    # 5. Draw TP2 Line (Emerald Dashed)
    y_tp2 = y_from_price(tp2)
    for x in range(c_left, c_right, 10):
        draw.line([(x, y_tp2), (min(x + 6, c_right), y_tp2)], fill=(16, 185, 129, 255), width=2)
    draw.rectangle([(c_right + 4, y_tp2 - 11), (c_right + 104, y_tp2 + 11)], fill=(16, 185, 129, 255))
    draw.text((c_right + 8, y_tp2 - 8), f"🚀 TP2 {tp2:.5f}", font=font_mono, fill=(15, 23, 42, 255))

    # Footer Watermark
    draw.rectangle([(0, height - 32), (width, height)], fill=(15, 23, 42, 255))
    draw.line([(0, height - 32), (width, height - 32)], fill=(30, 41, 59, 255), width=1)
    draw.text((20, height - 24), "⚡ GTC Global Trade Capital MT5 Live Session | 1:500 Leverage | Balance: $100.00", font=font_small, fill=(100, 116, 139, 255))
    draw.text((width - 240, height - 24), "Cyan: EMA20 | Purple: EMA50", font=font_small, fill=(148, 163, 184, 255))

    im.save(out_path, "PNG")
    print(f"Chart saved to {out_path}")
    return out_path

if __name__ == "__main__":
    out_file = os.path.join(LOGS_DIR, "test_chart.png")
    render_sample_chart(symbol="EURUSD", action="BUY", entry=1.05420, sl=1.04880, tp1=1.06200, tp2=1.06950, out_path=out_file)

    # Test Telegram sendPhoto with Inline Keyboard
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendPhoto"
    caption = (
        "⚡ <b>[سیگنال هوشمند ورود MT5 با حد ضرر ضد هانت]</b>\n"
        "━━━━━━━━━━━━━━━━━━━━━\n"
        "📈 <b>نماد:</b> <code>EURUSD</code> (خرید BUY)\n"
        "🎯 <b>استراتژی:</b> Macro Divergence + D1/H4 Liquidity Sweep\n"
        "💵 <b>قیمت ورود:</b> <code>1.05420</code>\n"
        "🛡️ <b>حد ضرر داینامیک ضد هانت (SL):</b> <code>1.04880</code> (-54 pips)\n"
        "    ↳ <i>فراتر از نقدینگی کف قبلی + 1.5 ATR-14 برای دفع فریب بروکر</i>\n"
        "🎯 <b>حد سود اول (TP1):</b> <code>1.06200</code> (+78 pips)\n"
        "🚀 <b>حد سود دوم (TP2):</b> <code>1.06950</code> (+153 pips)\n"
        "⚖️ <b>نسبت R/R:</b> <code>1:2.8</code> | <b>حجم:</b> <code>0.01 Lot</code>\n"
        "💼 <b>حساب:</b> <code>#57452535486</code> (GTC-Demo $100.00)\n"
        "━━━━━━━━━━━━━━━━━━━━━\n"
        "🔒 <i>ریسک‌فری خودکار پس از رسیدن به +25 پیپ فعال است.</i>"
    )

    inline_keyboard = {
        "inline_keyboard": [
            [
                {"text": "💼 بالانس و وضعیت اکانت ($100)", "callback_data": "btn_balance"},
                {"text": "📊 پوزیشن‌های باز", "callback_data": "btn_positions"}
            ],
            [
                {"text": "📸 عکس چارت زنده", "callback_data": "btn_chart"},
                {"text": "📈 سیگنال‌های ۲۸ جفت ارز", "callback_data": "btn_signals"}
            ]
        ]
    }

    with open(out_file, "rb") as f:
        files = {"photo": ("chart.png", f, "image/png")}
        data = {
            "chat_id": CHAT_ID,
            "caption": caption,
            "parse_mode": "HTML",
            "reply_markup": json.dumps(inline_keyboard)
        }
        res = requests.post(url, data=data, files=files, proxies=PROXIES, timeout=12)
        print("Telegram sendPhoto Response:", res.status_code, res.text)


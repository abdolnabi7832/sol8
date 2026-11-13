# Forex Intelligence Suite — Institutional Command Center

## Phases 1, 2 & 3: Institutional Core Engine, 7-Gate Risk Shield & REST/WebSocket APIs

### 1. Executive Summary & Architecture Stack
The **Forex Intelligence Suite — Institutional Command Center** is an institutional quantitative Forex trading platform modeled on the macroeconomic methodology of former Goldman Sachs trader **Anton Kreil**.

- **Runtime:** Java 21 LTS + Spring Boot 3.3.5
- **Time-Series Persistence:** Disk-based H2 Database (`AUTO_SERVER=TRUE`) synchronized atomically with `./data/Excel_Logs` (`.json` and `.csv`).
- **Mathematical Engine:** Package `com.quantfx.suite.engine` implementing Anton Kreil 6-Pillar Macro Scoring v2.0, exponential time-decay, Taylor Rule deviation, 4-regime state machine, and predictive nowcasting.
- **Risk Shield Engine:** 7-Layer Zero-Risk Protocol (`PairSignalMatrixEngine.java`) filtering all 28 Forex cross pairs.
- **Capital Management:** Fractional Kelly Criterion, 1000-Path Monte Carlo ruin simulator, Parametric VaR, and HHI Concentration Index (`MonteCarloRiskEngine.java`).
- **AI Copilot:** Gemini API intelligent proxy with real-time institutional dashboard state injection and deterministic fallback (`QuantAiCopilotService.java`).
- **Unified Deployment:** WebMvc serves SPA frontend from `classpath:/static/` on the same port (`8080`) as the REST APIs.

---

### 2. Anton Kreil 7-Layer Risk Protocol (7-Gate Shield)

Every single one of the **28 canonical currency pairs** must pass through the 7 Security Gates before signal generation:

1. **Gate 1 (Macro Divergence & Regime Gate):**
   - Net spread: $\Delta \text{Score} = \text{Score}_B - \text{Score}_Q \in [-20.0, +20.0]$.
   - Regime divergence validation: Highest conviction occurs when one currency is in `HAWKISH_EXPANSION` or `GOLDILOCKS_GROWTH` and the counterpart is in `STAGFLATION_TRAP` or `DEFLATIONARY_RECESSION`.
2. **Gate 2 (Carry Trade & Central Bank Divergence Gate):**
   - Rate spread: $\text{CarryDiff} = \text{Rate}_B - \text{Rate}_Q$.
   - Directional compliance: Severe carry drag ($> 2.0\%$ against trade direction) cancels `STRONG` tier and applies $-15\%$ confidence penalty.
3. **Gate 3 (Data Freshness & Timestamp Integrity Gate):**
   - Recency verification: If $> 40\%$ of indicators in Pillars 1-4 are `STALE`, confidence penalized by $-20\%$.
   - Ultra-fresh catalyst ($\Delta t \le 3$ days) aligned with signal direction awards $+10\%$ confidence boost.
4. **Gate 4 (Multi-Stage Temporal News Shield):**
   - $\Delta t \le 12$ hours or $\le 1$ hour post-release: `🔴 NEWS_LOCKED` $\rightarrow$ Signal forced to `LOCKED (NEWS SHIELD)`.
   - $12 < \Delta t \le 36$ hours: `🟡 PRE_NEWS_CAUTION` $\rightarrow$ Position size throttled to $0.50\times$.
   - $\Delta t > 36$ hours: `🟢 CLEAR_HORIZON`.
5. **Gate 5 (Multi-Timeframe Structure Gate - HTF/MTF/LTF):**
   - Alignment with Daily HTF structure. Macro bullish with Bearish HTF structure flags `STRUCTURE_MACRO_CONFLICT` and transitions signal to `WAIT FOR BOS`.
6. **Gate 6 (Portfolio Correlation & Single-Currency Exposure Gate):**
   - Dynamically audits active portfolio risk across all 8 currencies. Prevents net single-currency risk from exceeding configured ceiling (e.g. $2.0\%$).
7. **Gate 7 (Execution Quality & ATR Volatility Stop Gate):**
   - If key catalyst is ultra-fresh ($\le 72$ hours), ATR stop loss is automatically widened by $1.25\times$ and lot size scaled down to preserve identical dollar risk while avoiding wick/shadow stopouts.

#### Signal Tiers:
- `💎 ULTRA-SAFE BUY` / `💎 ULTRA-SAFE SELL`: Passes all 7 gates, $|\Delta\text{Score}| \ge 5.0$, Confidence $\ge 80\%$.
- `🟢 STRONG BUY` / `🔴 STRONG SELL`: $|\Delta\text{Score}| \ge 4.0$ without news lock.
- `🟢 BUY` / `🔴 SELL`: $2.2 \le |\Delta\text{Score}| < 4.0$.
- `🔒 LOCKED (NEWS SHIELD)`: News lock active.
- `⏳ WAIT FOR BOS`: Macro/structure conflict.
- `⚪ NO TRADE / RANGE`: $|\Delta\text{Score}| < 2.2$.

---

### 3. Quantitative Risk & Capital Management

- **Fractional Kelly Criterion:** $K = \max\left(0, W - \frac{1-W}{R}\right)$. Delivers Full Kelly, Half Kelly ($0.5K$), and Quarter Kelly ($0.25K$ - Institutional Ultra-Safe sizing).
- **1000-Path Monte Carlo Simulator:** Simulates 100-trade paths to determine **Probability of Ruin (%)**, median equity ($P50$), 5th percentile worst-case ($P5$), and 95th percentile best-case ($P95$).
- **Parametric VaR (95% & 99%):** Value at Risk based on portfolio volatility.
- **Herfindahl-Hirschman Concentration Index (HHI):** $\text{HHI} = \sum_{j=1}^N (s_j \times 100)^2$ with real-time USD and IRT (Toman) equity conversion.

---

### 4. REST API Endpoints Specification

| Method | Endpoint | Description |
| :---: | :--- | :--- |
| `GET` | `/api/v1/bootstrap` | Single comprehensive payload: 8 currencies, 20 indicators, calendar, 28 pairs signals, portfolio, active trades, journal, psychology, market structure, settings |
| `POST` | `/api/v1/scraper/sync-all` | Immediate execution of macro & calendar scrapers with WebSocket broadcast |
| `PUT` | `/api/v1/macro/{ccy}` | Updates indicators, dates, or switches mode (AUTO/MANUAL/HYBRID) with instant dual file sync |
| `POST` | `/api/v1/predict/event/{eventId}` | Runs 3-scenario branch analysis (Beat, Inline, Miss) for calendar release |
| `POST` | `/api/v1/predict/what-if` | Hypothetical scenario simulator for 8 currencies and 28 pairs |
| `GET/POST`| `/api/v1/portfolio` | Retrieve / update portfolio balance, equity, IRT exchange rate, drawdown |
| `GET/POST`| `/api/v1/active-trades` | Retrieve / open active trade positions |
| `DELETE` | `/api/v1/active-trades/{tradeId}` | Closes trade and automatically records to Trade Journal |
| `POST` | `/api/v1/execution` | Instant order execution with 7-gate position sizing |
| `GET/POST`| `/api/v1/journal` | Trade journal management |
| `GET/POST`| `/api/v1/psychology` | Daily mindset, discipline, sleep, and emotional logs |
| `GET/POST`| `/api/v1/market-structure`| Technical trend and market structure (BOS, CHoCH) per pair |
| `GET/POST`| `/api/v1/settings` | User risk settings, Gemini API key, language, theme |
| `POST` | `/api/v1/risk/monte-carlo`| Runs 1000-path Monte Carlo ruin simulation |
| `GET` | `/api/v1/risk/kelly` | Computes Fractional Kelly sizing |
| `GET` | `/api/v1/risk/snapshot` | Returns real-time VaR, HHI, and Toman equity snapshot |
| `POST` | `/api/v1/ai/chat` | AI Copilot reasoning proxy with full real-time dashboard context |
| `WS` | `/ws-forex` | STOMP WebSocket topics (`/topic/macro-updates`, `/topic/calendar-updates`) |

---

### 5. Running the Complete Backend

```powershell
cd "backend-java"

# Execute all 22 Unit and Integration Tests
.\mvnw.cmd test

# Run Spring Boot Institutional Server
.\mvnw.cmd spring-boot:run
```

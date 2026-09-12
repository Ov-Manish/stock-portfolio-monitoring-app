# Stock Portfolio Monitoring App — Backend Architecture Report
### Monolith vs Microservices | Java / Spring Boot / PostgreSQL / Kafka / RabbitMQ

---

## 1. Executive Summary

This is a **backend-only, event-driven portfolio tracking system** for Indian equities (NSE/BSE). It is not a trading/execution platform — no order placement, no broker integration for trading. The core engineering problems are:

1. Ingesting continuously-changing market prices (Kafka) without hammering PostgreSQL on every tick.
2. Keeping portfolio valuation (gain/loss) correct and fast for many users watching overlapping stocks.
3. Evaluating alert thresholds without duplicate/missed notifications, and delivering notifications asynchronously (RabbitMQ).

**Recommendation (short form, justified in §24):** Build a **Modular Monolith** first. Microservices are designed here in full because you explicitly asked for both, and because decomposing a well-modularized monolith later is materially easier — but for a solo/fellowship project, a distributed system multiplies operational and testing overhead (5+ databases, Kafka, RabbitMQ, service discovery, distributed tracing) without a corresponding *learning-per-hour* payoff at this stage, given you already built microservices once with Fundoo Notes. If the goal is specifically to demonstrate microservices maturity for placements, the Microservices design here is implementable as-is.

---

## 2. Requirements Analysis & Scope Guardrails

In scope: registration/login, JWT auth, portfolio CRUD (manual + Excel upload), watchlists, market-price monitoring, threshold alerts, gain/loss analytics, dividend info, 52-week high/low, activity feed.

Explicitly **out of scope** (per your instruction and financial-domain research): order execution, options/futures, crypto, payments, AI recommendations, brokerage integration for trading. Where Zerodha/Upstox/Angel One appear below, it is **only as a market-data source**, not for placing trades.

---

## 3. Confirmed vs Ambiguous vs Conflicting Requirements

| Item | Status | Notes |
|---|---|---|
| Stock-level alerts | CONFIRMED | Explicit in Use Case |
| Portfolio-level alerts | AMBIGUOUS/CONFLICTING | Use Case only describes stock-level; mentor mentioned portfolio-level. Both are designed below (§9) as separate `alert_scope`. **Ask mentor which is required for MVP.** |
| Kafka for market data | CONFIRMED | Mentor's explicit instruction |
| RabbitMQ for alerts/notifications | ASSUMED/RECOMMENDED | Not explicitly mandated by mentor beyond "async notification processing" — but it's the natural fit (see §8 for why Kafka ≠ RabbitMQ here) |
| Excel upload format | AMBIGUOUS | Use Case says `.xls` (legacy Excel 97-2003, Apache POI `HSSFWorkbook`). Modern exports are usually `.xlsx`. **Ask mentor** whether both must be supported — recommend supporting both via Apache POI's `WorkbookFactory` (auto-detects format), trivial extra cost. |
| Stock master fields (ISIN, exchange, sector) | ASSUMED | Use Case doesn't specify; financial-domain research says ISIN + exchange are necessary for correctness (same company can list on NSE and BSE with different tickers but one ISIN). Included in schema. |
| Real-time meaning | AMBIGUOUS | No sub-second requirement stated. Recommend defining "real-time" as **≤5–15 second staleness** via polling/websocket-to-Kafka bridge (§6), not tick-by-tick — see justification below. |
| Multiple watchlists per user | ASSUMED | Use Case implies one, but data model supports N at near-zero extra cost — recommend allowing multiple named watchlists. |
| Transactions (buy/sell history) vs Holdings-only | AMBIGUOUS | Use Case's "manual holding creation / update quantity/buy price" reads as a **current-state model** (Holding is the source of truth), not a ledger of trades. A Transaction table is **not required** for MVP scope as written — flagged as a mentor question. |

---

## 4. Financial Domain Model — What's Actually Needed

| Concept | Table / Field / Derived? | Reasoning |
|---|---|---|
| Stock / Instrument | **Table** (`stock`) | Master data, validated against on upload |
| Company | Folded into `stock` (name, sector) | A separate `company` table only pays off if you model multi-listing (NSE+BSE same company) — do it via `isin` field instead, simpler for this scope |
| Exchange | **Field** on `stock` (`exchange` enum: NSE/BSE) | Needed because ticker alone isn't globally unique |
| Trading Symbol / ISIN | **Fields** on `stock` | ISIN is the true unique identity of an instrument across exchanges |
| Market Quote / Current Price | **Table**, latest-only (`market_price`) | See §7 storage strategy |
| Historical Price / OHLCV | **Table**, retained selectively (`price_history_daily`) | Needed for 52-week high/low, charts |
| Volume | Field on price tables | Not used elsewhere in this scope |
| Dividend | **Table** (`dividend`) | Explicit requirement |
| 52-week High/Low | **Derived**, cached | Computed from `price_history_daily`, refreshed daily (not per-tick) — recomputing per tick is wasteful and the number barely moves intraday |
| Portfolio | **Table** | Root aggregate per user |
| Holding | **Table** | Quantity + avg buy price = source of truth (see §9.1) |
| Position | Not used | "Position" is a trading-platform term (implies you can be short); not applicable — holdings are always long, quantity ≥ 0 |
| Transaction | **Not required** for current scope (see §3) — noted as future extension | |
| Invested Value | **Derived** = `quantity × avg_buy_price` | Never stored, always recomputed |
| Current Value | **Derived** = `quantity × latest_price` | Never stored |
| Unrealized P&L | **Derived** = `current_value − invested_value` | |
| Realized P&L | Not applicable | No sell/close-position tracking in scope |
| Daily Gain/Loss | **Derived**, needs previous close | `(current_price − prev_close) × quantity`; `prev_close` comes from `price_history_daily` |
| Portfolio Allocation | **Derived** at read time | `holding_current_value / portfolio_total_current_value` |

**Confirmed relationship:** `User → Portfolio → Holding → Stock` is correct: one user has (typically) one portfolio; a portfolio has many holdings; each holding references exactly one stock. `User → Watchlist → WatchlistStock → Stock` mirrors this pattern for watch-only stocks.

---

## 5. Finance API Research & Recommendation

Researched current (2026) official sources for NSE/BSE data access relevant to a Java/Spring backend.

**Zerodha Kite Connect** — <cite index="3-1">the full API suite with real-time WebSocket streaming and historical candle data costs ₹500/month</cite>, a price drop from the old ₹2000+₹2000 split. <cite index="4-1">The free "Personal" plan omits both live and historical market data — it only exposes your own order/portfolio management.</cite> Officially supported Java client exists (`javakiteconnect`), well documented, but **requires a funded/active Zerodha trading + demat account** to generate API credentials — a real barrier for a student project with no brokerage account.

**Upstox API v2** — Free to integrate, with <cite index="12-1">a rate limit of 10 requests/second and WebSocket support for up to 100 symbols per connection</cite>, covering order management, historical data, live market feed, and portfolio data. Same constraint as Zerodha: **an active Upstox trading account is required** to obtain an API key.

**Angel One SmartAPI** — Same broker-account-gated model as the above two; not independently verified here in depth since the account barrier rules it out for a project without a live demat account, same as Zerodha/Upstox.

**Twelve Data** — <cite index="26-1">offers free stock data APIs covering the US, Canada, India, and 50+ other exchanges</cite>, no brokerage account needed — just an API key. Free tier is rate-limited (historically ~8 req/min, 800 req/day) and lacks WebSocket streaming on the free plan (WebSocket is a paid-tier feature per third-party comparisons). Good fit for **polling-based** ingestion.

**Alpha Vantage / Finnhub** — Global-market-first providers with weaker/inconsistent NSE coverage historically; not chosen as primary because NSE/BSE symbol coverage and Indian-specific fields (like ISIN mapping) are less reliable than India-focused sources — worth re-verifying directly with their docs if pursued.

### Recommendation
- **Primary (for a project without a live brokerage account): Twelve Data**, polling-based ingestion (see below for why polling is fine here). It doesn't need a demat account, has a workable free tier for a portfolio of a modest stock universe, and has an official Java-friendly REST API.
- **If you do have/can get a Zerodha or Upstox account: Kite Connect (₹500/mo) or Upstox (free)** as primary, since both provide true WebSocket ticks and are the "textbook correct" architecture your mentor is likely expecting, with Twelve Data as a documented backup/fallback provider in your reliability section (§19).
- **Backup provider:** whichever of the above you don't pick as primary — the ingestion layer should be written against an internal `MarketDataProvider` interface so swapping is a config change, not a rewrite (Adapter pattern, detailed in §6).

### Polling vs WebSocket — what "real-time" realistically means here
For a portfolio-monitoring (not trading) app, sub-second ticks are unnecessary — a user checking gain/loss doesn't need millisecond freshness. **Recommend: poll every 5–15 seconds during market hours (9:15 AM–3:30 PM IST) for symbols that are actually held/watched by at least one user**, falling back to a longer interval or none outside market hours. If you have WebSocket access (Kite/Upstox), consume the WebSocket in the ingestion service and publish to Kafka at the same throttled cadence — Kafka should carry **normalized, deduplicated** price-change events, not raw exchange ticks.

---

## 6. Market Data Architecture — Ingestion Pipeline

```
Finance API (poll or WebSocket)
        │
        ▼
Market-Data Ingestion Service (Spring Boot component/service)
  - MarketDataProvider interface (TwelveDataAdapter / KiteAdapter)
  - Maps External API DTO → Internal PriceQuote model
  - Dedup: only emit if price changed vs last-seen-in-memory value
        │
        ▼
Kafka Producer → Topic: market.price.updated
        │
        ▼
Kafka Consumer (Price Persistence Consumer)
  - Upserts market_price (latest-only table)
  - Appends to price_history_daily at most once/day (EOD close) or on first-seen-today
        │
        ├──▶ Consumer Group: portfolio-valuation
        │      (triggers recompute of affected holdings' derived values — see §14 scalability)
        │
        └──▶ Consumer Group: alert-evaluation
               (checks thresholds, may emit alert.triggered → RabbitMQ)
```

**External API DTO → Internal Model → Kafka Event** (do not forward the vendor payload as-is):
1. `TwelveDataQuoteDTO` / `KiteTickDTO` — raw shape owned by the vendor, changes without notice.
2. `PriceQuote` — internal domain model: `symbol, exchange, price (BigDecimal), asOf (Instant)`.
3. `StockPriceUpdatedEvent` — the Kafka wire event, versioned and stable regardless of vendor.

```json
{
  "eventId": "uuid",
  "schemaVersion": 1,
  "stockId": "uuid",
  "symbol": "INFY",
  "exchange": "NSE",
  "price": 1701.25,
  "currency": "INR",
  "timestamp": "2026-09-02T09:20:11Z",
  "source": "twelvedata",
  "correlationId": "uuid"
}
```

This decoupling means a vendor switch (Twelve Data → Kite) only touches the adapter layer, never the Kafka contract or downstream consumers.

### Java Stream API vs Kafka Streams (explicit distinction, since the Use Case conflates them)
- **Java Stream API** (`java.util.stream`) is an **in-JVM, synchronous, in-memory** functional API for iterating over a `Collection` — e.g., `holdings.stream().map(h -> h.currentValue()).reduce(...)` to sum a portfolio's current value inside one request. It has nothing to do with messaging.
- **Kafka Streams** is a **separate library** for building stream-processing topologies that consume/produce Kafka topics continuously, across the network, stateful with RocksDB-backed stores. Not required at this project's scale — a plain `@KafkaListener` consumer is sufficient; introducing Kafka Streams here would be unjustified complexity. Use Java Stream API for in-memory portfolio math; use Kafka only for the price/alert event pipeline.

---

## 7. Market Price Storage Strategy

| Option | Verdict |
|---|---|
| A. Store every price event | Rejected — at 5–15s polling × N symbols, this grows unbounded for no query benefit; nothing in this app needs tick-level history |
| B. Store only latest price | Insufficient alone — no 52-week high/low, no daily gain/loss (needs previous close) |
| **C. Latest + selected history (chosen)** | `market_price` (1 row per stock, upserted) + `price_history_daily` (1 row per stock per trading day: open/high/low/close/volume) |
| D. Latest + OHLC aggregates only | Subsumed by C — daily OHLC *is* the selected history here |
| E. Hybrid with Redis cache | Recommended as an **addition**, not a replacement — see §14 |

**Why C:** `market_price` answers "what's the current price" in O(1) with an index on `stock_id`, used on every portfolio-valuation read. `price_history_daily` answers 52-week high/low (`MAX(high)`/`MIN(low)` over 252 trading days) and daily gain/loss (`close` from yesterday's row) without unbounded growth — roughly 252 rows/year/stock, trivial. Index `(stock_id, price_date)`; consider partitioning `price_history_daily` by year once it's large (not needed at project scale).

---

## 8. Kafka Architecture (Market Data)

| Decision | Choice | Why |
|---|---|---|
| Topic name | `market.price.updated` | Domain-event naming, dot-namespaced |
| Partitions | 6 (tune to broker count; over-provision modestly since partition count can only increase later) | Parallelism across consumer instances |
| **Partition key** | `symbol` (e.g. `"INFY"`, combined with exchange if needed: `"NSE:INFY"`) | Guarantees **ordering per stock** — critical, because out-of-order price updates for the same stock would corrupt "latest price" and duplicate/miss alert triggers. Cross-stock ordering doesn't matter. |
| Hot-partition risk | Real, for index-heavy stocks (RELIANCE, INFY get updated far more often than small-caps) | Acceptable at this scale (dozens–hundreds of symbols); if it became a real problem, sub-partitioning by `symbol + time-bucket` is the standard mitigation, not needed here |
| Consumer groups | `price-persistence`, `portfolio-valuation`, `alert-evaluation` — separate groups so each gets every message independently | Fan-out without coupling |
| Delivery semantics | At-least-once (Kafka default with manual offset commit after successful processing) | Simpler than exactly-once transactions; idempotency handles duplicates (below) |
| Idempotency | `eventId` unique constraint / upsert keyed by `(stock_id)` for `market_price`; alert evaluation checks "already triggered and not yet cooled down" before emitting | Prevents duplicate DB writes and duplicate alerts on redelivery |
| Retry / DLT | 3 retries with backoff (Spring Kafka `DefaultErrorHandler` + `ExponentialBackOff`), then to `market.price.updated.DLT` | Standard Spring Kafka pattern |
| Retention | 24–48 hours on the topic itself (it's a pipe, not a store — `price_history_daily`/`market_price` in Postgres are the store) | Keeps broker storage small; replay window is enough to recover from consumer downtime |
| Offset strategy | Manual commit after DB write succeeds (not auto-commit) | Avoids "committed but not persisted" data loss on crash |

---

## 9. Portfolio / Holding & Alert Business Rules

### 9.1 Holding as source of truth
`Holding` stores **`quantity`** and **`avg_buy_price`** (BigDecimal) — these are input data, not derivable from anything else, so they are the source of truth. Current price, current value, and P&L are **never stored on Holding** — always computed at read time from `Holding × market_price`, using Java Stream API to map/reduce across a portfolio's holdings. Portfolio totals are likewise always derived, not stored, to avoid staleness/consistency bugs (a stored "portfolio total" would need to be updated on every price tick for every affected user — expensive and error-prone; computing on read, aided by caching per §14, is simpler and always correct).

**Average buy price** should exist and be recalculated on each new manual/uploaded lot addition using the weighted-average formula:
`new_avg = ((old_qty × old_avg) + (added_qty × added_price)) / (old_qty + added_qty)`

**Portfolio Snapshots** — not required for the stated scope (no historical portfolio-value chart requested), but cheap to add later as a nightly batch job writing one row per portfolio per day if a "portfolio value over time" chart becomes a requirement.

### 9.2 Alert state machine
Alert Definition (user-configured) → Alert Event (a trigger instance) → Notification (delivery record) are modeled as **three separate tables**, not one, because a single definition can fire many times over its life and each firing needs independent audit/history.

```
Alert Definition: { stockId | portfolioId, direction: UPPER|LOWER, thresholdValue,
                     baselineType: ABSOLUTE_PRICE | PERCENT_FROM_REFERENCE,
                     referencePrice, isActive, repeatable, cooldownMinutes }
```

Using your example — INFY reference ₹1690, upper threshold ₹1700:
- ₹1690 → ₹1699: no alert (not crossed).
- ₹1699 → ₹1701: **threshold crossed** (previous price was below, new price is at/above) → fire `AlertEvent`, set `last_triggered_at`.
- ₹1701 → ₹1710: **recommended logic:** if `repeatable = false` (one-time), do **not** re-fire while price stays above threshold — it already fired. If `repeatable = true`, only re-fire after price re-crosses back below the threshold and crosses above again ("re-arm on reset"), **not** on every further increase — otherwise a stock hovering at the boundary spams alerts. A `cooldown_minutes` field is an optional simpler alternative (suppress re-fire for N minutes regardless of re-arm) if the mentor prefers that over reset-based re-arming — **flag as a mentor question**.

This crossing-detection requires comparing **previous price vs new price** against the threshold, not just checking "is current price above threshold" — so alert evaluation needs the last-known price (available from `market_price`, or carried in-memory in the consumer, before it's overwritten).

Stock-level alerts evaluate against a single stock's price. Portfolio-level alerts (per mentor's mention) evaluate against **overall portfolio gain/loss %**, recomputed portfolio-wide on every relevant price tick — more expensive, and its exact definition (which stocks trigger recompute? all holdings, or only on holdings that changed?) is genuinely ambiguous and should be confirmed with the mentor before implementation.

---

## 10. RabbitMQ Architecture (Alerts/Notifications)

```
Alert Evaluation (Kafka consumer) → emits AlertTriggeredEvent
        │
        ▼
RabbitMQ Exchange: "alerts.exchange" (type: topic)
        │  routing key: "alert.stock.triggered" / "alert.portfolio.triggered"
        ▼
Queue: "notification.email.queue"  (bound with routing key "alert.*.triggered")
Queue: "notification.sms.queue"    (optional, bound same way)
        │
        ▼
Notification Consumer → Email/SMS Provider (e.g. SMTP / SendGrid, Twilio)
        │
        ▼
notification table updated: SENT | FAILED
```

- **Exchange type: topic** (not direct/fanout) — lets you route stock-alerts and portfolio-alerts to potentially different queues later without renaming anything, using routing-key patterns.
- **Acknowledgement:** manual ack, only after the notification provider call succeeds (or after durably recording a retry-pending state) — never auto-ack before delivery is attempted.
- **Retry / DLX:** each queue configured with a Dead Letter Exchange; failed messages (SMTP down, rate-limited) retried with a delay queue (TTL + DLX "retry loop" pattern) up to N times, then parked in `notification.dlq` for manual/alerted inspection.
- **Persistence:** durable exchange, durable queue, persistent messages — a crash must not silently drop a pending notification.
- **Idempotency/duplicate prevention:** unique constraint on `(alert_event_id, channel)` in the `notification` table — if the same `AlertEvent` is redelivered (Kafka at-least-once upstream, or RabbitMQ redelivery after a crash before ack), the insert is a no-op via `ON CONFLICT DO NOTHING`, so the user never gets duplicate emails for one trigger.

**Why RabbitMQ here and Kafka for market data:** market data is a high-throughput, replayable **stream** with many independent consumer groups reading the same events (persistence, valuation, alerts) — Kafka's log-based retention and consumer-group fan-out fit that. Alerts→notifications is a **classic task queue**: one message, one job (send this email), needs routing/priority/DLQ semantics, and RabbitMQ's per-message ack/retry model is the more natural, lighter-weight fit than repurposing Kafka as a task queue.

---

## 11. Activity Feed, Dividend, 52-Week Data

- **Activity Feed:** a genuine **table** (`activity`), not derived — it's an audit/UX log of discrete events (`portfolio_created`, `holding_added`, `holding_deleted`, `watchlist_updated`, `alert_triggered`, etc.), each with `user_id, type, message, metadata (JSONB), created_at`. Append-only, indexed on `(user_id, created_at DESC)`, cursor-paginated (see §17). Retention: keep indefinitely at this scale, or archive >12 months old if it grows large — not a concern for a fellowship project's data volume.
- **Dividend:** table (`dividend`) with `stock_id, amount_per_share, ex_dividend_date, record_date, payment_date, frequency`. Sourced from the Finance API where available (Twelve Data has fundamentals endpoints) or manually seeded for a demo dataset if the free tier doesn't expose it reliably — worth verifying directly against current Twelve Data docs before committing.
- **52-week High/Low:** derived from `price_history_daily` (`MAX(high)`/`MIN(low)` over the trailing 252 trading days), recomputed **once daily** (scheduled job) and cached on the `stock` row (`week52_high`, `week52_low` — these are the one deliberate exception to "always derive," because recomputing a 252-row aggregate on every portfolio read is wasteful for a number that only needs daily freshness).

---

## 12. MONOLITH — Database Design

### 12.1 Design stance: Modular Monolith
Package-by-feature (mirrors your Fundoo Notes structure), single PostgreSQL database, but **module boundaries are enforced in code** (no module reaches into another's repository directly — only through a service interface), so a future split to microservices is a refactor, not a rewrite.

### 12.2 Tables

**`users`**
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | default `gen_random_uuid()` |
| name | VARCHAR(100) NOT NULL | |
| email | VARCHAR(255) NOT NULL UNIQUE | |
| password_hash | VARCHAR(255) NOT NULL | BCrypt |
| created_at, updated_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

**`stock`**
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| symbol | VARCHAR(20) NOT NULL | |
| exchange | VARCHAR(10) NOT NULL CHECK (exchange IN ('NSE','BSE')) | |
| isin | VARCHAR(12) | nullable if unavailable from provider |
| company_name | VARCHAR(255) NOT NULL | |
| sector | VARCHAR(100) | |
| currency | VARCHAR(3) NOT NULL DEFAULT 'INR' | |
| status | VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DELISTED')) | |
| week52_high, week52_low | NUMERIC(14,4) | cached, see §11 |
| UNIQUE(symbol, exchange) | | |

**`portfolio`**
| id UUID PK | user_id UUID FK→users UNIQUE (1:1 for MVP) | created_at, updated_at |

**`holding`**
| id UUID PK | portfolio_id FK→portfolio | stock_id FK→stock | quantity NUMERIC(18,4) NOT NULL CHECK (quantity >= 0) | avg_buy_price NUMERIC(14,4) NOT NULL CHECK (avg_buy_price >= 0) | version INT (optimistic lock) | created_at, updated_at | **UNIQUE(portfolio_id, stock_id)**

**`market_price`** (latest-only)
| stock_id UUID PK/FK→stock | price NUMERIC(14,4) NOT NULL | as_of TIMESTAMPTZ NOT NULL | source VARCHAR(30) |

**`price_history_daily`**
| id UUID PK | stock_id FK→stock | price_date DATE NOT NULL | open, high, low, close NUMERIC(14,4) | volume BIGINT | **UNIQUE(stock_id, price_date)**, index on `(stock_id, price_date DESC)`

**`dividend`**: `id, stock_id FK, amount_per_share NUMERIC(10,4), ex_dividend_date DATE, record_date DATE, payment_date DATE, frequency VARCHAR(20)`

**`watchlist`**: `id, user_id FK, name VARCHAR(100) NOT NULL DEFAULT 'My Watchlist', created_at` — UNIQUE(user_id, name)

**`watchlist_stock`**: `id, watchlist_id FK, stock_id FK, added_at` — UNIQUE(watchlist_id, stock_id)

**`alert_definition`**: `id, user_id FK, scope VARCHAR(10) CHECK (scope IN ('STOCK','PORTFOLIO')), stock_id FK NULL, portfolio_id FK NULL, direction VARCHAR(5) CHECK (direction IN ('UPPER','LOWER')), threshold_value NUMERIC(14,4), baseline_type VARCHAR(20), reference_price NUMERIC(14,4), is_active BOOLEAN DEFAULT true, repeatable BOOLEAN DEFAULT false, cooldown_minutes INT, last_triggered_at TIMESTAMPTZ, created_at` — CHECK: exactly one of `stock_id`/`portfolio_id` is non-null, matching `scope`.

**`alert_event`**: `id, alert_definition_id FK, triggered_price NUMERIC(14,4), triggered_at TIMESTAMPTZ, event_source_id UUID` (the Kafka `eventId` that caused it — enables idempotent dedup)

**`notification`**: `id, alert_event_id FK, channel VARCHAR(10) CHECK (channel IN ('EMAIL','SMS')), status VARCHAR(10) CHECK (status IN ('PENDING','SENT','FAILED')), attempt_count INT DEFAULT 0, sent_at TIMESTAMPTZ, created_at` — **UNIQUE(alert_event_id, channel)** (idempotency, §10)

**`activity`**: `id, user_id FK, type VARCHAR(50), message TEXT, metadata JSONB, created_at TIMESTAMPTZ` — index `(user_id, created_at DESC)`

### 12.3 Representative DDL (excerpt — pattern repeats for remaining tables above)

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  symbol VARCHAR(20) NOT NULL,
  exchange VARCHAR(10) NOT NULL CHECK (exchange IN ('NSE','BSE')),
  isin VARCHAR(12),
  company_name VARCHAR(255) NOT NULL,
  sector VARCHAR(100),
  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DELISTED')),
  week52_high NUMERIC(14,4),
  week52_low NUMERIC(14,4),
  UNIQUE (symbol, exchange)
);

CREATE TABLE portfolio (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE holding (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  portfolio_id UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
  stock_id UUID NOT NULL REFERENCES stock(id),
  quantity NUMERIC(18,4) NOT NULL CHECK (quantity >= 0),
  avg_buy_price NUMERIC(14,4) NOT NULL CHECK (avg_buy_price >= 0),
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (portfolio_id, stock_id)
);

CREATE TABLE market_price (
  stock_id UUID PRIMARY KEY REFERENCES stock(id),
  price NUMERIC(14,4) NOT NULL,
  as_of TIMESTAMPTZ NOT NULL,
  source VARCHAR(30)
);

CREATE TABLE price_history_daily (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_id UUID NOT NULL REFERENCES stock(id),
  price_date DATE NOT NULL,
  open NUMERIC(14,4), high NUMERIC(14,4), low NUMERIC(14,4), close NUMERIC(14,4),
  volume BIGINT,
  UNIQUE (stock_id, price_date)
);
CREATE INDEX idx_price_history_stock_date ON price_history_daily (stock_id, price_date DESC);
```

**Why NUMERIC not FLOAT/DOUBLE:** IEEE-754 floats cannot represent most base-10 decimals exactly (`0.1 + 0.2 ≠ 0.3` in binary floating point). For money, that rounding error compounds across quantity × price × many holdings and is unacceptable even at paisa-level precision — PostgreSQL's `NUMERIC` is exact, arbitrary-precision decimal arithmetic, the standard choice for all financial systems.

**UUID vs BIGINT:** UUID (`gen_random_uuid()`, via `pgcrypto`) chosen for all PKs — safe to generate client-side/pre-persist, safe across future service splits (no cross-service ID collisions), avoids leaking row-count information via sequential IDs. Tradeoff: slightly larger index size and worse locality than `BIGINT`/`BIGSERIAL` — acceptable at this data volume; would reconsider only at genuine high-write-throughput scale.

**Optimistic locking:** `holding.version` (JPA `@Version`) — protects against lost updates when a user's Excel upload and a manual edit race, without holding a DB lock.

**Soft delete:** not used for `holding` (hard delete is fine — a deleted holding has no compliance/audit requirement here); `activity` table already captures the "holding was deleted" fact for history.

---

## 13. Monolith ERD (textual)

```
users 1───1 portfolio 1───N holding N───1 stock 1───1 market_price
users 1───N watchlist 1───N watchlist_stock N───1 stock
users 1───N alert_definition ───optional──▶ stock / portfolio
alert_definition 1───N alert_event 1───N notification
users 1───N activity
stock 1───N price_history_daily
stock 1───N dividend
```

---

## 14. Monolith System Design

```
                     ┌─────────────────────────────────────┐
Finance API ───────▶ │  Spring Boot Modular Monolith        │
                     │  ┌────────┐ ┌──────────┐ ┌────────┐ │
                     │  │ auth   │ │ portfolio│ │ market │ │
                     │  │ module │ │  module  │ │ -data  │ │
                     │  └────────┘ └──────────┘ │ module │ │
                     │  ┌────────┐ ┌──────────┐ └────────┘ │
                     │  │watchlist│ │  alert   │ ┌────────┐ │
                     │  │ module │ │  module  │ │activity│ │
                     │  └────────┘ └──────────┘ │ module │ │
                     │        internal Kafka producer/     │
                     │        consumer beans (embedded)     │
                     └───────────────┬───────────────────────┘
                                      │
                        ┌─────────────┼─────────────┐
                        ▼             ▼             ▼
                     Kafka        PostgreSQL      RabbitMQ
                  (market.price   (single DB,     (alerts→
                   .updated)      schema per       notifications)
                                  module optional)
```

Package structure (feature-based, matching your Fundoo Notes conventions):
```
com.stockmonitor
 ├── auth/        (controller, service, service-impl, dto, config — JWT filter chain)
 ├── user/
 ├── stock/        (stock master + Excel-upload validation)
 ├── portfolio/     (portfolio, holding, valuation logic — Java Stream API here)
 ├── marketdata/    (provider adapters, Kafka producer/consumer, market_price, price_history)
 ├── watchlist/
 ├── alert/         (alert_definition, evaluation consumer, alert_event)
 ├── notification/  (RabbitMQ producer/consumer, notification table)
 ├── activity/
 ├── exception/     (GlobalExceptionHandler)
 └── config/
```

### Read-path scalability inside the monolith (100k users / hot stock like INFY)
Recomputing every affected user's portfolio valuation on *every single price tick* doesn't scale (20,000 holders of INFY × every tick). Recommended approach:
- **Don't push valuation on every tick.** Store only `market_price` (already the design). Compute a user's portfolio valuation **on read** (when they open the app / call the API), using Java Streams over their holdings joined with `market_price` — this is O(holdings-per-user), not O(all-users).
- **Cache `market_price` reads in Redis** (or Spring's in-memory `Caffeine` cache if you want to avoid adding Redis as a dependency) with a short TTL (a few seconds) to absorb read bursts without hitting Postgres on every valuation request.
- **Alert evaluation** is the one place that must react to *every* tick (not just on-read) — but it only touches `alert_definition` rows for that specific `stock_id` (indexed), a small, bounded set, not all 20,000 holders.

---

## 15. MICROSERVICES — Bounded Contexts & Database-per-Service

Derived bounded contexts (not 1 service per table):

| Service | Responsibility | Owns DB |
|---|---|---|
| **auth-service** | Register/login, JWT issuance, password hashing | `authdb` (users, credentials) |
| **portfolio-service** | Portfolio, Holding, Excel upload, valuation | `portfoliodb` (portfolio, holding) |
| **stock-service** | Stock master, dividend, 52-week cache | `stockdb` (stock, dividend) |
| **market-data-service** | Provider adapters, Kafka ingestion, latest + historical price | `marketdatadb` (market_price, price_history_daily) |
| **watchlist-service** | Watchlists | `watchlistdb` (watchlist, watchlist_stock) |
| **alert-service** | Alert definitions, evaluation, alert events | `alertdb` (alert_definition, alert_event) |
| **notification-service** | RabbitMQ consumer, email/SMS delivery, notification history | `notificationdb` (notification) |
| **activity-service** | Activity feed aggregation | `activitydb` (activity) |

Justification for **not** further splitting: `auth` + `user` merge into one service (identity is one bounded context here — no separate "profile management" complexity to justify a split). `notification` is separate from `alert` because alerting (business rule evaluation) and delivery (infrastructure concern, retry/provider-specific) change for different reasons and at different rates — classic separation of concerns.

### No cross-service FKs — how cross-service data is handled
- `holding.stock_id` in `portfolio-service` is **just a UUID**, not an enforced FK — validity is checked at write time via a synchronous REST call to `stock-service` (or a locally cached read model, below), not a DB constraint.
- **Local read models / controlled duplication:** `portfolio-service` maintains a small local cache table `stock_ref (stock_id, symbol, exchange)` populated by consuming a `stock.created`/`stock.updated` event from `stock-service` via Kafka — avoids a synchronous call on every read, standard CQRS-ish read-model pattern.
- **market-data-service → alert-service / portfolio-service:** communicated purely via the `market.price.updated` Kafka topic (already designed in §8) — both are independent consumers, no REST coupling needed for the hot path.
- **Eventual consistency:** e.g., a newly added stock in `stock-service` might take a few hundred ms to appear in `portfolio-service`'s local `stock_ref` cache before it can be used in a holding — acceptable for this domain (not a payments system requiring strict consistency).

---

## 16. Microservices System Design

```
                        ┌───────────────┐
        Client (n/a) ─▶ │  API Gateway   │  (Spring Cloud Gateway) — JWT validation, routing
                        └───────┬───────┘
      ┌───────────┬─────────────┼─────────────┬─────────────┬──────────────┐
      ▼           ▼             ▼             ▼             ▼              ▼
  auth-svc   portfolio-svc  stock-svc   watchlist-svc   alert-svc   activity-svc
   (authdb)   (portfoliodb)  (stockdb)   (watchlistdb)   (alertdb)   (activitydb)
                    │             ▲             
                    │(REST/cache) │             
                    ▼             │             
             market-data-svc ─────┘
              (marketdatadb)
                    │  Kafka: market.price.updated
                    ▼
              alert-svc consumes ──▶ RabbitMQ ──▶ notification-svc (notificationdb) ──▶ Email/SMS
```

Add **service-registry** (Eureka, as in Fundoo Notes) for discovery, and each service independently deployable/scalable. `market-data-service` is the one service worth scaling independently under load (it's the ingestion hot path) — a key argument *for* microservices if ingestion volume genuinely grows (many more symbols/users), and a weak argument *against* if it doesn't (over-engineering for a scale this project won't actually reach).

---

## 17. REST API Design (representative endpoints)

| Method | URL | Notes |
|---|---|---|
| POST | `/api/auth/register` | validates password policy, hashes, 201 or 409 on duplicate email |
| POST | `/api/auth/login` | returns JWT, 401 on bad credentials |
| GET | `/api/stocks?query=INFY` | search stock master, paginated |
| POST | `/api/portfolio/holdings` | manual add, 201 / 400 (validation) / 404 (unknown stock) |
| POST | `/api/portfolio/holdings/upload` | multipart `.xls`/`.xlsx`, 202 (async processing) or 200 with per-row results |
| PUT | `/api/portfolio/holdings/{id}` | update qty/buy price, optimistic-lock 409 on version conflict |
| DELETE | `/api/portfolio/holdings/{id}` | single delete |
| DELETE | `/api/portfolio` | delete entire portfolio, requires confirmation flag in body |
| GET | `/api/portfolio/summary` | totals: invested, current, gain/loss, gain/loss % |
| GET | `/api/watchlists`, `POST /api/watchlists`, `POST /api/watchlists/{id}/stocks` | |
| POST | `/api/alerts` | create alert definition |
| GET | `/api/alerts/{id}/history` | alert_event history, cursor-paginated |
| GET | `/api/activity?cursor=...&limit=20` | **cursor/keyset pagination** (below) |
| GET | `/api/stocks/{id}/dividends`, `/api/stocks/{id}/52-week` | |

**Pagination:** use **cursor/keyset** (`created_at, id` composite cursor) for `activity` and `alert history` — these grow unbounded and offset pagination degrades (`OFFSET 50000` scans and discards 50,000 rows). Use simple **offset/page** for `stocks` search results — bounded, small result sets, and users expect page numbers in a search UI.

All authenticated endpoints require `Authorization: Bearer <JWT>`; standard 400/401/403/404/409/422/500 status codes; request validation via Bean Validation (`@Valid` + `@NotNull`/`@DecimalMin` etc.) on DTOs.

---

## 18. Scalability Strategy (100k users, 20k holding INFY)

| Approach | Verdict |
|---|---|
| Query all holdings on every price event | **Rejected** — O(20,000) DB writes per tick, doesn't scale |
| Precomputed/stored portfolio state, updated per tick | **Rejected as default** — expensive fan-out, staleness/consistency risk (§9.1 already argues against storing derived values) |
| **On-read computation + short-TTL cache for `market_price`** | **Recommended** — described in §14; scales because cost is proportional to *active readers*, not *total holders* |
| Materialized/precomputed state for very hot paths only | Optional future optimization if a specific endpoint (e.g. a leaderboard-style "top movers") genuinely needs it — not needed for per-user portfolio summary |
| Redis for `market_price` | Recommended addition in both Monolith and Microservices designs — same technique, either as a Spring `@Cacheable` layer (monolith) or as `market-data-service`'s dedicated cache in front of `marketdatadb` (microservices) |

---

## 19. Reliability & Idempotency

| Failure | Detection | Recovery | Consistency impact |
|---|---|---|---|
| Finance API down/timeout/rate-limited | HTTP error / timeout in adapter | Circuit breaker (Resilience4j) → fall back to backup provider or skip cycle; `market_price.as_of` grows stale, surfaced to UI as "prices may be delayed" | Stale but not wrong |
| Kafka producer failure | Send callback failure | Retry with backoff; if persistent, alerting/ops visibility | Missed price update for that tick — next successful poll corrects it |
| Kafka consumer failure / offset commit failure | Exception in listener | Spring Kafka retry + DLT (§8) | At-least-once — duplicates handled by upsert idempotency |
| Duplicate Kafka event | `eventId` seen before | Upsert semantics on `market_price` (last-write-wins by `as_of`), `alert_event.event_source_id` unique check | No double-alerting |
| PostgreSQL failure | Connection pool errors | Standard managed-Postgres HA/replica failover (infra-level, outside app scope) | Requests fail fast (503) during outage |
| RabbitMQ failure | Publish confirm failure | Durable queues + publisher confirms; retry queue | Notification delayed, not lost, once broker recovers |
| Email/SMS provider failure | Non-2xx from provider | Retry with DLX delay pattern (§10), cap attempts, mark `FAILED` after N | User sees alert in-app (activity feed) even if email fails |
| Duplicate alert / duplicate notification | `alert_event.event_source_id` unique; `notification (alert_event_id, channel)` unique | `ON CONFLICT DO NOTHING` | Prevented, not just detected |

**Security:** JWT (short-lived access token, refresh-token pattern), BCrypt password hashing, `@PreAuthorize`/method security for authorization, Bean Validation for input, Excel upload restricted by file size/type/row-count with virus-scan-worthy handling of untrusted binary input (Apache POI, not `eval`-style parsing — inherently safer), Finance API/Kafka/RabbitMQ/DB credentials via environment variables or a secrets manager (never in `application.yml` committed to git) — Spring Cloud Config with encrypted values if using microservices.

**Observability:** Spring Boot Actuator + Micrometer → Prometheus metrics for all services; structured logging (correlation/trace ID propagated from HTTP request through Kafka `correlationId` through RabbitMQ headers) — you already have Zipkin/Micrometer Tracing experience from Fundoo Notes, directly reusable here across the Kafka/RabbitMQ hops too (trace context propagation through message headers, not just HTTP).

---

## 20. Testing Strategy

- **Unit tests:** valuation math (gain/loss formulas), alert crossing-detection logic (pure functions, easy to test exhaustively with the INFY example table from §9.2), weighted-average buy-price calculation.
- **Repository tests:** `@DataJpaTest` with Testcontainers PostgreSQL — verify constraints (UNIQUE, CHECK) actually fire.
- **Controller/REST tests:** `@WebMvcTest` + MockMvc for validation/status-code contracts; Postman collection for manual/exploratory + CI-runnable via Newman.
- **Kafka:** `@EmbeddedKafka` or Testcontainers Kafka — test producer serialization, consumer idempotency (send same `eventId` twice, assert one row).
- **RabbitMQ:** Testcontainers RabbitMQ — test DLX/retry behavior by forcing consumer failures.
- **Excel upload:** test with malformed rows, duplicate stocks in one file, unknown ticker — assert partial-success/error reporting, not silent failure.
- **Failure/retry scenarios:** integration test that kills the DB connection mid-consumer-processing and asserts the message is redelivered, not lost.

---

## 21. Monolith vs Microservices — Comparison

| Dimension | Monolith (Modular) | Microservices |
|---|---|---|
| Complexity | Low-moderate | High (8 services, 8 databases, gateway, registry) |
| Dev effort (solo) | Lower — one deployable, one DB connection | Higher — cross-service contracts, more boilerplate |
| Transactions | Simple local `@Transactional` across modules | Distributed — eventual consistency, sagas if needed |
| Joins | Native SQL joins across all entities | Impossible across DBs — replaced by local read models |
| Data duplication | None | Deliberate, controlled (`stock_ref` cache, etc.) |
| Scalability | Scale the whole app together | Scale `market-data-service` independently — real advantage here |
| Failure isolation | A bug in one module can affect the process | A crash in `notification-service` doesn't take down `portfolio-service` |
| Kafka/RabbitMQ | Same design, embedded in one app | Same design, distributed across services |
| Testing | Simpler — one context to spin up | Harder — needs contract tests, more Testcontainers instances |
| Monitoring | One app to instrument | 8 services, more Actuator/Prometheus/Zipkin setup (you have this experience already) |
| Deployment | One artifact/container | 8+ artifacts, more CI/CD | 
| Debugging | Straightforward stack traces | Requires distributed tracing (correlation IDs, already in §19) |
| Operational overhead | Low | High for a solo learner |
| Learning value | You've already demonstrated this pattern (Fundoo Notes) | High if the goal is to *also* demonstrate event-driven microservices with Kafka specifically (which Fundoo Notes didn't use) |
| Production suitability | Fine for this project's real scale | Justified only if ingestion volume/team size actually grows |

---

## 22. Final Recommendation

Given this is (a) backend-only, (b) a fellowship/portfolio project, and (c) you already have a completed microservices project (Fundoo Notes) demonstrating that skill: build the **Modular Monolith** as the primary implementation — it lets you focus your limited time on the genuinely new, valuable parts of *this* project (Kafka market-data pipeline, alert state machine, RabbitMQ notification flow, financial-domain correctness) rather than re-paying microservices infrastructure tax you've already paid once. The module boundaries above are drawn so that a later microservices split (if you want a second demonstration project, or if a mentor requires it) is a mechanical extraction, not a redesign — the DB schema, Kafka topics, and RabbitMQ design in this report are **identical in both versions**, only the deployment topology changes.

If your mentor specifically wants to see event-driven **microservices** as the deliverable (common in fintech-domain fellowships), the Microservices design in §15–16 is directly implementable as specified, with `market-data-service` as the standout justified-for-independent-scaling service.

---

## 23. Questions for Mentor

1. Are **portfolio-level alerts** required for MVP, or is stock-level sufficient for v1? (§3, §9.2)
2. Repeatable alerts: **cooldown-timer** or **re-arm-on-reset** semantics? (§9.2)
3. Must Excel upload support both legacy `.xls` and modern `.xlsx`, or strictly `.xls` as the Use Case states? (§3)
4. Is a **Transaction/trade-history** ledger required, or is Holding (current-state only) sufficient? (§3, §4)
5. Do you have (or can you get) a Zerodha/Upstox account for true WebSocket data, or should I proceed with a no-broker-account provider like Twelve Data? (§5)
6. Is **Monolith or Microservices** the required final deliverable for grading, or is either acceptable with justification?
7. Any specific SLA/definition intended for "real-time" (e.g., must it be <5s, or is 15s acceptable)? (§5)
8. Should Portfolio Snapshots (historical portfolio-value chart) be in scope, or explicitly deferred?

---

## 24. Implementation Roadmap

1. **Foundation:** Postgres schema (§12) + `users`/`auth` module with JWT (reuse Spring Security knowledge from Fundoo Notes gap analysis).
2. **Stock master + Portfolio/Holding CRUD** (manual add first, Excel upload second) — get valuation math (Java Streams) correct and unit-tested before adding any async pipeline.
3. **Market-data ingestion:** provider adapter (Twelve Data or Kite) → Kafka producer → persistence consumer, writing to `market_price`/`price_history_daily`.
4. **Alert engine:** alert_definition CRUD → evaluation consumer → `alert_event`, tested against the INFY crossing scenarios (§9.2) as the core unit-test suite.
5. **RabbitMQ notification pipeline:** exchange/queue/DLX setup, email provider integration, idempotent `notification` writes.
6. **Watchlist, Activity feed, Dividend/52-week endpoints** — lower-risk, additive.
7. **Observability + resilience:** Actuator/Micrometer/tracing, Resilience4j circuit breaker around the Finance API adapter, retry/DLT tuning.
8. **Testing pass:** fill in Testcontainers-based Kafka/RabbitMQ/Postgres integration tests alongside each module rather than at the end.
9. *(Optional, if pursuing Microservices)* Extract modules into services in dependency order: `stock-service` → `market-data-service` → `alert-service`/`notification-service` → `portfolio-service` → `watchlist-service`/`activity-service`, adding the API Gateway and service registry last.

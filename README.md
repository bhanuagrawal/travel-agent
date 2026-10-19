# travel-agent

A travel-disruption assistant that answers with **real data instead of vibes**.

Ask it:

> "My flight out of Delhi is delayed 4 hours because of weather — what are my rights?"

...and it decides *on its own* to call its tools (live flight positions, current weather), looks up the airline-policy text it has indexed, and answers with something like *"EU261 may entitle you to €250–€600, though severe weather can exempt the airline."*

The LLM picks the tools. The tools talk to real APIs. The answer is grounded in what was actually retrieved — not in what the model remembered.

**Stack:** Kotlin · Spring Boot 4.1 · Spring AI 2.0 · Ollama · Redis · Kafka · MCP · JWT

> **Status, honestly:** everything described below runs locally today. A few pieces are deliberately parked — RAG ingestion, persistent chat memory, upstream retry — and they're listed under [Known gaps](#known-gaps-on-purpose-listed-honestly) rather than quietly left out of the description. This is a learning build in progress, not a finished product.

---

## What I was trying to learn

I'm an Android engineer working toward backend / AI-engineer work. This project was my way of learning four things properly, end to end:

1. **Agents** — what actually makes an "agent" different from a chat wrapper (tools + retrieval + reasoning loop), and where small LLMs break.
2. **Kafka** — real topics, partitions, consumer groups, offsets. Not a hand-wave.
3. **Resilience** — what happens when the upstream API you don't control starts failing at 3am.
4. **Security / observability** — JWT done properly (revocation is the hard part), and traces that connect a user request to a downstream call.

The scope was deliberately bigger than one person's comfort zone. That was the point.

---

## Architecture

```
  Open-Meteo ─┐                      ┌── Ollama :11434      (chat model + embeddings)
  OpenSky    ─┼──►  travel-agent ────┼── MCP clients        (POST /mcp, tool exposure)
              │       :8080          ├── REST + JWT         (/api/v1/**, /api/auth/**)
              │         │            └── Redis :6379        (cache · chat memory · token state)
              │         │
              │         └── Kafka :9092 ──► travel-analytics-service :8082
              │            topic `flight-searched`    GET /api/v1/analytics/popular-routes
              └─────────────◄── RestClient + circuit breaker (trending routes)
```

**The split, and why:**

| Piece | Role | Why it's there |
|---|---|---|
| `travel-agent` :8080 | Producer + AI agent | Owns the user-facing API, the LLM, and the live data tools |
| `travel-analytics-service` :8082 | Consumer + read model | Aggregates search events into "popular routes" without the agent knowing how |
| Redis | Hot state | Cache (protect rate-limited upstreams), chat memory, refresh tokens + JWT denylist |
| Kafka | Event bus | Decouples "a user searched" from "someone wants to count it" |
| Ollama | LLM | Free, local, private, unlimited iteration — swap to a cloud API by config |

---

## How a request flows

**Agent question** — `POST /api/v1/agent/ask` (`ADMIN` only)

1. `ChatClient` sends the question + the JSON schema of every `@Tool` method to the LLM.
2. The model replies with a `tool_calls` payload instead of prose — *"call `getWeather(28.61, 77.20)`"*.
3. Spring AI executes the tool in-process, feeds the result back, and loops until the model stops asking.
4. `MessageChatMemoryAdvisor` loads/re-saves the conversation for that `conversationId`, so follow-up questions work.
5. A tool that throws doesn't fail the request: Spring AI converts the exception into an error result and hands it back to the model, which then decides what to do. Convenient, and slightly dangerous — the model can answer as if it had data. Making those failures loud is next on my list.

**Tool access from outside** — `POST /mcp` (Streamable HTTP, JSON-RPC)

The same weather/flight capabilities are exposed as an **MCP server**, so any MCP client (opencode, Claude Desktop, …) can call them without knowing Kotlin exists:

```bash
# initialize → Mcp-Session-Id → tools/list → getWeather(28.6139, 77.2090)
# → "Temp: 32.1°C, Wind: 8.5 km/h"
```

That was the most instructive part of the project: the same business logic is reachable two ways — in-process tool calling for our own agent, and MCP for everyone else.

---

## API surface

| Method | Path | Auth | Does |
|---|---|---|---|
| `POST` | `/api/auth/login` | – | Returns access token + refresh token |
| `POST` | `/api/auth/refresh` | – | Rotates the refresh token, issues a new pair |
| `POST` | `/api/auth/logout` | Bearer | Revokes the refresh token *and* denylists the access token |
| `GET` | `/api/v1/health` | Bearer | Liveness |
| `GET` | `/api/v1/weather/{lat}/{lon}` | Bearer | Open-Meteo current weather (cached 10 min) |
| `GET` | `/api/v1/flights/{lat}/{lon}` | Bearer | OpenSky flights in a 1° box (cached 30 s) + publishes a search event + returns trending routes |
| `GET` | `/api/v1/advice/{lat}/{lon}?city=` | Bearer | Rule-based advice (weather + nearest airborne flight) |
| `GET` | `/api/v1/agent/ping?q=` | `ADMIN` | Raw LLM call, no tools |
| `POST` | `/api/v1/agent/ask` | `ADMIN` | The agent |
| `POST` | `/mcp` | – | MCP server (weather + flight tools) |

Two users are seeded in memory (`user`, `admin`) with **BCrypt** hashes — regenerate them before you use this anywhere real:

```bash
htpasswd -bnBC 10 "" 'your-password'
```

Live metrics: `/actuator/health`, `/actuator/circuitbreakers`, `/actuator/metrics`.

---

## Running it locally

```bash
# 1. Kafka + Zookeeper (from the monorepo root, where docker-compose.yaml lives)
docker compose up -d
terraform apply                      # creates topic `flight-searched`, 3 partitions
                                     # (or: kafka-topics.sh --create ...)

# 2. Redis
brew services start redis

# 3. LLM + embedding model
brew services start ollama
ollama pull llama3.2:3b && ollama pull nomic-embed-text

# 4. Run the agent (the secret is mandatory — the app fails fast without it)
export TRAVEL_SECURITY_JWT_SECRET=$(openssl rand -base64 32)
./gradlew bootRun

# 5. Analytics consumer (separate terminal, sibling directory)
cd ../travel-analytics-service && ./gradlew bootRun

# 6. UI (separate terminal, back in travel-agent/) → http://localhost:5173
cd frontend && npm install && npm run dev
```

Smoke test:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"..."}' | jq -r .token)

curl -s localhost:8080/api/v1/flights/28.6139/77.2090 -H "Authorization: Bearer $TOKEN"
curl -s "localhost:8082/api/v1/analytics/popular-routes?limit=5"
```

Traces (optional): the app exports OTLP to `localhost:4318` with 100 % sampling in dev, and `traceId`/`spanId` are baked into every log line — so a log line and a trace span are one click apart.

---

## Decisions I'd defend in a code review

These are the parts I'd actually want to be asked about.

**Cache keys are a public contract.** The first version keyed weather as `weather:28.6139:77.209` — `Double.toString()` silently dropped the trailing zero, so `77.209` and `77.2090` were two different cache entries. Fixed with `"%.4f:%.4f".format(Locale.ROOT, lat, lon)`. Locale matters too: a comma decimal separator would have produced keys nothing else could ever read.

**Cache TTL should follow data volatility, not convenience.** Weather stays useful for ~10 minutes; aircraft positions are stale in seconds. That's why the two caches have different TTLs — one shared value would either hammer OpenSky or hand out coordinates from a different minute.

**A call to a nice-to-have gets a timeout and a breaker.** `GET /api/v1/flights/...` also asks the analytics service for trending routes. If that hangs, the user's actual flight data still has to come back — so the analytics client is its own `RestClient` with connect 500 ms / read 1 s, wrapped in a Resilience4j circuit breaker, and the fallback lives in `AnalyticsService` as a plain `try/catch`. Deliberately **not** a `fallbackMethod` on the annotated client: that swallows the exception, so anything layered around it (Retry, metrics) sees a *success*. When retry gets added here, the nesting has to stay `Retry(CircuitBreaker(call))`.

**Revocation is the part of JWT that hurts.** Access tokens live 1 hour (stateless, restart-proof). Refresh tokens are opaque UUIDs, SHA-256-hashed at rest in Redis, TTL-managed by Redis, and **single-use with rotation**. Logout kills the refresh token *and* writes the access token's `jti` into a Redis denylist for its remaining lifetime, checked by a custom `JwtRevocationValidator`. It's not free — that denylist lookup is a per-request Redis hit — and it's the honest cost of wanting both stateless validation and real logout.

**Login brute force is cheap to stop, so it should be stopped.** A Redis fixed-window counter (5 failures / 15 min per username) rather than nothing. Not a substitute for edge rate limiting, but it closes the toy-project hole.

**Messy external APIs deserve a lenient parse, not a strict DTO.** OpenSky returns positional arrays-of-arrays full of nulls; Jackson 3 hard-fails on null collection content. So the payload is read as a `JsonNode` tree and mapped index-by-index. Open-Meteo's clean JSON gets a proper DTO. Different upstream, different adapter — the service layer never sees either shape.

**Missing configuration should crash at startup, not at 3am.** `TRAVEL_SECURITY_JWT_SECRET` has no default. Forget to set it and the app refuses to boot with a placeholder-resolution error instead of signing tokens with an empty key.

---

## Known gaps (on purpose, listed honestly)

- **RAG ingestion is currently disabled.** The pipeline (`TextReader` → `TokenTextSplitter` → `SimpleVectorStore` → `QuestionAnswerAdvisor`) works — I verified it citing EU261 figures from `docs/flight-policies.md` — but ingestion ran on every app start into an *in-memory* store. I commented it out while moving to a persistent vector store (pgvector / Redis Stack). Ingestion should be a versioned, idempotent job, not an `ApplicationRunner`.
- **`GET /api/v1/flights/...` publishes a hardcoded `DEL → BOM`** event. Route extraction from a real search payload is the next step; the plumbing is what I was proving.
- **No retry or stale fallback on the upstream APIs yet.** Timeouts and a breaker only exist on the analytics call. Open-Meteo and OpenSky are still plain `RestClient` calls with no timeout, no retry, and no stale-cache tier — so a transient 503 from either one surfaces as a 500. The caches already have the right shape for a `fresh` + longer-lived `stale` key; I just haven't wired the second tier.
- **"Upstream is down" and "there is nothing to show" currently look the same.** `FlightService.flightsInBox` returns an empty list both when OpenSky says there's no traffic and — via the exception path — when the call itself failed. That distinction has to be explicit (`List?`, or a small result type) before an agent's answer can be trusted, because right now it would happily tell you the sky is empty.
- **Tool failures are visible only in the logs.** Spring AI turns a tool exception into an error result for the model, so the HTTP request still succeeds and the model may answer as if it had data. The system prompt doesn't yet forbid guessing, and there's no per-tool failure counter. That's the single most valuable thing to fix here.
- **MCP endpoint is unauthenticated** (needed for a local client to connect at all). Auth + rate limiting before it's exposed anywhere real.
- **Chat memory is in-memory** — Redis memory needs Redis Stack. Same story as the vector store: same API, swap the repository.
- **Local `llama3.2:3b` is small.** Tool calling works but is noticeably flakier and more repetitive than a 7B+ model. Cloud provider is a starter + properties swap, not a rewrite.
- **Tests are thin** — the Boot context test only. An agent without an eval harness is an agent you can't refactor; that's the most important thing missing here.
- **Kafka producer has no correlation ID yet**, and the consumer is at-least-once with auto-commit and no dead-letter topic.

Next on the list: tool-failure visibility (system-prompt rule + a failure counter) → upstream retry and the stale-cache tier → a persistent vector store so RAG ingestion can be switched back on → correlation ID across HTTP + Kafka → Redis-backed read model → typed `JsonDeserializer` + DLT → Postgres + Flyway for real users.

---

## Repo map (the interesting bits)

```
src/main/kotlin/com/dabblepoint/travelagent/
├── agent/TravelTools.kt          # @Tool — what the LLM can call in-process
├── mcp.kt                        # @McpTool — the same capability over MCP
├── config/
│   ├── ChatClientConfig.kt       # system prompt + memory advisor + RAG advisor
│   ├── SecurityConfig.kt         # filter chain, JWT decoder, role mapping, users
│   └── AnalyticsClientConfig.kt  # RestClient with connect/read timeouts
├── service/
│   ├── WeatherService.kt         # cache-aside over Open-Meteo
│   ├── FlightService.kt          # cache-aside over OpenSky
│   ├── TokenService.kt           # HS256 issuance
│   ├── RefreshTokenStore.kt      # rotating, hashed, Redis-TTL refresh tokens
│   └── TokenDenylist.kt          # jti denylist for real logout
├── cache/                        # Redis cache-aside with per-data TTLs
├── kafka/FilghtEventProducer.kt  # publishes to `flight-searched`, keyed by user
└── rag/PolicyIngestor.kt         # doc → chunks → embeddings → vector store (currently commented out)
```

`frontend/` is a React 19 + Vite SPA that proxies `/api` to `:8080`. It's a teaching UI more than a product: it colour-codes the JWT segments, decodes header/payload live, counts down to expiry, silently refreshes on `401`, auto-logs-out when the refresh token dies, and lets you fire authenticated requests by hand. Watching a token expire in real time taught me more about JWT than any article.

---

## How this was built

I used AI as a **tutor and a sparring partner**, not an autopilot. The loop was: I propose a design → get pushed back on → argue about the tradeoff → write the code myself → verify it against the real thing (curl, `redis-cli`, logs, `javap` on the actual jar when a tutorial lied about an API).

`NOTES.md` in this repo is the unedited log of that — 60+ doubts and the answers, including the parts where I got it wrong, plus a *production gap ledger* listing every shortcut I knowingly took and what the real version looks like. Boot 4.1 / Spring AI 2.0 / Security 7 are new enough that most tutorials are stale; the notes record what the classpath actually said.

I'd rather show the reasoning than a polished repo with no fingerprints on it. The commit history is real development history, not a staged demo.

If you're reviewing this for a role: the parts I'd point at are the resilience design, the token lifecycle, and the failure-visibility work — those are the ones where the naive version looked fine and wasn't.

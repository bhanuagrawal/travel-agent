# Travel Agent — Learning Notes

> Learning project: build a production-style AI agent with **Spring Boot + Spring AI**, and learn **Kafka**, **Redis**, and **agent best practices** along the way.
> Rule: this file is the only file that gets edited unless explicitly asked otherwise.

---

## 1. Goal

**One sentence:** a travel-disruption agent — you ask a question; it decides which live data to fetch (weather, flights), looks up your policy rules (RAG), and answers in natural language, grounded in real data.

**User story:** *"My flight from Delhi is delayed 4 hours because of weather. What are my rights?"* → agent calls `getFlight` + `getWeather` + `searchPolicies`, then the LLM combines them into *"€250–€600 compensation may apply, but severe weather can exempt the airline…"* (an **agent**, not a chatbot, because the LLM chooses tools and reasons over retrieved facts).

Build an **"Eco-Flight Travel Intelligence Agent"** that:
- Ingests live **flight** data (OpenSky) and **weather** data (Open-Meteo)
- Streams flight events through **Kafka**
- Answers policy questions using **RAG** (PDF policy docs → embeddings → vector store)
- Uses an **LLM** (Spring AI) with **tools** + **RAG** to generate grounded advice
- Exposes everything via **REST**
- Deploys to a **lightweight VM**

Primary goals: **industry best practices for building AI agents**, **using Spring**, **learning Spring**, **not becoming Spring-only**, and learning **Kafka + Redis**.

**Why each piece exists:** Spring AI tools → LLM acts on live data · RAG → answers from your docs (no hallucination) · chat memory → remembers (LLM is stateless) · Redis cache → don't hammer rate-limited APIs + survive outages (stale fallback) · Kafka → decouple/stream/replay events · MCP → expose tools to any agent · best-practices layer → observability, guardrails, eval, tests.

---

## 2. Locked decisions

| Decision | Choice | Why |
|---|---|---|
| Language | Kotlin | Modern, null-safe, idiomatic Spring support |
| Build | Gradle Kotlin DSL (`build.gradle.kts`) | Modern standard for Kotlin/Spring |
| Spring Boot | 4.1.1 | Current; Jackson 3 by default |
| Kotlin | 2.3.21 | Bundled with project |
| Java | 17 (toolchain) | LTS |
| AI framework | **Spring AI 2.0.1** | Official Spring; best platform integration (observability/security/events/state) |
| LLM (dev) | Ollama (`qwen2.5:7b` or `llama3.1:8b`) | Free, local, private, unlimited iteration |
| LLM (prod) | Cheap cloud API (DeepSeek / Gemini free tier) | Lightweight VM can't host an LLM |
| Embeddings | `nomic-embed-text` (Ollama, local) | DeepSeek has no embeddings API; keep RAG local/free |
| Event bus | **Kafka** (real, VM sized ~4GB) | User wants to learn real Kafka |
| Cache/state | **Redis 8.6.2** (already installed) | Cache + chat memory; vector store TBD |
| Vector store | Redis Stack or SimpleVectorStore (TBD) | Local Redis lacks RediSearch |
| Deploy target | Lightweight VM | External LLM API, tuned JVM / native image |

### Rejected / deferred
- **LangChain4j** — more popular agent SDK (13.1k★), richer agent features, but not Spring-native; chosen Spring AI instead. Not complementary to Spring AI (~80% overlap).
- **Local LLM in production** — impossible on a lightweight VM.
- **Redpanda** — offered as lighter Kafka alternative; user chose real Kafka.

---

## 2b. Production gap ledger — shortcuts we've taken

> Rule: **we are not building a school project.** Every simplified step is flagged here with its production replacement. Steps going forward are tagged `[PROD]` or `[SHORTCUT — replace with X]`.

| Area | What we built | Production reality | Tag |
|---|---|---|---|
| HTTP clients | `RestClient`, no timeouts | **connect/read timeouts mandatory** (hung upstream blocks threads) | ⚠️ SHORTCUT |
| Resilience (Step 15) | hand-rolled retry + `Thread.sleep` | **Resilience4j** `@Retry`+`@CircuitBreaker`+`@Bulkhead`+Micrometer, jittered backoff, retry only 5xx/timeouts | ⚠️ SHORTCUT |
| Vector store | `SimpleVectorStore` (in-memory) | **persistent** store (pgvector / Redis Stack / Qdrant) | ⚠️ SHORTCUT |
| Chat memory | in-memory | **Redis/JDBC** persistent memory | ⚠️ SHORTCUT |
| RAG ingestion | `ApplicationRunner` every startup | **separate idempotent/versioned ingestion pipeline** | ⚠️ SHORTCUT |
| Knowledge base | hand-written `flight-policies.md` | **real PDFs/docs** (PDF/Tika readers) | ⚠️ DUMMY CONTENT |
| Orchestrator (P2) | rule-based `buildAdvice` | superseded by LLM agent (stepping stone) | ⚠️ STEPPING STONE |
| OpenSky access | anonymous (rate-limited) | **OAuth2 client credentials** | ⚠️ SHORTCUT |
| LLM | local `llama3.2:3b` | cloud API (DeepSeek/OpenAI) on the VM | ⚠️ DEV-ONLY |
| MCP server | unauthenticated | auth (Spring Security/MCP Security) + rate limit | ⚠️ SHORTCUT |
| Security / observability / eval | none yet | observability, guardrails, eval, tests | ⏳ NOT BUILT |
| Kafka | not built | event pipeline | ⏳ NOT BUILT |

---

## 3. Architecture

```
OpenSky (live flights) ──┐
                         ├─► Kafka topic `flight-events` ──► Consumer / Orchestrator
Open-Meteo (weather)  ───┘                                         │
                                                                   ▼
                                          Redis (cache + vector store + chat memory)
                                                                   │
                                                                   ▼
                                          Spring AI ChatClient (LLM + tools + RAG)
                                                                   │
                                                                   ▼
                                          REST: /api/v1/agent/ask, /api/v1/events
```

- **Kafka** = events / trigger (event-driven ingestion)
- **Redis** = hot state (cache, RAG vector store, chat memory)
- **Spring AI** = reasoning (tools + RAG + LLM)

---

## 4. Phase plan

| Phase | Deliverable | Status |
|---|---|---|
| P0 | Setup, health endpoint | ✅ done |
| P1 | Tools: weather + flights (real APIs) | ✅ done |
| P2 | Rule-based orchestrator + `/api/v1/advice` | ✅ done |
| P3 | Kafka event pipeline (VM-sized) | ⏳ deferred |
| P4 | Redis caching (cache-aside + TTL) | 🟡 in progress |
| P5 | RAG: PDF → embeddings → vector store + `searchPolicies` tool | ⏳ |
| P6 | Spring AI agent: LLM + tools + RAG + memory, REST | ⏳ |
| P7 | Best-practices layer: observability, guardrails, evaluation, tests, security | ⏳ |
| P8 | MCP server (optional) | ⏳ |

---

## 5. Q&A log (doubts → answers)

**D1 · Is the Gemini plan good?**
→ Architecture is sound (Kafka + RAG + LLM travel agent). Ignore its Maven/Gradle flip-flops and "Kotlin 1.9.25" advice; real stack is Boot 4.1.1 + Kotlin 2.3.21.

**D2 · Why is OpenSky parsing so ugly?**
→ OpenSky's API design: `states` is arrays-of-arrays (bandwidth for ~10k aircraft); indices are the contract. Options: keep the adapter (chosen), custom Jackson deserializer, or switch to `adsb.lol` (named JSON, no rate limits).

**D3 · Will we use MCP?**
→ Not required for core. MCP is a tool-exposure protocol, not a replacement for Kafka/RAG/LLM. Optional Phase 8: expose our services as an MCP server.

**D4 · Keep my doubts + your summaries in notes**
→ Done; format is *Doubt → Summary*, appended each turn. (Now persisted in this file.)

**D5 · If not MCP, how does FlightService reach the agent?**
→ The agent *is* our app; `FlightService` is a Spring bean injected into the orchestrator and called in-process. MCP only needed when the agent lives outside our JVM.

**D6 · Are we aligned? Is my plan good?**
→ Plan is good: standard RAG + tool-calling agent. Aligned: tools (P1) + agent using tools (P6) + PDF/RAG (P5) + REST (P6). Gap: Kafka was dropped from the restatement — kept since you want to learn it.

**D7 · Does Spring AI work with Spring Boot 4.1.1?**
→ Yes, confirmed. Spring AI 2.0.0 upgraded to Boot 4.1.0; use Spring AI 2.0.1.

**D8 · How to fold Redis + Kafka into the use case?**
→ Reframe as "Live Flight Disruption Advisor": Kafka = event trigger; Redis = cache + RAG vector store + chat memory.

**D9 · Spring AI vs more popular agent SDK?**
→ More popular = LangChain4j (13.1k★). Spring AI = official. Both support Boot 4.1.1. Chose Spring AI.

**D10 · Goal is agent best practices + Spring, not Spring-only?**
→ Spring AI still: best practice for production agents is the platform (observability/security/events/state), which Spring provides. Key caveat: best practice = architecture, not framework (Anthropic *Building Effective Agents*, *12-Factor Agents*).

**D11 · LangChain4j vs Spring AI, not wanting to be Spring-only?**
→ Frameworks don't transfer; concepts, protocols (MCP, OpenTelemetry), and patterns do. Spring AI as host + framework-agnostic core (ports/adapters) + MCP + OTel.

**D12 · Why not each? Different purposes?**
→ Direct competitors (~80% overlap); pick one. Spring AI = Spring-first integration layer. LangChain4j = portable Java agent framework. Downsides: Spring AI = young/API churn, less popular, thinner agent abstractions, Spring-shaped. LangChain4j = not Spring-official (hand-wire glue), bigger/stabilizing surface, LangChain brand less favored in production.

**D13 · Is LangChain4j the same as LangChain?**
→ No. LangChain = Python/JS by LangChain Inc. LangChain4j = independent Java project, inspired by but not a port of LangChain. Shared concepts, different API.

**D14 · Infra status / Docker?**
→ No Docker/colima/podman. Redis 8.6.2 installed + running; lacks RediSearch (`FT.*`), only `vectorset`. Homebrew + Java 17 present. Paths: (A) install colima → Docker Compose; (B) native — Redis for cache/memory, SimpleVectorStore for RAG, `brew install kafka`.

**D15 · Local LLM vs cheap API (DeepSeek)?**
→ M4 / 16GB runs 7-8B local models, but only 12GB disk free. Use Ollama dev + DeepSeek/Gemini prod. DeepSeek cheap (~$0.14-0.28/M), OpenAI-compatible, no embeddings API.

**D16 · Deploying to a lightweight VM — changes the plan?**
→ Yes. No local LLM → cloud API. Kafka JVM heavy → Redpanda/managed/Redis Streams (or size the VM). Redis fine. Consider GraalVM native image (~50MB RAM).

**D17 · VM good enough for Kafka / next code?**
→ VM sized for real Kafka (~4GB). Next code = P4 Redis caching.

**D18 · How does Kafka work?**
→ Distributed append-only log: topic → partitions → offsets. Producers append (key-hashed → partition). Consumer groups assign each partition to one consumer. Pull-based, committed offsets, retained/replayable, replicated with a leader. Delivery: at-least-once / at-most-once / exactly-once.

**D19 · Is Kafka a central DB table?**
→ Partly (topic≈table, message≈row, offset≈PK). But append-only, sequential reads (no queries), distributed (not centralized), and each consumer group has its own offset. Mental model = distributed replayable commit log.

**D20 · If I know Kotlin Flow, how does it relate to Kafka?**
→ Different layers: Flow = in-process reactive stream; Kafka = distributed durable log. Concepts transfer; Kafka adds durability/distribution/replay. Bridge: `KafkaReceiver.receive().asFlow()` or `callbackFlow` around `@KafkaListener`.

**D21 · What's Kafka's use case?**
→ Event-driven decoupling + fan-out. Order flow: API publishes → returns fast → downstream services consume independently. Uses: async decoupling, pub/sub, load leveling, event streaming, event sourcing/CQRS, CDC, log aggregation, replay. Correction: Kafka isn't the user-facing notify channel.

**D22 · Do events get removed after all consumers consume?**
→ No. Deletion is by retention (time/size, default 7 days), never on consumption. Segment-granular. Lagging consumer can miss data. Exception: log compaction (`cleanup.policy=compact`).

**D23 · Does the consumer maintain the last processed message?**
→ Logically yes (its position); Kafka persists it in `__consumer_offsets` keyed by (group, topic, partition). Commit timing defines delivery: before = at-most-once, after = at-least-once, transactional = exactly-once. Practical default = at-least-once + idempotent consumers. Spring Kafka `AckMode`: RECORD/BATCH/MANUAL/TIME/COUNT.

**D24 · Why do we need Redis?**
→ Three roles: cache external APIs (TTL, rate-limit protection), RAG vector store, chat memory. Why Redis vs in-memory/DB: microsecond reads, built-in TTL, shared state across instances, lightweight, multi-purpose. Redis = hot state layer.

**D25 · Does Redis need credentials like a DB server?**
→ By default no (`requirepass` unset); Homebrew binds to localhost. Supports `requirepass`, ACL (Redis 6+), TLS. Never expose 6379 unauthenticated. Dev = localhost no-auth; VM = password/ACL + bind private + firewall.

**D26 · If the network is trusted, does a DB also not need credentials?**
→ Technically yes (Postgres `trust` auth, Redis no `requirepass`). But best practice authenticates anyway: defense in depth, insider/lateral access, least privilege, audit, blast radius, compliance, zero-trust. Rule: localhost → no auth OK; any shared/remote env → always auth. Distinction is localhost vs reachable, not DB vs Redis.

**D27 · "No beans of 'ObjectMapper' type found"**
→ Spring Boot 4.1.1 defaults to **Jackson 3** (`tools.jackson.databind.ObjectMapper`); the injected type was Jackson 2 (`com.fasterxml.jackson.databind`). Fix: import `tools.jackson.databind.ObjectMapper` + swap dependency to `tools.jackson.module:jackson-module-kotlin`. Annotations stay `com.fasterxml.jackson.annotation`.

**D28 · Notes in a file / Notion offline?**
→ Notes now live in `NOTES.md` (only file edited unless asked). **Notion has no offline file creation** — no local-file API or watched folder; only the online Notion API (token) or manual Markdown import (Notion → Import → Markdown). No Notion MCP connected.

**D29 · Ollama — what/where is it?**
→ Ollama is **Homebrew-installed** (`/opt/homebrew/bin/ollama`, v0.21.0), **not Docker** (no Docker/OrbStack on the machine). It was **not running**. `~/.ollama` has **6.3GB** of models already pulled: **`llama3.2:3b`** + **`llava:latest`** (vision). Missing: `qwen2.5:7b`, `nomic-embed-text`. Plan: start with `llama3.2:3b` (already local, supports tool calling), pull `nomic-embed-text` for RAG embeddings, optionally `qwen2.5:7b` later. Start via `brew services start ollama` (or `ollama serve`).

**D30 · What does "the model supports tool calling" mean?**
→ The model does **not** execute tools — it only **requests** them by emitting a structured `tool_calls` payload (function name + JSON args) instead of prose. "Supports tool calling" = it can reliably reply in the exact format the API expects. **Agentic loop:** send user msg + tool definitions → model emits tool call → *your code* executes the function → send result back → model finalizes (repeat if needed). **Native** (API `tools` param, trained format — reliable) vs **prompt-based/ReAct** (parse JSON yourself — fragile). Small models (`llama3.2:3b`) support it but less reliably than bigger ones. **Spring AI** auto-converts `@Tool` methods into JSON-schema definitions and runs the whole loop.

**D31 · If I sign up for a cloud LLM service, what changes?**
→ **Config, not code.** Add `spring-ai-starter-model-openai` (or `-google-genai` / `-anthropic`), then set `spring.ai.model.chat=openai` + `spring.ai.openai.base-url` / `api-key=${ENV}` / `chat.options.model`. DeepSeek (`https://api.deepseek.com`), Groq, OpenRouter all work via the OpenAI starter (OpenAI-compatible). **Embeddings:** DeepSeek has none → keep Ollama embeddings or use OpenAI/Gemini. **Key hygiene:** env var + Spring profile (`application-cloud.properties`, `--spring.profiles.active=cloud`), never commit. Recommended: Ollama dev, cloud profile for the VM.

**D32 · Do you need a model to generate embeddings?**
→ **Yes — a dedicated embedding model**, separate from the chat LLM. Embedding models are encoder-only (BERT-like) and output fixed-length vectors for similarity; chat models are generative decoders and **cannot** produce embeddings. Dev: `nomic-embed-text` (Ollama, 768-dim, free). Cloud: OpenAI `text-embedding-3-small` (1536-dim), Gemini. **Rules:** (1) same model for ingest *and* query, (2) dimension is fixed at index time — switching models means re-embedding + recreating the index, (3) embeddings are much cheaper than generation.

**D33 · What embedding models do enterprises/startups use?**
→ **Managed API:** OpenAI `text-embedding-3-small` (default for most startups, 1536-dim), `-large` (3072); **Cohere embed-v3/v4** (enterprise, multilingual); **Voyage voyage-3** (strong RAG); Google `text-embedding-004`; AWS Titan/Bedrock (compliance). **Self-hosted OSS:** **BAAI `bge`/`bge-m3`**, **`intfloat/e5`**, `nomic-embed-text-v1.5`, `jina-embeddings-v3`, `all-MiniLM-L6-v2` (prototyping). **Startups** default to OpenAI small → move to Cohere/Voyage/OSS for cost/privacy/quality. **Enterprises** use cloud-provider embeddings for compliance or self-host `bge`/`e5` (often fine-tuned). Selection: evaluate on your own data (not just MTEB), dimension, context length, multilingual, cost, latency, privacy/licensing. **Key point:** hybrid search (BM25 + vectors) + a **reranker** usually improves RAG more than swapping embedding models.

**D34 · Can Ollama do embeddings?**
→ **Yes.** Ollama has an embeddings API (`/api/embed`) and serves embedding models; Spring AI's Ollama starter auto-configures an `EmbeddingModel` bean (`spring.ai.ollama.embedding.options.model`). Current gaps: Ollama **not running** and **no embedding model pulled** (only `llama3.2:3b` + `llava`). Fix: `brew services start ollama` + `ollama pull nomic-embed-text` (~275MB, 768-dim). Alternatives: `mxbai-embed-large` (1024), `all-minilm` (384), `bge-m3` (multilingual).

**D35 · What is Ollama?**
→ A **local LLM runtime** — "Docker for LLMs" (pull/run/list/remove). Bundles: model registry/downloader, inference engine (built on **llama.cpp**; Metal on M4, CUDA on NVIDIA, CPU), an **HTTP server on `localhost:11434`** (own `/api/*` + OpenAI-compatible), and a CLI. Serves chat, embeddings, vision. **Fully offline** once a model is pulled (internet only to download). Alternatives: llama.cpp (engine), LM Studio (GUI), **vLLM/TGI** (production GPU serving), cloud APIs (scale). Trade-off: free/private/offline but limited to local hardware → Ollama for dev, cloud API for the VM.

**D36 · How to remove all models and install what's needed?**
→ Start Ollama first. List: `ollama list`. Remove one: `ollama rm <model>`. Remove all: `ollama list | tail -n +2 | awk '{print $1}' | xargs -n1 ollama rm`. Hard reset: stop service + `rm -rf ~/.ollama/models` + start. **Install:** `ollama pull llama3.2:3b` (chat, ~2GB) + `ollama pull nomic-embed-text` (embeddings, ~275MB); optional `qwen2.5:7b` for better tool calling. `llava` (vision, ~4.7GB) not needed. Total needed ≈ 2.3GB.

**D37 · Is there an official/near-official "skill" for Spring Boot / Spring AI?**
→ **No official Anthropic Skill or official Spring docs MCP.** Spring's *official* AI contribution is the **MCP Java SDK** (Spring co-maintains) + **Spring AI MCP Boot Starters** — for *building* MCP, not knowledge. **Near-official docs MCPs:** **Context7** (Upstash; covers Spring AI at `context7.com/spring-projects/spring-ai` + Spring Boot) and **springdocs-mcp** (`@enokdev/springdocs-mcp`, v1.3.0, Spring Boot + Spring AI + Framework, 12 tools incl. version-migration/best-practices/diagnostics). **Community Claude skills (lag — some still say Boot 3.x):** `piomin/claude-ai-spring-boot`, `jeffallan` spring-boot-engineer, `claudeers`, `awesome-copilot/java-springboot`. **Note:** my training cutoff predates Boot 4.x / Spring AI 2.x, so I verify APIs against docs; adding a docs MCP (Context7 and/or springdocs-mcp) via `opencode.json` would make answers reliably current (needs user OK — config edit outside the notes-only rule).

**D38 · Added Spring docs MCPs to opencode.**
→ Added to **global** `~/.config/opencode/opencode.jsonc`: `spring-docs` (local: `npx -y @enokdev/springdocs-mcp@latest`) + `context7` (remote: `https://mcp.context7.com/mcp`). Verified: JSON valid, package on npm (1.2.8), Context7 reachable (405 on GET = expected). **Must restart opencode** (config not hot-reloaded). Context7 anonymous = rate-limited; add `headers.Authorization: Bearer {env:CONTEXT7_API_KEY}` later for higher limits. Tools available after restart: spring-docs (`search_spring_docs`, `get_spring_reference`, `compare_spring_versions`, `get_spring_best_practices`, `diagnose_spring_issues`) + context7 (`resolve-library-id`, `query-docs`).

**D39 · Chat memory: Redis needs Redis Stack (important correction).**
→ From official 2.0 docs: **`RedisChatMemoryRepository` requires Redis Stack** (Query Engine + RedisJSON, Stack 7.0+/Redis 8.0+). Local Redis **8.6.2 lacks it** (only `vectorset`, no `FT.*`) → Redis memory won't work locally. Starter is `spring-ai-starter-model-chat-memory-repository-redis` (not `...chat-memory-redis`). **Plan:** use the **auto-configured in-memory `ChatMemory`** (`InMemoryChatMemoryRepository` + `MessageWindowChatMemory`) now — identical API, swap repository later. Advisor = `MessageChatMemoryAdvisor`; `ChatMemory.CONVERSATION_ID` is **required** (else `IllegalArgumentException`). **Limitation:** these repositories don't persist tool-call messages (only user/assistant text); Spring AI Session project handles that. Same Redis Stack requirement will affect the **RAG Redis vector store** → plan SimpleVectorStore or install Redis Stack.

**D40 · If we expose our tools as a public MCP server, can you call them?**
→ **Yes** — opencode is an MCP **client**; if the Spring app exposes an MCP server (`spring-ai-starter-mcp-server`, `@McpTool`/`ToolCallbackProvider`) and it's **registered in `opencode.json` `mcp`** + reachable, I can call its tools (same as the linkedin MCP). **Reachable:** since opencode runs locally, **localhost suffices**; "public" is only for other clients. Transports: stdio or streamable-HTTP/SSE. **The client executes, not me** — opencode calls the tool and feeds results back. **Distinct from ChatClient `@Tool`:** in-process tool calling (same JVM) vs MCP (cross-process, any client). **Security:** if public → auth (API key/OAuth) + rate limiting + never expose write/destructive tools; read-only weather/flights are safe candidates.

**D41 · Exposing our Spring app as an MCP server (2.0 API).**
→ Starter: **`spring-ai-starter-mcp-server-webmvc`** (Streamable-HTTP; default endpoint `POST /mcp`). Config: `spring.ai.mcp.server.protocol=STREAMABLE`, `.name`, `.version`, `.type=SYNC`. Annotations: **`@McpTool`** / **`@McpToolParam`** (in `org.springframework.ai.mcp.annotation.*`; auto-import if unresolved). Keep `@Tool` (in-process ChatClient) and `@McpTool` (external MCP) in separate classes. **HTTP MCP transports are unauthenticated by default** — secure (Spring Security / MCP Security) before exposing beyond localhost. Next: register `travel-agent` in opencode `mcp` config, restart, and I can call the tools.

**D42 · MCP endpoint 404.**
→ Added starter + config, but `TravelMcpTools.kt` wasn't created and the running app predated the change → `/mcp` = 404 (endpoint registers only on fresh boot). Fix: create `mcp/TravelMcpTools.kt` + **restart** the app. Verify with a JSON-RPC POST to `/mcp` (`tools/list`, `Accept: application/json, text/event-stream`) — expect a JSON-RPC reply or `initialize`-required, **not 404**.

**D43 · MCP works end-to-end; registered in opencode.**
→ Verified the Streamable-HTTP MCP server: `initialize` handshake → `Mcp-Session-Id` header → `tools/list` returned **`getWeather`** + **`getFlight`** (`serverInfo: travel-agent 1.0.0`). A raw `tools/list` without `initialize` returns `-32601 "Session ID missing"` (expected — the client does the handshake). `tools/call getWeather` reached the tool but **Open-Meteo returned `503 The service is overloaded`** (transient, external) — MCP surfaced it as `isError:true` content. **Best-practice gap identified:** no retry / circuit breaker / stale-cache fallback on upstream failure → add Resilience4j + serve stale cache. Registered `travel-agent` (`type: remote`, `url: http://localhost:8080/mcp`) in global `opencode.jsonc`; **restart opencode** to load, app must be running.

**D44 · How to verify opencode actually invoked the MCP tool?**
→ Three checks: (1) **opencode UI** renders each tool call inline (tool name + args + result) before my text; (2) **server log** — add `log.info("MCP getWeather called: lat={}, lon={}", lat, lon)` in `TravelMcpTools` and watch the console (best practice anyway); (3) **deterministic test** — restart opencode, keep app running, ask me: *"Call the travel-agent getWeather tool for lat 28.6139, lon 77.2090"*. Verify: tool-call block + console log line + weather text in my answer. Note: an Open-Meteo 503 still proves invocation (call + log happen, result is an error).

**D45 · What prompt to test MCP invocation?**
→ Explicit (deterministic): `Call the travel-agent MCP tool getWeather with lat=28.6139 and lon=77.2090.` Also: `Call the travel-agent getFlight tool for lat=28.6139, lon=77.2090.` Natural (tests auto-selection): `What's the weather in Delhi right now? Use your MCP tools.` Requires opencode restarted + app running.

**D46 · MCP invocation confirmed (end-to-end success).**
→ With the natural prompt, opencode (MCP client) called `travel-agent_getWeather(28.6139, 77.2090)` → returned **`Temp: 32.1°C, Wind: 8.5 km/h`**. Full loop: opencode → `POST localhost:8080/mcp` → `TravelMcpTools.getWeather` → `WeatherService` → Open-Meteo → back. Proves the Spring app's tools are callable by any MCP client (cross-process, framework-agnostic). Open-Meteo 503 earlier was transient.

**D47 · What is chat memory? Is it for UI or context?**
→ LLMs are **stateless**; chat memory = **storing the conversation and re-sending recent messages with each request** so the model has context. On each call the advisor loads the conversation for that `conversationId`, prepends it to the prompt, and stores the new user+assistant messages. **Not for UI** — that's **chat history** (full transcript persisted in a DB for display/audit; not sent to the LLM). **Chat memory IS for context** — a **bounded working set** (last N) because every re-sent message costs tokens (money + latency). Alternatives: token-window, vector-store memory. In our app: `conversationId` scopes memory; `MessageChatMemoryAdvisor` handles load/inject/store. Note: duplicated LLM output (`...32.1°C... 32.1°C...`) is `llama3.2:3b` repetitiveness, not a memory bug → lower temperature / tighten system prompt / use `qwen2.5:7b`.

**D48 · ApplicationRunner, ingestion timing, and 2.0 ETL API fixes.**
→ **`ApplicationRunner`**: Spring Boot interface; `run(ApplicationArguments)` runs **once after the context is fully started** (seeding, warm-up, one-off jobs). Sibling `CommandLineRunner` (raw args). **Ingestion timing:** doing it on every app startup is **prototyping only**; production treats ingestion as a **data pipeline** — separate idempotent/versioned job + a **persistent** vector store (pgvector/Redis Stack), not app boot (avoids re-embedding every restart + half-loaded stores). Fine for us now (SimpleVectorStore is in-memory). **API fixes (Spring AI 2.0):** `contentAsString` → use **`TextReader(Resource).read()`** (`org.springframework.ai.reader.TextReader`); `TokenTextSplitter` **constructors deprecated** → use **`TokenTextSplitter.builder().build()`**. ETL interfaces: `DocumentReader` (`read()`), `DocumentTransformer` (`apply()`/`transform()`), `DocumentWriter` (`write()`/`accept()`); readers available: `TextReader`, `JsonReader`, `MarkdownDocumentReader` (`spring-ai-markdown-document-reader`), `PagePdfDocumentReader`/`ParagraphPdfDocumentReader` (`spring-ai-pdf-document-reader`), `TikaDocumentReader` (`spring-ai-tika-document-reader`), `JsoupDocumentReader`.

**D49 · OpenSky tool fails: Jackson 3 rejects null content.**
→ OpenSky `states` rows contain **nulls** (indices 7,11,12,13,14). Jackson 3 fails with `InvalidNullException` on **null collection content** even though the type is `List<Any?>`. **Fix:** deserialize the messy external payload as a lenient **`JsonNode`** (`states: JsonNode?`) and map manually — `get(i)?.takeIf { it.isNumber }?.asDouble()` / `?.asString()` / `?.asBoolean()`; `get(i)` returns `NullNode`, so nothing throws. **Lesson:** typed DTOs suit strict, well-known APIs (Open-Meteo); a **lenient JSON tree** suits messy/positional external APIs (OpenSky). Confirmed **RAG works** — `QuestionAnswerAdvisor` cited EU261/€250–€600 from `flight-policies.md`.

**D50 · Jackson 3 renamed JsonNode methods.**
→ `JsonNode.elements()` no longer resolves. Avoid renamed iterators — use **index-based** iteration: `if (!states.isArray) emptyList() else (0 until states.size()).mapNotNull { states.get(it).toFlightState() }` (`size()`/`get(int)` are stable; `get` returns `NullNode` for nulls). Jackson 3 renames: `elements()`→`values()`, `asText()`→`asString()`, `fields()`→`properties()`, `fieldNames()`→`propertyNames()`.

**D51 · Tools verified working via MCP (post-fix).**
→ opencode called both MCP tools successfully: `getWeather(28.6139,77.2090)` → `Temp: 31.9°C, Wind: 8.5 km/h`; `getFlight(28.6139,77.2090)` → `Flight AIC7ZZ (India) · 28.3784, 76.4648 · alt 3444m · 127 m/s`. The `JsonNode` fix resolved the OpenSky `InvalidNullException`. Confirmed working end-to-end: real API tools, Redis cache-aside, ChatClient tool-calling, in-memory chat memory, RAG (QuestionAnswerAdvisor), MCP server.

**D52 · Upstream resilience (retry + stale-cache fallback).**→ Resilience4j now has a **`resilience4j-spring-boot4`** starter (2.4.0) but may require **Java 21** (project is 17) — verify first. Dependency-free approach (Step 15): **retry with backoff** on the upstream call (3 attempts, 200/400/800ms) + **stale-cache fallback** — `WeatherCache` keeps a `fresh` key (10m TTL) and a `stale` key (24h TTL); `getCurrentWeather` = fresh cache → retry upstream → on failure serve stale (log a warning) instead of erroring. Same shape for flights. Production toolkit: Resilience4j `@Retry`/`@CircuitBreaker`/bulkhead + metrics.

**D53 · Resilience done PROPERLY (`[PROD]`), not the shortcut.**
→ User: *we are not building a school project — flag every scaled-down/dummy step.* Adopted a tagging convention: **`[PROD]`** vs **`[SHORTCUT — replace with X]`**; added a **Production gap ledger** (§2b). **Resilience4j 2.4.0** (`resilience4j-spring-boot4`) verified **Java 17** (bytecode major 61) → no toolchain bump. Step 15 `[PROD]`: (a) deps `resilience4j-spring-boot4:2.4.0` + `spring-boot-starter-aop` + `-actuator`; (b) **HTTP timeouts** on `RestClient` via `JdkClientHttpRequestFactory` (connect 3s / read 8s); (c) extract **`WeatherClient`/`FlightClient`** with `@Retry(name="weather")` + `@CircuitBreaker(name="weather", fallbackMethod="unavailable")` — separate beans avoid the **AOP self-invocation trap**; (d) `WeatherService` = fresh cache → resilient client → **stale fallback**; (e) `resilience4j.*.configs.default` in properties (retry only `ResourceAccessException` + `HttpServerErrorException` = timeouts/5xx, never 4xx; breaker 50% over 10 calls, 30s open). Metrics: `/actuator/circuitbreakers`, `/actuator/retries`.

**D54 · Why doesn't the agent show tool errors?**
→ Spring AI contract: a tool **`RuntimeException` is converted to an error result and fed back to the LLM** (the model then decides — retry or answer anyway); a **declared checked exception / `Error` bubbles up** and fails the call. So a failed tool appears in **logs**, not in the agent's reply — the model silently continued. **Anti-pattern:** silent tool failure → the LLM may answer as if it had data or hallucinate. **Fixes (P7 observability + guardrails):** (1) system-prompt rule "if a tool fails/unavailable, say so, never guess"; (2) log + **Micrometer counter** per tool failure (`agent.tool.failures{tool=...}`); (3) return a structured `ToolTrace` (which tools ran/failed) with the answer; (4) resilience reduces failures but doesn't replace visibility.

**D55 · Step 16 `[PROD]` — tool-failure visibility (implemented).**
→ (a) `ChatClientConfig` system prompt now instructs the model to **explicitly say when data couldn't be retrieved — never guess**; (b) clients inject `MeterRegistry` and increment **`agent.upstream.failures{service=open-meteo|opensky}`** in the `unavailable` fallback (+ warn log); (c) fixed the **"empty vs unavailable" conflation** — `FlightClient.fetch` returns `List?` (**null = upstream failed**, `[]` = genuinely no flights), so `getFlightSummary` says *"Flight data is currently unavailable"* vs *"No airborne flights"*. Spring AI also emits built-in **tool-call observations** (Micrometer) → visible via actuator. Verify: `/actuator/metrics/agent.upstream.failures`.

**D56 · How does the agent know if output is a tool call vs a JSON text response?**
→ It **doesn't parse text** — native tool calling uses a **separate structured field**. The provider response has `content` (text) vs `tool_calls` (array of `{id, function:{name, arguments}}`), plus `finish_reason` (`tool_calls` vs `stop`). The model is trained/constrained to populate `tool_calls` when it wants a tool; `arguments` is a JSON **string inside that field**. Text containing JSON is just text (channel disambiguates, not syntax). **Prompt-based/ReAct** (no native support) *does* parse text via regex — ambiguous and fragile, hence native tool calling is preferred. **Spring AI:** provider response → `AssistantMessage` with `toolCalls`; `ToolCallingManager` checks `hasToolCalls()` (execute + loop) else returns `content`. A model can return both text and tool calls. "Structured output" (JSON-schema-constrained) is a *separate* feature from tool calling.

**D57 · Is an LLM response a key-value pair?**
→ **Yes, but nested JSON**, not flat. Envelope: `{id, model, choices[], usage{}}` → `choices[i]` = `{message, finish_reason}` → `message` = `{role, content, tool_calls[]}` → `tool_calls[i]` = `{id, function{name, arguments}}`. Nuances: `content` is a string **or** an array of content parts (multimodal); `arguments` is a **JSON string inside a JSON field** (parsed only after the runtime decides it's a tool call); a message can have both `content` and `tool_calls`. **Spring AI maps it:** `ChatResponse` → `List<Generation>` → `AssistantMessage` (`getText()`, `getToolCalls()`). **Streaming** = a stream of `choices[0].delta` fragments (tool-call fragments merged by index), not one object.

**D58 · At the token level the output is unknown — how is it structured?**
→ Raw output **is** an arbitrary token sequence; structure is imposed by three layers: (1) **training** — the model learned to emit special control tokens/markers for tool calls; (2) **inference server** parses those tokens and splits them into `content` vs `tool_calls` before you see JSON; (3) **constrained decoding** (optional) restricts sampling to a grammar so JSON stays valid. **When it still breaks:** malformed `arguments` / unknown tool name (hallucinated) / wrong arg types → validated against the declared schema → becomes a **tool error result fed back to the model** (which may retry); prose that looks like a call is treated as **text and never executed**. Safety property: **only things in the structured `tool_calls` field execute**, and they're schema-validated. Prompt-based/ReAct has no such bounds (parse text + hope) — that's the fragility.

**D59 · Resilience flow + the Retry/CircuitBreaker/fallback trap (Step 15 correction).**
→ **Patterns:** Bulkhead (cap concurrency) · TimeLimiter (cap duration) · RateLimiter (cap rate) · CircuitBreaker (stop calling failing dep) · Retry (re-attempt with backoff) · + HTTP timeout below. **Default aspect order (outermost→innermost): `Retry( CircuitBreaker( RateLimiter( TimeLimiter( Bulkhead( method ) ) ) ) )`** — Retry is **outside** CB. **CB states:** CLOSED (count failures) → OPEN (reject immediately with `CallNotPermittedException`, after `wait-duration-in-open-state`) → HALF_OPEN (trial calls) → CLOSED/OPEN. **TRAP:** a `fallbackMethod` on the client **swallows the exception**, so with Retry outside CB, Retry sees success → **retry never runs**. **Correct design:** no fallback on the client (let exceptions propagate so Retry works); do the fallback (stale cache / null) in the **service** via try/catch; and set `resilience4j.retry.configs.default.ignore-exceptions=io.github.resilience4j.circuitbreaker.CallNotPermittedException` so an open breaker isn't retried. Failure regimes: timeout = don't hang · retry = survive blips · breaker = stop hammering · fallback = still answer.

---

## 6. Concepts learned

- **DI / beans**: `@Bean`/`@Configuration` → Spring-managed singletons; constructor injection (explicit, immutable, testable); two same-type beans resolved by parameter name = bean method name.
- **REST layer**: `RestClient` (modern sync HTTP client, Boot 3+/4); `@RestController` + `@GetMapping`; returning a Kotlin object → auto JSON via Jackson.
- **DTOs**: `@JsonProperty` for snake_case mapping; unknown JSON fields ignored by default.
- **Service layering**: controller = HTTP only; `@Service` = business logic; orchestrator = composition.
- **Cache-aside pattern**: read cache → miss → call source → write cache with TTL. Redis commands `GET`, `SET key val EX ttl`, `TTL`. Declarative alternative `@Cacheable` has an AOP self-invocation trap.
- **Kafka**: topic/partition/offset, producers, consumer groups, pull-based, retention, compaction, offsets in `__consumer_offsets`, delivery semantics.
- **Kotlin Flow vs Kafka**: in-process reactive stream vs distributed durable log; bridge via `asFlow()`.
- **Spring AI building blocks**: `ChatClient` (LLM calls), `@Tool` (expose services to LLM), `QuestionAnswerAdvisor`/vector store (RAG), `DocumentReader` (PDF ingest).
- **Agent→service patterns**: (A) in-process DI, (B) LLM tool-calling via `@Tool`, (C) MCP server for external clients.
- **Provider-agnostic design**: Spring AI OpenAI starter = any OpenAI-compatible endpoint via `base-url` (DeepSeek/Groq/OpenRouter/vLLM/LM Studio).
- **Boot 4 = Jackson 3**: packages moved to `tools.jackson.*`; annotations stayed `com.fasterxml.jackson.annotation`.
- **Portable AI skills**: concepts (RAG/tools/agents/memory/eval) + protocols (MCP, OTel GenAI) + patterns (orchestration/guardrails) — not the framework API.

---

## 7. Current code state

```
src/main/kotlin/com/dabblepoint/travelagent/
├── TravelAgentApplication.kt
├── config/RestClientConfig.kt          # openMeteoRestClient, openSkyRestClient beans
├── config/ChatClientConfig.kt          # ChatClient (system prompt); memory advisor pending
├── controller/FlightAgentController.kt # /health,/weather,/flights,/advice,/agent/ping,/agent/ask
├── agent/TravelTools.kt                # @Tool getWeather/getFlight (in-process)
├── mcp.kt                              # @McpTool getWeather/getFlight (MCP server exposure)
├── model/OpenSkyModels.kt, WeatherResponse.kt, TravelAdvice.kt, AskRequest.kt
├── service/FlightService.kt, WeatherService.kt, TravelOrchestratorService.kt
└── cache/WeatherCache.kt, FlightCache.kt   # Redis cache-aside
```

Endpoints: `GET /api/v1/{health,weather,flights,advice,agent/ping}`, `POST /api/v1/agent/ask`, MCP `POST /mcp`.
Verified working: Redis caching, ChatClient tool calling, MCP server (opencode called `getWeather` → 32.1°C).
Pending: chat memory advisor (Step 12), RAG (P5), Kafka (P3), best-practices layer (P7).

---

## 8. Progress log
- **Step 7 — Verify Redis caching.** Run app; `curl /api/v1/advice`; check `redis-cli KEYS 'weather:*'`, `TTL`, `GET`; second call served from cache. Prereq: Jackson 3 fix (D27) + Redis running.
- **Step 8 — Canonical cache key.** Diagnosis: caching worked, but key was `weather:28.6139:77.209` (not `...77.2090`) because `Double.toString()` drops the trailing zero. Fix: format with fixed precision + `Locale.ROOT` → `"weather:%.4f:%.4f".format(Locale.ROOT, lat, lon)`; make `key()` private. Takeaway: cache keys are a stable public contract; never use raw `Double.toString()` (trailing zeros + locale commas).
- **Step 9 — Cache flights (`FlightCache`).** Same cache-aside pattern, **TTL 30s** (flight positions change fast; weather 10 min). Key `flights:%.4f:%.4f:%.2f`. Deserialize `List<FlightState>` via `Array<FlightState>::class.java` (Kotlin generics erased) then `.toList()`. Takeaway: TTL matches data volatility; cache key must include all query params (`deg`).
- **Step 10 — Spring AI starter + `ChatClient` (start agent phase).** Added `spring-ai-bom:2.0.1` via `dependencyManagement` imports + `spring-ai-starter-model-ollama`. Config: `spring.ai.ollama.base-url`, `chat.options.model`, `embedding.options.model`. `ChatClientConfig` builds a `ChatClient` with a default system prompt. Test endpoint `GET /api/v1/agent/ping?q=...` → `chatClient.prompt().user(q).call().content()`. Prereq: `ollama serve` + a pulled model. Note: Spring AI 2.0 renamed starters to `spring-ai-starter-model-*`. Local models available: `llama3.2:3b`, `llava:latest`.
- **Step 11 — First `@Tool` + tool-enabled endpoint.** `agent/TravelTools.kt` with `@Tool`/`@ToolParam` methods wrapping `WeatherService`/`FlightService` (Spring AI auto-generates JSON-schema tool definitions). `AskRequest` DTO + `POST /api/v1/agent/ask` using `chatClient.prompt().user(q).tools(travelTools).call().content()`. Model decides when to call tools. `llama3.2:3b` tool calling may be flaky → fall back to `qwen2.5:7b`.
- **Step 12 — Chat memory (in-memory).** Redis memory needs Redis Stack (D39), so use Spring AI's auto-configured `ChatMemory` (`InMemoryChatMemoryRepository` + `MessageWindowChatMemory`) — no dependency. `ChatClientConfig` injects `ChatMemory` and attaches `MessageChatMemoryAdvisor.builder(chatMemory).build()`. `AskRequest` gains `conversationId`; per-call `advisors { it.param(ChatMemory.CONVERSATION_ID, id) }` (required, else `IllegalArgumentException`). Same `conversationId` = remembers prior turns. Swap to `RedisChatMemoryRepository` when Redis Stack is available.
- **Step 13 — RAG ingestion (embeddings → vector store).** `SimpleVectorStore` was NOT on the classpath → added `org.springframework.ai:spring-ai-vector-store`. `RagConfig` builds `SimpleVectorStore.builder(embeddingModel).build()`. `PolicyIngestor` (an `ApplicationRunner`) reads `classpath:docs/flight-policies.md` via `TextReader(...).read()`, chunks with `TokenTextSplitter.builder().build().apply(docs)`, and `vectorStore.add(chunks)` at startup; logs chunk count. In-memory → re-ingests on restart; swap to pgvector/Redis Stack later. Other modules in the BOM: `spring-ai-vector-store-advisor`, `spring-ai-rag`, `spring-ai-pdf-document-reader`, `spring-ai-tika/markdown/jsoup-document-reader`.
- **Step 14 — `QuestionAnswerAdvisor` (RAG answers).** Added `org.springframework.ai:spring-ai-vector-store-advisor`. `ChatClientConfig` adds `QuestionAnswerAdvisor.builder(vectorStore).build()` as a second default advisor (after `MessageChatMemoryAdvisor`). Before each call it does a similarity search and appends retrieved policy text to the prompt → answers grounded in the ingested docs. Tune via `SearchRequest.builder().topK(4).similarityThreshold(0.5)`. **Caveat:** retrieves on every call (latency/tokens) → Step 16 makes retrieval a `searchPolicies` `@Tool` instead.
- **Step 17 — Spring Security wiring (exercise, in progress; user types backend).** Deps: `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`. Plan: (a) properties `travel.security.jwt.secret` (≥32B, `openssl rand -base64 32`) + `spring.security.oauth2.resourceserver.jwt.secret-key=${travel.security.jwt.secret}` → Boot auto-configures HS256 `NimbusJwtDecoder`; (b) `config/SecurityConfig.kt` — lambda-DSL `SecurityFilterChain`: csrf off (no cookies → CSRF inapplicable), `STATELESS`, permit `POST /api/auth/login` + `/mcp` [SHORTCUT — would otherwise 401 opencode's MCP client], `/api/**` + `anyRequest().authenticated()`, `.oauth2ResourceServer { jwt() }`; in-memory `UserDetailsService` (`user`/`{noop}password`, roles USER), `DaoAuthenticationProvider` (default DelegatingPasswordEncoder), `ProviderManager` as `AuthenticationManager`; (c) `service/TokenService.kt` — `NimbusJwtEncoder` + `OctetSequenceJWK(SecretKeySpec)`, HS256, claims `sub`/`roles`(authorities→`["ROLE_USER"]`)/`iat`/`exp`(+1h); (d) `controller/AuthController.kt` — `POST /api/auth/login`: `AuthenticationManager.authenticate(unauthenticated(u,p))` → `200 {"token"}` / catch `AuthenticationException` → `401 {"error"}`. Verify: health 401 (was 200) → login 200/401 → UI decode+countdown → tester 200 with token → restart-still-authorized (stateless proof) → tampered signature → 401 auto-logout. **Known gap:** `roles` claim inert (default converter maps only `scope`) → next step adds `JwtAuthenticationConverter` for `hasRole` + 403 demo.

## 9. Next steps

1. Verify/debug tool calling (logs, prompt tuning)
2. Redis chat memory (conversation state) — see Step 12
3. RAG: PDF → chunks → embeddings → vector store
4. `searchPolicies` tool (RAG-backed)
5. RAG-grounded agent with citations

## 10. Troubleshooting

- **`curl: (3) URL rejected: Malformed input to a URL function`** — raw spaces in the URL query string (strict newer curl). Fix: `curl -G <url> --data-urlencode "q=..."`, or pre-encode (`%20` / `+`). Body params (`-d '{...}'`) are fine.
- **Verify tool calling** — set `logging.level.org.springframework.ai=DEBUG`; log shows model tool call → Spring AI invokes the `@Tool` method → result fed back → final answer.
- **`No beans of 'ObjectMapper' type found`** — Jackson 2 vs 3 (see D27).
- **Redis key `TTL -2`** — wrong key format (see D28/Step 8); check `redis-cli KEYS '*'`.

---

## 11. Phase: Spring Security via a login screen (started)

> Goal: learn Spring Security hands-on. UI first (done), then backend security wiring as the exercise.

### Decisions
| Decision | Choice | Why |
|---|---|---|
| UI stack | React + Vite in `frontend/` (separate server, `:5173`) | Realistic SPA workflow; learn the dev-proxy pattern |
| Auth style | **JWT** (stateless) first | Typical for SPA/mobile/APIs; session (cookie) becomes the comparison lesson later |
| Ownership rule | Agent edits `frontend/` only; **backend code untouched** | Security wiring on the Spring side is the learning exercise |

### CORS, answered by construction
Browser on `:5173` → API on `:8080` is cross-origin; the *server* would need `Access-Control-Allow-Origin` headers (`http.cors()` + `CorsConfigurationSource` — a backend change). Instead `vite.config.js` proxies `/api/*` → `http://localhost:8080`: browser sees same-origin, Vite forwards server-to-server (no browser = no CORS enforcement). Bonus: real CORS + credentials requires `Allow-Credentials: true` and exact origin (no `*`) — the proxy sidesteps that for dev. In prod: build `frontend/dist` into `static/` (same origin) or configure CORS properly.

### JWT contract the backend must implement (the exercise)
- `POST /api/auth/login` — JSON `{ "username", "password" }` → `200 { "token": "<jwt>" }`, `401` on bad credentials
- Protected endpoints accept `Authorization: Bearer <jwt>` (UI already sends it on every call)
- Suggested path: `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`; `SecurityFilterChain` (permit `/api/auth/login`, protect the rest, `STATELESS`); in-memory `UserDetailsService` (`user`/`password`); issue HS256 JWT (nimbus-jose-jwt, ships with the starter); claims to include: `sub`, `roles`, `iat`, `exp` (UI renders them live)
- UI already visualizes: colored header/payload/signature segments, decoded claims tables, live `exp` countdown, `GET` tester that logs out on `401`

### Built so far (frontend/ only)
`frontend/` — React 19 + Vite 8: login form (`POST /api/auth/login` via proxy), token in `localStorage` (`travel-agent-jwt`; known XSS trade-off, httpOnly cookie = prod lesson), dashboard with JWT decode panel + expiry countdown + protected-API tester, 401 → auto-logout, graceful errors (API down / 404 / 401). Verified: login error path, decode/countdown, proxied `GET /api/v1/health` → `200 OK` (endpoint currently unprotected — evidence security isn't wired yet).

Run: `cd frontend && npm run dev` → http://localhost:5173 (Spring on `:8080`).

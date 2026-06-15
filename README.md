# aidevs4

Solutions to the 25 tasks from the **aidevs4** course — a single Spring Boot CLI. Each task lives in `tasks/taskNN.../` and implements the shared `Task` interface; common infrastructure handles talking to the course Hub (`hub.ag3nts.org`) and to LLMs.

## Stack

- **Java 25** + **Spring Boot 3.5.14** + **Maven**
- **Spring AI 1.1.7** (`spring-ai-starter-model-openai`) → **OpenRouter** (OpenAI-compatible, only `base-url` changes)
- **OpenAI directly** for TTS / Whisper STT (the audio task; OpenRouter doesn't serve those)
- **Langfuse** via OpenTelemetry (Micrometer Tracing → OTel OTLP exporter)
- `RestClient` for the Hub, `commons-csv` (CSV inputs), `jtokkit` (local token counting), `spring-dotenv` (`.env`), Jackson, validation

## Requirements

- JDK 25 (Amazon Corretto 25 on the dev machine; `JAVA_HOME` must point at it)
- Maven 3.9+ (or use the committed wrapper `./mvnw` / `mvnw.cmd`)
- API accounts and keys:
  - **OpenRouter** — `openrouter.ai`
  - **OpenAI** — `platform.openai.com` (only needed for the audio task `phonecall`)
  - **AI Devs Hub** — `hub.ag3nts.org` (key shown at the top of the page)
  - **Langfuse Cloud** — `cloud.langfuse.com` (public + secret key)

## Configuration

Copy `.env.example` to `.env` and fill in the values:

```
OPENROUTER_API_KEY=sk-or-v1-...
OPENAI_API_KEY=sk-...                     # audio (TTS/STT) for the phonecall task
AIDEVS_API_KEY=...
OTEL_EXPORTER_OTLP_ENDPOINT=https://cloud.langfuse.com/api/public/otel
LANGFUSE_AUTH_HEADER=Basic <base64(public:secret)>

# Optional, per-task:
PROXY_URL=...                             # public tunnel to /proxy (proxy task)
NEGOTIATIONS_URL=...                      # public tunnel to /api/negotiations (negotiations task)
RAILWAY_ROUTE=...                         # railway task
```

`.env` is in `.gitignore` — **never commit keys**.

## Running

Run from the **project root** (where `pom.xml` lives). On PowerShell the `-D` argument must be quoted. Because the system default `java` may be JDK 11, set `JAVA_HOME` to a JDK 25 first:

```powershell
$env:JAVA_HOME="C:\Program Files\Amazon Corretto\jdk25.0.3_9"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--task=people"
```

or from the built JAR:

```powershell
.\mvnw.cmd package
java -jar target/aidevs4-0.0.1-SNAPSHOT.jar --task=people
```

Running with no `--task` prints the available task names and exits.

### Tasks

Pass one of these as `--task=<name>`:

`people` · `findhim` · `proxy` · `sendit` · `railway` · `categorize` · `electricity` · `failure` · `mailbox` · `drone` · `evaluation` · `firmware` · `reactor` · `negotiations` · `savethem` · `okoeditor` · `windpower` · `domatowo` · `filesystem` · `foodwarehouse` · `radiomonitoring` · `phonecall` · `shellaccess` · `goingthere` · `timetravel`

Most tasks run as a CLI and exit. **`proxy`** and **`negotiations`** are web tasks: the app keeps a server up on port **3000** so the Hub can call back into a `@RestController`; expose the relevant endpoint via a tunnel (e.g. `ngrok`) and set `PROXY_URL` / `NEGOTIATIONS_URL`.

## Tests

Deterministic, never hit the network or an LLM — only the pure-logic pieces are unit-tested.

```powershell
.\mvnw.cmd test
.\mvnw.cmd "-Dtest=FlagExtractorTest" test            # single class
.\mvnw.cmd "-Dtest=FlagExtractorTest#methodName" test  # single method
```

## Layout

```
src/main/java/com/morawski/dev/aidevs/
├── Aidevs4Application.java     # main(); loads .env, picks CLI vs web mode
├── config/                     # @ConfigurationProperties, RestClient/HttpClient beans
├── hub/                        # HubClient (POST /verify, GET /data & /dane), FlagExtractor
├── llm/                        # LlmService (ChatClient + structured output), AudioService (TTS/STT)
├── common/                     # CsvReader, GeoUtils
└── tasks/
    ├── Task.java
    ├── TaskRunner.java         # ApplicationRunner → --task=<name>
    └── task01people/           # one package per task (task02..task25 follow the same pattern)
```

## Observability

Every LLM call shows up in Langfuse as a trace: prompt, completion, model, tokens, cost, latency. For nested chains add `@Observed(name = "...")` to the `solve()` method.

## Status

All 25 tasks implemented. Core wiring (`Task` / `TaskRunner` / `HubClient` / `LlmService`) is in place and shared across tasks.

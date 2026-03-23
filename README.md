# aidevs4

Solutions to 25 tasks from the **aidevs4** course — a single Spring Boot CLI, each task lives in `tasks/taskNN.../` and implements the shared `Task` interface.

## Stack

- **Java 25** + **Spring Boot 3.5.14** + **Maven**
- **Spring AI 1.1.7** (`spring-ai-starter-model-openai`) → **OpenRouter** (OpenAI-compatible, only `base-url` changes)
- **Langfuse** via OpenTelemetry (Micrometer Tracing → OTel OTLP exporter)
- `RestClient` for talking to the Hub (`hub.ag3nts.org`), `commons-csv`, Jackson, validation

## Requirements

- JDK 25
- Maven 3.9+
- API accounts and keys:
  - **OpenRouter** — `openrouter.ai`
  - **AI Devs Hub** — `hub.ag3nts.org` (key shown at the top of the page)
  - **Langfuse Cloud** — `cloud.langfuse.com` (public + secret key)

## Configuration

Copy `.env.example` to `.env` and fill in:

```
OPENROUTER_API_KEY=sk-or-v1-...
AIDEVS_API_KEY=...
OTEL_EXPORTER_OTLP_ENDPOINT=https://cloud.langfuse.com/api/public/otel
LANGFUSE_AUTH_HEADER=Basic <base64(public:secret)>
```

`.env` is in `.gitignore` — **never commit keys**.

## Running

Run from the **project root** (where `pom.xml` lives). On PowerShell the `-D` argument must be quoted:

```powershell
.\mvnw spring-boot:run "-Dspring-boot.run.arguments=--task=people"
```

or from the built JAR:

```powershell
.\mvnw package
java -jar target/aidevs4-0.0.1-SNAPSHOT.jar --task=people
```

## Layout

```
src/main/java/com/morawski/dev/aidevs/
├── AidevsApplication.java
├── config/      # @ConfigurationProperties, RestClient bean, ChatClient
├── hub/         # HubClient (POST /verify, GET /data), FlagExtractor
├── llm/         # LlmService — ChatClient wrapper + structured output
├── common/      # CsvReader, Downloader
└── tasks/
    ├── Task.java
    ├── TaskRunner.java         # ApplicationRunner → --task=<name>
    └── task01people/           # task 1 (task02..task25 follow the same pattern)
```

## Observability

Every LLM call shows up in Langfuse as a trace: prompt, completion, model, tokens, cost, latency. For nested chains add `@Observed(name = "...")` to the `solve()` method.

## Status

Fresh project — `pom.xml` from Spring Initializr, waiting for the core wiring (`Task` / `TaskRunner` / `HubClient` / `LlmService`) and the first task.

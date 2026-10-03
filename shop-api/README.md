# Shop API – Spring Boot + SQLite + Docker
THIS IS generated using CLAUDE for me to learn agentic ai
claude suggested a simple web app to query customer behaviour

REST API over a SQLite database holding **products**, **inventory** and **2M+ sales rows**.

## Run
```bash
docker compose up --build
```
First start generates the data (a few seconds to a minute depending on your machine and
`APP_SEED_SALES`). It is stored in the `shop-data` volume, so later starts are instant.
Wait until `docker compose ps` shows the service as healthy.

Reset data: `docker compose down -v`

## Endpoints
| Method | Path | Notes |
|---|---|---|
| GET | `/api/products?category=Toys&page=0&size=20` | |
| GET | `/api/products/{id}` | |
| GET | `/api/inventory?lowStock=true&page=0&size=20` | items at/below reorder level |
| GET | `/api/inventory/{productId}` | |
| PUT | `/api/inventory/{productId}` | body `{"quantityOnHand": 100}` |
| GET | `/api/sales?productId=&customerId=&region=&from=&to=&afterId=&size=` | keyset pagination via `afterId` |
| GET | `/api/sales/{id}` | |
| POST | `/api/sales` | creates sale and decrements stock atomically; 409 if not enough stock |
| GET | `/api/reports/stats` | row counts |
| GET | `/api/reports/sales-by-region?from=2024-01-01&to=2024-12-31` | |
| GET | `/api/reports/top-products?limit=10` | |
| GET | `/api/reports/monthly-revenue?year=2024` | |
| GET | `/actuator/health` | |

## Try it
```bash
curl localhost:8080/api/reports/stats
curl "localhost:8080/api/sales?region=north&from=2024-01-01&to=2024-01-31&size=5"
curl "localhost:8080/api/sales?afterId=1000&size=5"
curl localhost:8080/api/reports/top-products?limit=5
curl -X POST localhost:8080/api/sales -H 'Content-Type: application/json' \
     -d '{"productId":1,"customerId":42,"region":"east","quantity":2}'
```

## Run without Docker
Java 17 + Maven: `mvn spring-boot:run` (DB file goes to `./data/shop.db`).

## Notes
- SQLite runs in WAL mode: many concurrent readers, one writer at a time (`busy_timeout` handles contention).
- Fine for demos, internal tools and learning. For heavy concurrent writes, move to PostgreSQL.

## AI agent (OpenAI)
`POST /api/agent/chat` takes a natural-language question. The model calls read-only tools
(stats, products, stock, low stock, top products, region/monthly reports), the app executes them
against SQLite, and the model writes the answer. Max 6 model calls per question.

Setup: copy `.env.example` to `.env`, fill in `OPENAI_API_KEY`, then `docker compose up --build -d`.
```bash
curl -X POST localhost:8080/api/agent/chat -H 'Content-Type: application/json' \
  -H 'X-Agent-Token: <your AGENT_TOKEN, if set>' \
  -d '{"question":"Which of the top 20 selling products are low on stock?"}'
```
The response includes `toolCalls` (what the agent did), `steps`, and `totalTokens`.

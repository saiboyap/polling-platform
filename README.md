# Polling Platform

A production-grade, event-driven real-time polling platform built with Spring Boot and React. Users can create polls, cast votes, and watch results update live through WebSocket connections with SSE fallback.

[![Backend](https://img.shields.io/badge/backend-Spring%20Boot%203.2-green)](https://polling-platform-production-180d.up.railway.app)
[![Frontend](https://img.shields.io/badge/frontend-React%2018-blue)](https://affectionate-charisma-production-49dc.up.railway.app)
[![Java](https://img.shields.io/badge/java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![TypeScript](https://img.shields.io/badge/typescript-5.3-blue)](https://www.typescriptlang.org/)

## Live Demo

| Service | URL |
|---------|-----|
| Frontend | https://affectionate-charisma-production-49dc.up.railway.app |
| Backend API | https://polling-platform-production-180d.up.railway.app |
| Swagger UI | https://polling-platform-production-180d.up.railway.app/swagger-ui.html |
| Health Check | https://polling-platform-production-180d.up.railway.app/health |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Railway Cloud                           │
│                                                                 │
│  ┌──────────────┐    HTTPS     ┌──────────────────────────┐   │
│  │   React SPA  │ ──────────► │ nginx (envsubst PORT)    │   │
│  │  (Vite/TS)   │             │ /api/* → backend         │   │
│  └──────────────┘             │ /ws    → backend         │   │
│                               └────────────┬─────────────┘   │
│                                            │                   │
│                                            ▼                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                 Spring Boot 3.2 Application              │  │
│  │                                                          │  │
│  │  ┌──────────┐  ┌────────────┐  ┌─────────────────────┐  │  │
│  │  │JWT Filter│  │REST Layer  │  │STOMP WebSocket      │  │  │
│  │  │Correlation│  │+Validation │  │+ SSE Fallback       │  │  │
│  │  │ID Filter │  │            │  └─────────────────────┘  │  │
│  │  └──────────┘  └────────────┘                           │  │
│  │                                                          │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │       Service Layer                                │  │  │
│  │  │  AuditLog AOP · CircuitBreaker · Rate Limiting    │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  │                                                          │  │
│  │  ┌────────────┐  ┌──────────────┐  ┌─────────────────┐  │  │
│  │  │JPA / Hiber-│  │Redis Cache   │  │Kafka Producer/  │  │  │
│  │  │nate + Flyway│  │(optional)    │  │Consumer (opt.)  │  │  │
│  │  └────────────┘  └──────────────┘  └─────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
│              │                   │                              │
│       ┌──────┴──────┐    ┌───────┴──────┐                     │
│       │ PostgreSQL  │    │   Redis 7    │ (optional)           │
│       │  (Railway)  │    │              │                      │
│       └─────────────┘    └──────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

## Features

- **Three poll types** — Single choice, multi-choice (configurable max selections), free-text open-ended responses
- **Real-time results** — Live vote count updates via STOMP/WebSocket with automatic SSE fallback (8 s timeout)
- **JWT authentication** — Stateless auth with configurable token expiry (default 24 h)
- **Redis caching** — Optional vote count cache with DB fallback and Resilience4j circuit breaker
- **Kafka events** — Optional event streaming for `PollCreated` and `VoteSubmitted` events with idempotency
- **Rate limiting** — Per-IP Redis-backed rate limiting (auth: 10 rpm, votes: 20 rpm, general: 200 rpm)
- **Trending polls** — Redis sorted-set ranking by vote volume
- **Audit logging** — Async AOP-based audit trail for all mutating operations
- **Correlation IDs** — Every request tagged with a UUID for distributed tracing
- **Admin API** — Platform-wide statistics for ADMIN-role users
- **Graceful shutdown** — 30 s drain window for in-flight requests

## Tech Stack

### Backend
| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Runtime |
| Spring Boot | 3.2.1 | Application framework |
| Spring Data JPA + Hibernate 6 | 3.2.1 | ORM |
| Spring Security | 3.2.1 | JWT-based authentication |
| Spring WebSocket (STOMP) | 3.2.1 | Real-time messaging |
| Spring Data Redis | 3.2.1 | Cache and rate limiting |
| Spring Kafka | 3.1.x | Event streaming |
| PostgreSQL Driver | 42.x | Database |
| Flyway | 9.x | Schema migrations |
| JJWT | 0.12.3 | JWT generation and validation |
| Resilience4j | 2.1.0 | Circuit breaker and retry |
| Lombok | latest | Boilerplate reduction |
| SpringDoc OpenAPI | 2.3.0 | Interactive API docs |
| Logstash Logback Encoder | 7.4 | Structured JSON logging |

### Frontend
| Technology | Version | Purpose |
|------------|---------|---------|
| React | 18.2 | UI framework |
| TypeScript | 5.3 | Type safety |
| Vite | 5.1 | Build tool and dev server |
| Tailwind CSS | 3.4 | Utility-first styling |
| Zustand | 4.5 | Client-side state management |
| Axios | 1.6 | HTTP client with retry interceptors |
| STOMP.js | 7.0 | WebSocket/STOMP client |
| SockJS | 1.6 | WebSocket transport polyfill |
| React Router | 6.21 | Client-side routing |

### Infrastructure
| Technology | Purpose |
|------------|---------|
| PostgreSQL 15 | Primary database |
| Redis 7 | Vote cache, rate limiting, trending (optional) |
| Apache Kafka + Zookeeper | Event streaming (optional) |
| Docker / Docker Compose | Containerisation and local orchestration |
| nginx 1.25 | Static file serving and API reverse proxy |
| Railway | Cloud platform (auto-deploy from GitHub) |

---

## Local Development Setup

### Prerequisites

- Java 17+ — `java -version`
- Maven 3.9+ — `mvn -version`
- Node.js 20+ — `node -v`
- Docker and Docker Compose

### 1. Clone

```bash
git clone https://github.com/saiboyap/polling-platform.git
cd polling-platform
```

### 2. Start infrastructure

```bash
# Minimal setup (PostgreSQL + Redis)
docker compose up postgres redis -d

# Full setup (adds Kafka for event streaming)
docker compose up postgres redis kafka zookeeper -d
```

### 3. Run the backend

```bash
cd backend
```

Set environment variables (or create `src/main/resources/application-local.yml`):

```bash
export PGHOST=localhost
export PGPORT=5432
export PGDATABASE=polling_db
export PGUSER=polling_user
export PGPASSWORD=polling_pass
export REDIS_ENABLED=true
export SPRING_REDIS_HOST=localhost
export KAFKA_ENABLED=false
```

```bash
mvn spring-boot:run
```

API base: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 4. Run the frontend

```bash
cd ../frontend
npm install
```

Create `.env.local`:

```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=http://localhost:8080/ws
```

```bash
npm run dev
```

App: `http://localhost:5173`

---

## Docker Compose (Full Stack)

```bash
# Build and start all services
docker compose up --build

# Run in background
docker compose up --build -d

# Tail logs
docker compose logs -f backend

# Stop and clean up
docker compose down -v
```

Services exposed:

| Service | Port |
|---------|------|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |

To enable Redis and Kafka features, set in `docker-compose.yml` under the `backend` service:

```yaml
environment:
  REDIS_ENABLED: "true"
  KAFKA_ENABLED: "true"
```

---

## Environment Variables Reference

### Backend

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PORT` | No | `8080` | Server listen port (Railway sets this automatically) |
| `PGHOST` | **Yes** | — | PostgreSQL hostname |
| `PGPORT` | **Yes** | — | PostgreSQL port |
| `PGDATABASE` | **Yes** | — | Database name |
| `PGUSER` | **Yes** | — | Database username |
| `PGPASSWORD` | **Yes** | — | Database password |
| `JWT_SECRET` | No | (base64 default) | HS256 signing key — **always override in production** |
| `JWT_EXPIRATION` | No | `86400000` | Token TTL in milliseconds (24 h) |
| `REDIS_ENABLED` | No | `false` | Enable Redis cache, rate limiting and trending |
| `SPRING_REDIS_HOST` | No | `localhost` | Redis hostname |
| `SPRING_REDIS_PORT` | No | `6379` | Redis port |
| `KAFKA_ENABLED` | No | `false` | Enable Kafka event publishing and consumption |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker list |

### Frontend (build-time via Vite)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `VITE_API_BASE_URL` | No | `/api` | Backend API prefix (relative for production) |
| `VITE_WS_URL` | No | `/ws` | WebSocket endpoint |
| `PORT` | No | `80` | nginx listen port (Railway sets this automatically) |

---

## API Quick Reference

Full documentation with request/response examples: [`docs/API.md`](docs/API.md)  
Interactive docs: [Swagger UI](https://polling-platform-production-180d.up.railway.app/swagger-ui.html)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | None | Create account |
| `POST` | `/api/auth/login` | None | Login, receive JWT |
| `GET` | `/api/polls` | None | List active polls (paginated) |
| `POST` | `/api/polls` | Bearer | Create a poll |
| `GET` | `/api/polls/{id}` | None | Poll details |
| `PATCH` | `/api/polls/{id}/close` | Bearer (owner) | Close a poll |
| `POST` | `/api/polls/{id}/vote` | Bearer | Submit vote |
| `GET` | `/api/polls/{id}/results` | None | Live results |
| `GET` | `/api/polls/{id}/stream` | None | SSE live stream |
| `GET` | `/api/polls/trending` | None | Trending by votes |
| `GET` | `/api/admin/stats` | Bearer (ADMIN) | Platform stats |
| `GET` | `/health` | None | Health check |

---

## Deployment

See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for full Railway, Docker, and Kubernetes guides.

**Railway quick start:**
1. Fork the repository
2. Create a Railway project → New Service → GitHub repo
3. Add a PostgreSQL plugin
4. Set the required environment variables (`PGHOST`, `PGPORT`, etc. are auto-set by the plugin)
5. Push to `main` — Railway deploys automatically

---

## Documentation Index

| Document | Description |
|----------|-------------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System design, component diagrams, data flows, schema |
| [`docs/API.md`](docs/API.md) | Complete endpoint reference with request/response examples |
| [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) | Developer setup, code structure, contribution guide |
| [`docs/AGILE.md`](docs/AGILE.md) | Product vision, user stories, sprint breakdown |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Railway, Docker, and Kubernetes deployment guides |
| [`docs/TESTING.md`](docs/TESTING.md) | Testing strategy, unit/integration/E2E guidance |

---

## Project Structure

```
polling-platform/
├── backend/
│   ├── src/main/java/com/polling/platform/
│   │   ├── annotation/        @AuditLogged custom annotation
│   │   ├── aspect/            AuditLogAspect (AOP)
│   │   ├── config/            Spring beans (Security, WebSocket, Redis, Kafka, OpenAPI)
│   │   ├── controller/        REST controllers (Auth, Poll, Vote, SSE, Trending, Admin, Health)
│   │   ├── dto/               Request/response DTOs and Kafka event payloads
│   │   ├── entity/            JPA entities (User, Poll, PollOption, Vote, FreeTextVote, AuditLog…)
│   │   ├── exception/         Custom exceptions + GlobalExceptionHandler
│   │   ├── filter/            Servlet filters (CorrelationId, RateLimit)
│   │   ├── kafka/             Kafka producer gateway and consumer
│   │   ├── repository/        Spring Data JPA repositories
│   │   ├── security/          JwtTokenProvider + JwtAuthenticationFilter
│   │   ├── service/           Business logic (Poll, Vote, Auth, Audit, Redis, Trending)
│   │   └── websocket/         WebSocketEventPublisher + SseEmitterRegistry
│   ├── src/main/resources/
│   │   ├── application.yml    Master configuration
│   │   └── db/migration/      Flyway V1–V4 SQL scripts
│   └── Dockerfile             Multi-stage build (Maven → Amazon Corretto 17 Alpine)
├── frontend/
│   ├── src/
│   │   ├── components/        Reusable UI (Button, Input, Navbar, PollCard, VoteOptions…)
│   │   ├── hooks/             usePolls, usePoll, useVote, useRealtimeVotes, useAuth
│   │   ├── pages/             HomePage, PollDetailPage, LoginPage, RegisterPage
│   │   ├── services/          pollService.ts, api.ts (Axios with retry interceptor)
│   │   ├── store/             authStore.ts, pollStore.ts (Zustand)
│   │   └── types/             poll.ts, common.ts
│   ├── nginx.conf             nginx template with ${PORT} placeholder
│   └── Dockerfile             Multi-stage build (Node 20 → nginx 1.25 Alpine)
├── docker-compose.yml         Full local stack (Postgres, Redis, Kafka, backend, frontend)
└── docs/                      Project documentation
```

---

## License

MIT

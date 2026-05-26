# PollHub – Event-Driven Polling Platform

A production-style full-stack polling platform with real-time vote updates powered by WebSockets, Kafka event streaming, and Redis caching.

## Tech Stack

| Layer      | Technology                                          |
|------------|-----------------------------------------------------|
| Frontend   | React 18, TypeScript, Tailwind CSS, Zustand, Vite  |
| Backend    | Java 17, Spring Boot 3.2, Spring Security + JWT    |
| Messaging  | Apache Kafka (poll-created, vote-submitted, DLQ)   |
| Cache      | Redis (live vote counts per poll)                  |
| Database   | PostgreSQL 15 + Flyway migrations                  |
| WebSocket  | Spring WebSocket / STOMP + SockJS                  |
| Container  | Docker Compose                                      |

---

## Quick Start (Docker)

```bash
# From the repo root
docker compose up --build
```

| Service  | URL                        |
|----------|----------------------------|
| Frontend | http://localhost:3000      |
| Backend  | http://localhost:8080      |
| Postgres | localhost:5432             |
| Redis    | localhost:6379             |
| Kafka    | localhost:9092             |

---

## Local Development (without Docker)

### Prerequisites
- Java 17+, Maven 3.9+
- Node 20+, npm 10+
- Running PostgreSQL, Redis, Kafka/Zookeeper

### Backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts on **port 8080**.  
Set environment variables or edit `application.yml` to point at your local services:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/polling_db
SPRING_DATASOURCE_USERNAME=polling_user
SPRING_DATASOURCE_PASSWORD=polling_pass
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SPRING_REDIS_HOST=localhost
JWT_SECRET=<base64-encoded-256-bit-key>
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on **port 3000** with a Vite proxy forwarding `/api` and `/ws` to the backend.

---

## Project Structure

```
polling-platform/
├── docker-compose.yml
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/polling/platform/
│       ├── config/          # Security, Kafka, Redis, WebSocket
│       ├── controller/      # AuthController, PollController, VoteController
│       ├── dto/
│       │   ├── event/       # Kafka event POJOs
│       │   ├── request/     # Validated request DTOs
│       │   └── response/    # API response DTOs
│       ├── entity/          # JPA entities (User, Poll, PollOption, Vote)
│       ├── exception/       # GlobalExceptionHandler + custom exceptions
│       ├── kafka/
│       │   ├── consumer/    # PollEventConsumer (with DLQ routing)
│       │   └── producer/    # PollEventProducer
│       ├── repository/      # Spring Data JPA repositories
│       ├── security/        # JwtTokenProvider, JwtAuthenticationFilter
│       ├── service/         # AuthService, PollService, VoteService, RedisVoteCacheService
│       └── websocket/       # WebSocketEventPublisher
└── frontend/
    └── src/
        ├── components/
        │   ├── auth/        # LoginForm, RegisterForm
        │   ├── common/      # Button, Input, LoadingSpinner, Navbar
        │   └── poll/        # PollCard, PollList, CreatePollForm, VoteResults
        ├── hooks/           # useAuth, usePolls, useWebSocket
        ├── pages/           # HomePage, LoginPage, RegisterPage, PollDetail, CreatePoll
        ├── services/        # api.ts (Axios), authService, pollService
        ├── store/           # Zustand: authStore, pollStore
        ├── types/           # TypeScript interfaces
        └── utils/           # tokenUtils, formatUtils
```

---

## API Reference

### Auth
| Method | Endpoint             | Auth | Description      |
|--------|----------------------|------|------------------|
| POST   | `/api/auth/register` | No   | Create account   |
| POST   | `/api/auth/login`    | No   | Get JWT token    |

### Polls
| Method | Endpoint              | Auth    | Description           |
|--------|-----------------------|---------|-----------------------|
| GET    | `/api/polls`          | No      | List active polls     |
| POST   | `/api/polls`          | Yes     | Create poll           |
| GET    | `/api/polls/:id`      | No      | Get poll by ID        |
| PATCH  | `/api/polls/:id/close`| Yes     | Close your own poll   |

### Votes
| Method | Endpoint                   | Auth | Description             |
|--------|----------------------------|------|-------------------------|
| POST   | `/api/votes`               | Yes  | Submit a vote           |
| GET    | `/api/votes/:pollId/results`| No  | Get live vote counts    |

### WebSocket
Subscribe to `/topic/polls/{pollId}/votes` to receive real-time vote-count updates.  
Connect via STOMP over SockJS at `/ws`.

---

## Kafka Topics

| Topic                  | Producer    | Consumer            | Purpose                       |
|------------------------|-------------|---------------------|-------------------------------|
| `poll-created-topic`   | VoteService | PollEventConsumer   | Initialise Redis cache        |
| `vote-submitted-topic` | VoteService | PollEventConsumer   | Audit / downstream analytics  |
| `dlq-topic`            | Consumer    | DLQ consumer        | Failed message quarantine     |

---

## Security

- JWT Bearer token, 24-hour expiry (configurable via `JWT_EXPIRATION`)
- Roles: `USER` (default), `ADMIN`
- CORS configured for all origins in development — tighten in production

---

## Environment Variables

| Variable                    | Default                         | Description              |
|-----------------------------|---------------------------------|--------------------------|
| `SPRING_DATASOURCE_URL`     | `jdbc:postgresql://localhost...`| Postgres JDBC URL        |
| `SPRING_DATASOURCE_USERNAME`| `polling_user`                  | DB username              |
| `SPRING_DATASOURCE_PASSWORD`| `polling_pass`                  | DB password              |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`          | Kafka brokers            |
| `SPRING_REDIS_HOST`         | `localhost`                     | Redis host               |
| `SPRING_REDIS_PORT`         | `6379`                          | Redis port               |
| `JWT_SECRET`                | (base64 key in yml)             | HMAC-SHA signing key     |
| `JWT_EXPIRATION`            | `86400000` (24h)                | Token TTL in ms          |
| `VITE_API_URL`              | `/api` (proxy in dev)           | Frontend API base URL    |
| `VITE_WS_URL`               | `/ws` (proxy in dev)            | Frontend WebSocket URL   |

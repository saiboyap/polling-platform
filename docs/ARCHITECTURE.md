# Architecture

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [Component Overview](#2-component-overview)
3. [Request Flow Diagrams](#3-request-flow-diagrams)
4. [Database Schema](#4-database-schema)
5. [Event-Driven Flow](#5-event-driven-flow)
6. [Real-Time Architecture](#6-real-time-architecture)
7. [Security Architecture](#7-security-architecture)
8. [Resilience and Fault Tolerance](#8-resilience-and-fault-tolerance)
9. [Scalability Considerations](#9-scalability-considerations)

---

## 1. High-Level Architecture

The platform follows a layered, hexagonal-inspired architecture with clear separation between the HTTP boundary, business logic, and infrastructure adapters (database, cache, event bus).

```
┌──────────────────────────────────────────────────────────────────┐
│                        Client Browser                            │
│                                                                  │
│   React SPA (Vite + TypeScript + Tailwind)                      │
│   ├── Zustand state store                                        │
│   ├── Axios HTTP client (retry interceptor, JWT header)         │
│   └── STOMP/SockJS + SSE fallback (useRealtimeVotes hook)       │
└──────────────────────┬───────────────────────────────────────────┘
                       │ HTTPS  /  WSS
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│                    nginx (frontend service)                      │
│  Serves /dist static assets                                      │
│  Proxies /api/* and /ws to backend service                       │
└──────────────────────┬───────────────────────────────────────────┘
                       │ HTTP
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│              Spring Boot 3.2 Application                         │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Inbound Filters (chain order)                          │    │
│  │  1. CorrelationIdFilter  — injects X-Correlation-ID     │    │
│  │  2. RateLimitFilter      — Redis token bucket (opt.)    │    │
│  │  3. JwtAuthenticationFilter — validates Bearer token    │    │
│  └──────────────────────────┬──────────────────────────────┘    │
│                             ▼                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Controller Layer                                       │    │
│  │  AuthController · PollController · VoteController      │    │
│  │  SseController · TrendingController · AdminController  │    │
│  │  HealthController                                       │    │
│  └──────────────────────────┬──────────────────────────────┘    │
│                             ▼                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Service Layer  (AOP-decorated)                         │    │
│  │  @AuditLogged aspect → async audit trail                │    │
│  │  @CircuitBreaker / @Retry (Resilience4j on Redis/Kafka) │    │
│  │                                                         │    │
│  │  AuthService · PollService · VoteService                │    │
│  │  RedisVoteCacheService · TrendingPollService            │    │
│  │  AuditLogService (@Async)                               │    │
│  └──────────┬──────────────────────────┬───────────────────┘    │
│             │                          │                        │
│      ┌──────┴──────┐           ┌───────┴────────┐              │
│      │ JPA Repos   │           │ Infrastructure  │              │
│      │ (Hibernate) │           │ Adapters        │              │
│      └──────┬──────┘           │                │              │
│             │                  │ ┌────────────┐  │              │
│             │                  │ │Redis Cache │  │              │
│      ┌──────┴──────┐           │ │(Lettuce)   │  │              │
│      │ PostgreSQL  │           │ └────────────┘  │              │
│      │             │           │ ┌────────────┐  │              │
│      └─────────────┘           │ │Kafka       │  │              │
│                                │ │Producer/   │  │              │
│                                │ │Consumer    │  │              │
│                                │ └────────────┘  │              │
│                                │ ┌────────────┐  │              │
│                                │ │WebSocket   │  │              │
│                                │ │SseRegistry │  │              │
│                                │ └────────────┘  │              │
│                                └────────────────┘              │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Overview

### Backend Packages

| Package | Responsibility |
|---------|----------------|
| `controller` | HTTP endpoints. Thin — only parameter extraction and delegation to services. |
| `service` | Business rules, orchestration, transaction boundaries. |
| `repository` | Spring Data JPA interfaces. Custom JPQL queries annotated with `@Query`. |
| `entity` | JPA-mapped domain objects. Hibernate 6 / `@GeneratedValue(UUID)`. |
| `dto` | Immutable request/response shapes. Separate from entities to decouple API contract from schema. |
| `security` | `JwtTokenProvider` (sign/validate HS256 tokens), `JwtAuthenticationFilter` (per-request validation). |
| `filter` | `CorrelationIdFilter` tags each request with a UUID in MDC. `RateLimitFilter` uses Redis INCR for sliding window. |
| `websocket` | `WebSocketEventPublisher` fans out vote updates to STOMP topics and SSE connections. `SseEmitterRegistry` manages live `SseEmitter` instances. |
| `kafka` | `PollEventProducer` wraps a `KafkaSendGateway`. `PollEventConsumer` processes events idempotently via `ProcessedEvent` table. |
| `aspect` | `AuditLogAspect` intercepts `@AuditLogged` methods, extracts actor/entity from return value, and delegates to async `AuditLogService`. |
| `config` | Spring `@Bean` definitions: `SecurityFilterChain`, `WebSocketMessageBrokerConfigurer`, `RedisTemplate`, Kafka factories, OpenAPI spec. |
| `exception` | `GlobalExceptionHandler` (`@RestControllerAdvice`) maps every exception type to an HTTP status code and structured `ErrorResponse`. |

### Frontend Modules

| Module | Responsibility |
|--------|----------------|
| `pages/` | Route-level components (`HomePage`, `PollDetailPage`, `LoginPage`, `RegisterPage`). Handle page-level state. |
| `components/` | Reusable UI primitives (`Button`, `Input`, `Navbar`) and domain components (`PollCard`, `VoteOptions`, `VoteResults`, `ResultsBar`). |
| `hooks/` | `usePolls` (paginated feed), `usePoll` (single poll), `useVote` (submit), `useRealtimeVotes` (STOMP+SSE), `useAuth` (login/register). |
| `services/` | `pollService.ts` — typed wrappers over Axios. `api.ts` — Axios instance with base URL, JWT header injection, and 3-retry interceptor. |
| `store/` | `authStore` (JWT token, user identity). `pollStore` (feed cache, live vote counts). Both powered by Zustand. |
| `types/` | `poll.ts` — all TypeScript interfaces (`Poll`, `PollResults`, `CastVoteRequest`, etc.). |

---

## 3. Request Flow Diagrams

### Authentication Flow

```
Client                nginx           Spring Boot
  │                     │                  │
  ├─POST /api/auth/login─►                  │
  │                     ├─── proxy ────────►│
  │                     │                  ├─ AuthService.login()
  │                     │                  ├─ BCrypt.matches(password)
  │                     │                  ├─ JwtTokenProvider.generate()
  │                     │                  │     (HS256, 24 h expiry)
  │                     │◄──── 200 ────────┤
  │◄─ { token, username }─┤                 │
  │                     │                  │
  │ (store JWT in Zustand authStore)        │
  │                     │                  │
  ├─GET /api/polls ─────►                   │
  │  Authorization: Bearer <token>          │
  │                     ├─── proxy ─────────►
  │                     │                  ├─ JwtAuthenticationFilter
  │                     │                  │   validates signature + expiry
  │                     │                  │   sets SecurityContext
  │                     │                  ├─ PollController.getActivePolls()
  │                     │◄──── 200 polls ──┤
```

### Vote Submission Flow

```
Client          nginx       Spring Boot        PostgreSQL       Redis
  │               │              │                  │              │
  ├─POST /vote────►              │                  │              │
  │               ├──proxy──────►│                  │              │
  │               │              ├─validatePollActive              │
  │               │              ├─checkDuplicate────►              │
  │               │              │◄─────────────────┤              │
  │               │              ├─saveVote──────────►              │
  │               │              │◄─────────────────┤              │
  │               │              ├─incrementCount─────────────────►│
  │               │              ├─resolvedCounts──────────────────►
  │               │              │◄────────────────────────────────┤
  │               │              ├─publishVoteUpdate               │
  │               │              │   (STOMP broadcast + SSE fans)  │
  │               │              ├─return PollResultsResponse      │
  │               │◄─201 results─┤                  │              │
  │◄──────────────┤              │                  │              │
  │               │              │                  │              │
  │ (setResults, setHasVoted)    │                  │              │
```

### Real-Time Update Flow

```
VoterClient          Server              WatcherClient
     │                  │                      │
     │ POST /vote        │                      │
     ├──────────────────►│                      │
     │                  │ publishVoteUpdate()   │
     │                  ├─ STOMP /topic/polls/{id}/votes
     │                  │                      │◄──STOMP message
     │                  ├─ SSE broadcast        │
     │                  │                      │◄──SSE event
     │◄── 201 response ─┤                      │
     │                  │              (updateVoteCounts in store)
     │                  │              (VoteResults re-renders)
```

---

## 4. Database Schema

### Entity Relationship Diagram

```
┌─────────────┐       ┌─────────────────┐       ┌──────────────────┐
│    users    │       │      polls      │       │   poll_options   │
├─────────────┤       ├─────────────────┤       ├──────────────────┤
│ id (UUID PK)│◄──┐   │ id (UUID PK)   │──────►│ id (UUID PK)     │
│ username    │   └───│ created_by (FK) │       │ poll_id (FK)     │
│ email       │       │ question        │       │ option_text      │
│ password    │       │ status          │       │ version (bigint) │
│ role        │       │ poll_type       │       └──────────────────┘
│ created_at  │       │ max_choices     │                │
│ updated_at  │       │ expires_at      │                │
└─────────────┘       │ created_at      │                │
       │              │ updated_at      │                │
       │              └─────────────────┘                │
       │                       │                         │
       │              ┌────────┴────────┐      ┌────────┴──────┐
       │              │     votes       │      │               │
       │              ├─────────────────┤      │               │
       └──────────────│ poll_id (FK)    │      │               │
                      │ user_id (FK)    │◄─────┘               │
                      │ option_id (FK)──┘                      │
                      │ voted_at        │                      │
                      └─────────────────┘                      │
                                                               │
       ┌──────────────────────────────────────────────────────┘
       │
┌──────┴──────────────┐   ┌──────────────────┐   ┌──────────────────┐
│   free_text_votes   │   │   audit_logs     │   │ processed_events │
├─────────────────────┤   ├──────────────────┤   ├──────────────────┤
│ id (UUID PK)        │   │ id (UUID PK)     │   │ event_id (UUID)  │
│ poll_id (FK)        │   │ event_type       │   │ processed_at     │
│ user_id (FK)        │   │ actor            │   └──────────────────┘
│ response_text (TEXT)│   │ entity_type      │
│ voted_at            │   │ entity_id        │
└─────────────────────┘   │ ip_address       │
                          │ created_at       │
                          └──────────────────┘
```

### Key Constraints

| Table | Constraint | Purpose |
|-------|-----------|---------|
| `votes` | `UNIQUE(poll_id, user_id, option_id)` | Prevents duplicate option votes (multi-choice safe) |
| `free_text_votes` | `UNIQUE(poll_id, user_id)` | One response per user per free-text poll |
| `users` | `UNIQUE(username)`, `UNIQUE(email)` | Account uniqueness |
| `processed_events` | `UNIQUE(event_id)` | Kafka consumer idempotency |

### Flyway Migration History

| Version | Description |
|---------|-------------|
| V1 | Base schema — users, polls, poll_options, votes |
| V2 | Add poll_type / max_choices to polls; relax votes unique constraint; create free_text_votes |
| V3 | Create processed_events table for Kafka idempotency |
| V4 | Create audit_logs table |

---

## 5. Event-Driven Flow

When Kafka is enabled (`KAFKA_ENABLED=true`), the platform publishes and consumes two event types:

### PollCreated Event

```
PollService.createPoll()
    └─► PollEventProducer.publishPollCreated()
            └─► KafkaSendGateway.send(topic="poll-created", payload=PollCreatedEvent)
                    └─► Kafka Topic: poll-created
                            └─► PollEventConsumer.handlePollCreated()
                                    ├─ EventIdempotencyService.isProcessed(eventId) → skip if seen
                                    └─ (notification, analytics, etc.)
```

### VoteSubmitted Event

```
VoteService.castVote()
    └─► PollEventProducer.publishVoteSubmitted()
            └─► KafkaSendGateway.send(topic="vote-submitted", payload=VoteSubmittedEvent)
                    └─► Kafka Topic: vote-submitted
                            └─► PollEventConsumer.handleVoteSubmitted()
                                    ├─ EventIdempotencyService.isProcessed(eventId) → skip if seen
                                    └─ (leaderboard update, analytics, etc.)
```

### Idempotency

Each event carries a UUID `eventId`. Before processing, `PollEventConsumer` checks `processed_events`. If found, the event is silently skipped. Otherwise, the event is processed and the ID stored — all within a single DB transaction to prevent race conditions.

---

## 6. Real-Time Architecture

The platform uses a two-tier real-time strategy:

```
┌────────────────────────────────────────────────────────┐
│               Client (useRealtimeVotes hook)            │
│                                                         │
│  Phase 1: Try STOMP over SockJS                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  new Client({ webSocketFactory: () => SockJS() }) │  │
│  │  client.subscribe("/topic/polls/{id}/votes")      │  │
│  └──────────────────────────────────────────────────┘  │
│                          │                              │
│                     WS_TIMEOUT (8s)                     │
│                          │                              │
│  Phase 2: Fallback to SSE (if WebSocket fails)          │
│  ┌──────────────────────────────────────────────────┐  │
│  │  new EventSource("/api/polls/{id}/stream")        │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

Server side:
┌───────────────────────────────────────────────────────┐
│  WebSocketEventPublisher.publishVoteUpdate()           │
│  ├─ messagingTemplate.convertAndSend(                 │
│  │       "/topic/polls/{id}/votes", voteCounts)       │
│  │   → all active STOMP subscribers notified          │
│  └─ sseEmitterRegistry.broadcast(pollId, voteCounts)  │
│       → all active SseEmitter instances notified      │
└───────────────────────────────────────────────────────┘
```

**Vote count source priority (server-side):**

1. Redis hash `poll:votes:{pollId}` (fast, ~1 ms)
2. Database query `SELECT option_id, COUNT(*) FROM votes WHERE poll_id = ?` (fallback)
3. If DB result populated and Redis available → seed Redis cache

---

## 7. Security Architecture

### Authentication

- **Mechanism**: Stateless JWT (HS256, JJWT 0.12.3)
- **Token lifetime**: 24 hours (configurable via `JWT_EXPIRATION`)
- **Storage**: Client stores token in Zustand in-memory state; never in localStorage (XSS mitigation)
- **Filter**: `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter` on every request

### Authorisation

```
Public endpoints:
  GET /api/polls, GET /api/polls/*, GET /api/polls/*/results
  GET /api/polls/*/stream, GET /api/polls/trending
  POST /api/auth/register, POST /api/auth/login
  GET /health, GET /actuator/health, GET /actuator/info
  WebSocket /ws/**, Swagger /swagger-ui/**, /v3/api-docs/**

Authenticated (any role):
  POST /api/polls           — create poll
  POST /api/polls/*/vote    — cast vote
  PATCH /api/polls/*/close  — close own poll (service validates ownership)

Admin only:
  GET /api/admin/**         — @PreAuthorize("hasRole('ADMIN')")
  GET /actuator/**          — hasRole('ADMIN') except health/info
```

### Password Security

- BCrypt with default cost factor (10 rounds)
- Passwords never returned in any API response

### CORS

All origins permitted (`allowedOriginPatterns: "*"`) with credentials enabled. For production hardening, restrict to the known frontend domain.

### Rate Limiting (Redis-backed, optional)

| Bucket | Limit | Window |
|--------|-------|--------|
| `/api/auth/**` | 10 req | 1 min per IP |
| `POST /api/polls/*/vote` | 20 req | 1 min per IP |
| All other paths | 200 req | 1 min per IP |

Fail-open: Redis outage passes all requests through with a warning log.

### Headers

Every response includes:
- `X-RateLimit-Limit` / `X-RateLimit-Remaining` (when Redis enabled)
- `X-Correlation-ID` (injected by `CorrelationIdFilter`, echoed from request or generated)

---

## 8. Resilience and Fault Tolerance

### Circuit Breakers (Resilience4j)

| Name | Protects | Config |
|------|----------|--------|
| `redis` | All Redis operations | 10-call window, 50% failure threshold, 30 s open state |
| `kafka-producer` | Kafka publishing | 10-call window, 60% failure threshold, 60 s open state, 3 retries |

Fallback strategies:
- **Redis down**: vote counts served from PostgreSQL; rate limiting disabled; trending returns empty list
- **Kafka down**: events silently dropped; vote result still returned to client

### Optional Infrastructure

Both Redis and Kafka are fully optional. Without them, the application provides:
- Full poll CRUD and voting against PostgreSQL only
- Real-time updates via STOMP in-memory broker (no Redis needed for pub/sub)
- No rate limiting, no trending, no event history

### Graceful Shutdown

`server.shutdown: graceful` with a 30-second drain window ensures in-flight requests complete before the process exits (critical for Railway's rolling deploys).

### JVM Memory Tuning (Railway free tier — 512 MB limit)

```
-Xms32m                    initial heap
-Xmx180m                   maximum heap
-XX:+UseSerialGC           single-threaded GC (lower overhead)
-XX:MaxMetaspaceSize=160m  class metadata cap
-XX:CompressedClassSpaceSize=64m
-XX:ReservedCodeCacheSize=32m
```

---

## 9. Scalability Considerations

### Current Architecture (Single-Instance)

The in-memory STOMP broker works only within a single JVM. This is sufficient for the current Railway free-tier deployment.

### Horizontal Scaling Path

To run multiple backend instances:

1. **Replace in-memory STOMP broker with RabbitMQ or Redis pub/sub relay**

   ```java
   // In WebSocketConfig.java
   registry.enableStompBrokerRelay("/topic")
       .setRelayHost("rabbitmq-host")
       .setRelayPort(61613);
   ```

2. **Redis already used for vote counts** — all instances share the same cache naturally.

3. **Kafka already decouples producers from consumers** — scale consumer group independently.

4. **Session affinity** not required (JWT is stateless; WebSocket connections are sticky per user, not per session).

### Database Scaling

- Read replicas for `GET /api/polls` feed queries (currently N+1 on `createdBy` — add `JOIN FETCH` to `findByStatus` for production)
- Connection pool (`HikariCP`): `max-pool-size=10`, tune per instance count
- Add index on `polls.created_at DESC` for feed pagination performance at scale

### Caching Strategy

| Data | Cache Key | TTL | Invalidation |
|------|-----------|-----|--------------|
| Vote counts (per option) | `poll:votes:{pollId}` | 7 days | Incremented on each vote |
| Trending scores | `trending:polls` (sorted set) | No TTL | Incremented on each vote |

For large deployments, add poll metadata caching (question, options, status) to reduce DB reads on the feed endpoint.

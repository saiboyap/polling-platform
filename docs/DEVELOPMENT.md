# Developer Guide

## Table of Contents

1. [Local Environment Setup](#1-local-environment-setup)
2. [Project Structure](#2-project-structure)
3. [Backend Architecture Deep-Dive](#3-backend-architecture-deep-dive)
4. [Frontend Architecture Deep-Dive](#4-frontend-architecture-deep-dive)
5. [How to Add a New Feature](#5-how-to-add-a-new-feature)
6. [Database Migrations](#6-database-migrations)
7. [Testing Guide](#7-testing-guide)
8. [Code Style and Conventions](#8-code-style-and-conventions)
9. [Git Workflow](#9-git-workflow)
10. [Debugging Tips](#10-debugging-tips)

---

## 1. Local Environment Setup

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 17 LTS | [Adoptium](https://adoptium.net/) or `sdk install java 17` (sdkman) |
| Maven | 3.9+ | Included in most Java IDEs; or `brew install maven` |
| Node.js | 20 LTS | [nodejs.org](https://nodejs.org/) or `nvm install 20` |
| Docker | 24+ | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| Git | 2.x | `brew install git` / system package manager |

Recommended IDE: **IntelliJ IDEA** (backend) + **VS Code** (frontend), or IntelliJ IDEA Ultimate for both.

### Quick Start

```bash
# 1. Clone
git clone https://github.com/saiboyap/polling-platform.git
cd polling-platform

# 2. Start databases
docker compose up postgres redis -d

# 3. Backend
cd backend
export PGHOST=localhost PGPORT=5432 PGDATABASE=polling_db PGUSER=polling_user PGPASSWORD=polling_pass
export REDIS_ENABLED=true SPRING_REDIS_HOST=localhost
mvn spring-boot:run

# 4. Frontend (new terminal)
cd ../frontend
npm install
echo "VITE_API_BASE_URL=http://localhost:8080/api" > .env.local
echo "VITE_WS_URL=http://localhost:8080/ws" >> .env.local
npm run dev
```

Backend: `http://localhost:8080`  
Frontend: `http://localhost:5173`  
Swagger: `http://localhost:8080/swagger-ui.html`

### Enable Kafka Locally

```bash
docker compose up kafka zookeeper -d

# In backend terminal, add:
export KAFKA_ENABLED=true
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### Enable Flyway Locally

When running locally with a fresh database, enable Flyway to apply migrations:

```bash
# application-local.yml (create in src/main/resources/)
spring:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
```

---

## 2. Project Structure

```
polling-platform/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/polling/platform/
│   │   │   │   ├── annotation/
│   │   │   │   │   └── AuditLogged.java          Custom annotation for audit trail
│   │   │   │   ├── aspect/
│   │   │   │   │   └── AuditLogAspect.java        @Around advice writes audit entries
│   │   │   │   ├── config/
│   │   │   │   │   ├── KafkaConfig.java           Topic definitions, factory config
│   │   │   │   │   ├── OpenApiConfig.java         Swagger security scheme, server URLs
│   │   │   │   │   ├── RedisConfig.java           RedisTemplate bean (conditional)
│   │   │   │   │   ├── SecurityConfig.java        Filter chain, CORS, endpoint rules
│   │   │   │   │   └── WebSocketConfig.java       STOMP endpoints and broker config
│   │   │   │   ├── controller/
│   │   │   │   │   ├── AdminController.java       GET /api/admin/stats
│   │   │   │   │   ├── AuthController.java        POST /api/auth/register|login
│   │   │   │   │   ├── HealthController.java      GET /health
│   │   │   │   │   ├── PollController.java        CRUD + vote + results
│   │   │   │   │   ├── SseController.java         GET /api/polls/{id}/stream
│   │   │   │   │   ├── TrendingController.java    GET /api/polls/trending
│   │   │   │   │   └── VoteController.java        Standalone /api/votes/{id} endpoints
│   │   │   │   ├── dto/
│   │   │   │   │   ├── event/                     Kafka event payloads
│   │   │   │   │   ├── request/                   Inbound DTOs (LoginRequest, etc.)
│   │   │   │   │   └── response/                  Outbound DTOs (PollResponse, etc.)
│   │   │   │   ├── entity/                        JPA entities (mapped to DB tables)
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GlobalExceptionHandler.java  @RestControllerAdvice
│   │   │   │   │   └── *.java                      Custom exception classes
│   │   │   │   ├── filter/
│   │   │   │   │   ├── CorrelationIdFilter.java   Injects X-Correlation-ID into MDC
│   │   │   │   │   └── RateLimitFilter.java       Redis sliding-window rate limiter
│   │   │   │   ├── kafka/
│   │   │   │   │   ├── config/KafkaConsumerConfig.java
│   │   │   │   │   ├── consumer/PollEventConsumer.java
│   │   │   │   │   └── producer/
│   │   │   │   │       ├── KafkaSendGateway.java  Low-level send wrapper
│   │   │   │   │       └── PollEventProducer.java  Domain-level publish methods
│   │   │   │   ├── repository/                    Spring Data JPA interfaces
│   │   │   │   ├── security/
│   │   │   │   │   ├── JwtAuthenticationFilter.java  Per-request token validation
│   │   │   │   │   └── JwtTokenProvider.java         Token generate/parse
│   │   │   │   ├── service/
│   │   │   │   │   ├── AuthService.java           register/login business logic
│   │   │   │   │   ├── AuditLogService.java       @Async DB write
│   │   │   │   │   ├── EventIdempotencyService.java  Kafka dedup
│   │   │   │   │   ├── PollService.java           Poll CRUD + vote counts
│   │   │   │   │   ├── RedisVoteCacheService.java  @ConditionalOnProperty(redis.enabled)
│   │   │   │   │   ├── TrendingPollService.java   @ConditionalOnProperty(redis.enabled)
│   │   │   │   │   ├── UserDetailsServiceImpl.java  Spring Security UserDetails
│   │   │   │   │   └── VoteService.java           Vote casting + result building
│   │   │   │   └── websocket/
│   │   │   │       ├── SseEmitterRegistry.java    Manages open SSE connections
│   │   │   │       └── WebSocketEventPublisher.java  Broadcast to STOMP + SSE
│   │   │   └── resources/
│   │   │       ├── application.yml                Master config
│   │   │       └── db/migration/
│   │   │           ├── V1__init_schema.sql
│   │   │           ├── V2__add_poll_types_and_free_text.sql
│   │   │           ├── V3__processed_events.sql
│   │   │           └── V4__audit_log.sql
│   │   └── test/                                  Test classes
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   ├── auth/
│   │   │   │   ├── LoginForm.tsx
│   │   │   │   └── RegisterForm.tsx
│   │   │   ├── common/
│   │   │   │   ├── Button.tsx                     Reusable button with variants
│   │   │   │   ├── Input.tsx                      Form input with error display
│   │   │   │   ├── LoadingSpinner.tsx
│   │   │   │   └── Navbar.tsx                     Top navigation bar
│   │   │   └── poll/
│   │   │       ├── CreatePollForm.tsx              Poll creation form fields
│   │   │       ├── CreatePollModal.tsx             Modal wrapper for creation form
│   │   │       ├── PollCard.tsx                    Feed list item
│   │   │       ├── PollList.tsx                    Paginated grid of PollCards
│   │   │       ├── ResultsBar.tsx                  Percentage bar for one option
│   │   │       ├── VoteOptions.tsx                 Choice buttons or textarea
│   │   │       └── VoteResults.tsx                 Results display (bars or text list)
│   │   ├── hooks/
│   │   │   ├── useAuth.ts                          Login/register mutations
│   │   │   ├── usePolls.ts                         Feed, single poll, and vote hooks
│   │   │   ├── useRealtimeVotes.ts                 STOMP+SSE connection manager
│   │   │   └── useWebSocket.ts                     Low-level WebSocket utilities
│   │   ├── pages/
│   │   │   ├── HomePage.tsx                        Poll feed with auto-refresh
│   │   │   ├── PollDetailPage.tsx                  Voting form + live results
│   │   │   ├── LoginPage.tsx
│   │   │   └── RegisterPage.tsx
│   │   ├── services/
│   │   │   ├── api.ts                             Axios instance + interceptors
│   │   │   └── pollService.ts                     Typed API wrapper functions
│   │   ├── store/
│   │   │   ├── authStore.ts                       Zustand: JWT, username, isAuthenticated
│   │   │   └── pollStore.ts                       Zustand: feed cache, live counts
│   │   ├── types/
│   │   │   ├── common.ts                          ApiResponse, pagination types
│   │   │   └── poll.ts                            Poll, PollResults, CastVoteRequest…
│   │   ├── utils/
│   │   │   └── formatUtils.ts                     Date formatting helpers
│   │   ├── App.tsx                                Router setup
│   │   └── main.tsx                               ReactDOM.createRoot entry point
│   ├── .env.local                                 Local env vars (git-ignored)
│   ├── nginx.conf                                 nginx template (${PORT} placeholder)
│   ├── package.json
│   └── Dockerfile
│
├── docker-compose.yml
└── docs/
```

---

## 3. Backend Architecture Deep-Dive

### Request Pipeline

Every HTTP request passes through this chain:

```
DispatcherServlet
  → CorrelationIdFilter   (adds X-Correlation-ID to MDC)
  → RateLimitFilter       (Redis INCR, optional)
  → JwtAuthenticationFilter (validates Bearer token)
  → SecurityFilterChain   (authorisation rules)
  → Controller Method     (validates, delegates to service)
  → Service Layer         (AOP intercepts @AuditLogged, @CircuitBreaker)
  → Repository / Redis / Kafka
```

### Optional Infrastructure Pattern

Redis and Kafka are made optional using the same pattern throughout:

```java
// Bean: only registered when redis.enabled=true
@Service
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
public class RedisVoteCacheService { ... }

// Consumer: @Autowired(required = false) + null guard
@Autowired(required = false)
@Nullable
private RedisVoteCacheService cacheService;

// Usage:
if (cacheService != null) {
    cacheService.incrementVoteCount(pollId, optionId);
}
```

This allows the application to start and operate correctly without any Redis or Kafka infrastructure.

### Transaction Boundaries

- `VoteService.castVote` — `@Transactional`: save vote, increment cache, publish WebSocket event, return results — all in one transaction. If any step fails the DB write rolls back.
- `AuditLogService.record` — `@Async` + its own try/catch. Runs on the task executor thread pool; never affects the main request transaction.
- `PollEventConsumer` — each Kafka message processed in its own `@Transactional`: check processed_events, process, save processed_event — atomic idempotency.

### Adding a New Service

1. Create the service class in `service/`:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MyNewService {

    private final SomeRepository someRepository;

    @Transactional
    public ResultDto doSomething(UUID id) {
        // business logic
    }
}
```

2. Add repository interface in `repository/` if needed (extends `JpaRepository<Entity, UUID>`).

3. Create the controller in `controller/`:

```java
@RestController
@RequestMapping("/api/my-resource")
@RequiredArgsConstructor
public class MyController {

    private final MyNewService myNewService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResultDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(myNewService.doSomething(id), "Done"));
    }
}
```

4. Update `SecurityConfig` if the endpoint has different auth requirements.

---

## 4. Frontend Architecture Deep-Dive

### State Management

Two Zustand stores handle all client state:

**`authStore`**
```typescript
interface AuthState {
  token: string | null;
  user: { username: string; role: string } | null;
  isAuthenticated: boolean;
  login: (token: string, username: string, role: string) => void;
  logout: () => void;
}
```

**`pollStore`**
```typescript
interface PollStoreState {
  polls: Poll[];
  selectedPoll: Poll | null;
  voteCounts: Record<string, Record<string, number>>; // pollId → optionId → count
  isLoading: boolean;
  error: string | null;
  // setters...
  updateVoteCounts: (pollId: string, counts: Record<string, number>) => void;
}
```

### Axios Interceptors (`api.ts`)

The Axios instance has two interceptors:

1. **Request interceptor**: injects `Authorization: Bearer <token>` from `authStore`
2. **Response error interceptor**: retries up to 3 times with 2-second delays, skipping `400`, `401`, `403`, `404`, `409`, `422` (non-retriable errors)

### Real-Time Hook (`useRealtimeVotes`)

```typescript
// Phase 1: Try STOMP over SockJS
const client = new Client({
  webSocketFactory: () => new SockJS(WS_URL),
  reconnectDelay: 5_000,
  onConnect: () => {
    client.subscribe(`/topic/polls/${pollId}/votes`, handleMessage);
  },
});

// Phase 2: After 8 seconds without a connection, fall back to SSE
setTimeout(() => {
  if (!wsConnected.current) {
    client.deactivate();
    const sse = new EventSource(`/api/polls/${pollId}/stream`);
    sse.onmessage = (e) => handleMessage(e.data);
  }
}, 8_000);
```

### Adding a New Page

1. Create the page component in `pages/`:

```typescript
// pages/MyPage.tsx
const MyPage: React.FC = () => {
  return <div>My page content</div>;
};

export default MyPage;
```

2. Register the route in `App.tsx`:

```typescript
<Route path="/my-path" element={<MyPage />} />
```

3. Add navigation link in `components/common/Navbar.tsx` if needed.

---

## 5. How to Add a New Feature

### Example: Add a "Scheduled Polls" feature

**Backend steps:**

1. **Migration**: add `scheduled_at TIMESTAMP` column to `polls` in a new `V5__scheduled_polls.sql`
2. **Entity**: add `scheduledAt` field to `Poll.java`
3. **DTO**: add `scheduledAt` to `CreatePollRequest`, `PollResponse`
4. **Service**: update `PollService.createPoll` and `getActivePolls` filter logic
5. **Scheduler**: add a `@Scheduled` job or Kafka-delayed event to activate scheduled polls
6. **Tests**: unit tests for scheduling logic; integration test for the `/api/polls` filter

**Frontend steps:**

1. **Type**: add `scheduledAt?: string` to `Poll` interface in `types/poll.ts`
2. **Form**: add a datetime picker to `CreatePollForm.tsx`
3. **Display**: show scheduled badge in `PollCard.tsx` and `PollDetailPage.tsx`
4. **Validation**: disable vote button for not-yet-active polls

---

## 6. Database Migrations

All schema changes use Flyway. Never modify existing migration files after they have been applied to any environment.

### Creating a Migration

1. Create `backend/src/main/resources/db/migration/V{N}__{description}.sql`
2. Use lowercase, underscored description: `V5__add_scheduled_at_to_polls.sql`
3. Write reversible SQL where possible (but Flyway doesn't auto-rollback — plan carefully)

### Re-enabling Flyway for Production

Currently `spring.flyway.enabled: false` and `ddl-auto: update` (Railway-deployed). To switch back:

1. Verify Railway DB schema matches Flyway V1–V4 expectations
2. If the `flyway_schema_history` table is missing, set `spring.flyway.baseline-on-migrate: true` and `spring.flyway.baseline-version: 0`
3. Change `spring.flyway.enabled: true` and `ddl-auto: validate`
4. Deploy and verify the migration log

### Migration Best Practices

- Keep each migration small and focused
- Always test migrations on a copy of production data before deploying
- Do NOT use `CREATE EXTENSION` — Railway's managed PostgreSQL restricts superuser commands
- Use `IF NOT EXISTS` / `IF EXISTS` for safety in `CREATE`/`DROP` statements

---

## 7. Testing Guide

See [`docs/TESTING.md`](TESTING.md) for the full strategy. Quick reference:

```bash
# Run all backend tests
cd backend && mvn test

# Run with coverage report
mvn test jacoco:report
# Report: target/site/jacoco/index.html

# Run a specific test class
mvn test -Dtest=VoteServiceTest

# Lint frontend
cd frontend && npm run lint

# Frontend type check
npm run build   # tsc is run as part of the build
```

---

## 8. Code Style and Conventions

### Backend (Java)

- **Lombok**: use `@RequiredArgsConstructor` + `final` fields for constructor injection. Non-final optional beans use `@Autowired(required = false) @Nullable`.
- **No comments on obvious code**: only add a comment when the WHY is non-obvious (workaround, invariant, constraint).
- **DTOs vs Entities**: never expose entities directly from controllers. Always map to DTOs.
- **Exceptions**: throw domain exceptions (`PollClosedException`, `DuplicateVoteException`) from services. The `GlobalExceptionHandler` maps them to HTTP status codes.
- **Transaction scope**: annotate service methods, not controllers. Keep transactions as short as possible.
- **Logging**: use `log.info` for business events, `log.debug` for diagnostic detail, `log.warn` for recoverable issues (Redis fallback, audit failure), `log.error` only in `@ExceptionHandler(Exception.class)`.

### Frontend (TypeScript/React)

- **Hooks over classes**: all components are functional with hooks.
- **Zustand over Context**: prefer Zustand stores for any state shared between components or pages.
- **Typed API responses**: always use the types in `types/` — never use `any`.
- **Error boundaries**: each page handles its own error state (check `error` from hooks).
- **Tailwind**: utility classes only — no custom CSS files. Complex recurring patterns extracted into components.
- **No inline arrow functions** in JSX event handlers for performance (define handlers at component level).

### Git Commit Convention

```
<type>(<scope>): <short description>

<optional body>
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`

Examples:
```
feat(vote): add multi-choice vote validation
fix(feed): use freeTextVoteRepository for FREE_TEXT counts
docs: add architecture and API reference
```

---

## 9. Git Workflow

### Branch Strategy

```
main          ← production-ready, auto-deployed to Railway
  └── feature/add-scheduled-polls
  └── fix/free-text-vote-count
  └── docs/update-api-reference
```

### Workflow

```bash
# 1. Start from latest main
git checkout main && git pull

# 2. Create feature branch
git checkout -b feature/my-feature

# 3. Develop with small, focused commits
git add <specific-files>
git commit -m "feat(poll): add expiry validation"

# 4. Push and open pull request
git push -u origin feature/my-feature
# Open PR on GitHub against main

# 5. After review and approval, squash-merge to main
```

### Rules

- Never commit directly to `main` for production features
- Never use `git push --force` on `main`
- Include failing tests in commits only if the PR description explains why
- Do not commit `.env` files or credentials

---

## 10. Debugging Tips

### Backend

**Application won't start:**
- Check for duplicate top-level YAML keys in `application.yml` (SnakeYAML 2.x throws `DuplicateKeyException`)
- Confirm all required env vars are set: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`
- Run with `--debug` for full auto-configuration report: `mvn spring-boot:run -Dspring-boot.run.arguments=--debug`

**Database connection failure:**
- Verify PostgreSQL is running: `docker compose ps postgres`
- Test connection: `psql -h localhost -U polling_user -d polling_db`
- Check `sslmode` — Railway requires `?sslmode=require`; local Docker does not

**Redis connection noise:**
- Set `REDIS_ENABLED=false` to silence Redis errors during local development without Redis
- The circuit breaker opens after 5 failures — check `/actuator/health` for circuit state

**Real-time votes not updating:**
- Check browser console for WebSocket errors
- Confirm `/ws` is reachable (nginx proxies it separately from `/api/`)
- If WebSocket fails after 8 s, SSE fallback activates — check `GET /api/polls/{id}/stream` in Network tab

### Frontend

**API calls return CORS errors:**
- Confirm `VITE_API_BASE_URL` is set in `.env.local`
- For production, nginx proxies `/api/` so CORS is not an issue

**JWT token not sent:**
- Check `authStore.token` in Redux DevTools or log it; the request interceptor in `api.ts` reads from there

**Stale data after creating a poll:**
- `handlePollCreated` calls `setPage(0)` then `refetch()` — confirm the handler is wired to `CreatePollModal.onCreated`

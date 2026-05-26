# Agile Documentation

## Table of Contents

1. [Product Vision](#1-product-vision)
2. [Personas and Goals](#2-personas-and-goals)
3. [User Stories](#3-user-stories)
4. [Sprint Breakdown](#4-sprint-breakdown)
5. [Definition of Done](#5-definition-of-done)
6. [Acceptance Criteria](#6-acceptance-criteria)
7. [Backlog (Future Enhancements)](#7-backlog-future-enhancements)

---

## 1. Product Vision

**For** teams, communities and individuals **who** need to gather quick, structured opinions at scale, **the Polling Platform is** a real-time web application **that** lets anyone create polls, vote, and watch live results update instantly — without page refreshes or delays. **Unlike** static form-based tools, **our platform** broadcasts every vote to all connected viewers in real time using WebSocket technology backed by an event-driven, highly-resilient architecture.

### Strategic Goals

| Goal | Metric |
|------|--------|
| Instant feedback | Results visible to all viewers < 500 ms after vote submission |
| High availability | Degrade gracefully when Redis/Kafka are unavailable; DB-only mode works |
| Extensibility | New poll types or integrations add with < 1 day of backend work |
| Production-grade | JWT auth, rate limiting, audit trail, structured logging, circuit breakers |

---

## 2. Personas and Goals

### Alice — Poll Creator
- Creates polls for team decisions and community feedback
- Wants to choose poll type (choice vs open text) and set optional expiry
- Needs to see results in real time and close a poll when done
- Manages polls she owns; cannot modify others' polls

### Bob — Voter
- Discovers polls on the feed; wants to vote quickly without friction
- Expects to vote once per poll (enforced)
- Wants to see live results immediately after voting
- Can browse results for polls he hasn't voted on yet

### Carol — Read-Only Viewer
- Views results without voting (not logged in)
- Expects results to update in real time while watching

### Dave — Admin
- Monitors platform health and aggregate statistics
- Has access to `/api/admin/stats`
- Can close any poll (future scope)

---

## 3. User Stories

### Authentication (Epic: Auth)

| ID | Story | Priority |
|----|-------|----------|
| AUTH-1 | As a new user, I want to register with a username, email and password so I can create and vote on polls | Must Have |
| AUTH-2 | As a returning user, I want to log in and receive a token that keeps me authenticated for 24 hours | Must Have |
| AUTH-3 | As a user, I want the app to remember my session so I don't have to log in on every page load | Should Have |
| AUTH-4 | As a user, I want to log out and have my session immediately invalidated on the client | Should Have |

### Poll Management (Epic: Polls)

| ID | Story | Priority |
|----|-------|----------|
| POLL-1 | As Alice, I want to create a single-choice poll with at least 2 options so my team can pick one answer | Must Have |
| POLL-2 | As Alice, I want to create a multi-choice poll and set a maximum number of selections | Should Have |
| POLL-3 | As Alice, I want to create a free-text poll to gather open-ended written responses | Should Have |
| POLL-4 | As Alice, I want to set an expiry time on my poll so it closes automatically | Could Have |
| POLL-5 | As Alice, I want to close my poll manually when I have enough responses | Must Have |
| POLL-6 | As Bob, I want to browse a paginated feed of active polls so I can find ones to vote on | Must Have |
| POLL-7 | As Bob, I want to click a poll to see its full details and options | Must Have |

### Voting (Epic: Voting)

| ID | Story | Priority |
|----|-------|----------|
| VOTE-1 | As Bob, I want to cast a vote on a single-choice poll by selecting one option | Must Have |
| VOTE-2 | As Bob, I want to select multiple options on a multi-choice poll up to the maximum | Should Have |
| VOTE-3 | As Bob, I want to submit a text response to a free-text poll | Should Have |
| VOTE-4 | As Bob, I want to see an error message if I try to vote a second time on the same poll (not a silent fail) | Must Have |
| VOTE-5 | As Bob, I want to see the updated results immediately after submitting my vote | Must Have |
| VOTE-6 | As Carol (unauthenticated), I want to see poll results and a prompt to log in to vote | Should Have |

### Real-Time Experience (Epic: Real-Time)

| ID | Story | Priority |
|----|-------|----------|
| RT-1 | As Carol, I want the results on an open poll detail page to update automatically as others vote | Must Have |
| RT-2 | As Carol, I want to see a live indicator showing whether my connection is receiving real-time updates | Should Have |
| RT-3 | As Bob, I want the poll feed to refresh periodically so new polls and recent vote counts are visible | Should Have |
| RT-4 | As any user, I want real-time updates to work even in environments that block WebSockets (SSE fallback) | Should Have |

### Administration and Operations (Epic: Ops)

| ID | Story | Priority |
|----|-------|----------|
| OPS-1 | As Dave, I want a health endpoint I can monitor to know if the API is up | Must Have |
| OPS-2 | As Dave, I want platform statistics (total polls, users, votes) via a protected admin endpoint | Should Have |
| OPS-3 | As Dave, I want every API request tagged with a correlation ID for log tracing | Should Have |
| OPS-4 | As Dave, I want all write operations recorded in an audit log | Should Have |
| OPS-5 | As Dave, I want rate limiting on auth and vote endpoints to prevent abuse | Could Have |
| OPS-6 | As Dave, I want trending polls visible (top polls by votes) | Could Have |

---

## 4. Sprint Breakdown

### Sprint 1 — Foundation (Phase 1)

**Goal**: A working full-stack scaffold with authentication and basic poll CRUD. The team can register, log in, and view polls.

**Deliverables:**

| Story | Tasks |
|-------|-------|
| AUTH-1 | Create User entity, UserRepository, AuthService.register, POST /api/auth/register |
| AUTH-2 | JwtTokenProvider, JwtAuthenticationFilter, POST /api/auth/login |
| AUTH-3 | Store JWT in Zustand authStore; inject header in Axios |
| AUTH-4 | Logout clears authStore |
| POLL-1 | Poll + PollOption entities; PollService.createPoll; POST /api/polls |
| POLL-6 | PollService.getActivePolls with pagination; GET /api/polls |
| POLL-7 | PollService.getPollById; GET /api/polls/{id} |

**Frontend work:**
- React + Vite scaffold, Tailwind CSS setup
- `LoginPage`, `RegisterPage`, `HomePage` with `PollCard` list
- `PollDetailPage` with poll metadata display

**Definition of Done**: User can register, log in, create a SINGLE_CHOICE poll, and see it appear on the feed.

---

### Sprint 2 — Voting and Results (Phase 2)

**Goal**: Users can vote and see results. The 409 duplicate-vote case is handled gracefully.

**Deliverables:**

| Story | Tasks |
|-------|-------|
| VOTE-1 | VoteService.castSingleChoice; POST /api/polls/{id}/vote |
| VOTE-2 | MULTI_CHOICE poll creation and voting; maxChoices validation |
| VOTE-3 | FREE_TEXT poll creation; VoteService.castFreeText; FreeTextVote entity |
| VOTE-4 | DuplicateVoteException → 409; frontend shows amber "already voted" banner |
| VOTE-5 | VoteService.getResults; GET /api/polls/{id}/results |
| VOTE-6 | Login prompt for unauthenticated users on detail page |
| POLL-5 | PollService.closePoll; PATCH /api/polls/{id}/close |
| OPS-1 | HealthController; GET /health |

**Frontend work:**
- `VoteOptions` component (radio buttons, checkboxes, textarea)
- `VoteResults` component with percentage bars
- `ResultsBar` for individual option display
- `hasVoted` / `alreadyVoted` state management in `PollDetailPage`
- `CreatePollModal` and `CreatePollForm`

**Definition of Done**: All three poll types can be created and voted on. Results display correctly. Duplicate vote shows a friendly amber message.

---

### Sprint 3 — Real-Time Features (Phase 3)

**Goal**: Live vote updates reach all connected viewers without polling. WebSocket with SSE fallback.

**Deliverables:**

| Story | Tasks |
|-------|-------|
| RT-1 | WebSocketConfig (STOMP); WebSocketEventPublisher; broadcast on castVote |
| RT-2 | Connection indicator in PollDetailPage header (Live / Live (SSE) / Connecting…) |
| RT-3 | setInterval(refetch, 60000) in HomePage |
| RT-4 | SseController + SseEmitterRegistry; 8 s WebSocket timeout triggers SSE fallback |

**Frontend work:**
- `useRealtimeVotes` hook (STOMP client + SSE fallback with 8 s timer)
- Update `pollStore.updateVoteCounts` on every received STOMP/SSE message
- `VoteResults` reads live counts from `pollStore.voteCounts[pollId]` for instant updates

**Definition of Done**: Two browser windows open on the same poll detail page; voting in one updates results in the other < 1 second without a manual refresh. SSE fallback confirmed working in a WebSocket-blocking environment.

---

### Sprint 4 — Event-Driven Infrastructure (Phase 4)

**Goal**: Optional Kafka event streaming and Redis vote caching with circuit breaker protection.

**Deliverables:**

| Story | Tasks |
|-------|-------|
| (Infra) | Kafka topic config, PollEventProducer, KafkaSendGateway |
| (Infra) | PollEventConsumer with idempotency (ProcessedEvent table) |
| (Infra) | RedisVoteCacheService (getAllVoteCounts, incrementVoteCount, seedFromDatabase) |
| (Infra) | Resilience4j circuit breakers on redis and kafka-producer |
| OPS-5 | RateLimitFilter (Redis sliding window) |
| OPS-6 | TrendingPollService (sorted set) + TrendingController |
| (Ops) | CorrelationIdFilter |
| OPS-3 | MDC correlation ID in all log lines |
| OPS-4 | AuditLogAspect + AuditLogService (@Async) |

**Configuration:**
- `@ConditionalOnProperty` on all Redis and Kafka beans
- `@Autowired(required = false) @Nullable` at all consumer sites
- Null guards at every call site

**Definition of Done**: `REDIS_ENABLED=false KAFKA_ENABLED=false` starts cleanly without any errors. With both enabled, vote counts are served from Redis cache and events appear in Kafka topics.

---

### Sprint 5 — Production Hardening and Deployment (Phase 5)

**Goal**: The application deploys reliably on Railway's free tier and handles edge cases gracefully.

**Deliverables:**

| Task | Description |
|------|-------------|
| Railway deployment | Backend + Frontend services deployed; PostgreSQL plugin linked |
| JVM memory tuning | `-Xmx180m -XX:MaxMetaspaceSize=160m` etc. to stay within 512 MB |
| nginx PORT substitution | `envsubst '${PORT}'` in Dockerfile; `ENTRYPOINT []` prevents double-processing |
| `server.port: ${PORT:8080}` | Backend listens on Railway-assigned dynamic port |
| Graceful shutdown | `server.shutdown: graceful`, 30 s drain window |
| Flyway pgcrypto fix | Removed `CREATE EXTENSION pgcrypto`; PostgreSQL 13+ has `gen_random_uuid()` built-in |
| LEFT JOIN FETCH fix | PollRepository uses `LEFT JOIN FETCH p.options` to handle FREE_TEXT polls with no options |
| Axios retry interceptor | 3 retries, 2 s delay, skip non-retriable 4xx codes |
| 30 s Axios timeout | Accommodates Railway cold-start latency |
| OPS-2 | AdminController with aggregate statistics |
| Swagger UI | SpringDoc OpenAPI setup; publicly accessible |
| Documentation | README, ARCHITECTURE, API, DEVELOPMENT, AGILE, DEPLOYMENT, TESTING |

**Definition of Done**: Both Railway services deploy without errors; health check returns 200; all three poll types can be created and voted on from the live frontend URL.

---

## 5. Definition of Done

A user story is considered **Done** when all of the following are true:

### Code Quality
- [ ] All acceptance criteria are met (see section 6)
- [ ] No compiler warnings or TypeScript errors
- [ ] ESLint passes for frontend code (`npm run lint`)
- [ ] Code reviewed and approved by at least one other developer (for team projects)

### Testing
- [ ] Unit tests written for new business logic
- [ ] Integration test covers the happy path
- [ ] Edge cases tested (empty lists, expired polls, duplicate votes)
- [ ] No previously passing tests are now failing

### Documentation
- [ ] New/changed endpoints documented in `docs/API.md`
- [ ] New env variables added to the environment variables table in `README.md`
- [ ] New Flyway migration includes a comment describing the change

### Deployment
- [ ] Feature works correctly in local Docker Compose
- [ ] Feature works correctly in Railway staging/production
- [ ] No new `ERROR` log entries appear during normal operation
- [ ] `GET /health` still returns 200 after deployment

---

## 6. Acceptance Criteria

### AUTH-1 Registration
- **Given** a valid username, email and password  
  **When** POST /api/auth/register is called  
  **Then** a 201 response is returned with a valid JWT token
- **Given** a username that already exists  
  **When** registration is attempted  
  **Then** a 400 error is returned with a descriptive message
- **Given** a password shorter than 8 characters  
  **When** registration is attempted  
  **Then** a 400 error is returned

### VOTE-4 Duplicate Vote Handling
- **Given** a user who has already voted on a poll  
  **When** they attempt to vote again  
  **Then** the API returns 409
- **Given** a 409 response in the frontend  
  **When** the user is on the poll detail page  
  **Then** an amber "You have already voted on this poll." banner is shown
- **Given** a 409 response  
  **Then** the green "Vote recorded!" success banner does NOT appear
- **Given** a 409 response  
  **Then** the voting form is hidden (user cannot re-submit)
- **Given** a 409 response  
  **Then** the current results ARE still displayed

### RT-1 Real-Time Updates
- **Given** two browser tabs open on the same poll  
  **When** a vote is cast in Tab A  
  **Then** Tab B's results update within 1 second without a manual refresh
- **Given** a STOMP WebSocket connection fails to establish within 8 seconds  
  **When** the fallback triggers  
  **Then** the connection type indicator changes to "Live (SSE)"
- **Given** the SSE fallback is active  
  **When** a vote is cast  
  **Then** the SSE client receives an event with updated counts

### POLL-3 Free-Text Poll
- **Given** a free-text poll  
  **When** a user submits a text response  
  **Then** the response is saved and the total response count increments
- **Given** a free-text poll  
  **When** the results are fetched  
  **Then** `freeTextResponses` contains all submitted texts (newest first)
- **Given** a free-text poll on the feed  
  **Then** `totalVotes` correctly reflects the number of text responses (not 0)
- **Given** an empty text response  
  **Then** submission is rejected with a 400 error

### OPS-1 Health Check
- **Given** the backend is running  
  **When** GET /health is called  
  **Then** `{"status":"UP"}` is returned with HTTP 200
- **Given** the database is unreachable  
  **When** GET /actuator/health is called  
  **Then** the response reflects the DB component as DOWN

---

## 7. Backlog (Future Enhancements)

These items have been identified but are not yet scheduled:

| ID | Story | Notes |
|----|-------|-------|
| BACK-1 | Poll expiry — auto-close via `@Scheduled` job | V5 migration: add `scheduled_at`; Spring `@Scheduled` every minute |
| BACK-2 | Poll categories / tags | Many-to-many: polls ↔ tags |
| BACK-3 | Anonymous voting option | Allow votes without login (IP-based dedup) |
| BACK-4 | Results export (CSV / JSON) | New endpoint `GET /api/polls/{id}/export` |
| BACK-5 | Poll embed widget | Iframe-compatible endpoint |
| BACK-6 | Email notifications (new votes on owned polls) | Spring Mail integration |
| BACK-7 | Admin: close any poll | Add `@PreAuthorize("hasRole('ADMIN')")` to closePoll |
| BACK-8 | Horizontal scaling | Replace in-memory STOMP broker with RabbitMQ relay |
| BACK-9 | Dark mode | Tailwind `dark:` variants |
| BACK-10 | Re-enable Flyway | Coordinate with Railway DB state; `baseline-on-migrate` |

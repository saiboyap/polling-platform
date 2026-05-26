# API Reference

Base URL (production): `https://polling-platform-production-180d.up.railway.app`  
Base URL (local): `http://localhost:8080`  
Interactive docs: `/swagger-ui.html`

All endpoints under `/api/` return JSON. Successful responses are wrapped in:

```json
{
  "success": true,
  "message": "Human-readable status",
  "data": { ... }
}
```

Error responses follow:

```json
{
  "status": 404,
  "message": "Poll not found with id: <uuid>",
  "timestamp": "2026-05-26T10:30:00",
  "correlationId": "3e8f1c2a-..."
}
```

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Polls](#2-polls)
3. [Voting and Results](#3-voting-and-results)
4. [Real-Time Streaming](#4-real-time-streaming)
5. [Trending](#5-trending)
6. [Admin](#6-admin)
7. [Health and Ops](#7-health-and-ops)
8. [Error Reference](#8-error-reference)
9. [WebSocket Events](#9-websocket-events)
10. [Rate Limiting](#10-rate-limiting)

---

## 1. Authentication

### POST /api/auth/register

Create a new user account.

**Request body:**
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "Secret123!"
}
```

| Field | Type | Constraints |
|-------|------|-------------|
| `username` | string | 3–50 characters, unique |
| `email` | string | Valid email format, unique |
| `password` | string | Minimum 8 characters |

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "alice",
    "role": "USER",
    "expiresIn": 86400000
  }
}
```

**Error responses:**

| Status | Condition |
|--------|-----------|
| `400` | Validation failure (missing field, invalid email, password too short) |
| `400` | Username or email already taken |
| `429` | Rate limit exceeded (10 requests/minute per IP) |

---

### POST /api/auth/login

Authenticate and receive a JWT token.

**Request body:**
```json
{
  "username": "alice",
  "password": "Secret123!"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "alice",
    "role": "USER",
    "expiresIn": 86400000
  }
}
```

**Error responses:**

| Status | Condition |
|--------|-----------|
| `401` | Invalid username or password |
| `429` | Rate limit exceeded |

---

## 2. Polls

### GET /api/polls

List active polls, newest first. Public endpoint.

**Query parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | `0` | Zero-based page number |
| `size` | integer | `10` | Page size (max 50) |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Polls retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "question": "Which backend framework do you prefer?",
        "createdBy": "alice",
        "status": "ACTIVE",
        "pollType": "SINGLE_CHOICE",
        "maxChoices": 1,
        "options": [
          { "id": "...", "optionText": "Spring Boot", "voteCount": 42 },
          { "id": "...", "optionText": "Django",      "voteCount": 17 },
          { "id": "...", "optionText": "Express.js",  "voteCount": 11 }
        ],
        "expiresAt": null,
        "createdAt": "2026-05-26T09:00:00",
        "totalVotes": 70
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 10,
    "first": true,
    "last": true
  }
}
```

For `FREE_TEXT` polls, `options` is an empty array and `totalVotes` reflects the count of text responses.

---

### POST /api/polls

Create a new poll. **Requires authentication.**

**Headers:** `Authorization: Bearer <token>`

**Request body — Single choice:**
```json
{
  "question": "Which backend framework do you prefer?",
  "pollType": "SINGLE_CHOICE",
  "options": ["Spring Boot", "Django", "Express.js"],
  "expiresAt": "2026-06-01T00:00:00"
}
```

**Request body — Multi choice:**
```json
{
  "question": "Which languages do you use? (pick up to 3)",
  "pollType": "MULTI_CHOICE",
  "maxChoices": 3,
  "options": ["Java", "Python", "TypeScript", "Go", "Rust"]
}
```

**Request body — Free text:**
```json
{
  "question": "What feature should we build next?",
  "pollType": "FREE_TEXT"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `question` | string | Yes | Poll question text |
| `pollType` | enum | Yes | `SINGLE_CHOICE`, `MULTI_CHOICE`, or `FREE_TEXT` |
| `options` | string[] | For choice polls | Minimum 2 options |
| `maxChoices` | integer | For `MULTI_CHOICE` | Max selectable options (≥ 1) |
| `expiresAt` | ISO datetime | No | Optional expiry time |

**Response `201 Created`:** Same shape as a single poll in the list response.

**Error responses:**

| Status | Condition |
|--------|-----------|
| `400` | Less than 2 options for choice poll; `maxChoices` < 1 |
| `401` | Missing or invalid token |

---

### GET /api/polls/{id}

Get a single poll by UUID. Public endpoint.

**Path parameter:** `id` — poll UUID

**Response `200 OK`:** Same shape as poll object in list response.

**Error responses:**

| Status | Condition |
|--------|-----------|
| `404` | Poll not found |

---

### PATCH /api/polls/{id}/close

Close a poll (stops accepting votes). **Owner only.**

**Headers:** `Authorization: Bearer <token>`

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Poll closed successfully",
  "data": null
}
```

**Error responses:**

| Status | Condition |
|--------|-----------|
| `401` | Not authenticated |
| `403` | Authenticated but not the poll owner |
| `404` | Poll not found |

---

## 3. Voting and Results

### POST /api/polls/{pollId}/vote

Submit a vote. **Requires authentication.**

**Headers:** `Authorization: Bearer <token>`

**Request body — choice polls:**
```json
{
  "optionIds": ["<option-uuid-1>"]
}
```

For `MULTI_CHOICE`, provide up to `maxChoices` option IDs:
```json
{
  "optionIds": ["<option-uuid-1>", "<option-uuid-2>"]
}
```

**Request body — free text:**
```json
{
  "freeText": "I would love to see dark mode support!"
}
```

**Response `201 Created` — choice poll:**
```json
{
  "success": true,
  "message": "Vote submitted successfully",
  "data": {
    "pollId": "550e8400-...",
    "question": "Which backend framework do you prefer?",
    "pollType": "SINGLE_CHOICE",
    "status": "ACTIVE",
    "maxChoices": 1,
    "totalResponses": 71,
    "optionResults": [
      {
        "optionId": "...",
        "optionText": "Spring Boot",
        "voteCount": 43,
        "percentage": 60.6
      },
      {
        "optionId": "...",
        "optionText": "Django",
        "voteCount": 17,
        "percentage": 23.9
      },
      {
        "optionId": "...",
        "optionText": "Express.js",
        "voteCount": 11,
        "percentage": 15.5
      }
    ]
  }
}
```

**Response `201 Created` — free text poll:**
```json
{
  "success": true,
  "message": "Vote submitted successfully",
  "data": {
    "pollId": "...",
    "question": "What feature should we build next?",
    "pollType": "FREE_TEXT",
    "status": "ACTIVE",
    "maxChoices": 0,
    "totalResponses": 5,
    "freeTextResponses": [
      "Dark mode support",
      "Mobile app",
      "Export results to CSV",
      "Poll templates",
      "Embed widget"
    ]
  }
}
```

**Error responses:**

| Status | Condition |
|--------|-----------|
| `400` | Poll is closed or expired |
| `400` | Wrong number of option IDs selected |
| `400` | `freeText` is null or blank for free-text poll |
| `401` | Not authenticated |
| `404` | Poll or option not found |
| `409` | User has already voted on this poll |
| `429` | Vote rate limit exceeded (20 req/min per IP, Redis required) |

---

### GET /api/polls/{pollId}/results

Get current results without voting. Public endpoint. Sourced from Redis cache with DB fallback.

**Response `200 OK`:** Same shape as the `data` object in the vote response above.

---

## 4. Real-Time Streaming

### GET /api/polls/{pollId}/stream

Open a Server-Sent Events stream to receive live vote-count updates.

**Response:** `Content-Type: text/event-stream`

The connection stays open. Each event delivered looks like:

```
data: {"<option-uuid-1>":43,"<option-uuid-2>":17,"<option-uuid-3>":11}
```

For free-text polls:
```
data: {"_freeTextTotal":6}
```

The client should update the displayed results on each event. The stream closes when the poll ends or the client disconnects.

**Notes:**
- Used automatically by the frontend as a fallback when STOMP/WebSocket fails to connect within 8 seconds.
- The `SseEmitter` has no expiry (`timeout = 0`); the connection lives until network disconnect or server shutdown.
- Graceful server shutdown flushes open emitters.

---

## 5. Trending

### GET /api/polls/trending

Return the top polls ranked by total votes. Backed by a Redis sorted set. Returns an empty list when Redis is unavailable.

**Query parameters:**

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `limit` | integer | `10` | 1–50 | Number of polls to return |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Trending polls retrieved",
  "data": [
    {
      "pollId": "550e8400-...",
      "question": "Which backend framework do you prefer?",
      "createdBy": "alice",
      "status": "ACTIVE",
      "pollType": "SINGLE_CHOICE",
      "totalVotes": 71,
      "rank": 1
    }
  ]
}
```

---

## 6. Admin

All `/api/admin/**` endpoints require `ADMIN` role.

**Headers:** `Authorization: Bearer <admin-token>`

### GET /api/admin/stats

Platform-wide aggregate statistics.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Stats retrieved",
  "data": {
    "totalPolls": 42,
    "activePolls": 15,
    "closedPolls": 24,
    "expiredPolls": 3,
    "totalUsers": 312,
    "totalVotes": 4891,
    "totalFreeTextResponses": 208
  }
}
```

**Error responses:**

| Status | Condition |
|--------|-----------|
| `401` | Not authenticated |
| `403` | Authenticated but not ADMIN |

---

## 7. Health and Ops

### GET /health

Simple health check — always returns 200 if the server is running.

```json
{ "status": "UP" }
```

### GET /actuator/health

Spring Boot Actuator health with component details. Public for liveness/readiness; component details require ADMIN.

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL", "validationQuery": "isValid()" } },
    "redis": { "status": "UP" },
    "circuitBreakers": { "status": "UP" }
  }
}
```

When Redis or Kafka is disabled, those components are excluded from the health response (`management.health.redis.enabled: false`).

### GET /actuator/health/liveness

Returns `200 UP` if the application is alive (Kubernetes liveness probe).

### GET /actuator/health/readiness

Returns `200 UP` if the application is ready to accept traffic (Kubernetes readiness probe).

---

## 8. Error Reference

| HTTP Status | When Used |
|-------------|-----------|
| `200 OK` | Successful GET, PATCH |
| `201 Created` | Successful POST (poll created, vote submitted, user registered) |
| `400 Bad Request` | Validation error, business rule violation (poll closed, invalid option count) |
| `401 Unauthorized` | Missing token, expired token, invalid credentials |
| `403 Forbidden` | Authenticated but insufficient permissions (not owner, not ADMIN) |
| `404 Not Found` | Resource does not exist (poll, user, option) |
| `409 Conflict` | Duplicate vote |
| `429 Too Many Requests` | Rate limit exceeded; `Retry-After: 60` header included |
| `500 Internal Server Error` | Unexpected server error; `correlationId` included for log correlation |

---

## 9. WebSocket Events

Connect via STOMP over SockJS:

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  reconnectDelay: 5000,
  onConnect: () => {
    client.subscribe(`/topic/polls/${pollId}/votes`, (message) => {
      const counts = JSON.parse(message.body);
      // counts: { "<optionId>": <voteCount>, ... }
      // or:     { "_freeTextTotal": <number> }
    });
  },
});
client.activate();
```

### STOMP Endpoint

`/ws` — SockJS endpoint

### STOMP Destinations

| Destination | Direction | Payload | Description |
|-------------|-----------|---------|-------------|
| `/topic/polls/{pollId}/votes` | Server → Client | `{ "<optionId>": count, ... }` | Broadcast on every new vote |

For free-text polls the payload uses the special key `_freeTextTotal`:
```json
{ "_freeTextTotal": 6 }
```

### Connection Lifecycle

1. Client connects to `/ws` via SockJS
2. STOMP handshake completes (CONNECT → CONNECTED)
3. Client subscribes to `/topic/polls/{pollId}/votes`
4. Server broadcasts on every vote via `messagingTemplate.convertAndSend()`
5. Client disconnects on page unload (cleanup in `useEffect` return)

---

## 10. Rate Limiting

Rate limiting is only active when `REDIS_ENABLED=true`.

| Endpoint Group | Limit | Window | Redis Key Pattern |
|---------------|-------|--------|-------------------|
| `/api/auth/**` | 10 req | 1 minute | `rl:<ip>:auth` |
| `POST /api/polls/*/vote` | 20 req | 1 minute | `rl:<ip>:vote` |
| Everything else | 200 req | 1 minute | `rl:<ip>:default` |

**Rate limit headers** (present on every response when Redis is enabled):

```
X-RateLimit-Limit: 200
X-RateLimit-Remaining: 199
```

**429 response body:**
```json
{
  "status": 429,
  "message": "Rate limit exceeded. Retry after 60 seconds.",
  "timestamp": "2026-05-26T10:30:00"
}
```

**Fail-open behaviour**: if Redis is unavailable the filter passes all requests through and logs a warning. No requests are blocked due to infrastructure failure.

**Client IP resolution order:**
1. `X-Forwarded-For` header (first IP)
2. `X-Real-IP` header
3. `request.getRemoteAddr()`

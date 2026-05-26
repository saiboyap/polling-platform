# Deployment Guide

## Table of Contents

1. [Railway Deployment](#1-railway-deployment)
2. [Docker Compose (Local / Staging)](#2-docker-compose-local--staging)
3. [Manual Docker Deployment](#3-manual-docker-deployment)
4. [Kubernetes Deployment](#4-kubernetes-deployment)
5. [Environment Variables Reference](#5-environment-variables-reference)
6. [CI/CD Pipeline](#6-cicd-pipeline)
7. [Monitoring and Health Checks](#7-monitoring-and-health-checks)
8. [Troubleshooting Deployments](#8-troubleshooting-deployments)

---

## 1. Railway Deployment

Railway is the current production platform. Both backend and frontend are deployed as separate services within one project.

### Prerequisites

- [Railway account](https://railway.app/)
- [Railway CLI](https://docs.railway.app/develop/cli) (optional but useful for log access)
- GitHub repository forked or cloned

### Step-by-Step

#### 1.1 Create a Railway Project

1. Log in to [railway.app](https://railway.app)
2. Click **New Project**
3. Select **Deploy from GitHub repo**
4. Authorise Railway to access your GitHub organisation/account
5. Select `polling-platform`

#### 1.2 Deploy the Backend Service

1. In the new project, Railway will detect the repository
2. Click **New Service → GitHub Repo** (select the repo again or use the existing detection)
3. Set the **Root Directory** to `backend`
4. Railway detects the `Dockerfile` and builds it automatically

**Configure environment variables** (Settings → Variables):

```
PGHOST         <auto-set by Postgres plugin>
PGPORT         <auto-set by Postgres plugin>
PGDATABASE     <auto-set by Postgres plugin>
PGUSER         <auto-set by Postgres plugin>
PGPASSWORD     <auto-set by Postgres plugin>
JWT_SECRET     <generate with: openssl rand -base64 64>
REDIS_ENABLED  false
KAFKA_ENABLED  false
```

> Railway automatically injects `PORT` — do not set it manually.

#### 1.3 Add a PostgreSQL Database

1. In the project view, click **New → Database → PostgreSQL**
2. Railway creates a managed PostgreSQL instance and automatically exposes `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` to all services in the project via **Reference Variables**
3. In the backend service, link the variables: Settings → Variables → Add Reference Variable for each PG variable

#### 1.4 Deploy the Frontend Service

1. Click **New Service → GitHub Repo** (same repo)
2. Set **Root Directory** to `frontend`
3. Railway detects the `Dockerfile` and builds it

No environment variables are required. The nginx template substitutes `${PORT}` at container start using the Railway-injected `PORT` variable, and all API calls are proxied to the backend service URL hardcoded in `nginx.conf`.

> If the backend URL changes, update `nginx.conf` `proxy_pass` directives and redeploy.

#### 1.5 Verify Deployment

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login
railway login

# Tail backend logs
railway logs --service backend

# Tail frontend logs
railway logs --service frontend
```

Health check URLs:
- Backend: `https://<backend-domain>.up.railway.app/health`
- Frontend: `https://<frontend-domain>.up.railway.app`

#### 1.6 Enable Optional Services

**With Redis:**
1. Add **New → Database → Redis** to the project
2. Railway exposes `REDIS_URL` — but the app uses individual host/port variables
3. Set in the backend service: `SPRING_REDIS_HOST=<redis-host>`, `SPRING_REDIS_PORT=6379`, `REDIS_ENABLED=true`

**With Kafka:**
Kafka is not available as a Railway managed service. Use [Upstash](https://upstash.com/) or [Confluent Cloud](https://www.confluent.io/):
```
KAFKA_ENABLED=true
SPRING_KAFKA_BOOTSTRAP_SERVERS=<broker-url>:9092
```

### Deployment Lifecycle

Railway triggers a new deployment on every push to `main`. The pipeline:

1. Clones the repository
2. Builds the Docker image (multi-stage)
3. Runs health checks on the new container
4. Replaces the old container (zero-downtime rolling deploy leveraging `server.shutdown: graceful`)

---

## 2. Docker Compose (Local / Staging)

### Start All Services

```bash
docker compose up --build
```

Services:

| Service | Container Port | Host Port |
|---------|---------------|-----------|
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |
| Zookeeper | 2181 | 2181 |
| Kafka | 29092 (internal), 9092 (external) | 9092 |
| Backend | 8080 | 8080 |
| Frontend | 80 | 3000 |

### Enable Redis and Kafka Features

Edit `docker-compose.yml` backend environment:

```yaml
services:
  backend:
    environment:
      REDIS_ENABLED: "true"
      KAFKA_ENABLED: "true"
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: "6379"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:29092"
```

### Useful Commands

```bash
# Start only databases (lean dev setup)
docker compose up postgres redis -d

# Rebuild a specific service after code change
docker compose up --build backend -d

# View logs for one service
docker compose logs -f backend

# Enter the PostgreSQL container
docker compose exec postgres psql -U polling_user -d polling_db

# Flush Redis
docker compose exec redis redis-cli FLUSHALL

# Stop and remove containers (keep volumes)
docker compose down

# Stop and remove containers AND volumes (clean slate)
docker compose down -v
```

---

## 3. Manual Docker Deployment

Build and run without Compose:

### Backend

```bash
cd backend

docker build -t polling-backend:latest .

docker run -d \
  --name polling-backend \
  -p 8080:8080 \
  -e PGHOST=<host> \
  -e PGPORT=5432 \
  -e PGDATABASE=polling_db \
  -e PGUSER=polling_user \
  -e PGPASSWORD=<password> \
  -e JWT_SECRET=<secret> \
  polling-backend:latest
```

### Frontend

```bash
cd frontend

docker build -t polling-frontend:latest .

docker run -d \
  --name polling-frontend \
  -p 3000:80 \
  polling-frontend:latest
```

The frontend nginx config proxies `/api/` and `/ws` to the hardcoded backend URL. For a different backend URL, update `nginx.conf` before building.

---

## 4. Kubernetes Deployment

Below is a reference deployment for a single-node or small cluster. Adapt resource limits and replica counts for production.

### Backend Deployment

```yaml
# k8s/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: polling-backend
  labels:
    app: polling-backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: polling-backend
  template:
    metadata:
      labels:
        app: polling-backend
    spec:
      containers:
        - name: polling-backend
          image: your-registry/polling-backend:latest
          ports:
            - containerPort: 8080
          env:
            - name: PGHOST
              valueFrom:
                secretKeyRef:
                  name: polling-db-secret
                  key: host
            - name: PGPORT
              value: "5432"
            - name: PGDATABASE
              valueFrom:
                secretKeyRef:
                  name: polling-db-secret
                  key: database
            - name: PGUSER
              valueFrom:
                secretKeyRef:
                  name: polling-db-secret
                  key: username
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: polling-db-secret
                  key: password
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: polling-jwt-secret
                  key: value
            - name: REDIS_ENABLED
              value: "true"
            - name: SPRING_REDIS_HOST
              value: "redis-service"
            - name: KAFKA_ENABLED
              value: "false"
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          lifecycle:
            preStop:
              exec:
                command: ["sleep", "15"]   # allow load balancer to drain
---
apiVersion: v1
kind: Service
metadata:
  name: polling-backend-service
spec:
  selector:
    app: polling-backend
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

### Frontend Deployment

```yaml
# k8s/frontend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: polling-frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: polling-frontend
  template:
    metadata:
      labels:
        app: polling-frontend
    spec:
      containers:
        - name: polling-frontend
          image: your-registry/polling-frontend:latest
          ports:
            - containerPort: 80
          resources:
            requests:
              memory: "64Mi"
              cpu: "50m"
            limits:
              memory: "128Mi"
              cpu: "200m"
---
apiVersion: v1
kind: Service
metadata:
  name: polling-frontend-service
spec:
  selector:
    app: polling-frontend
  ports:
    - port: 80
      targetPort: 80
  type: ClusterIP
```

### Ingress

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: polling-ingress
  annotations:
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
spec:
  rules:
    - host: your-domain.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: polling-frontend-service
                port:
                  number: 80
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: polling-backend-service
                port:
                  number: 8080
          - path: /ws
            pathType: Prefix
            backend:
              service:
                name: polling-backend-service
                port:
                  number: 8080
```

> For WebSocket support in Kubernetes Ingress, ensure the ingress controller is configured with `proxy-read-timeout: 3600` to prevent idle WebSocket connections from being terminated.

### Kubernetes Secrets

```bash
kubectl create secret generic polling-db-secret \
  --from-literal=host=<pg-host> \
  --from-literal=database=polling_db \
  --from-literal=username=polling_user \
  --from-literal=password=<pg-password>

kubectl create secret generic polling-jwt-secret \
  --from-literal=value=$(openssl rand -base64 64)
```

### Apply

```bash
kubectl apply -f k8s/
kubectl rollout status deployment/polling-backend
kubectl rollout status deployment/polling-frontend
```

---

## 5. Environment Variables Reference

### Backend — Complete Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PORT` | No | `8080` | Server listen port (Railway/K8s inject this) |
| `PGHOST` | **Yes** | — | PostgreSQL hostname |
| `PGPORT` | **Yes** | — | PostgreSQL port |
| `PGDATABASE` | **Yes** | — | Database name |
| `PGUSER` | **Yes** | — | Database username |
| `PGPASSWORD` | **Yes** | — | Database password |
| `JWT_SECRET` | No | base64 default | HS256 signing key — **must override in production** |
| `JWT_EXPIRATION` | No | `86400000` | Token TTL (ms). 86400000 = 24 h |
| `REDIS_ENABLED` | No | `false` | Enables Redis cache, rate limiting, trending |
| `SPRING_REDIS_HOST` | Cond. | `localhost` | Redis hostname (required if Redis enabled) |
| `SPRING_REDIS_PORT` | No | `6379` | Redis port |
| `KAFKA_ENABLED` | No | `false` | Enables Kafka producer and consumer |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Cond. | `localhost:9092` | Kafka brokers (required if Kafka enabled) |

### Frontend — Build-Time (Vite)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `VITE_API_BASE_URL` | No | `/api` | API base path |
| `VITE_WS_URL` | No | `/ws` | WebSocket endpoint |
| `PORT` | No | `80` | nginx listen port (set at container start via envsubst) |

---

## 6. CI/CD Pipeline

### Current: Railway Auto-Deploy

Railway polls the connected GitHub repository and deploys on every push to `main`:

```
git push origin main
       │
       ▼
Railway detects push
       │
       ▼
Docker build (backend/Dockerfile or frontend/Dockerfile)
       │
  ┌────┴────┐
  │ Success │  → Deploy new container → Health check → Swap traffic
  │ Failure │  → Rollback to previous image → Alert
  └─────────┘
```

Build times on Railway free tier: ~3–5 minutes (Maven dependency resolution cached).

### Recommended: GitHub Actions

Create `.github/workflows/deploy.yml`:

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test-backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: polling_db
          POSTGRES_USER: polling_user
          POSTGRES_PASSWORD: polling_pass
        ports: ["5432:5432"]
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      - name: Run backend tests
        working-directory: ./backend
        env:
          PGHOST: localhost
          PGPORT: 5432
          PGDATABASE: polling_db
          PGUSER: polling_user
          PGPASSWORD: polling_pass
        run: mvn test

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
        working-directory: ./frontend
      - run: npm run lint
        working-directory: ./frontend
      - run: npm run build
        working-directory: ./frontend

  deploy:
    needs: [test-backend, test-frontend]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to Railway
        uses: bervProject/railway-deploy@v1.0.0
        with:
          railway_token: ${{ secrets.RAILWAY_TOKEN }}
          service: backend   # run again for frontend
```

Set `RAILWAY_TOKEN` in GitHub repository secrets.

---

## 7. Monitoring and Health Checks

### Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /health` | Public | Simple UP/DOWN — fastest check |
| `GET /actuator/health` | Public (summary) / ADMIN (details) | Spring Boot actuator health |
| `GET /actuator/health/liveness` | Public | Kubernetes liveness probe |
| `GET /actuator/health/readiness` | Public | Kubernetes readiness probe |
| `GET /actuator/metrics` | ADMIN | Micrometer metrics |
| `GET /actuator/circuitbreakers` | ADMIN | Resilience4j circuit breaker states |
| `GET /actuator/info` | Public | App name, version |

### Log Monitoring

All logs are structured JSON (Logstash Logback Encoder):

```json
{
  "timestamp": "2026-05-26T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.polling.platform.service.VoteService",
  "message": "Single-choice vote: poll=550e... option=abc1... user=alice",
  "correlationId": "3e8f1c2a-...",
  "thread": "http-nio-8080-exec-1"
}
```

Every request injects a `correlationId` via `CorrelationIdFilter` (MDC). Use this to trace a single request across all log lines.

**Railway log access:**
```bash
railway logs --service backend --tail 100
```

**Key log patterns to watch:**

| Pattern | Severity | Meaning |
|---------|----------|---------|
| `Redis circuit open` | WARN | Redis unreachable; DB fallback active |
| `Audit write failed` | WARN | Audit log DB insert failed (non-critical) |
| `Rate limit exceeded` | WARN | Client hitting rate limit |
| `Unhandled exception` | ERROR | Unexpected 500 — correlationId available for tracing |
| `WebSocket publish failed` | WARN | STOMP broadcast error (vote was still saved) |

### Railway Metrics

Railway provides basic CPU, memory, and network graphs in the service dashboard. For production, supplement with:

- **Grafana Cloud** or **Datadog** — ship logs from Railway via the logging plugin
- **Uptime monitoring** — [BetterUptime](https://betterstack.com/better-uptime) or [UptimeRobot](https://uptimerobot.com/) polling `GET /health` every 60 s

---

## 8. Troubleshooting Deployments

### Backend fails to start

**Symptom:** Railway shows the deployment as failed; container exits immediately.

**Diagnosis:**
```bash
railway logs --service backend | head -100
```

**Common causes:**

| Error | Fix |
|-------|-----|
| `Could not resolve placeholder 'PGHOST'` | PG environment variables not set or not linked from the PostgreSQL plugin |
| `DuplicateKeyException` in YAML parsing | Duplicate top-level key in `application.yml` — check for two `spring:` blocks |
| `OutOfMemoryError: Metaspace` | JVM flags not applied; verify Dockerfile `CMD` line |
| `Connection refused` on port 5432 | PostgreSQL not ready; Hikari pool times out |
| Flyway checksum mismatch | A migration file was modified after being applied; use `repair` or `baseline` |

### Backend returns 502 from frontend

**Symptom:** nginx proxies requests but gets no response from the backend.

**Causes:**
1. Backend container not running (check Railway dashboard)
2. `proxy_pass` URL in `nginx.conf` points to wrong backend domain
3. Backend is starting up (cold start can take 60–90 s on free tier with 512 MB RAM)

### Frontend 502 on initial load

**Symptom:** Visiting the frontend URL returns an nginx 502.

**Cause:** Railway has assigned a different `PORT` value than 80, but nginx is still listening on 80.

**Fix:** Confirm the Dockerfile `CMD` runs `envsubst '${PORT}'` to substitute the port before starting nginx. Verify `ENTRYPOINT []` is present to prevent the default nginx image entrypoint from running.

### WebSocket connects but no updates

**Symptom:** Connection indicator shows "Live" but vote changes are not reflected.

**Causes:**
1. The STOMP subscription topic does not match the publish destination
2. Railway's reverse proxy is terminating the WebSocket connection — set `proxy_read_timeout 3600` in nginx

**Verify with browser DevTools → Network → WS tab**: look for the STOMP SUBSCRIBE frame and confirm the destination is `/topic/polls/{id}/votes`.

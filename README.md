# ReportX

A full-stack Minecraft player reporting and staff moderation system, consisting of three components that work together:

- **Plugin** — Bukkit/Spigot plugin (Java) that players and staff interact with in-game
- **API** — Spring Boot REST API that stores and serves all report data
- **Dashboard** — Vue 3 web dashboard for staff to manage reports from a browser

---

## Architecture Overview

```
Minecraft Server
  └── ReportX Plugin (Bukkit/Spigot)
        ├── Stores data in MySQL (shared with API)
        └── Polls Spring Boot API every N seconds
              └── Fires in-game notifications on status changes

Spring Boot API (Port 8080)
  ├── JWT-secured REST endpoints
  ├── Rate limiting per token/IP
  └── MySQL database (shared with Plugin)

Vue 3 Dashboard (Port 5173 / your domain)
  ├── Staff login via one-time token from /reportadmin web
  ├── Report management (claim, resolve, reject, escalate)
  ├── Player lookup with Minecraft head previews
  ├── Audit log, stats, chat & inventory snapshots
  └── Communicates exclusively with the Spring Boot API
```

---

## Requirements

| Component | Requirement |
|-----------|-------------|
| Plugin    | Java 17+, Spigot / Paper 1.21+ |
| API       | Java 17+, MySQL 8+ |
| Dashboard | Node.js 18+ |
| Database  | MySQL 8 (shared between Plugin and API) |

---

## Project Structure

```
ReportX/
├── Plugin/                          # Bukkit plugin source
│   └── src/main/
│       ├── java/me/abdoabk/reportx/
│       │   ├── ReportXPlugin.java
│       │   ├── command/             # /report, /reports, /reportadmin
│       │   ├── gui/                 # In-game GUIs
│       │   ├── listener/            # Chat & inventory listeners
│       │   ├── model/               # Report, Note, AuditLog models
│       │   ├── repository/          # H2 & MySQL repositories
│       │   ├── service/             # ReportService, EvidenceService
│       │   ├── web/                 # WebTokenService, DashboardNotificationPoller
│       │   └── webhook/             # Discord webhook
│       └── resources/
│           ├── config.yml
│           ├── messages.yml
│           └── plugin.yml
│
├── api_Spring_boot/                 # Spring Boot REST API source
│   └── src/main/
│       ├── java/me/abdoabk/reportxapi/
│       │   ├── config/              # SecurityConfig, RateLimitFilter, TokenCleanupTask
│       │   ├── controller/          # AuthController, ReportController
│       │   ├── dto/                 # Request DTOs
│       │   ├── entity/              # JPA entities
│       │   ├── repository/          # Spring Data JPA repositories
│       │   └── security/            # JwtUtil, JwtAuthFilter
│       └── resources/
│           └── application.properties
│
└── Web_Dashboard/                   # Vue 3 frontend source
    └── src/
        ├── api/reports.js           # Axios API client
        ├── components/              # StatCard, StatusBadge, PageHeader
        ├── router/index.js          # Vue Router
        ├── stores/                  # Pinia: auth, reports
        └── views/
            ├── DashboardView.vue
            ├── ReportsView.vue
            ├── ReportView.vue
            ├── PlayerLookupView.vue
            ├── AuditLogView.vue
            ├── LoginView.vue
            ├── AuthView.vue
            └── TokenAlreadyUsedView.vue
```

---

## Setup Guide

### Step 1 — MySQL Database

The Plugin and the API **share the same MySQL database**. Create it once:

```sql
CREATE DATABASE reportx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'reportx'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON reportx.* TO 'reportx'@'%';
FLUSH PRIVILEGES;
```

If you're using Docker:

```bash
docker run -d \
  --name reportx-mysql \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=reportx \
  -p 3306:3306 \
  mysql:8
```

---

### Step 2 — Spring Boot API

**1. Configure `application.properties`:**

```properties
# Database
db.host=127.0.0.1
db.port=3306
db.name=reportx
db.username=root
db.password=your_password

# JWT — REQUIRED: change to a random 64+ character string
reportx.jwt.secret=CHANGE_THIS_TO_A_LONG_RANDOM_SECRET_KEY_MINIMUM_64_CHARACTERS
reportx.jwt.ttl-minutes=30

# CORS — set to your dashboard URL (comma-separated for multiple)
reportx.cors.allowed-origins=http://localhost:5173,https://your-dashboard-domain.com

# Plugin shared secret — must match plugin's config.yml
reportx.plugin.secret=CHANGE_THIS_PLUGIN_SECRET

# Rate limiting
reportx.ratelimit.requests-per-window=100
reportx.ratelimit.window-seconds=60
```

**2. Build and run:**

```bash
cd api_Spring_boot
./mvnw clean package -DskipTests
java -jar target/reportx-api-*.jar
```

The API starts on port `8080` by default.

---

### Step 3 — Bukkit Plugin

**1. Set `database.type: MYSQL` in `config.yml`** and fill in your MySQL credentials:

```yaml
database:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: reportx
    username: root
    password: your_password
```

**2. Configure the web dashboard section:**

```yaml
web-dashboard:
  url: "http://localhost:5173"           # Your Vue dashboard URL
  api-url: "http://localhost:8080"       # Your Spring Boot API URL
  plugin-secret: "CHANGE_THIS_PLUGIN_SECRET"  # Must match application.properties
  poll-interval-seconds: 10
  poll-notifications: true
  token-ttl-minutes: 30
```

**3. Drop the compiled `.jar`** into your server's `plugins/` folder and restart.

---

### Step 4 — Vue Dashboard

**1. Create a `.env` file** in the `Web_Dashboard` folder:

```env
VITE_API_URL=http://localhost:8080
```

**2. Install dependencies and run:**

```bash
cd Web_Dashboard
npm install
npm run dev
```

For production:

```bash
npm run build
# Serve the dist/ folder with Nginx, Apache, or any static host
```

---

## Staff Login Flow

Staff members log into the dashboard using a one-time token generated in-game:

1. Staff member runs `/reportadmin web` in Minecraft
2. A clickable link appears in chat (valid for 30 minutes)
3. Clicking the link opens the dashboard and logs them in automatically
4. The session lasts 30 minutes, then expires

If the link has already been used, the dashboard shows a dedicated **"Link Already Used"** page with instructions to generate a new one.

---

## In-Game Commands

### Player Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/report <player> <reason>` | `reportx.use` | Submit a report against a player |
| `/reports [page]` | `reportx.use` | View your own submitted reports |

### Staff Commands (`/reportadmin`)

| Subcommand | Permission | Description |
|-----------|-----------|-------------|
| `/reportadmin list [page]` | `reportx.staff` | List all open reports |
| `/reportadmin view <id>` | `reportx.staff` | View a specific report |
| `/reportadmin claim <id>` | `reportx.staff` | Claim a report |
| `/reportadmin close <id> <verdict>` | `reportx.staff` | Resolve a claimed report |
| `/reportadmin reject <id>` | `reportx.staff` | Reject a report |
| `/reportadmin escalate <id>` | `reportx.staff` | Escalate a report |
| `/reportadmin tp <id>` | `reportx.teleport` | Teleport to the report location |
| `/reportadmin note <id> <text>` | `reportx.staff` | Add a staff note |
| `/reportadmin web` | `reportx.staff` | Generate a dashboard login link |
| `/reportadmin stats` | `reportx.admin` | View server report statistics |
| `/reportadmin audit [page]` | `reportx.audit` | View audit log |
| `/reportadmin reload` | `reportx.admin` | Reload configuration |

---

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `reportx.use` | `true` (all players) | Submit and view own reports |
| `reportx.staff` | `op` | Access staff commands and dashboard |
| `reportx.admin` | `op` | Stats, reload, admin functions |
| `reportx.bypass.cooldown` | `op` | Bypass report submission cooldown |
| `reportx.override.claim` | `op` | Close reports claimed by other staff |
| `reportx.teleport` | `op` | Teleport to report locations |
| `reportx.audit` | `op` | View audit log |

---

## Dashboard Features

| Feature | Description |
|---------|-------------|
| **Dashboard** | Stats overview — total reports, by status, avg resolution time, top accused players |
| **Reports List** | Paginated, filterable by status and search term |
| **Report Detail** | Full report info, chat snapshot, inventory snapshot, staff notes, status actions |
| **Player Lookup** | Search by name or UUID — shows player head, last seen, report history. Autocomplete with Minecraft head previews |
| **Audit Log** | Full history of all staff actions |
| **Claim Protection** | Only the staff member who claimed a report (or ADMIN/SENIOR_STAFF) can resolve or reject it |
| **Token Already Used** | Dedicated page shown when a one-time login link is reused |

---

## Staff Roles & Permissions (Dashboard)

| Role | Claim | Resolve own | Resolve others | View IP | Stats & Audit |
|------|-------|-------------|---------------|---------|--------------|
| `STAFF` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `SENIOR_STAFF` | ✅ | ✅ | ✅ | ❌ | ✅ |
| `ADMIN` | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## API Endpoints

All endpoints require a `Bearer <jwt>` header unless marked public.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/auth/exchange` | Public | Exchange plugin token for JWT |
| `GET` | `/api/auth/me` | JWT | Get current staff info |
| `GET` | `/api/reports` | STAFF+ | List reports (paginated, filterable) |
| `GET` | `/api/reports/stats` | SENIOR_STAFF+ | Dashboard statistics |
| `GET` | `/api/reports/:id` | STAFF+ | Get single report |
| `PATCH` | `/api/reports/:id/status` | SENIOR_STAFF+ | Update report status |
| `POST` | `/api/reports/:id/notes` | STAFF+ | Add staff note |
| `GET` | `/api/players/:uuid` | STAFF+ | Player lookup by UUID |
| `GET` | `/api/players/search?name=` | STAFF+ | Player search by name |
| `GET` | `/api/players/search?name=&suggestions=true` | STAFF+ | Autocomplete suggestions |
| `GET` | `/api/audit-log` | SENIOR_STAFF+ | Audit log |
| `GET` | `/api/internal/poll-notifications` | Plugin Secret | Plugin notification poll |

---

## Security

- **JWT authentication** — 30-minute sessions, signed with a configurable secret
- **Rate limiting** — 100 requests per 60 seconds per token (or IP for unauthenticated), returns `429` when exceeded
- **One-time login tokens** — generated in-game, expire after 30 minutes, single-use only
- **Claim ownership** — only the claimer or ADMIN/SENIOR_STAFF can resolve a claimed report, enforced both in the API (403) and the UI
- **Plugin secret** — internal poll endpoint protected by a shared secret header (`X-Plugin-Secret`)
- **CORS** — configured per-origin, only your dashboard domain is allowed
- **Input sanitisation** — configurable via `security.sanitize-input` in `config.yml`

---

## Discord Webhooks

ReportX can post to a Discord channel when reports are submitted, resolved, or escalated.

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/YOUR/WEBHOOK"
  notify-new-report: true
  notify-resolved: true
  notify-escalated: true
  server-name: "My Server"
```

---

## Evidence Collection

When a report is submitted, the plugin automatically captures:

- **Chat snapshot** — last N lines of chat (configurable, default 20)
- **Inventory snapshot** — accused player's inventory at time of report
- **Combat snapshot** — recent combat interactions

All snapshots are stored in the database and viewable in the dashboard report detail page.

---

## Configuration Reference

### `config.yml` (Plugin)

```yaml
report:
  cooldown-seconds: 120       # Time between report submissions per player
  max-per-day: 5              # Max reports a player can submit per day
  allow-offline: false        # Allow reporting offline players
  min-reason-length: 5        # Minimum reason character length
  max-reason-length: 200      # Maximum reason character length

evidence:
  chat-snapshot: true         # Capture chat history
  chat-lines: 20              # Number of chat lines to capture
  inventory-snapshot: true    # Capture accused inventory
  combat-snapshot: true       # Capture combat log

staff:
  auto-vanish-on-tp: true     # Vanish when teleporting to report location
  unvanish-after-seconds: 30  # Auto-unvanish after this many seconds
  broadcast-claim: true       # Broadcast when a report is claimed
  broadcast-resolve: true     # Broadcast when a report is resolved
  require-claim-to-close: true # Staff must claim before resolving
```

### `application.properties` (API)

```properties
reportx.ratelimit.requests-per-window=100   # Requests allowed per window
reportx.ratelimit.window-seconds=60         # Window size in seconds
reportx.token.cleanup-interval-minutes=60   # How often expired tokens are purged
reportx.jwt.ttl-minutes=30                  # Dashboard session length
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Plugin | Java 17, Bukkit/Spigot API 1.21 |
| API | Java 17, Spring Boot 4, Spring Security, Spring Data JPA, JJWT |
| Dashboard | Vue 3, Vite, Pinia, Vue Router, Axios, Tailwind CSS, Chart.js |
| Database | MySQL 8 |

---

## Author

Made by **abdoabk**
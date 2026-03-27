# ReportX 🔴

> **Lightweight, scalable, and secure player reporting & moderation workflow plugin**  
> Designed for Minecraft servers running **Paper / Spigot 1.21.1**

---

## ✨ Features

- **In-game report submission** with `/report <player> <reason>`
- **Staff GUI panel** with paginated report management
- **Report lifecycle** — Open → Claimed → Resolved/Rejected/Escalated
- **Evidence capture** — chat snapshots, inventory snapshots, coordinates
- **Discord webhook** integration for real-time notifications
- **Anti-abuse** — cooldowns, daily limits, duplicate detection
- **Async database** operations (H2 embedded or MySQL)
- **Staff audit logging** for accountability
- **Statistics dashboard** for admins

---

## 📦 Installation

1. Download `ReportX.jar` and place it in your server's `/plugins/` folder
2. Restart your server
3. Configure `plugins/ReportX/config.yml`
4. *(Optional)* Edit `plugins/ReportX/messages.yml` to customize all messages
5. Reload with `/report reload`

---

## ⚙️ Configuration

### `config.yml`

```yaml
report:
  cooldown-seconds: 120    # Cooldown between reports per player
  max-per-day: 5           # Max reports a player can submit per day
  allow-offline: false     # Allow reporting offline players

evidence:
  chat-snapshot: true      # Capture last N chat messages from accused
  chat-lines: 20
  inventory-snapshot: false

database:
  type: H2                 # H2 (embedded) or MYSQL
  mysql:
    host: localhost
    port: 3306
    database: reportx
    username: root
    password: password

discord:
  enabled: false
  webhook-url: ""
  server-name: "My Server"
```

### MySQL Setup

Set `database.type: MYSQL` and fill in your credentials. The plugin will auto-create all tables on first launch.

You can also run `schema.sql` manually on your MySQL server.

---

## 🎮 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/report <player> <reason>` | Submit a report | `reportx.use` |
| `/reports [page]` | View your submitted reports | `reportx.use` |
| `/rstaff [page]` | Open staff report GUI | `reportx.staff` |
| `/report claim <id>` | Claim a report | `reportx.staff` |
| `/report close <id> <verdict>` | Close/resolve a report | `reportx.staff` |
| `/report reject <id>` | Reject a report | `reportx.staff` |
| `/report escalate <id>` | Escalate a report | `reportx.staff` |
| `/report note <id> <message>` | Add internal note | `reportx.staff` |
| `/report view <id>` | View report details | `reportx.staff` |
| `/report tp <id>` | Teleport to report location | `reportx.teleport` |
| `/report stats` | View statistics | `reportx.admin` |
| `/report audit [limit]` | View audit log | `reportx.audit` |
| `/report reload` | Reload config/messages | `reportx.admin` |

---

## 🔐 Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `reportx.use` | Submit and view own reports | `true` |
| `reportx.staff` | Manage reports | `op` |
| `reportx.admin` | Config, stats, reload | `op` |
| `reportx.bypass.cooldown` | Skip report cooldown | `op` |
| `reportx.override.claim` | Close reports claimed by others | `op` |
| `reportx.teleport` | Teleport to report locations | `op` |
| `reportx.audit` | View audit logs | `op` |

---

## 🖥️ Staff GUI

Open the staff panel with `/rstaff`. The GUI shows all Open and Claimed reports.

| Click | Action |
|-------|--------|
| **Left Click** | Claim report |
| **Right Click** | View full details in chat |
| **Shift + Click** | Close report |

**Status colors:**
- 🟡 Yellow Wool — Open
- 🔵 Blue Wool — Claimed
- 🟢 Green Wool — Resolved
- 🔴 Red Wool — Rejected
- 🟣 Purple Wool — Escalated

---

## 💬 Discord Webhooks

1. Create a webhook in your Discord server (Channel Settings → Integrations → Webhooks)
2. Copy the webhook URL
3. Set `discord.enabled: true` and paste the URL in `config.yml`
4. Choose which events to notify:
   - `notify-new-report: true`
   - `notify-resolved: true`
   - `notify-escalated: true`

See `discord-webhook-template.json` for example payloads.

---

## 🛡️ Security

- **Input sanitization** — all user input is sanitized before storage
- **SQL injection protection** — all queries use prepared statements
- **UUID-based tracking** — immune to name changes
- **GDPR mode** — disable IP storage with `security.gdpr-mode: true`
- **Staff audit log** — all staff actions are permanently logged

---

## 🗄️ Database

**H2 (default):** Zero-configuration embedded database. Data stored in `plugins/ReportX/reportx-data.mv.db`. Perfect for small to medium servers.

**MySQL:** For larger networks or servers that require shared database access. Set `database.type: MYSQL` and configure credentials. Supports connection pooling via HikariCP.

---

## 📊 Architecture

```
me.abdoabk.reportx
├── ReportXPlugin.java           # Main plugin class
├── command/
│   ├── ReportCommand.java       # /report command handler
│   ├── ReportsCommand.java      # /reports player GUI
│   └── StaffCommand.java        # /rstaff staff GUI
├── service/
│   ├── ReportService.java       # Core business logic
│   ├── EvidenceService.java     # Evidence capture
│   └── NotificationService.java # Staff/Discord notifications
├── repository/
│   ├── ReportRepository.java    # Interface
│   ├── AbstractSQLRepository.java # Base SQL implementation
│   ├── H2ReportRepository.java  # H2 implementation
│   └── MySQLReportRepository.java # MySQL implementation
├── gui/
│   ├── GUIManager.java          # GUI session tracking
│   ├── PlayerReportGUI.java     # Player GUI
│   └── StaffReportGUI.java      # Staff GUI
├── model/
│   ├── Report.java              # Report entity
│   ├── ReportStatus.java        # Status enum
│   ├── Note.java                # Note entity
│   └── AuditLog.java            # Audit log entity
├── listener/
│   ├── ChatListener.java        # Chat evidence capture
│   └── InventoryListener.java   # GUI click handling
├── webhook/
│   └── DiscordWebhook.java      # Discord integration
└── util/
    ├── MessageUtil.java         # Message formatting
    └── TimeUtil.java            # Time formatting
```

---

## 🔨 Building from Source

Requirements: **Java 21**, **Maven 3.8+**

```bash
git clone https://github.com/abdoabk/ReportX.git
cd ReportX
mvn clean package
```

The compiled JAR will be in `target/ReportX-1.0.0.jar`.

---

## 📋 Non-Functional Requirements Met

- ✅ Async database operations (no main-thread blocking)
- ✅ Compatible with Paper 1.21.1
- ✅ Memory efficient (evidence stored per-session, not all in memory)
- ✅ Supports 1000+ reports without lag
- ✅ Clean Architecture pattern
- ✅ Modular for future expansion

---

## 📄 License

MIT License — Free to use, modify, and distribute.

---

**Made with ❤️ by abdoabk**

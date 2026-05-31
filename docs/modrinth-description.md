# Auth

**Auth** is a proxy-first authentication system for Minecraft networks. It uses one proxy plugin for Velocity/BungeeCord and lightweight backend guard plugins for Spigot/Paper/Purpur/Folia servers.

Players authenticate on the proxy with `/login` or `/register`, while backend servers stay protected until the proxy confirms the player is logged in.

---

## Features

| Feature | Status |
|---|---:|
| Velocity support | Yes |
| BungeeCord support | Yes |
| Spigot backend guard | Yes |
| Paper/Purpur backend guard | Yes |
| Folia backend guard | Yes |
| SQLite database | Yes |
| MySQL / MariaDB / PostgreSQL | Yes |
| `/login` and `/register` | Yes |
| Session login | Yes |
| Authenticator 2FA | Yes |
| Admin `/zauth` commands | Yes |
| Backend movement/chat/command protection | Yes |
| Backend inventory/drop/damage protection | Yes |
| Config auto-merge on update | Yes |
| bStats support | Yes |

---

## Downloads

Auth builds multiple jars. Install the correct jar for each server type.

| Jar | Install on |
|---|---|
| `Auth-Proxy-<version>.jar` | Velocity or BungeeCord proxy |
| `Auth-Backend-SpigotLegacy-<version>.jar` | Spigot-style 1.16-1.19 backends |
| `Auth-Backend-Spigot-<version>.jar` | Spigot-style 1.20-1.21 backends |
| `Auth-Backend-Paper-<version>.jar` | Paper/Purpur 1.21+ backends |
| `Auth-Backend-Folia-<version>.jar` | Folia 1.21+ backends |

Do **not** install the proxy jar on backend servers.  
Do **not** install backend jars on the proxy.

---

## How It Works

1. A player joins the proxy.
2. Auth checks whether the player is registered.
3. The player logs in, registers, or verifies 2FA.
4. The proxy sends the authenticated state to backend servers.
5. Backend servers block gameplay until the proxy confirms login.

The backend and proxy communicate automatically over:

```text
zcraftauth:state
```

No backend database setup is required.

---

## Commands

| Command | Description |
|---|---|
| `/login <password>` | Log into your account |
| `/l <password>` | Login alias |
| `/register <password> <password>` | Register a new account |
| `/reg <password> <password>` | Register alias |
| `/logout` | Log out and lock backend access again |
| `/changepass <old> <new>` | Change your password |
| `/2fa enable` | Generate an authenticator secret |
| `/2fa verify <code>` | Verify your 2FA login |
| `/2fa disable <code>` | Disable authenticator 2FA |

---

## Admin Commands

`/zauth` requires:

```text
zcraftauth.admin
```

| Command | Description |
|---|---|
| `/zauth status` | Show authenticated player count |
| `/zauth unregister <player>` | Delete a player login |
| `/zauth setpassword <player> <password>` | Change a player password |
| `/zauth disable2fa <player>` | Remove a player's 2FA |
| `/zauth forcelogin <player>` | Mark a player as logged in |
| `/zauth logout <player>` | Mark a player as logged out |

---

## Database Support

Auth supports:

- SQLite
- MySQL
- MariaDB
- PostgreSQL

Default config:

```yml
database:
  type: sqlite

  sqlite:
    file: auth.db
```

External database example:

```yml
database:
  type: mysql

  external:
    host: localhost
    port: 3306
    database: zcraft_auth
    username: root
    password: ""
    ssl: false
```

---

## Session Login

Auth can restore a player session when their IP matches the last known login IP.

```yml
session:
  enabled: true
```

This helps avoid unnecessary re-logins while keeping backend servers protected.

---

## Timeouts

Login and register timeouts are separate.

```yml
general:
  login-timeout: 0
  register-timeout: 0
```

Set either value to `0` to disable that timeout.

---

## Backend Protection

The backend plugin is intentionally small and low-impact. It does not connect to the database and does not run repeating tasks.

It can block:

- Movement
- Chat
- Commands
- Inventory clicks
- Item drops
- Item pickup
- Damage
- Attacking

Backend config:

```yml
block-movement: true
allow-chat: false
enable-bypass-permission: false

allowed-commands:
  - "/login"
  - "/register"
  - "/l"
  - "/reg"
  - "/2fa"
  - "/totp"
  - "/authenticator"
```

Operators are **not bypassed by default**.

---

## 2FA Authenticator

Players can enable authenticator-based 2FA:

```text
/2fa enable
```

On future logins, after entering the correct password, they must verify:

```text
/2fa verify <code>
```

Compatible with common authenticator apps such as Google Authenticator, Authy, 2FAS, and similar TOTP apps.

---

## Performance

Auth is designed to stay light on backend servers:

- Backend plugin has no database connection.
- Backend plugin does not hash passwords.
- Backend plugin does not perform account lookups.
- Backend plugin only listens for protection events and proxy state.
- Proxy database operations run asynchronously.
- Prompt bars are event-driven.

This keeps backend TPS/MSPT impact minimal.

---

## Metrics

Auth uses bStats project id:

```text
31667
```

Metrics run from the Bukkit/Paper backend plugin.

---

## Currently Not Ported

The older Paper-only codebase contained more planned or legacy systems. These are **not yet fully ported** into the current proxy-first system:

- E-mail recovery and confirmation
- Country whitelist/blacklist
- Built-in proxy AntiBot
- Premium Mojang bypass
- Custom external table/column mapping
- Legacy hash import/migration formats
- Editable per-language message packs
- Automatic database backups
- Account importers

These are planned candidates for future proxy-side implementation.

---

## Requirements

| Component | Supported |
|---|---|
| Java | 21+ |
| Proxy | Velocity, BungeeCord |
| Backend | Spigot-style, Paper, Purpur, Folia |
| Minecraft | Backend variants for 1.16-1.19, 1.20-1.21, and 1.21+ families |

---

## Installation

1. Install `Auth-Proxy-<version>.jar` on your proxy.
2. Install the matching `Auth-Backend-<version>.jar` on each backend server.
3. Start the proxy once to generate the config.
4. Edit the proxy config if needed.
5. Restart the proxy and backend servers.
6. Join and register with:

```text
/register <password> <password>
```

---

## Support

If something does not work, include:

- Proxy type and version
- Backend server type and version
- Auth proxy jar name
- Auth backend jar name
- Console error logs
- Config database type

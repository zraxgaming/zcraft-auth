# Setup

This project builds two different plugin jars from one repository.

## Which Jar Goes Where

| Jar | Server Type | Required? |
| --- | --- | --- |
| `Auth-Proxy-<version>.jar` | Velocity or BungeeCord | Yes, once per network |
| `Auth-Backend-<version>.jar` | Paper/Purpur backend servers | Yes, on every protected backend |

Do not install the backend jar on the proxy. Do not install the proxy jar on backend servers.

## How They Connect

The backend plugin auto-detects the proxy plugin through Bukkit plugin messaging:

1. A player joins a backend server.
2. `Auth-Backend` sends `BACKEND_HELLO` on `zcraftauth:state`.
3. `Auth-Proxy` replies with `AUTH_STATE`.
4. The backend allows or blocks the player based on that state.

No IPs, ports, tokens, or backend database credentials are needed for this handshake.

## Proxy Responsibilities

The proxy jar handles:

- `/login`, `/l`
- `/register`, `/reg`
- `/logout`, `/changepass`
- `/2fa enable|verify|disable`
- `/zauth` admin actions
- Password hashing
- Authenticator 2FA
- SQLite/MySQL/MariaDB/PostgreSQL storage
- Login and register timeout
- Velocity boss bar prompts, with Bungee action bar fallback
- Sending auth state to backend servers

If the proxy cannot initialize the configured database, players are disconnected with an auth-not-ready message and the console logs the database/config error.

## Backend Responsibilities

The backend jar handles:

- Blocking movement before login
- Blocking chat before login
- Blocking commands before login except configured auth commands
- Asking the proxy for auth state on join
- Running bStats for the Bukkit/Paper side

The backend jar is intentionally small and does not touch the auth database.

## Maven Profiles

GitHub Actions uses these profiles:

```bash
mvn -B -DskipTests clean package -Pproxy
mvn -B -DskipTests clean package -Pbackend
```

Outputs:

- `target/Auth-Proxy-<version>.jar`
- `target/Auth-Backend-<version>.jar`

## Database

SQLite is the default and creates one local database file in the proxy plugin folder:

```yml
database:
  type: sqlite
  sqlite:
    file: auth.db
```

For an external database:

```yml
database:
  type: mysql
  external:
    host: localhost
    port: 3306
    database: zcraft_auth
    username: root
    password: ""
```

Supported values for `database.type`:

- `sqlite`
- `mysql`
- `mariadb`
- `postgresql`

## Timeouts

Timeouts are disabled with `0`:

```yml
general:
  login-timeout: 0
  register-timeout: 0
```

## Backend Config

Backend config is local to each backend server:

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

## Admin Commands

`/zauth` requires `zcraftauth.admin`.

| Command | Purpose |
| --- | --- |
| `/zauth status` | Show authenticated count |
| `/zauth unregister <player>` | Delete a login |
| `/zauth setpassword <player> <password>` | Change a password |
| `/zauth disable2fa <player>` | Remove authenticator 2FA |
| `/zauth forcelogin <player>` | Mark a player logged in |
| `/zauth logout <player>` | Mark a player logged out |

## Config Updates

The proxy config loader merges missing keys from the bundled default config into existing configs. It keeps your existing values and adds new keys when the plugin updates.

Binary self-updating is not automatic yet; use GitHub Actions release artifacts for jar updates.

Operators are not bypassed by default. If you turn on `enable-bypass-permission`, only players with `zcraftauth.backend.bypass` can skip backend protection.

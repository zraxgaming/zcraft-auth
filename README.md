# Auth

[![Build](https://img.shields.io/github/actions/workflow/status/zraxgaming/zcraft-auth/build.yml?branch=main&label=build)](https://github.com/zraxgaming/zcraft-auth/actions)
[![Java](https://img.shields.io/badge/Java-21-ED8B00)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/zraxgaming/zcraft-auth)](LICENSE)

Auth is now a two-plugin proxy authentication system:

- `Auth-Proxy` runs on Velocity or BungeeCord and owns authentication.
- `Auth-Backend` runs on Paper/Purpur backend servers and only guards players until the proxy says they are logged in.

The backend and proxy automatically talk over the `zcraftauth:state` plugin message channel. Backend servers do not need database settings.

## Project Layout

| Path | Purpose |
| --- | --- |
| `src/proxy/java` | Velocity/Bungee auth plugin source |
| `src/proxy/resources` | Proxy plugin descriptors |
| `src/backend/java` | Small Paper/Purpur backend guard |
| `src/backend/resources` | Backend `plugin.yml` and tiny backend config |
| `src/main/resources/config.yml` | Default proxy auth config |
| `docs/setup.md` | Install and operation notes |

The older `src/main/java` Paper auth code is kept for compatibility/reference while the active proxy/backend builds come from the Maven profiles below.

## Builds

GitHub Actions builds and uploads two jars:

| Artifact | Install Location | Responsibilities |
| --- | --- | --- |
| `Auth-Proxy-<version>.jar` | Velocity or BungeeCord `plugins/` | `/login`, `/register`, passwords, database, auth state |
| `Auth-Backend-<version>.jar` | Every Paper/Purpur backend `plugins/` | Movement/chat/command guard, proxy state handshake, bStats |

Manual profile commands:

```bash
mvn -B -DskipTests clean package -Pproxy
mvn -B -DskipTests clean package -Pbackend
```

## Install

1. Install `Auth-Proxy-*.jar` on the proxy.
2. Install `Auth-Backend-*.jar` on every backend server.
3. Start the proxy once to generate `config.yml`.
4. Edit only the proxy config if you want MySQL, MariaDB, or PostgreSQL instead of SQLite.
5. Restart the proxy and backend servers.

## Commands

| Command | Purpose |
| --- | --- |
| `/login <password>` | Log in |
| `/l <password>` | Login alias |
| `/register <password> <password>` | Create account |
| `/reg <password> <password>` | Register alias |

## Proxy Config

The proxy config is intentionally small:

```yml
general:
  login-timeout: 0
  register-timeout: 0

database:
  type: sqlite
  sqlite:
    file: auth.db
  external:
    host: localhost
    port: 3306
    database: zcraft_auth
    username: root
    password: ""
```

Set `login-timeout` or `register-timeout` to `0` to disable timeout kicks. Set `database.type` to `mysql`, `mariadb`, or `postgresql` to use the `external` block.

## Backend Config

Backend config is deliberately tiny:

```yml
block-movement: true
allow-chat: false
allowed-commands:
  - "/login"
  - "/register"
  - "/l"
  - "/reg"
```

The backend jar never connects to the database.

## Notes

- Java 21 is required.
- Velocity and BungeeCord are supported by the same proxy jar.
- Paper and Purpur 1.21.x are the intended backend targets.
- bStats runs from the backend plugin because the project is registered for Bukkit/Paper.
- Same-IP player limits are disabled by default.

## License

This project is distributed under CC-BY-NC-SA 4.0. See [LICENSE](LICENSE).

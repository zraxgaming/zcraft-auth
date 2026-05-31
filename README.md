# Auth

[![Build](https://img.shields.io/github/actions/workflow/status/zraxgaming/zcraft-auth/build.yml?branch=main&label=build)](https://github.com/zraxgaming/zcraft-auth/actions)
[![Java](https://img.shields.io/badge/Java-21-ED8B00)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/zraxgaming/zcraft-auth)](LICENSE)

Proxy-first Minecraft authentication for Velocity/Bungee networks, with a tiny Paper/Purpur backend guard.

## Builds

GitHub Actions builds two jars:

- `Auth-Proxy-<version>.jar`
  - Put this on Velocity or BungeeCord.
  - Owns `/login`, `/register`, passwords, auth state, and the database.
  - If the database/config cannot load, players are cleanly disconnected instead of causing event errors.
- `Auth-Backend-<version>.jar`
  - Put this on every backend Paper/Purpur server.
  - No database and no heavy auth logic.
  - Blocks players until the proxy confirms they are logged in.
  - Runs bStats from the backend server, because bStats was registered for Bukkit/Paper.

## Install

1. Put `Auth-Proxy-*.jar` in the proxy `plugins/` folder.
2. Put `Auth-Backend-*.jar` in each backend server `plugins/` folder.
3. Start the proxy once so `config.yml` is generated.
4. Edit only the proxy config if you want MySQL/MariaDB/PostgreSQL instead of SQLite.

The backend and proxy auto-detect each other over the `zcraftauth:state` plugin message channel. No backend database config is needed.

## Commands

| Command | Purpose |
| --- | --- |
| `/login <password>` | Log in |
| `/l <password>` | Alias for login |
| `/register <password> <password>` | Create account |
| `/reg <password> <password>` | Alias for register |

## Config

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

## Notes

- Java 21 is required.
- Backend servers should be Paper or Purpur 1.21.x.
- Velocity and Bungee are supported by the same proxy jar.
- Same-IP player limits are disabled by default.
- More setup notes: [docs/setup.md](docs/setup.md).

## License

This project is distributed under CC-BY-NC-SA 4.0. See [LICENSE](LICENSE).

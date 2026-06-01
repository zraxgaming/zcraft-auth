# Auth

[![Build](https://img.shields.io/github/actions/workflow/status/zraxgaming/zcraft-auth/build.yml?branch=main&label=build)](https://github.com/zraxgaming/zcraft-auth/actions)
[![Java](https://img.shields.io/badge/Java-21-ED8B00)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/zraxgaming/zcraft-auth)](LICENSE)
[![Donate](https://img.shields.io/badge/Donate-PayPal-blue?logo=paypal)](https://www.paypal.com/ncp/payment/ZXN242B97VA24)


Auth is now a two-plugin proxy authentication system:

- `Auth-Proxy` runs on Velocity or BungeeCord and owns authentication.
- `Auth-Backend` runs on Spigot/Paper/Purpur/Folia backend servers and only guards players until the proxy says they are logged in.

The backend and proxy automatically talk over the `zcraftauth:state` plugin message channel. Backend servers do not need database settings.

## Project Layout

| Path | Purpose |
| --- | --- |
| `src/proxy/java` | Velocity/Bungee auth plugin source |
| `src/proxy/resources` | Proxy plugin descriptors |
| `src/backend/java` | Small Spigot/Paper/Purpur/Folia backend guard |
| `src/backend/resources` | Backend `plugin.yml` and tiny backend config |
| `src/main/resources/config.yml` | Default proxy auth config |
| `docs/setup.md` | Install and operation notes |

The older `src/main/java` Paper auth code is kept for compatibility/reference while the active proxy/backend builds come from the Maven profiles below.

## Builds

GitHub Actions builds and uploads one proxy jar plus dedicated backend jars:

| Artifact | Install Location | Responsibilities |
| --- | --- | --- |
| `Auth-Proxy-<version>.jar` | Velocity or BungeeCord `plugins/` | `/login`, `/register`, passwords, database, auth state |
| `Auth-Backend-SpigotLegacy-<version>.jar` | Spigot-style 1.16-1.19 backend `plugins/` | Movement/chat/command guard, proxy state handshake, bStats |
| `Auth-Backend-Spigot-<version>.jar` | Spigot-style 1.20-1.21 backend `plugins/` | Movement/chat/command guard, proxy state handshake, bStats |
| `Auth-Backend-Paper-<version>.jar` | Paper/Purpur 1.21+ backend `plugins/` | Movement/chat/command guard, proxy state handshake, bStats |
| `Auth-Backend-Folia-<version>.jar` | Folia 1.21+ backend `plugins/` | Movement/chat/command guard, proxy state handshake, bStats |

Manual profile commands:

```bash
mvn -B -DskipTests clean package -Pproxy
mvn -B -DskipTests clean package -Pbackend -Dbackend.finalName=Auth-Backend-Paper-1.0.0
```

## Install

1. Install `Auth-Proxy-*.jar` on the proxy.
2. Install the backend jar that matches each backend server family.
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
| `/logout` | Log out and lock backend access again |
| `/changepass <old> <new>` | Change your password |
| `/2fa enable` | Generate an authenticator secret |
| `/2fa verify <code>` | Finish login when 2FA is required |
| `/2fa disable <code>` | Disable authenticator 2FA |
| `/zauth status` | Show authenticated count |
| `/zauth unregister <player>` | Delete a user's login |
| `/zauth setpassword <player> <password>` | Change a user's password |
| `/zauth disable2fa <player>` | Remove a user's authenticator |
| `/zauth forcelogin <player>` | Mark a user logged in |
| `/zauth logout <player>` | Mark a user logged out |

## Proxy Config

The proxy config is intentionally small:

```yml
general:
  login-timeout: 0
  register-timeout: 0

prompts:
  bossbar: true
  actionbar-fallback: true

session:
  enabled: true

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

Existing proxy configs auto-merge missing default keys on startup, so new options are added without wiping your values.

## Backend Config

Backend config is deliberately tiny:

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

The backend jar never connects to the database.
Bypass is disabled by default, even for operators. Set `enable-bypass-permission: true` only if you intentionally want `zcraftauth.backend.bypass` to skip backend protection.

## Notes

- Java 21 is required.
- Velocity and BungeeCord are supported by the same proxy jar.
- Dedicated backend jars are produced for Spigot legacy, Spigot modern, Paper/Purpur, and Folia families.
- bStats runs from the backend plugin because the project is registered for Bukkit/Paper. Project id: `31667`.
- Same-IP player limits are disabled by default.

## License

This project is distributed under CC-BY-NC-SA 4.0. See [LICENSE](LICENSE).

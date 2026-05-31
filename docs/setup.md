# Auth Setup

Auth builds two plugin jars.

## Proxy Jar

Install `Auth-Proxy-<version>.jar` on either Velocity or BungeeCord. This jar owns:

- `/login` and `/register`
- Password hashing
- SQLite or external SQL storage
- Login/register timeout
- Sending auth state to backend servers

The proxy jar is the only jar that reads the main auth database. If the proxy cannot initialize the selected database, players are disconnected with an auth-not-ready message and the proxy console logs the config/database error.

## Backend Jar

Install `Auth-Backend-<version>.jar` on every Paper/Purpur backend server. This jar owns:

- Blocking movement/chat/commands until the proxy confirms login
- Auto-detecting the proxy through `zcraftauth:state`
- bStats metrics for the Bukkit/Paper side

The backend jar does not connect to the database.

Because the bStats project is registered as Bukkit/Paper, metrics run from this backend jar rather than from the proxy jar.

## Database

Use SQLite by default:

```yml
database:
  type: sqlite
```

For an external database, set `type` to `mysql`, `mariadb`, or `postgresql`, then fill the `external` block.

## Timeouts

Set either timeout to `0` to disable it:

```yml
general:
  login-timeout: 0
  register-timeout: 0
```

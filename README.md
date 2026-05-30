# Auth

Professional Minecraft authentication plugin for Paper 1.21.1+ and the wider 1.21.x line.

Originally built as a private ZCraft Studios network plugin, now documented for cleaner setup, easier config editing, and smoother server operations.

## Highlights

- Password login, register, logout, and password change
- Email verification and recovery
- TOTP 2FA support
- Session restore and premium auto-login
- Anti-bot protection
- GeoIP country restrictions
- Username spoofing protection
- Inventory protection while unauthenticated
- SQLite, MySQL, MariaDB, and PostgreSQL support
- Manual and automatic backups
- Account import tools
- Public API and custom events
- MiniMessage support in config and language files
- Feature toggles for major modules and commands

## Install

1. Build or copy `Auth-1.0.0.jar` into your `plugins/` folder.
2. Start the server once to generate `plugins/Auth/`.
3. Edit `config.yml`.
4. Run `/zauth reload` as an admin.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/login <password>` | Log in | `zcraftauth.login` |
| `/register <password> <confirm>` | Create account | `zcraftauth.register` |
| `/logout` | Log out | `zcraftauth.logout` |
| `/changepass <old> <new>` | Change password | `zcraftauth.changepassword` |
| `/email add <address>` | Register email | `zcraftauth.email` |
| `/email change <address>` | Change email | `zcraftauth.email` |
| `/email verify <code>` | Confirm email | `zcraftauth.email` |
| `/email recover` | Send temp password | `zcraftauth.email` |
| `/2fa enable` | Enable TOTP 2FA | `zcraftauth.2fa` |
| `/2fa disable <code>` | Disable TOTP 2FA | `zcraftauth.2fa` |
| `/2fa verify <code>` | Enter 2FA code | `zcraftauth.2fa` |
| `/forcelogin <player>` | Force login a player | `zcraftauth.forcelogin` |
| `/zauth reload` | Reload config and languages | `zcraftauth.admin` |
| `/zauth backup` | Manual database backup | `zcraftauth.admin` |
| `/zauth import` | Import accounts | `zcraftauth.admin` |
| `/zauth unregister <player>` | Delete account | `zcraftauth.admin` |
| `/zauth restrict <player> [ip]` | Lock account to IP | `zcraftauth.admin` |
| `/zauth unrestrict <player>` | Remove IP lock | `zcraftauth.admin` |
| `/zauth accounts` | Show stats | `zcraftauth.admin` |
| `/zauth antibot` | Show AntiBot status | `zcraftauth.admin` |

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `zcraftauth.login` | `true` | Use `/login` |
| `zcraftauth.register` | `true` | Use `/register` |
| `zcraftauth.logout` | `true` | Use `/logout` |
| `zcraftauth.changepassword` | `true` | Use `/changepass` |
| `zcraftauth.email` | `true` | Use `/email` |
| `zcraftauth.2fa` | `true` | Use `/2fa` |
| `zcraftauth.forcelogin` | `op` | Use `/forcelogin` |
| `zcraftauth.admin` | `op` | Admin commands and reloads |
| `zcraftauth.bypass.antibot` | `op` | Skip AntiBot checks |
| `zcraftauth.bypass.country` | `op` | Skip country restrictions |
| `zcraftauth.bypass.iplock` | `op` | Skip IP-lock checks |
| `zcraftauth.premium.bypass` | `false` | Force premium treatment |

## Config

`config.yml` contains feature toggles for the major modules:

- Player commands
- Session restore
- Premium auto-login
- AntiBot
- Country restrictions
- Spoofing protection
- Inventory protection
- Backups
- Discord logging
- Cache
- Legacy hash migration
- GUI prompts

All message fields support MiniMessage tags.

## License And Attribution

This project is distributed under CC-BY-NC-SA 4.0.

- Keep `LICENSE` in redistributed copies.
- Preserve the attribution in `plugin.yml`.
- Link the original repository: `https://github.com/zraxgaming/ffa-plugin`.
- If you fork or modify the project, clearly mark your changes in the README.
- Include the source attribution text from the license file in any modified source distribution.

License text: [LICENSE](LICENSE)

## Notes

- Existing `plugins/ZCraftAuth/` data is migrated into `plugins/Auth/` on first start.
- The admin command is `/zauth`; player commands are generic and non-branded.
- The plugin keeps existing permission nodes for compatibility with older configs and integrations.

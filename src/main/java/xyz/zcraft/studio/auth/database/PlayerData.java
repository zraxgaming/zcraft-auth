package xyz.zcraft.studio.auth.database;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable data-transfer object for a registered player account.
 * Use {@link Builder} to construct or update instances.
 */
public record PlayerData(
        UUID    uuid,
        String  username,
        String  passwordHash,
        String  email,
        boolean emailVerified,
        String  lastIp,
        Instant lastLogin,
        Instant registeredAt,
        String  totpSecret,
        String  sessionToken,
        Instant sessionExpiry,
        boolean premium,
        String  countryCode,
        boolean restricted,
        String  restrictedIp,
        boolean emailPending,
        String  emailPendingToken,
        Instant emailPendingExpiry
) {

    /** Convenience: whether this account has 2FA set up. */
    public boolean has2FA() { return totpSecret != null && !totpSecret.isBlank(); }

    /** Convenience: whether this account has an email set and verified. */
    public boolean hasVerifiedEmail() { return email != null && !email.isBlank() && emailVerified; }

    /** Returns a mutable builder pre-populated from this record. */
    public Builder toBuilder() { return new Builder(this); }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder(UUID uuid, String username) {
        return new Builder(uuid, username);
    }

    public static final class Builder {
        private UUID    uuid;
        private String  username;
        private String  passwordHash;
        private String  email;
        private boolean emailVerified;
        private String  lastIp;
        private Instant lastLogin;
        private Instant registeredAt   = Instant.now();
        private String  totpSecret;
        private String  sessionToken;
        private Instant sessionExpiry;
        private boolean premium;
        private String  countryCode;
        private boolean restricted;
        private String  restrictedIp;
        private boolean emailPending;
        private String  emailPendingToken;
        private Instant emailPendingExpiry;

        private Builder(UUID uuid, String username) {
            this.uuid = uuid;
            this.username = username;
        }

        private Builder(PlayerData src) {
            this.uuid               = src.uuid;
            this.username           = src.username;
            this.passwordHash       = src.passwordHash;
            this.email              = src.email;
            this.emailVerified      = src.emailVerified;
            this.lastIp             = src.lastIp;
            this.lastLogin          = src.lastLogin;
            this.registeredAt       = src.registeredAt;
            this.totpSecret         = src.totpSecret;
            this.sessionToken       = src.sessionToken;
            this.sessionExpiry      = src.sessionExpiry;
            this.premium            = src.premium;
            this.countryCode        = src.countryCode;
            this.restricted         = src.restricted;
            this.restrictedIp       = src.restrictedIp;
            this.emailPending       = src.emailPending;
            this.emailPendingToken  = src.emailPendingToken;
            this.emailPendingExpiry = src.emailPendingExpiry;
        }

        public Builder passwordHash(String h)            { this.passwordHash = h;             return this; }
        public Builder email(String e)                   { this.email = e;                    return this; }
        public Builder emailVerified(boolean v)          { this.emailVerified = v;             return this; }
        public Builder lastIp(String ip)                 { this.lastIp = ip;                  return this; }
        public Builder lastLogin(Instant t)              { this.lastLogin = t;                return this; }
        public Builder registeredAt(Instant t)           { this.registeredAt = t;             return this; }
        public Builder totpSecret(String s)              { this.totpSecret = s;               return this; }
        public Builder sessionToken(String t)            { this.sessionToken = t;             return this; }
        public Builder sessionExpiry(Instant t)          { this.sessionExpiry = t;            return this; }
        public Builder premium(boolean p)                { this.premium = p;                  return this; }
        public Builder countryCode(String c)             { this.countryCode = c;              return this; }
        public Builder restricted(boolean r)             { this.restricted = r;               return this; }
        public Builder restrictedIp(String ip)           { this.restrictedIp = ip;            return this; }
        public Builder emailPending(boolean ep)          { this.emailPending = ep;            return this; }
        public Builder emailPendingToken(String t)       { this.emailPendingToken = t;        return this; }
        public Builder emailPendingExpiry(Instant t)     { this.emailPendingExpiry = t;       return this; }

        public PlayerData build() {
            return new PlayerData(uuid, username, passwordHash, email, emailVerified,
                    lastIp, lastLogin, registeredAt, totpSecret, sessionToken, sessionExpiry,
                    premium, countryCode, restricted, restrictedIp,
                    emailPending, emailPendingToken, emailPendingExpiry);
        }
    }
}

package xyz.zcraft.studio.auth.email;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.auth.PasswordEngine;
import xyz.zcraft.studio.auth.database.PlayerData;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Handles all email operations:
 * - Address verification (confirmation token)
 * - Account password recovery
 * - SMTP rate limiting per IP
 */
public class EmailManager {

    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private final ZCraftAuth plugin;
    private final SecureRandom rng = new SecureRandom();

    // IP -> [attempt_count, window_start_ms]
    private final Map<String, long[]> rateLimits = new ConcurrentHashMap<>();

    public EmailManager(ZCraftAuth plugin) { this.plugin = plugin; }

    // ─── Validation ───────────────────────────────────────────────────────────

    public boolean isValidEmail(String email) {
        return email != null && EMAIL_REGEX.matcher(email).matches();
    }

    // ─── Rate limiting ────────────────────────────────────────────────────────

    public boolean isRateLimited(String ip) {
        int max = plugin.getConfigManager().getEmailRateLimit();
        long now = System.currentTimeMillis();
        long[] state = rateLimits.computeIfAbsent(ip, k -> new long[]{0, now});
        // Reset window after 1 hour
        if (now - state[1] > 3_600_000) { state[0] = 0; state[1] = now; }
        if (state[0] >= max) return true;
        state[0]++;
        return false;
    }

    // ─── Token generation ─────────────────────────────────────────────────────

    /** Generate a 6-digit numeric confirmation code. */
    public String generateCode() {
        return String.format("%06d", rng.nextInt(1_000_000));
    }

    /** Generate a secure alphanumeric temporary password (12 chars). */
    public String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    // ─── Send: Verification ───────────────────────────────────────────────────

    /**
     * Send a verification email to confirm an email address change/add.
     * Stores the pending token in the database and sends the email asynchronously.
     */
    public CompletableFuture<Boolean> sendVerification(PlayerData data, String targetEmail, String code) {
        if (!plugin.getConfigManager().isEmailEnabled())
            return CompletableFuture.completedFuture(false);

        String subject = fillTemplate(
                plugin.getConfigManager().getEmailTemplate("verification", "subject"), data, code);
        String body = fillTemplate(
                plugin.getConfigManager().getEmailTemplate("verification", "body"), data, code);

        return sendAsync(targetEmail, subject, body);
    }

    // ─── Send: Recovery ───────────────────────────────────────────────────────

    /**
     * Send a password recovery email.
     * Generates a temp password, re-hashes it, updates the DB, then emails it.
     */
    public CompletableFuture<Boolean> sendRecovery(PlayerData data) {
        if (!plugin.getConfigManager().isEmailEnabled())
            return CompletableFuture.completedFuture(false);
        if (!data.hasVerifiedEmail())
            return CompletableFuture.completedFuture(false);

        String tempPw = generateTempPassword();
        PasswordEngine engine = new PasswordEngine(plugin);
        String newHash = engine.hash(tempPw);

        PlayerData updated = data.toBuilder().passwordHash(newHash).build();
        plugin.getDatabase().updatePlayer(updated);

        String subject = fillTemplate(
                plugin.getConfigManager().getEmailTemplate("recovery", "subject"), data, tempPw);
        String body = fillTemplate(
                plugin.getConfigManager().getEmailTemplate("recovery", "body"), data, tempPw);

        return sendAsync(data.email(), subject, body);
    }

    // ─── SMTP ─────────────────────────────────────────────────────────────────

    private CompletableFuture<Boolean> sendAsync(String to, String subject, String body) {
        return CompletableFuture.supplyAsync(() -> {
            var cfg = plugin.getConfigManager();
            Properties props = new Properties();
            props.put("mail.smtp.host", cfg.getSmtpHost());
            props.put("mail.smtp.port", String.valueOf(cfg.getSmtpPort()));
            props.put("mail.smtp.auth", "true");
            if (cfg.isSmtpSSL()) {
                props.put("mail.smtp.ssl.enable", "true");
            }
            if (cfg.isSmtpStartTLS()) {
                props.put("mail.smtp.starttls.enable", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(cfg.getSmtpUsername(), cfg.getSmtpPassword());
                }
            });

            try {
                Message msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(cfg.getEmailFromAddress(), cfg.getEmailFromName()));
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
                msg.setSubject(subject);
                msg.setText(body);
                Transport.send(msg);
                plugin.getLogger().info("Email sent to " + to);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Email send failed: " + e.getMessage());
                return false;
            }
        });
    }

    // ─── Template filler ──────────────────────────────────────────────────────

    private String fillTemplate(String template, PlayerData data, String code) {
        int expiry = plugin.getConfigManager().getEmailTokenExpiry();
        return template
                .replace("{player}", data.username())
                .replace("{code}",   code)
                .replace("{email}",  data.email() != null ? data.email() : "")
                .replace("{expiry}", String.valueOf(expiry));
    }
}

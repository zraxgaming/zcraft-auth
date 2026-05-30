package xyz.zcraft.studio.auth.twofactor;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * TOTP-based two-factor authentication.
 * Uses dev.samstevens.totp library (RFC 6238 compliant).
 */
public class TwoFactorManager {

    private final ZCraftAuth      plugin;
    private final SecretGenerator secretGen;
    private final CodeGenerator   codeGen;
    private final CodeVerifier    verifier;

    public TwoFactorManager(ZCraftAuth plugin) {
        this.plugin    = plugin;
        this.secretGen = new DefaultSecretGenerator(32);
        this.codeGen   = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        int window     = plugin.getConfigManager().getTotpWindow();
        this.verifier  = new DefaultCodeVerifier(codeGen, new SystemTimeProvider());
        ((DefaultCodeVerifier) this.verifier).setTimePeriod(30);
        ((DefaultCodeVerifier) this.verifier).setAllowedTimePeriodDiscrepancy(window);
    }

    /** Generate a new random TOTP secret. */
    public String generateSecret() {
        return secretGen.generate();
    }

    /**
     * Verify a 6-digit TOTP code against a stored secret.
     * Accepts codes within the configured time window.
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        return verifier.isValidCode(secret, code.replace(" ", ""));
    }

    /**
     * Build a Google Authenticator-compatible QR code data URI.
     * @return base64 PNG data URI, or null on failure
     */
    public String generateQrDataUri(String username, String secret) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(plugin.getConfigManager().getTotpIssuer())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            QrGenerator gen  = new ZxingPngQrGenerator();
            byte[]      png  = gen.generate(data);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png);
        } catch (QrGenerationException e) {
            plugin.getLogger().log(Level.WARNING, "QR generation failed", e);
            return null;
        }
    }

    /**
     * Generate an otpauth:// URL for manual entry in authenticator apps.
     */
    public String generateOtpAuthUrl(String username, String secret) {
        String issuer = plugin.getConfigManager().getTotpIssuer();
        String label  = URLEncoder.encode(issuer + ":" + username, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=6&period=30";
    }
}

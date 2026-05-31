package xyz.zcraft.studio.auth.proxy;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class PasswordHasher {

    public String hash(String password) {
        return "$BCRYPT$" + BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }

    public boolean verify(String password, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String hash = storedHash.startsWith("$BCRYPT$") ? storedHash.substring(8) : storedHash;
        try {
            return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
        } catch (Exception ex) {
            return false;
        }
    }
}

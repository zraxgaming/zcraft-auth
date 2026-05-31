package xyz.zcraft.studio.auth.proxy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class AuthStateMessages {

    public static final String CHANNEL = "zcraftauth:state";
    public static final String AUTH_STATE = "AUTH_STATE";
    public static final String BACKEND_HELLO = "BACKEND_HELLO";

    private AuthStateMessages() {
    }

    public static byte[] authState(UUID uuid, boolean loggedIn) {
        return message(AUTH_STATE, uuid, loggedIn);
    }

    public static byte[] backendHello(UUID uuid) {
        return message(BACKEND_HELLO, uuid, false);
    }

    private static byte[] message(String action, UUID uuid, boolean loggedIn) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(action);
            out.writeUTF(uuid.toString());
            out.writeBoolean(loggedIn);
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write auth state message", ex);
        }
    }
}

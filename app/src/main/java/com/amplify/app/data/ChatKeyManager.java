package com.amplify.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Looks after the user's message-encryption keys on this phone.
 *
 *  - The key pair lives on the phone, locked with a key held by the Android Keystore.
 *  - The PUBLIC key is also saved in Supabase (user_keys) so others can write to you.
 *  - A copy of the key pair LOCKED WITH THE ACCOUNT PASSWORD is saved in Supabase
 *    (key_backups) so a new phone can get the keys back. Nobody can open that copy
 *    without the password.
 *
 * Call prepareAfterLogin(...) once, right after a successful login. It needs the
 * password the user just typed, because the password is the lock on the backup.
 *
 * Later code reads the keys with getKeyPair(context).
 * SessionManager.clear() (logout) calls clearLocal(), so keys never stay on a
 * phone after logging out. They come back from the backup on the next login.
 */
public final class ChatKeyManager {

    private static final String TAG = "AmplifyKeys";
    private static final String PREFS = "amplify_keys";
    private static final String PREF_USER = "user_id";
    private static final String PREF_PAIR = "key_pair";
    private static final String KEYSTORE_ALIAS = "amplify_chat_wrap_key";
    private static final String UPSERT = "resolution=merge-duplicates,return=minimal";

    private ChatKeyManager() {}

    /** All three methods are called on the main (UI) thread. */
    public interface Listener {
        /** Keys are ready, messaging can be used. */
        void onReady();

        /**
         * The old backup could not be opened with this password (usually after a password
         * reset). Ask the user. Run startFresh to create new keys (old messages become
         * unreadable) or skip to leave secure messaging off for now.
         */
        void onNeedsFreshStart(Runnable startFresh, Runnable skip);

        /** Something went wrong (for example no internet). Messaging stays off until next login. */
        void onFailed(String message);
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile KeyPair cachedPair;
    private static volatile String cachedUserId;

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Call right after a successful login (token already saved with SessionManager.saveSession). */
    public static void prepareAfterLogin(Context context, String password, Listener listener) {
        final Context app = context.getApplicationContext();
        final char[] pw = password.toCharArray();
        WORKER.execute(() -> {
            try {
                run(app, pw, listener);
            } catch (Exception e) {
                Log.e(TAG, "Key setup failed: " + e);
                postFailed(listener, "Could not set up secure messaging. Please log in again.");
            }
        });
    }

    /** The signed-in user's keys, or null when secure messaging is not set up on this phone. */
    public static KeyPair getKeyPair(Context context) {
        Context app = context.getApplicationContext();
        String userId = SessionManager.getUserId(app);
        if (userId == null) return null;
        KeyPair cached = cachedPair;
        if (cached != null && userId.equals(cachedUserId)) return cached;
        return loadLocal(app, userId);
    }

    public static boolean hasKeys(Context context) {
        return getKeyPair(context) != null;
    }

    /** Removes the keys from this phone (they stay safe in the password-locked backup). */
    public static void clearLocal(Context context) {
        prefs(context.getApplicationContext()).edit().clear().commit();
        cachedPair = null;
        cachedUserId = null;
    }

    // ------------------------------------------------------------------
    // The login-time flow (runs on a background thread)
    // ------------------------------------------------------------------
    private static void run(Context ctx, char[] password, Listener listener) throws Exception {
        String userId = SessionManager.getUserId(ctx);
        if (userId == null) {
            postFailed(listener, "You are not signed in. Please log in again.");
            return;
        }
        SupabaseApi api = new SupabaseApi(ctx);

        // 1) Keys already on this phone?
        KeyPair local = loadLocal(ctx, userId);
        if (local != null) {
            String serverPublic;
            try {
                serverPublic = fetchServerPublicKey(api, userId);
            } catch (Exception offline) {
                // Can't check right now: keep using the keys on this phone.
                postReady(listener);
                return;
            }
            String localPublic = ChatCrypto.publicKeyToString(local.getPublic());
            if (serverPublic == null || serverPublic.equals(localPublic)) {
                postReady(listener);
                // Quietly keep Supabase up to date. This also re-locks the backup with the
                // password used today, so a password change never strands the backup.
                try {
                    upload(api, userId, local, password);
                } catch (Exception e) {
                    Log.w(TAG, "Could not refresh the backup: " + e);
                }
                return;
            }
            // Supabase holds different keys: another phone started fresh. Ours are out of date.
            Log.w(TAG, "Keys on this phone are out of date, restoring from the backup.");
            clearLocal(ctx);
        }

        // 2) No usable keys here: look for a backup.
        String backupText;
        try {
            backupText = api.requestBlocking("GET",
                    "/rest/v1/key_backups?select=encrypted_key,salt,iterations&user_id=eq." + userId,
                    null, null);
        } catch (SupabaseApi.ApiException e) {
            postFailed(listener, e.getMessage());
            return;
        }
        JSONArray rows = new JSONArray(backupText);

        if (rows.length() == 0) {
            // First time ever for this account.
            createFresh(ctx, api, userId, password, listener);
            return;
        }

        JSONObject row = rows.getJSONObject(0);
        ChatCrypto.LockedKey locked = new ChatCrypto.LockedKey(
                row.getString("encrypted_key"), row.getString("salt"), row.getInt("iterations"));
        KeyPair restored;
        try {
            restored = ChatCrypto.unlockKeyPair(locked, password, userId);
        } catch (GeneralSecurityException cannotOpen) {
            Log.w(TAG, "Backup could not be opened with this password: " + cannotOpen);
            MAIN.post(() -> listener.onNeedsFreshStart(
                    () -> WORKER.execute(() -> createFreshSafely(ctx, api, userId, password, listener)),
                    () -> postFailed(listener,
                            "Secure messaging is off. Log in again to set it up.")));
            return;
        }

        saveLocal(ctx, userId, restored);
        postReady(listener);
        try {
            // Makes sure Supabase lists this public key too (repairs a half-finished setup).
            upload(api, userId, restored, password);
        } catch (Exception e) {
            Log.w(TAG, "Could not refresh the backup: " + e);
        }
    }

    private static void createFreshSafely(Context ctx, SupabaseApi api, String userId,
                                          char[] password, Listener listener) {
        try {
            createFresh(ctx, api, userId, password, listener);
        } catch (Exception e) {
            Log.e(TAG, "Could not create keys: " + e);
            postFailed(listener, "Could not create your message keys. Please try again.");
        }
    }

    /** New keys: saved in Supabase first, and only then on the phone, so a failure leaves nothing half-done. */
    private static void createFresh(Context ctx, SupabaseApi api, String userId,
                                    char[] password, Listener listener) throws Exception {
        KeyPair pair = ChatCrypto.generateKeyPair();
        try {
            upload(api, userId, pair, password);
        } catch (SupabaseApi.ApiException e) {
            postFailed(listener, e.getMessage());
            return;
        }
        saveLocal(ctx, userId, pair);
        postReady(listener);
    }

    /** Saves the public key and the password-locked backup in Supabase (insert or replace). */
    private static void upload(SupabaseApi api, String userId, KeyPair pair, char[] password)
            throws Exception {
        ChatCrypto.LockedKey locked = ChatCrypto.lockKeyPair(pair, password, userId);
        String now = nowIso();

        JSONObject backup = new JSONObject();
        backup.put("user_id", userId);
        backup.put("encrypted_key", locked.encryptedKey);
        backup.put("salt", locked.salt);
        backup.put("iterations", locked.iterations);
        backup.put("updated_at", now);
        api.requestBlocking("POST", "/rest/v1/key_backups?on_conflict=user_id",
                backup.toString(), UPSERT);

        JSONObject publicKey = new JSONObject();
        publicKey.put("user_id", userId);
        publicKey.put("public_key", ChatCrypto.publicKeyToString(pair.getPublic()));
        publicKey.put("updated_at", now);
        api.requestBlocking("POST", "/rest/v1/user_keys?on_conflict=user_id",
                publicKey.toString(), UPSERT);
    }

    /** Returns the public key Supabase has for this user, or null if there is none yet. */
    private static String fetchServerPublicKey(SupabaseApi api, String userId) throws Exception {
        String body = api.requestBlocking("GET",
                "/rest/v1/user_keys?select=public_key&user_id=eq." + userId, null, null);
        JSONArray rows = new JSONArray(body);
        return rows.length() == 0 ? null : rows.getJSONObject(0).getString("public_key");
    }

    // ------------------------------------------------------------------
    // Keys stored on the phone
    // ------------------------------------------------------------------
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void saveLocal(Context ctx, String userId, KeyPair pair) throws Exception {
        byte[] plain = ChatCrypto.keyPairToBytes(pair);
        try {
            String wrapped = wrapWithKeystore(plain);
            prefs(ctx).edit()
                    .putString(PREF_USER, userId)
                    .putString(PREF_PAIR, wrapped)
                    .commit();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
        cachedPair = pair;
        cachedUserId = userId;
    }

    /** Returns this user's keys from the phone, or null (and cleans up) if there are none usable. */
    private static KeyPair loadLocal(Context ctx, String userId) {
        SharedPreferences p = prefs(ctx);
        String storedUser = p.getString(PREF_USER, null);
        String wrapped = p.getString(PREF_PAIR, null);
        if (storedUser == null || wrapped == null) return null;

        if (!storedUser.equals(userId)) {
            // Keys of somebody who used this phone before: never reuse them.
            clearLocal(ctx);
            return null;
        }
        try {
            byte[] plain = unwrapWithKeystore(wrapped);
            try {
                KeyPair pair = ChatCrypto.keyPairFromBytes(plain);
                cachedPair = pair;
                cachedUserId = userId;
                return pair;
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (Exception e) {
            // For example the phone was restored from a backup and the Keystore key is gone.
            Log.w(TAG, "Saved keys could not be read, will restore from the backup: " + e);
            clearLocal(ctx);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Android Keystore: locks the stored key pair with a key that never leaves the chip
    // ------------------------------------------------------------------
    private static SecretKey keystoreKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static String wrapWithKeystore(byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey());
        byte[] iv = cipher.getIV();
        byte[] cipherText = cipher.doFinal(plain);
        byte[] out = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
        return ChatCrypto.toBase64(out);
    }

    private static byte[] unwrapWithKeystore(String wrapped) throws Exception {
        byte[] in = ChatCrypto.fromBase64(wrapped);
        if (in.length < 12 + 16) {
            throw new GeneralSecurityException("Stored key is too short");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(),
                new GCMParameterSpec(128, Arrays.copyOfRange(in, 0, 12)));
        return cipher.doFinal(in, 12, in.length - 12);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------
    private static void postReady(Listener listener) {
        MAIN.post(listener::onReady);
    }

    private static void postFailed(Listener listener, String message) {
        MAIN.post(() -> listener.onFailed(message));
    }

    private static String nowIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}

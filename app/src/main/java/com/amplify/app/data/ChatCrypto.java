package com.amplify.app.data;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * All the encryption for private messages lives in this one class.
 * It uses only the crypto that ships with Java/Android, and it has NO Android
 * imports, so it can also be tested on a normal computer.
 *
 * How a message is protected:
 *  - Every user has a key PAIR (P-256 curve). The public half is shared through
 *    Supabase (table user_keys). The private half never leaves the phone,
 *    except in a copy locked with the user's password (table key_backups).
 *  - To send a message, the phone makes a throw-away key pair, mixes it with the
 *    receiver's PUBLIC key (ECDH), turns the result into an AES-256 key (HKDF)
 *    and encrypts the text with AES-GCM. Only the receiver's PRIVATE key can
 *    redo that mix, so only the receiver can read it.
 *  - The same is done once more for the sender's own public key, so you can
 *    still read the messages you sent.
 *
 * The "aad" text (we use conversationId + ":" + senderId) is stitched into the
 * encryption. If someone copies a message into another chat, or pretends it was
 * sent by somebody else, decrypting fails.
 */
public final class ChatCrypto {

    private ChatCrypto() {}

    private static final int VERSION = 1;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_TAG_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int SALT_BYTES = 16;

    /** How many times the password is scrambled before it becomes a key. Slow on purpose. */
    public static final int BACKUP_ITERATIONS = 600_000;
    private static final int MIN_ITERATIONS = 10_000;
    private static final int MAX_ITERATIONS = 5_000_000;

    private static final byte[] MESSAGE_INFO = "amplify-dm-v1".getBytes(StandardCharsets.UTF_8);
    private static final String BACKUP_AAD_PREFIX = "amplify-key-backup-v1|";

    // The numbers that define the P-256 curve. Used to reject keys on any other curve.
    private static final BigInteger P256_P = new BigInteger(
            "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
    private static final BigInteger P256_A = P256_P.subtract(BigInteger.valueOf(3));
    private static final BigInteger P256_B = new BigInteger(
            "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);

    private static final SecureRandom RANDOM = new SecureRandom();

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    /** Makes a brand new key pair for a user. */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    /** Public key as text, ready to store in Supabase (user_keys.public_key). */
    public static String publicKeyToString(PublicKey key) {
        return toBase64(key.getEncoded());
    }

    public static PublicKey publicKeyFromString(String text) throws GeneralSecurityException {
        try {
            return publicKeyFromBytes(fromBase64(text));
        } catch (IllegalArgumentException e) {
            throw new InvalidKeyException("Bad public key text", e);
        }
    }

    /**
     * Turns bytes into a public key AND checks it really is a point on the P-256 curve.
     * A key from the server is never trusted without this check.
     */
    public static PublicKey publicKeyFromBytes(byte[] encoded) throws GeneralSecurityException {
        PublicKey key;
        try {
            key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (RuntimeException e) {
            throw new InvalidKeyException("Bad public key", e);
        }
        if (!(key instanceof ECPublicKey)) {
            throw new InvalidKeyException("Not an EC public key");
        }
        ECPublicKey ec = (ECPublicKey) key;
        EllipticCurve curve = ec.getParams().getCurve();
        if (!(curve.getField() instanceof ECFieldFp)) {
            throw new InvalidKeyException("Unsupported curve");
        }
        BigInteger p = ((ECFieldFp) curve.getField()).getP();
        if (!p.equals(P256_P)
                || !curve.getA().mod(p).equals(P256_A)
                || !curve.getB().mod(p).equals(P256_B)) {
            throw new InvalidKeyException("Unsupported curve");
        }
        ECPoint w = ec.getW();
        if (ECPoint.POINT_INFINITY.equals(w)) {
            throw new InvalidKeyException("Bad point");
        }
        BigInteger x = w.getAffineX();
        BigInteger y = w.getAffineY();
        if (x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0) {
            throw new InvalidKeyException("Bad point");
        }
        BigInteger left = y.multiply(y).mod(p);
        BigInteger right = x.multiply(x).multiply(x)
                .add(P256_A.multiply(x)).add(P256_B).mod(p);
        if (!left.equals(right)) {
            throw new InvalidKeyException("Public key is not on the curve");
        }
        return key;
    }

    /** Both halves of a key pair in one byte array (used for storage and for the backup). */
    public static byte[] keyPairToBytes(KeyPair pair) {
        byte[] pub = pair.getPublic().getEncoded();
        byte[] priv = pair.getPrivate().getEncoded();
        ByteBuffer buf = ByteBuffer.allocate(2 + pub.length + priv.length);
        buf.putShort((short) pub.length).put(pub).put(priv);
        Arrays.fill(priv, (byte) 0);
        return buf.array();
    }

    public static KeyPair keyPairFromBytes(byte[] data) throws GeneralSecurityException {
        if (data == null || data.length < 3) {
            throw new InvalidKeyException("Bad key data");
        }
        int pubLen = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
        if (pubLen < 1 || 2 + pubLen >= data.length) {
            throw new InvalidKeyException("Bad key data");
        }
        byte[] pubBytes = Arrays.copyOfRange(data, 2, 2 + pubLen);
        byte[] privBytes = Arrays.copyOfRange(data, 2 + pubLen, data.length);
        try {
            PublicKey pub = publicKeyFromBytes(pubBytes);
            PrivateKey priv = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            return new KeyPair(pub, priv);
        } catch (RuntimeException e) {
            throw new InvalidKeyException("Bad key data", e);
        } finally {
            Arrays.fill(privBytes, (byte) 0);
        }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    /**
     * Encrypts text so that only the owner of "recipient"'s private key can read it.
     * Returns text that is safe to store in Supabase.
     */
    public static String encryptFor(String plaintext, PublicKey recipient, String aad)
            throws GeneralSecurityException {
        KeyPair throwAway = generateKeyPair();
        byte[] throwAwayPub = throwAway.getPublic().getEncoded();
        byte[] shared = ecdh(throwAway.getPrivate(), recipient);
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        byte[] key = hkdfSha256(shared, salt, concat(MESSAGE_INFO, throwAwayPub), 32);
        try {
            byte[] cipherText = aesGcm(Cipher.ENCRYPT_MODE, key, iv, aadBytes(aad),
                    plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer out = ByteBuffer.allocate(
                    1 + 2 + throwAwayPub.length + SALT_BYTES + IV_BYTES + cipherText.length);
            out.put((byte) VERSION)
                    .putShort((short) throwAwayPub.length)
                    .put(throwAwayPub)
                    .put(salt)
                    .put(iv)
                    .put(cipherText);
            return toBase64(out.array());
        } finally {
            wipe(shared);
            wipe(key);
        }
    }

    /**
     * Reads a message made by encryptFor(). Throws GeneralSecurityException if the
     * message was changed, is not meant for this key, or "aad" does not match.
     */
    public static String decrypt(String encoded, PrivateKey mine, String aad)
            throws GeneralSecurityException {
        byte[] data;
        try {
            data = fromBase64(encoded);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Malformed message", e);
        }
        int minimum = 1 + 2 + SALT_BYTES + IV_BYTES + GCM_TAG_BYTES;
        if (data.length < minimum || data[0] != VERSION) {
            throw new GeneralSecurityException("Unsupported message");
        }
        int pubLen = ((data[1] & 0xff) << 8) | (data[2] & 0xff);
        int pos = 3;
        if (pubLen < 1 || pos + pubLen + SALT_BYTES + IV_BYTES + GCM_TAG_BYTES > data.length) {
            throw new GeneralSecurityException("Malformed message");
        }
        byte[] throwAwayPub = Arrays.copyOfRange(data, pos, pos + pubLen);
        pos += pubLen;
        byte[] salt = Arrays.copyOfRange(data, pos, pos + SALT_BYTES);
        pos += SALT_BYTES;
        byte[] iv = Arrays.copyOfRange(data, pos, pos + IV_BYTES);
        pos += IV_BYTES;
        byte[] cipherText = Arrays.copyOfRange(data, pos, data.length);

        PublicKey throwAwayKey = publicKeyFromBytes(throwAwayPub);
        byte[] shared = ecdh(mine, throwAwayKey);
        byte[] key = hkdfSha256(shared, salt, concat(MESSAGE_INFO, throwAwayPub), 32);
        try {
            byte[] plain = aesGcm(Cipher.DECRYPT_MODE, key, iv, aadBytes(aad), cipherText);
            try {
                return new String(plain, StandardCharsets.UTF_8);
            } finally {
                wipe(plain);
            }
        } finally {
            wipe(shared);
            wipe(key);
        }
    }

    // ------------------------------------------------------------------
    // Password-locked backup of the key pair (stored in key_backups)
    // ------------------------------------------------------------------

    /** The three values that go into the key_backups table. */
    public static final class LockedKey {
        public final String encryptedKey;
        public final String salt;
        public final int iterations;

        public LockedKey(String encryptedKey, String salt, int iterations) {
            this.encryptedKey = encryptedKey;
            this.salt = salt;
            this.iterations = iterations;
        }
    }

    /** Locks the key pair with the user's password. The userId is stitched in so a backup can't be moved to another account. */
    public static LockedKey lockKeyPair(KeyPair pair, char[] password, String userId)
            throws GeneralSecurityException {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        byte[] passwordBytes = passwordToBytes(password);
        byte[] key = pbkdf2HmacSha256(passwordBytes, salt, BACKUP_ITERATIONS, 32);
        byte[] payload = keyPairToBytes(pair);
        try {
            byte[] cipherText = aesGcm(Cipher.ENCRYPT_MODE, key, iv,
                    aadBytes(BACKUP_AAD_PREFIX + userId), payload);
            ByteBuffer out = ByteBuffer.allocate(1 + IV_BYTES + cipherText.length);
            out.put((byte) VERSION).put(iv).put(cipherText);
            return new LockedKey(toBase64(out.array()), toBase64(salt), BACKUP_ITERATIONS);
        } finally {
            wipe(passwordBytes);
            wipe(key);
            wipe(payload);
        }
    }

    /** Opens a backup. Throws GeneralSecurityException when the password is wrong. */
    public static KeyPair unlockKeyPair(LockedKey locked, char[] password, String userId)
            throws GeneralSecurityException {
        if (locked.iterations < MIN_ITERATIONS || locked.iterations > MAX_ITERATIONS) {
            throw new GeneralSecurityException("Unsupported backup settings");
        }
        byte[] data;
        byte[] salt;
        try {
            data = fromBase64(locked.encryptedKey);
            salt = fromBase64(locked.salt);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Malformed backup", e);
        }
        if (data.length < 1 + IV_BYTES + GCM_TAG_BYTES || data[0] != VERSION || salt.length == 0) {
            throw new GeneralSecurityException("Unsupported backup");
        }
        byte[] iv = Arrays.copyOfRange(data, 1, 1 + IV_BYTES);
        byte[] cipherText = Arrays.copyOfRange(data, 1 + IV_BYTES, data.length);

        byte[] passwordBytes = passwordToBytes(password);
        byte[] key = pbkdf2HmacSha256(passwordBytes, salt, locked.iterations, 32);
        byte[] payload = null;
        try {
            payload = aesGcm(Cipher.DECRYPT_MODE, key, iv,
                    aadBytes(BACKUP_AAD_PREFIX + userId), cipherText);
            return keyPairFromBytes(payload);
        } finally {
            wipe(passwordBytes);
            wipe(key);
            if (payload != null) wipe(payload);
        }
    }

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    private static byte[] ecdh(PrivateKey mine, PublicKey theirs) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(mine);
        agreement.doPhase(theirs, true);
        return agreement.generateSecret();
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] iv, byte[] aad, byte[] input)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        if (aad.length > 0) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(input);
    }

    /** HKDF (RFC 5869) with SHA-256. Only needs to produce up to 32 bytes here. */
    static byte[] hkdfSha256(byte[] inputKey, byte[] salt, byte[] info, int length)
            throws GeneralSecurityException {
        if (length < 1 || length > 32) {
            throw new IllegalArgumentException("length must be 1..32");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(inputKey);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        byte[] block = mac.doFinal();
        byte[] out = Arrays.copyOf(block, length);
        wipe(prk);
        wipe(block);
        return out;
    }

    /**
     * PBKDF2 (RFC 8018) with HMAC-SHA256, written out by hand on purpose.
     * Android's built-in version only exists from Android 8 and different Android
     * versions can treat non-English characters differently. This one always
     * uses UTF-8, so a password gives the same key on every phone.
     */
    static byte[] pbkdf2HmacSha256(byte[] password, byte[] salt, int iterations, int length)
            throws GeneralSecurityException {
        if (password.length == 0 || iterations < 1 || length < 1) {
            throw new IllegalArgumentException("Bad PBKDF2 input");
        }
        final int hashLength = 32;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(password, "HmacSHA256"));
        int blocks = (length + hashLength - 1) / hashLength;
        byte[] out = new byte[length];
        byte[] blockIndex = new byte[4];
        for (int i = 1; i <= blocks; i++) {
            blockIndex[0] = (byte) (i >>> 24);
            blockIndex[1] = (byte) (i >>> 16);
            blockIndex[2] = (byte) (i >>> 8);
            blockIndex[3] = (byte) i;
            mac.update(salt);
            mac.update(blockIndex);
            byte[] u = mac.doFinal();
            byte[] t = u.clone();
            for (int j = 1; j < iterations; j++) {
                u = mac.doFinal(u);
                for (int k = 0; k < hashLength; k++) {
                    t[k] ^= u[k];
                }
            }
            int offset = (i - 1) * hashLength;
            System.arraycopy(t, 0, out, offset, Math.min(hashLength, length - offset));
            wipe(u);
            wipe(t);
        }
        return out;
    }

    private static byte[] passwordToBytes(char[] password) {
        java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    private static byte[] aadBytes(String aad) {
        return aad == null ? new byte[0] : aad.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void wipe(byte[] bytes) {
        Arrays.fill(bytes, (byte) 0);
    }

    // ------------------------------------------------------------------
    // Base64 (written by hand because java.util.Base64 needs Android 8+)
    // ------------------------------------------------------------------
    private static final String B64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    public static String toBase64(byte[] in) {
        StringBuilder sb = new StringBuilder(((in.length + 2) / 3) * 4);
        for (int i = 0; i < in.length; i += 3) {
            int b0 = in[i] & 0xff;
            int b1 = i + 1 < in.length ? in[i + 1] & 0xff : 0;
            int b2 = i + 2 < in.length ? in[i + 2] & 0xff : 0;
            sb.append(B64.charAt(b0 >>> 2));
            sb.append(B64.charAt(((b0 & 3) << 4) | (b1 >>> 4)));
            sb.append(i + 1 < in.length ? B64.charAt(((b1 & 15) << 2) | (b2 >>> 6)) : '=');
            sb.append(i + 2 < in.length ? B64.charAt(b2 & 63) : '=');
        }
        return sb.toString();
    }

    /** Throws IllegalArgumentException if the text is not valid base64. */
    public static byte[] fromBase64(String s) {
        if (s == null || s.length() % 4 != 0) {
            throw new IllegalArgumentException("Bad base64 length");
        }
        if (s.isEmpty()) {
            return new byte[0];
        }
        int pad = s.endsWith("==") ? 2 : (s.endsWith("=") ? 1 : 0);
        int outLength = s.length() / 4 * 3 - pad;
        byte[] out = new byte[outLength];
        int o = 0;
        for (int i = 0; i < s.length(); i += 4) {
            boolean last = i + 4 == s.length();
            int c0 = b64Value(s.charAt(i));
            int c1 = b64Value(s.charAt(i + 1));
            int c2 = (last && pad == 2) ? 0 : b64Value(s.charAt(i + 2));
            int c3 = (last && pad >= 1) ? 0 : b64Value(s.charAt(i + 3));
            int n = (c0 << 18) | (c1 << 12) | (c2 << 6) | c3;
            if (o < outLength) out[o++] = (byte) (n >> 16);
            if (o < outLength) out[o++] = (byte) (n >> 8);
            if (o < outLength) out[o++] = (byte) n;
        }
        return out;
    }

    private static int b64Value(char c) {
        int v = B64.indexOf(c);
        if (v < 0) {
            throw new IllegalArgumentException("Bad base64 character");
        }
        return v;
    }
}

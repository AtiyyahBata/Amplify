package com.amplify.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.amplify.app.models.DirectMessage;
import com.amplify.app.models.MessagePreview;
import com.amplify.app.models.PersonResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.security.KeyPair;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Everything the private-message screens need from Supabase.
 *
 * Messages are encrypted here on the phone before they are sent and decrypted here after
 * they arrive. Supabase (and anyone looking at the dashboard) only ever sees scrambled text.
 *
 * Every method with a Callback answers on the main thread, so screens can update views
 * straight away.
 */
public class MessagingRepository {

    private static final String TAG = "AmplifyMessages";

    public static final String NO_KEYS_MESSAGE =
            "Secure messaging isn't set up on this phone. Log out and log in again.";

    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Other people's public keys, remembered for a few minutes so each send is quicker.
    private static final long KEY_CACHE_MS = 5 * 60 * 1000L;
    private static final Map<String, PublicKey> KEY_CACHE = new HashMap<>();
    private static final Map<String, Long> KEY_CACHE_TIME = new HashMap<>();

    private final Context context;
    private final SupabaseApi api;

    public MessagingRepository(Context context) {
        this.context = context.getApplicationContext();
        this.api = new SupabaseApi(this.context);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private interface Job<T> {
        T run() throws Exception;
    }

    private <T> void background(Job<T> job, SupabaseApi.Callback<T> callback) {
        POOL.execute(() -> {
            try {
                T result = job.run();
                MAIN.post(() -> callback.onSuccess(result));
            } catch (SupabaseApi.ApiException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected problem: " + e);
                MAIN.post(() -> callback.onError("Something went wrong. Please try again."));
            }
        });
    }

    /** JSON null comes back as the text "null" from optString, so this checks for it first. */
    static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        return o.optString(key, null);
    }

    private String myId() throws SupabaseApi.ApiException {
        String id = SessionManager.getUserId(context);
        if (id == null) {
            throw new SupabaseApi.ApiException(401, "You are not signed in. Please log in again.");
        }
        return id;
    }

    private KeyPair myKeys() throws SupabaseApi.ApiException {
        KeyPair pair = ChatKeyManager.getKeyPair(context);
        if (pair == null) throw new SupabaseApi.ApiException(0, NO_KEYS_MESSAGE);
        return pair;
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    // ------------------------------------------------------------------
    // Time helpers (public and static so they can be tested)
    // ------------------------------------------------------------------

    /**
     * Turns Supabase's time text, like "2026-10-14T10:05:03.123456+00:00", into milliseconds.
     * Returns 0 if it can't be read.
     */
    public static long parseTime(String iso) {
        if (iso == null || iso.length() < 19) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            String base = iso.substring(0, 19).replace(' ', 'T');
            long millis = f.parse(base).getTime();

            int i = 19;
            if (i < iso.length() && iso.charAt(i) == '.') {
                int start = ++i;
                while (i < iso.length() && Character.isDigit(iso.charAt(i))) i++;
                String frac = (iso.substring(start, i) + "000").substring(0, 3);
                millis += Integer.parseInt(frac);
            }

            if (i < iso.length() && (iso.charAt(i) == '+' || iso.charAt(i) == '-')) {
                int sign = iso.charAt(i) == '-' ? -1 : 1;
                String off = iso.substring(i + 1).replace(":", "");
                if (off.length() >= 2) {
                    int hours = Integer.parseInt(off.substring(0, 2));
                    int minutes = off.length() >= 4 ? Integer.parseInt(off.substring(2, 4)) : 0;
                    millis -= sign * (hours * 60L + minutes) * 60_000L;
                }
            }
            return millis;
        } catch (Exception e) {
            return 0;
        }
    }

    /** "now", "5m", "3h", "2d", or "14 Oct" for older chats. */
    public static String timeAgo(long then, long now) {
        if (then <= 0) return "";
        long diff = now - then;
        if (diff < 0) diff = 0;
        long minutes = diff / 60_000L;
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        if (days < 7) return days + "d";
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(then));
    }

    public static String clockTime(long millis) {
        if (millis <= 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    // ------------------------------------------------------------------
    // People search / starting a chat
    // ------------------------------------------------------------------

    public void searchPeople(String term, SupabaseApi.Callback<List<PersonResult>> callback) {
        background(() -> {
            String json = new JSONObject().put("term", term).toString();
            String body = api.requestBlocking("POST", "/rest/v1/rpc/search_people", json, null);
            JSONArray rows = new JSONArray(body);
            List<PersonResult> out = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                out.add(new PersonResult(str(r, "id"), str(r, "full_name"),
                        str(r, "field_of_study")));
            }
            return out;
        }, callback);
    }

    /** Finds the one chat between you and this person, or creates it. Returns its id. */
    public void startConversation(String otherUserId, SupabaseApi.Callback<String> callback) {
        background(() -> {
            String json = new JSONObject().put("other_user", otherUserId).toString();
            String body = api.requestBlocking("POST", "/rest/v1/rpc/start_conversation", json, null);
            return body.trim().replace("\"", "");
        }, callback);
    }

    // ------------------------------------------------------------------
    // The Messages list
    // ------------------------------------------------------------------

    private static class Row {
        MessagePreview preview;
        long sortKey;
    }

    public void loadConversations(SupabaseApi.Callback<List<MessagePreview>> callback) {
        background(() -> {
            String me = myId();
            KeyPair keys = myKeys();
            String body = api.requestBlocking("POST", "/rest/v1/rpc/my_conversations", "{}", null);
            JSONArray rows = new JSONArray(body);
            long now = System.currentTimeMillis();

            List<Row> list = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                String lastAt = str(r, "last_at");
                // A chat with no messages yet stays out of the list until someone writes.
                if (lastAt == null) continue;

                String convId = str(r, "conversation_id");
                String otherId = str(r, "other_user_id");
                String name = str(r, "other_name");
                if (name == null || name.trim().isEmpty()) name = "Unknown";

                String lastSender = str(r, "last_sender_id");
                boolean deleted = r.optBoolean("last_deleted", false);
                String cipher = str(r, "last_cipher");

                String preview;
                if (deleted) {
                    preview = "This message was deleted";
                } else if (cipher == null || lastSender == null) {
                    preview = "";
                } else {
                    try {
                        preview = ChatCrypto.decrypt(cipher, keys.getPrivate(),
                                convId + ":" + lastSender);
                    } catch (Exception e) {
                        preview = "🔒 Message can't be read";
                    }
                }
                preview = PostShareMessage.previewText(preview);
                if (lastSender != null && lastSender.equals(me) && !preview.isEmpty()) {
                    preview = "You: " + preview;
                }
                preview = preview.replace('\n', ' ');

                long when = parseTime(lastAt);
                Row row = new Row();
                row.sortKey = when;
                row.preview = new MessagePreview(name, PersonResult.initialsOf(name), preview,
                        timeAgo(when, now), convId, otherId,
                        (int) Math.min(r.optLong("unread_count", 0), 999));
                list.add(row);
            }

            Collections.sort(list, new Comparator<Row>() {
                @Override
                public int compare(Row a, Row b) {
                    return Long.compare(b.sortKey, a.sortKey);
                }
            });
            List<MessagePreview> out = new ArrayList<>();
            for (Row r : list) out.add(r.preview);
            return out;
        }, callback);
    }

    public void loadUnreadTotal(SupabaseApi.Callback<Integer> callback) {
        background(() -> {
            String body = api.requestBlocking("POST", "/rest/v1/rpc/unread_total", "{}", null);
            try {
                return (int) Math.min(Long.parseLong(body.trim()), 9999);
            } catch (NumberFormatException e) {
                return 0;
            }
        }, callback);
    }

    // ------------------------------------------------------------------
    // Messages inside one chat
    // ------------------------------------------------------------------

    /** Turns one database row into a readable message. Never throws. */
    DirectMessage decodeRow(JSONObject r, String me, KeyPair keys) {
        String id = str(r, "id");
        String convId = str(r, "conversation_id");
        String sender = str(r, "sender_id");
        boolean mine = me.equals(sender);
        long when = parseTime(str(r, "created_at"));
        boolean read = str(r, "read_at") != null;
        boolean deleted = str(r, "deleted_at") != null;

        String text = "";
        boolean bad = false;
        if (!deleted) {
            String cipher = str(r, mine ? "cipher_for_sender" : "cipher_for_recipient");
            try {
                if (cipher == null) throw new IllegalStateException("no cipher");
                text = ChatCrypto.decrypt(cipher, keys.getPrivate(), convId + ":" + sender);
            } catch (Exception e) {
                bad = true;
            }
        }
        return new DirectMessage(id, sender, text, when, clockTime(when),
                mine, read, deleted, bad, false);
    }

    /** For live updates: decodes a row that arrived through the realtime connection. */
    public void decodeRowAsync(JSONObject row, SupabaseApi.Callback<DirectMessage> callback) {
        background(() -> decodeRow(row, myId(), myKeys()), callback);
    }

    public void loadMessages(String conversationId, SupabaseApi.Callback<List<DirectMessage>> callback) {
        background(() -> {
            String me = myId();
            KeyPair keys = myKeys();
            String path = "/rest/v1/direct_messages?select=*&conversation_id=eq."
                    + enc(conversationId) + "&order=created_at.desc&limit=300";
            JSONArray rows = new JSONArray(api.requestBlocking("GET", path, null, null));
            List<DirectMessage> out = new ArrayList<>();
            for (int i = rows.length() - 1; i >= 0; i--) {   // oldest first
                out.add(decodeRow(rows.getJSONObject(i), me, keys));
            }
            return out;
        }, callback);
    }

    private PublicKey publicKeyOf(String userId) throws Exception {
        long now = System.currentTimeMillis();
        synchronized (KEY_CACHE) {
            Long at = KEY_CACHE_TIME.get(userId);
            if (at != null && now - at < KEY_CACHE_MS && KEY_CACHE.containsKey(userId)) {
                return KEY_CACHE.get(userId);
            }
        }
        String body = api.requestBlocking("GET",
                "/rest/v1/user_keys?select=public_key&user_id=eq." + enc(userId), null, null);
        JSONArray rows = new JSONArray(body);
        if (rows.length() == 0) {
            throw new SupabaseApi.ApiException(0,
                    "This person hasn't set up secure messaging yet. "
                            + "They need to log in to the app once first.");
        }
        PublicKey key = ChatCrypto.publicKeyFromString(rows.getJSONObject(0).getString("public_key"));
        synchronized (KEY_CACHE) {
            KEY_CACHE.put(userId, key);
            KEY_CACHE_TIME.put(userId, now);
        }
        return key;
    }

    /** Encrypts twice (one copy for each person), saves it, and returns the saved message. */
    public void sendMessage(String conversationId, String recipientId, String text,
                            SupabaseApi.Callback<DirectMessage> callback) {
        background(() -> {
            String me = myId();
            KeyPair keys = myKeys();
            PublicKey theirKey = publicKeyOf(recipientId);

            String aad = conversationId + ":" + me;
            String forRecipient = ChatCrypto.encryptFor(text, theirKey, aad);
            String forSender = ChatCrypto.encryptFor(text, keys.getPublic(), aad);

            JSONObject body = new JSONObject()
                    .put("conversation_id", conversationId)
                    .put("sender_id", me)
                    .put("recipient_id", recipientId)
                    .put("cipher_for_recipient", forRecipient)
                    .put("cipher_for_sender", forSender);

            String response = api.requestBlocking("POST", "/rest/v1/direct_messages",
                    body.toString(), "return=representation");
            JSONArray rows = new JSONArray(response);
            if (rows.length() == 0) {
                throw new SupabaseApi.ApiException(0, "The message could not be saved.");
            }
            return decodeRow(rows.getJSONObject(0), me, keys);
        }, callback);
    }

    /** Tells the server that everything in this chat sent to me has been seen. */
    public void markRead(String conversationId) {
        POOL.execute(() -> {
            try {
                String json = new JSONObject().put("conv_id", conversationId).toString();
                api.requestBlocking("POST", "/rest/v1/rpc/mark_conversation_read", json, null);
            } catch (Exception e) {
                Log.w(TAG, "markRead failed: " + e);
            }
        });
    }

    public void deleteMessage(String messageId, SupabaseApi.Callback<Boolean> callback) {
        background(() -> {
            String json = new JSONObject().put("msg_id", messageId).toString();
            api.requestBlocking("POST", "/rest/v1/rpc/delete_my_message", json, null);
            return Boolean.TRUE;
        }, callback);
    }

    /** Deletes the whole chat for both people. */
    public void deleteConversation(String conversationId, SupabaseApi.Callback<Boolean> callback) {
        background(() -> {
            api.requestBlocking("DELETE",
                    "/rest/v1/conversations?id=eq." + enc(conversationId), null, null);
            return Boolean.TRUE;
        }, callback);
    }
}

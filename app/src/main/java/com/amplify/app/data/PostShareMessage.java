package com.amplify.app.data;

import org.json.JSONException;
import org.json.JSONObject;

/** Helper for creating and parsing shared post messages in chat. */
public final class PostShareMessage {

    private PostShareMessage() {}

    public static String create(String postId, String title, String mediaType, String mediaUrl) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "post_share");
            json.put("postId", postId != null ? postId : "");
            json.put("title", title != null ? title : "");
            json.put("mediaType", mediaType != null ? mediaType : "");
            json.put("mediaUrl", mediaUrl != null ? mediaUrl : "");
            return json.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    private static JSONObject parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(text);
            if ("post_share".equals(json.optString("type"))) {
                return json;
            }
        } catch (Exception ignored) {
        }

        // Earlier app builds stored the post ID, media type, and URL in a marker line.
        // Keep those existing chat messages renderable after upgrading to the JSON format.
        String marker = "[AMPLIFY_POST:";
        if (text.startsWith(marker)) {
            int markerEnd = text.indexOf(']', marker.length());
            if (markerEnd > marker.length()) {
                try {
                    String[] fields = text.substring(marker.length(), markerEnd)
                            .split("\\|", -1);
                    String body = text.substring(markerEnd + 1).trim();
                    if (body.startsWith("\n")) body = body.substring(1);
                    int nextLine = body.indexOf('\n');
                    String title = nextLine < 0 ? body : body.substring(0, nextLine);
                    String titlePrefix = "Shared a post: ";
                    if (title.startsWith(titlePrefix)) {
                        title = title.substring(titlePrefix.length());
                    }

                    JSONObject legacy = new JSONObject();
                    legacy.put("type", "post_share");
                    legacy.put("postId", fields[0]);
                    legacy.put("title", title);
                    legacy.put("mediaType", fields.length > 1 ? fields[1] : "");
                    legacy.put("mediaUrl", fields.length > 2 ? fields[2] : "");
                    return legacy;
                } catch (JSONException ignored) {
                }
            }
        }
        return null;
    }

    public static String postId(String text) {
        JSONObject json = parse(text);
        if (json != null) {
            String id = json.optString("postId", "");
            return id.isEmpty() ? null : id;
        }
        return null;
    }

    public static String title(String text) {
        JSONObject json = parse(text);
        if (json != null) {
            return json.optString("title", "Shared Post");
        }
        return text != null ? text : "";
    }

    public static String mediaUrl(String text) {
        JSONObject json = parse(text);
        if (json != null) {
            String url = json.optString("mediaUrl", "");
            return url.isEmpty() ? null : url;
        }
        return null;
    }

    public static String mediaType(String text) {
        JSONObject json = parse(text);
        if (json != null) {
            return json.optString("mediaType", "");
        }
        return "";
    }

    public static String displayText(String text) {
        JSONObject json = parse(text);
        if (json != null) {
            return "Shared post: " + json.optString("title", "");
        }
        return text != null ? text : "";
    }

    public static String previewText(String text) {
        JSONObject json = parse(text);
        if (json != null) {
            String t = json.optString("title", "");
            return t.isEmpty() ? "📎 Shared Post" : "📎 " + t;
        }
        return text != null ? text : "";
    }
}

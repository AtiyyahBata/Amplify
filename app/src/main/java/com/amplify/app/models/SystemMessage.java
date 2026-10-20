package com.amplify.app.models;

import java.util.Locale;

/** One message from Lexi (a row of the system_messages table). */
public class SystemMessage {

    private final String id;
    private final String type;
    private final String title;
    private final String body;
    private final String postTitle;
    private final String postCaption;
    private final String mediaType;
    private final String mediaUrl;      // null when the post had no media
    private final long createdAtMillis;
    private final boolean unread;

    public SystemMessage(String id, String type, String title, String body,
                         String postTitle, String postCaption, String mediaType,
                         String mediaUrl, long createdAtMillis, boolean unread) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.body = body;
        this.postTitle = postTitle;
        this.postCaption = postCaption;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.createdAtMillis = createdAtMillis;
        this.unread = unread;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getPostTitle() { return postTitle; }
    public String getPostCaption() { return postCaption; }
    public String getMediaUrl() { return mediaUrl; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public boolean isUnread() { return unread; }

    /** True when the message carries a copy of a post to show. */
    public boolean hasPost() { return !postTitle.isEmpty(); }

    public boolean isImage() {
        return mediaUrl != null && mediaType.toLowerCase(Locale.ROOT).contains("image");
    }

    public boolean isVideo() {
        return mediaUrl != null && mediaType.toLowerCase(Locale.ROOT).contains("video");
    }
}

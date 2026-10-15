package com.amplify.app.models;

/** One private message as shown on screen (already decrypted). */
public class DirectMessage {

    private final String id;
    private final String senderId;
    private final String text;
    private final long createdAt;   // milliseconds
    private final String time;      // "14:05"
    private final boolean mine;
    private boolean read;
    private boolean deleted;
    private final boolean undecryptable;
    private final boolean pending;  // still being sent

    public DirectMessage(String id, String senderId, String text, long createdAt, String time,
                         boolean mine, boolean read, boolean deleted,
                         boolean undecryptable, boolean pending) {
        this.id = id;
        this.senderId = senderId;
        this.text = text == null ? "" : text;
        this.createdAt = createdAt;
        this.time = time == null ? "" : time;
        this.mine = mine;
        this.read = read;
        this.deleted = deleted;
        this.undecryptable = undecryptable;
        this.pending = pending;
    }

    /** A message that is shown straight away while it is still being sent. */
    public static DirectMessage pending(String tempId, String senderId, String text,
                                        long now, String time) {
        return new DirectMessage(tempId, senderId, text, now, time,
                true, false, false, false, true);
    }

    public String getId() { return id; }
    public String getSenderId() { return senderId; }
    public String getText() { return text; }
    public long getCreatedAt() { return createdAt; }
    public String getTime() { return time; }
    public boolean isMine() { return mine; }
    public boolean isRead() { return read; }
    public boolean isDeleted() { return deleted; }
    public boolean isUndecryptable() { return undecryptable; }
    public boolean isPending() { return pending; }

    public void markRead() { read = true; }

    public void markDeleted() { deleted = true; }
}

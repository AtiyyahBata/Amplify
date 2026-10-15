package com.amplify.app.models;

/** One row in the Messages list. */
public class MessagePreview {

    private final String name;
    private final String initials;
    private final String lastMessage;
    private final String timeAgo;
    private final String conversationId;
    private final String otherUserId;
    private final int unreadCount;

    /** Old constructor, kept so any leftover dummy data still compiles. */
    public MessagePreview(String name, String initials, String lastMessage, String timeAgo) {
        this(name, initials, lastMessage, timeAgo, null, null, 0);
    }

    public MessagePreview(String name, String initials, String lastMessage, String timeAgo,
                          String conversationId, String otherUserId, int unreadCount) {
        this.name = name;
        this.initials = initials;
        this.lastMessage = lastMessage;
        this.timeAgo = timeAgo;
        this.conversationId = conversationId;
        this.otherUserId = otherUserId;
        this.unreadCount = unreadCount;
    }

    public String getName() { return name; }
    public String getInitials() { return initials; }
    public String getLastMessage() { return lastMessage; }
    public String getTimeAgo() { return timeAgo; }
    public String getConversationId() { return conversationId; }
    public String getOtherUserId() { return otherUserId; }
    public int getUnreadCount() { return unreadCount; }
}

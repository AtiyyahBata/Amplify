package com.amplify.app.models;

/** One row in the Messages list - a preview of a conversation, not the full thread. */
public class MessagePreview {

    private String name;
    private String initials;
    private String lastMessage;
    private String timeAgo;
    private String conversationId;
    private String otherUserId;
    private int unreadCount;

    public MessagePreview(String name, String initials, String lastMessage, String timeAgo) {
        this.name = name;
        this.initials = initials;
        this.lastMessage = lastMessage;
        this.timeAgo = timeAgo;
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

package com.amplify.app.models;

/** One row in the Messages list - a preview of a conversation, not the full thread. */
public class MessagePreview {

    private String name;
    private String initials;
    private String lastMessage;
    private String timeAgo;

    public MessagePreview(String name, String initials, String lastMessage, String timeAgo) {
        this.name = name;
        this.initials = initials;
        this.lastMessage = lastMessage;
        this.timeAgo = timeAgo;
    }

    public String getName() { return name; }
    public String getInitials() { return initials; }
    public String getLastMessage() { return lastMessage; }
    public String getTimeAgo() { return timeAgo; }
}

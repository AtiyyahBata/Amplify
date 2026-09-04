package com.amplify.app.models;

/** A single bubble inside the Conversation screen. */
public class ChatMessage {

    public static final int TYPE_THEM = 0;
    public static final int TYPE_ME = 1;

    private String body;
    private int type;

    public ChatMessage(String body, int type) {
        this.body = body;
        this.type = type;
    }

    public String getBody() { return body; }
    public int getType() { return type; }
}

package com.amplify.app.models;

/** One message inside an AI chat (either the user's or the AI's). */
public class AiMessage {

    private final String role;     // "user" or "assistant"
    private final String content;
    private final String time;     // already formatted, e.g. "10:15"

    public AiMessage(String role, String content, String time) {
        this.role = role;
        this.content = content;
        this.time = time;
    }

    public boolean isUser() { return "user".equals(role); }
    public String getContent() { return content; }
    public String getTime() { return time; }
}

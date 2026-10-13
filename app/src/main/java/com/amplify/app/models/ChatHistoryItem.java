package com.amplify.app.models;

import java.io.Serializable;

/** Represents a single chat history thread summary in AI Chat History. */
public class ChatHistoryItem implements Serializable {

    private final String title;
    private final String preview;
    private final String date;

    public ChatHistoryItem(String title, String preview, String date) {
        this.title = title;
        this.preview = preview;
        this.date = date;
    }

    public String getTitle() { return title; }
    public String getPreview() { return preview; }
    public String getDate() { return date; }
}

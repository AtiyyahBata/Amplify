package com.amplify.app.models;

/**
 * One row in the AI chat history list.
 * Now carries the Supabase chat id so a tapped chat can be resumed or deleted.
 * The old 3-argument constructor is kept so DummyData still compiles.
 */
public class ChatHistoryItem {

    private final String id;
    private final String title;
    private final String preview;
    private final String date;

    public ChatHistoryItem(String id, String title, String preview, String date) {
        this.id = id;
        this.title = title;
        this.preview = preview;
        this.date = date;
    }

    /** Old constructor used by DummyData (no id). */
    public ChatHistoryItem(String title, String preview, String date) {
        this(null, title, preview, date);
    }

    public String getId() { return id; }
    public String getTitle() { return title == null ? "" : title; }
    public String getPreview() { return preview == null ? "" : preview; }
    public String getDate() { return date == null ? "" : date; }
}

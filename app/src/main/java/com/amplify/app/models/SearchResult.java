package com.amplify.app.models;

/** One resource returned by the (simulated) AI-powered search on the Search screen. */
public class SearchResult {

    private String format;
    private String title;
    private String source;

    public SearchResult(String format, String title, String source) {
        this.format = format;
        this.title = title;
        this.source = source;
    }

    public String getFormat() { return format; }
    public String getTitle() { return title; }
    public String getSource() { return source; }
}

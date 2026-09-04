package com.amplify.app.models;

/** A single comment on a post's comment thread (View Post screen). */
public class Comment {

    private String authorName;
    private String timeAgo;
    private String body;

    public Comment(String authorName, String timeAgo, String body) {
        this.authorName = authorName;
        this.timeAgo = timeAgo;
        this.body = body;
    }

    public String getAuthorName() { return authorName; }
    public String getTimeAgo() { return timeAgo; }
    public String getBody() { return body; }
}

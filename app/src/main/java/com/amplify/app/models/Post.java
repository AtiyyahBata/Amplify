package com.amplify.app.models;

/**
 * Represents a single community post shown on the Home feed and View Post screen.
 * This is a plain data holder only - it is not connected to Firebase yet.
 */
public class Post {

    private String authorName;
    private String authorInitials;
    private boolean authorVerified;
    private String title;
    private String body;
    private String tagOne;
    private String tagTwo;

    public Post(String authorName, String authorInitials, boolean authorVerified,
                String title, String body, String tagOne, String tagTwo) {
        this.authorName = authorName;
        this.authorInitials = authorInitials;
        this.authorVerified = authorVerified;
        this.title = title;
        this.body = body;
        this.tagOne = tagOne;
        this.tagTwo = tagTwo;
    }

    public String getAuthorName() { return authorName; }
    public String getAuthorInitials() { return authorInitials; }
    public boolean isAuthorVerified() { return authorVerified; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getTagOne() { return tagOne; }
    public String getTagTwo() { return tagTwo; }
}

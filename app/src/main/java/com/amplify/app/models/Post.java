
package com.amplify.app.models;

public class Post {

    private String id;
    private String userId;
    private String authorName;
    private String authorInitials;
    private boolean authorVerified;
    private String authorField;
    private String title;
    private String body;
    private String tagOne;
    private String tagTwo;
    private String mediaType;
    private String mediaPath;
    private String mediaUrl;
    private String authorAvatarUrl;

    // Keep compatibility with DummyData.java.
    public Post(String authorName, String authorInitials,
                boolean authorVerified, String title, String body,
                String tagOne, String tagTwo) {
        this(null, null, authorName, authorInitials, authorVerified,
                null, title, body, tagOne, tagTwo, null, null);
    }

    public Post(String id, String userId, String authorName,
                String authorInitials, boolean authorVerified,
                String authorField, String title, String body,
                String tagOne, String tagTwo, String mediaType,
                String mediaPath) {
        this.id = id;
        this.userId = userId;
        this.authorName = authorName;
        this.authorInitials = authorInitials;
        this.authorVerified = authorVerified;
        this.authorField = authorField;
        this.title = title;
        this.body = body;
        this.tagOne = tagOne;
        this.tagTwo = tagTwo;
        this.mediaType = mediaType;
        this.mediaPath = mediaPath;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorInitials() { return authorInitials; }
    public boolean isAuthorVerified() { return authorVerified; }
    public String getAuthorField() { return authorField; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getTagOne() { return tagOne; }
    public String getTagTwo() { return tagTwo; }
    public String getMediaType() { return mediaType; }
    public String getMediaPath() { return mediaPath; }
    public String getMediaUrl() { return mediaUrl; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public void setAuthorAvatarUrl(String authorAvatarUrl) {
        this.authorAvatarUrl = authorAvatarUrl;
    }
}

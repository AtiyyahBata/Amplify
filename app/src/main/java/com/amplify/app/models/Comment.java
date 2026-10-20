package com.amplify.app.models;

/** A single comment on a post's comment thread (View Post screen). */
public class Comment {

    private String id;
    private String postId;
    private String userId;
    private String authorName;
    private String authorAvatarUrl;
    private String body;
    private String createdAt;
    private String timeAgo;

    // Constructor for dummy data
    public Comment(String authorName, String timeAgo, String body) {
        this.authorName = authorName;
        this.timeAgo = timeAgo;
        this.body = body;
    }

    // Constructor for Supabase / ViewPostActivity data
    public Comment(
            String id,
            String postId,
            String userId,
            String authorName,
            String body,
            String createdAt
    ) {
        this(id, postId, userId, authorName, "", body, createdAt);
    }

    public Comment(
            String id,
            String postId,
            String userId,
            String authorName,
            String authorAvatarUrl,
            String body,
            String createdAt
    ) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.authorName = authorName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.body = body;
        this.createdAt = createdAt;
        this.timeAgo = createdAt;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public String getPostId() {
        return postId != null ? postId : "";
    }

    public String getUserId() {
        return userId != null ? userId : "";
    }

    public String getAuthorName() {
        return authorName != null ? authorName : "";
    }

    public String getAuthorAvatarUrl() {
        return authorAvatarUrl != null ? authorAvatarUrl : "";
    }

    public String getBody() {
        return body != null ? body : "";
    }

    public String getCreatedAt() {
        return createdAt != null ? createdAt : "";
    }

    public String getTimeAgo() {
        return timeAgo != null ? timeAgo : "";
    }
}

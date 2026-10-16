package com.amplify.app.ui.main.post;

public class Comment {

    private String id;
    private String postId;
    private String userId;
    private String authorName;
    private String body;
    private String createdAt;

    public Comment(
            String id,
            String postId,
            String userId,
            String authorName,
            String body,
            String createdAt
    ) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.authorName = authorName;
        this.body = body;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPostId() {
        return postId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getBody() {
        return body;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}

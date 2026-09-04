package com.amplify.app.data;

import com.amplify.app.models.ChatMessage;
import com.amplify.app.models.Comment;
import com.amplify.app.models.MessagePreview;
import com.amplify.app.models.Post;
import com.amplify.app.models.Report;
import com.amplify.app.models.SearchResult;
import com.amplify.app.models.VerifyRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides hard-coded placeholder data for every screen in the prototype.
 *
 * IMPORTANT: this class does not talk to Firebase or any backend. It only exists so
 * every screen has something realistic to display while the team builds the real
 * data layer described in Part I of the reference document (Firestore collections:
 * Users, VerificationRequests, Posts, Messages). Swap the methods below for real
 * repository calls once that layer exists - the UI code does not need to change.
 */
public class DummyData {

    public static List<Post> getFeedPosts() {
        List<Post> posts = new ArrayList<>();
        posts.add(new Post(
                "Reabetsoe Molakeng", "RM", true,
                "Reading a stack trace like a story, not a wall of text",
                "Most junior developers scroll straight to the bottom of a crash log looking for red text. " +
                        "Start at the first line that mentions your own package name instead - that's almost " +
                        "always where the real problem lives. Everything above it is usually framework noise.",
                "debugging", "android"));
        posts.add(new Post(
                "Almero Badenhorst", "AB", true,
                "Video: Firebase Auth in 12 minutes",
                "Covers email/password sign-in and wiring it to a Firestore user profile.",
                "firebase", "video"));
        posts.add(new Post(
                "Megan Azar", "MA", true,
                "What I'd tell a first-year about AI tools",
                "Use it to check your reasoning, not to skip having any.",
                "career", "ai"));
        return posts;
    }

    public static List<Comment> getCommentsForPost() {
        List<Comment> comments = new ArrayList<>();
        comments.add(new Comment("Megan Azar", "2h", "This would've saved me an hour last week, honestly."));
        comments.add(new Comment("Almero Badenhorst", "1h", "Adding: check the \"Caused by\" line too, not just the top frame."));
        return comments;
    }

    public static List<MessagePreview> getConversations() {
        List<MessagePreview> list = new ArrayList<>();
        list.add(new MessagePreview("Reabetsoe Molakeng", "RM", "Sure, happy to walk you through the setup", "2h"));
        list.add(new MessagePreview("Almero Badenhorst", "AB", "Thanks for the feedback on the video!", "1d"));
        list.add(new MessagePreview("Megan Azar", "MA", "Let's pair on that gap analysis post", "3d"));
        return list;
    }

    public static List<ChatMessage> getConversationThread() {
        List<ChatMessage> thread = new ArrayList<>();
        thread.add(new ChatMessage("Hey! Saw your comment on my debugging post.", ChatMessage.TYPE_THEM));
        thread.add(new ChatMessage("Yeah, that tip about the \"Caused by\" line was clutch", ChatMessage.TYPE_ME));
        thread.add(new ChatMessage("Sure, happy to walk you through the setup if you want to hop on a call", ChatMessage.TYPE_THEM));
        return thread;
    }

    public static List<SearchResult> getSearchResults(String query) {
        List<SearchResult> results = new ArrayList<>();
        results.add(new SearchResult("Blog", "A practical comparison, with code", "From the Amplify community"));
        results.add(new SearchResult("Video", "12-minute walkthrough with a live demo", "External resource"));
        return results;
    }

    public static List<VerifyRequest> getPendingVerifications() {
        List<VerifyRequest> list = new ArrayList<>();
        list.add(new VerifyRequest("Karabo Phiri", "KP", "BSc IT certificate"));
        list.add(new VerifyRequest("Thabo Nkosi", "TN", "Diploma certificate"));
        list.add(new VerifyRequest("Lerato van Wyk", "LV", "Degree scan"));
        return list;
    }

    public static List<Report> getOpenReports() {
        List<Report> list = new ArrayList<>();
        list.add(new Report("Post flagged: \"Quick JS trick\"", "Reported for: misleading content", "Remove post"));
        list.add(new Report("User flagged: @coder_x99", "Reported for: spam messages", "Suspend user"));
        return list;
    }
}

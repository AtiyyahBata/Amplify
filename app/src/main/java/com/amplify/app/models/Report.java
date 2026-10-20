package com.amplify.app.models;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A user-submitted complaint about a post or member (Admin > Reports).
 *
 * Reports come from the Supabase "reports" table (see AdminReportRepository).
 */
public class Report implements Serializable {

    // Original fields
    private final String subjectLabel;
    private final String reason;
    private final String actionLabel;

    // Real data from the reports table
    private String id = "";
    private String targetType = "post";
    private String postId = "";
    private String postTitle = "";
    private String reportedUserId = "";
    private String reportedName = "";
    private String reporterName = "";
    private String reasonCode = "";
    private String reasonLabel = "";
    private String description = "";
    private String status = "open";
    private String adminNote = "";
    private long createdAtMillis = 0;
    private long resolvedAtMillis = 0;



    public Report(String id, String targetType, String postId, String postTitle,
                  String reportedUserId, String reportedName, String reporterName,
                  String reasonCode, String reasonLabel, String description,
                  String status, String adminNote,
                  long createdAtMillis, long resolvedAtMillis) {
        this.id = id;
        this.targetType = targetType;
        this.postId = postId;
        this.postTitle = postTitle;
        this.reportedUserId = reportedUserId;
        this.reportedName = reportedName;
        this.reporterName = reporterName;
        this.reasonCode = reasonCode;
        this.reasonLabel = reasonLabel;
        this.description = description;
        this.status = status;
        this.adminNote = adminNote;
        this.createdAtMillis = createdAtMillis;
        this.resolvedAtMillis = resolvedAtMillis;

        boolean isPost = "post".equals(targetType);
        String title = postTitle.isEmpty() ? "Untitled post" : postTitle;
        this.subjectLabel = isPost
                ? "Post flagged: \"" + title + "\""
                : "User flagged: " + reportedName;
        this.reason = "Reported for: " + reasonLabel.toLowerCase(Locale.ROOT);
        this.actionLabel = isPost ? "Remove post" : "Suspend user";
    }

    // Original getters
    public String getSubjectLabel() { return subjectLabel; }
    public String getReason() { return reason; }
    public String getActionLabel() { return actionLabel; }

    // New getters
    public String getId() { return id; }
    public String getTargetType() { return targetType; }
    public String getPostId() { return postId; }
    public String getPostTitle() { return postTitle; }
    public String getReportedUserId() { return reportedUserId; }
    public String getReportedName() { return reportedName; }
    public String getReporterName() { return reporterName; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonLabel() { return reasonLabel; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getAdminNote() { return adminNote; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getResolvedAtMillis() { return resolvedAtMillis; }
    public boolean isOpen() { return "open".equals(status); }

    /** The small grey line under each report in the list. */
    public String getMetaText() {
        String by = "Reported by " + reporterName;
        if ("actioned".equals(status)) {
            return by + "  •  Actioned on " + formatDate(resolvedAtMillis);
        }
        if ("dismissed".equals(status)) {
            return by + "  •  Dismissed on " + formatDate(resolvedAtMillis);
        }
        return by + "  •  " + getAgeText();
    }

    /** "Just now", "5 min ago", "3h ago", "2d ago". */
    public String getAgeText() {
        if (createdAtMillis <= 0) return "";
        long minutes = (System.currentTimeMillis() - createdAtMillis) / 60000L;
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    public static String formatDate(long millis) {
        if (millis <= 0) return "an unknown date";
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                .format(new Date(millis));
    }
}

package com.amplify.app.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A verification request (Admin > Verify Users).
 *
 * The old 3-argument constructor is kept so DummyData still compiles.
 * Real requests from Supabase use the long constructor.
 */
public class VerifyRequest {

    // Original fields
    private final String userName;
    private final String initials;
    private final String documentLabel;

    // Real data from the verification_requests table
    private String id = "";
    private String userId = "";
    private String email = "";
    private String reason = "";
    private String university = "";
    private String certificatePath = "";
    private String status = "pending";
    private String adminNote = "";
    private boolean blocked = false;
    private long createdAtMillis = 0;
    private long reviewedAtMillis = 0;

    /** Old mock-data constructor (kept so DummyData still compiles). */
    public VerifyRequest(String userName, String initials, String documentLabel) {
        this.userName = userName;
        this.initials = initials;
        this.documentLabel = documentLabel;
    }

    public VerifyRequest(String id, String userId, String userName, String email,
                         String university, String reason, String certificatePath,
                         String status, String adminNote, boolean blocked,
                         long createdAtMillis, long reviewedAtMillis) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.initials = makeInitials(userName);
        this.email = email;
        this.university = university;
        this.documentLabel = university;
        this.reason = reason;
        this.certificatePath = certificatePath;
        this.status = status;
        this.adminNote = adminNote;
        this.blocked = blocked;
        this.createdAtMillis = createdAtMillis;
        this.reviewedAtMillis = reviewedAtMillis;
    }

    // Original getters
    public String getUserName() { return userName; }
    public String getInitials() { return initials; }
    public String getDocumentLabel() { return documentLabel; }

    // New getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getReason() { return reason; }
    public String getUniversity() { return university; }
    public String getCertificatePath() { return certificatePath; }
    public String getStatus() { return status; }
    public String getAdminNote() { return adminNote; }
    public boolean isBlocked() { return blocked; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getReviewedAtMillis() { return reviewedAtMillis; }
    public boolean isPending() { return "pending".equals(status); }

    /** "Waiting 3 days", "Waiting 5 hours" and so on. */
    public String getWaitingText() {
        if (createdAtMillis <= 0) return "Waiting";
        long minutes = (System.currentTimeMillis() - createdAtMillis) / 60000L;
        if (minutes < 60) return "Waiting less than an hour";
        long hours = minutes / 60;
        if (hours < 24) return "Waiting " + hours + (hours == 1 ? " hour" : " hours");
        long days = hours / 24;
        return "Waiting " + days + (days == 1 ? " day" : " days");
    }

    /** The small grey line under each name in the list. */
    public String getMetaText() {
        if ("approved".equals(status)) {
            return "Approved on " + formatDate(reviewedAtMillis);
        }
        if ("rejected".equals(status)) {
            return "Declined on " + formatDate(reviewedAtMillis)
                    + (blocked ? " · Blocked" : "");
        }
        return getWaitingText();
    }

    public static String formatDate(long millis) {
        if (millis <= 0) return "an unknown date";
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                .format(new Date(millis));
    }

    private static String makeInitials(String fullName) {
        if (fullName == null) return "U";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "U";
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1)
                + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}

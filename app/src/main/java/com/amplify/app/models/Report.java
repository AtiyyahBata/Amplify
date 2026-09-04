package com.amplify.app.models;

/** A user-submitted complaint about a post or member (Admin > Reports). */
public class Report {

    private String subjectLabel;
    private String reason;
    private String actionLabel;

    public Report(String subjectLabel, String reason, String actionLabel) {
        this.subjectLabel = subjectLabel;
        this.reason = reason;
        this.actionLabel = actionLabel;
    }

    public String getSubjectLabel() { return subjectLabel; }
    public String getReason() { return reason; }
    public String getActionLabel() { return actionLabel; }
}

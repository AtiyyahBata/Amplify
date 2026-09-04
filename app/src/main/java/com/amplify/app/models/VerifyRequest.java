package com.amplify.app.models;

/** A pending certificate the AI check could not confidently approve or reject (Admin > Verify Users). */
public class VerifyRequest {

    private String userName;
    private String initials;
    private String documentLabel;

    public VerifyRequest(String userName, String initials, String documentLabel) {
        this.userName = userName;
        this.initials = initials;
        this.documentLabel = documentLabel;
    }

    public String getUserName() { return userName; }
    public String getInitials() { return initials; }
    public String getDocumentLabel() { return documentLabel; }
}

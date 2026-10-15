package com.amplify.app.models;

/** One person in the "new message" search results. */
public class PersonResult {

    private final String id;
    private final String fullName;
    private final String fieldOfStudy;

    public PersonResult(String id, String fullName, String fieldOfStudy) {
        this.id = id;
        this.fullName = fullName == null ? "" : fullName;
        this.fieldOfStudy = fieldOfStudy == null ? "" : fieldOfStudy;
    }

    public String getId() { return id; }

    public String getFullName() { return fullName; }

    public String getFieldOfStudy() { return fieldOfStudy; }

    public String getInitials() { return initialsOf(fullName); }

    /** "Jane van Wyk" becomes "JW". One name gives one letter. Empty gives "?". */
    public static String initialsOf(String name) {
        if (name == null) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String first = parts[0].substring(0, 1).toUpperCase();
        if (parts.length == 1) return first;
        String last = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }
}

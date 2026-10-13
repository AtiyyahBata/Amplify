package com.amplify.app.data;

/**
 * Supabase connection details used by the AI chat.
 * These are the same values your LoginActivity and RegisterActivity use.
 *
 * ONLY the publishable (anon) key goes here. NEVER put the secret / service_role key in the app.
 */
public final class SupabaseConfig {

    // Project URL (no trailing slash)
    public static final String PROJECT_URL = "https://pggwokpdcqtigxmcrtxh.supabase.co";

    // Publishable key
    public static final String ANON_KEY = "sb_publishable_yoqGxzzEd6H2iRWyWckd-g_RkHgxhqC";

    private SupabaseConfig() {}
}
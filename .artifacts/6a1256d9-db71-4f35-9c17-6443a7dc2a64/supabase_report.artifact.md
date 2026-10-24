# Supabase Backend Report: Change-Email Flow & App Database Architecture

This report details the Supabase database schema, tables, columns, Row Level Security (RLS) policies, and dashboard settings required by the Amplify Android app, with specific focus on the email change flow and security constraints.

---

## 1. Tables and Columns Used by the App

The Amplify app interacts with the following Supabase tables via REST endpoints (`/rest/v1/`), RPC functions, and Auth (`/auth/v1/`):

| Table Name | Column Name | Implied Type | Flag / Notes |
| :--- | :--- | :--- | :--- |
| **`profiles`** | `id` | UUID / Text | Primary key (matches `auth.users.id`) |
| | `full_name` | Text | User's display name |
| | `field_of_study` | Text | Academic field/major |
| | `avatar_url` | Text | Storage URL for profile picture |
| | `github_url` | Text | Linked GitHub profile URL |
| | `linkedin_url` | Text | Linked LinkedIn profile URL |
| | `user_type` | Text | Role ("Normal", "Verified", "Admin") |
| | `email` | Text | ⚠️ **Holds user email address** (joined by admin verification views) |
| **`user_keys`** | `user_id` | UUID / Text | Foreign key to profiles/auth.users |
| | `public_key` | Text | End-to-end encryption public key |
| **`key_backups`** | `user_id` | UUID / Text | Foreign key to profiles/auth.users |
| | `encrypted_key` | Text | Password-locked private key backup |
| | `salt` | Text | KDF salt for key decryption |
| | `iterations` | Integer | PBKDF2 iteration count |
| **`verification_requests`**| `id` | UUID / BigInt | Primary key |
| | `user_id` | UUID / Text | Applicant user ID |
| | `status` | Text | Request status ("pending", "approved", "rejected") |
| | `admin_note` | Text | Admin feedback notes |
| | `blocked` | Boolean | Whether request is blocked |
| | `created_at` | Timestamp | Submission timestamp |
| | `reviewed_at` | Timestamp | Review timestamp |
| | `reason` | Text | Reason for verification |
| | `university` | Text | University name |
| | `certificate_path` | Storage Path | Path in `verification-certificates` bucket |
| **`posts`** | `id`, `user_id`, `title`, `caption`, `media_type`, `media_path`, `created_at`, `status` | Various | Community posts feed & details |
| **`comments`** | `id`, `post_id`, `user_id`, `content`, `created_at` | Various | Post comments |
| **`reports`** | `id`, `target_type`, `post_id`, `reporter_id`, `reported_user_id`, `reason`, `description`, `status`, `created_at` | Various | Post & content moderation reports |
| **`system_messages`** | `id`, `type`, `title`, `body`, `post_title`, `post_caption`, `post_media_type`, `post_media_path`, `read_at`, `created_at` | Various | Lexi system notifications |
| **`conversations` & `direct_messages`** | `id`, `conversation_id`, `sender_id`, `recipient_id`, `cipher_for_recipient`, `cipher_for_sender`, `created_at`, `deleted_at`, `read_at` | Various | Encrypted direct messaging |

---

## 2. Email Stored Outside Supabase Auth

**Yes.** As seen in `AdminVerificationRepository.java` (which queries `profiles!user_id(full_name, email, avatar_url)`), the `public.profiles` table maintains its own `email` column alongside Supabase Auth (`auth.users.email`).

To ensure `public.profiles.email` stays in sync with `auth.users.email` **only after the email change is confirmed** (i.e. when `auth.users.email` updates upon verification, not while pending in `new_email`), deploy the following database trigger:

```sql
-- REQUIRED: Ensure profiles table has email column
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email TEXT;

-- REQUIRED: Function to sync confirmed email from auth.users to public.profiles
CREATE OR REPLACE FUNCTION public.handle_user_email_sync()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE public.profiles
  SET email = NEW.email
  WHERE id = NEW.id;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- REQUIRED: Trigger on auth.users to update profiles.email only when confirmed email changes
DROP TRIGGER IF EXISTS on_auth_user_email_updated ON auth.users;
CREATE TRIGGER on_auth_user_email_updated
  AFTER INSERT OR UPDATE OF email ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_user_email_sync();
```

---

## 3. Row Level Security (RLS) Policies

Every table touched by the app requires RLS enabled with explicit policies. Special attention must be given to preventing users from editing restricted columns (like `user_type` verification status).

```sql
-- REQUIRED: Enable RLS on profiles table
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- GENERAL: Allow authenticated users to view all user profiles
DROP POLICY IF EXISTS "Profiles are viewable by authenticated users" ON public.profiles;
CREATE POLICY "Profiles are viewable by authenticated users" ON public.profiles
  FOR SELECT TO authenticated USING (true);

-- REQUIRED FOR EMAIL FLOW & PROFILE EDIT: Users can update their own profile, but cannot escalate user_type
DROP POLICY IF EXISTS "Users can update own profile without changing user_type" ON public.profiles;
CREATE POLICY "Users can update own profile without changing user_type" ON public.profiles
  FOR UPDATE TO authenticated
  USING (id = auth.uid())
  WITH CHECK (id = auth.uid() AND (user_type IS NOT DISTINCT FROM OLD.user_type));

-- GENERAL: Enable RLS on user_keys and key_backups
ALTER TABLE public.user_keys ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own keys" ON public.user_keys;
CREATE POLICY "Users can manage own keys" ON public.user_keys
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

ALTER TABLE public.key_backups ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own key backups" ON public.key_backups;
CREATE POLICY "Users can manage own key backups" ON public.key_backups
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- GENERAL: Enable RLS on verification_requests
ALTER TABLE public.verification_requests ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view and insert own verification requests" ON public.verification_requests;
CREATE POLICY "Users can view and insert own verification requests" ON public.verification_requests
  FOR SELECT, INSERT TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());
```

---

## 4. Supabase Dashboard Settings

The change-email flow and authentication depend heavily on the following Supabase Dashboard configurations:

1. **Authentication > URL Configuration > Site URL / Redirect URLs:**
   * Must include `amplify://auth-callback/**` so that email confirmation and recovery deep-links correctly return control to `AuthCallbackActivity` inside the Android app. *(Check this in dashboard)*
2. **Authentication > Providers > Email:**
   * **Confirm email:** Must be **Enabled** so Supabase sends verification emails when users sign up or request an email change. *(Check this in dashboard)*
   * **Secure email change:** Must be **Enabled** (requires confirmation from both the old and new email addresses before updating). *(Check this in dashboard)*
3. **Authentication > Email Templates > Change Email Address:**
   * Template body must include the confirmation link pointing correctly to the redirect flow. *(Check this in dashboard)*

---

## 5. Potential Failure Risks & Edge Cases

* **Pending Email State (`new_email`):** While an email change is pending confirmation, `auth.users.email` remains the *old* email, and the new address resides temporarily in `new_email`. The app's `AccountRepository` reads `new_email` via `/auth/v1/user` to display pending status.
* **Old Email Active During Transition:** Until the user clicks the confirmation link in their email inbox, Supabase continues to authenticate them using their old email and password.
* **Session / Token Refresh:** After an email change is confirmed in the browser/app callback, existing local JWT tokens still reflect the old email until refreshed or re-authenticated. The app handles session tokens via `SessionManager` and `SupabaseApi`.
* **Trigger Synchronization Delay:** The `on_auth_user_email_updated` trigger updates `public.profiles.email` asynchronously when Supabase commits the email change in `auth.users`.

---

## 6. Supabase Dashboard Checklist (Ordered Steps)

1. **Enable Email Provider & Confirmations:** Go to *Authentication > Providers > Email*, turn on **Confirm Email** and **Secure Email Change**.
2. **Configure Redirect URLs:** Go to *Authentication > URL Configuration*, add `amplify://auth-callback/**` to the allowed redirect URLs list.
3. **Run Database Migrations / SQL:** Execute the SQL snippets in Sections 2 and 3 in the Supabase SQL Editor to ensure `profiles.email` exists, RLS policies protect profile updates (preventing unauthorized `user_type` changes), and the email sync trigger is active.
4. **Customize Email Templates:** Verify the *Change Email Address* email template under *Authentication > Email Templates* contains the correct confirmation action link.

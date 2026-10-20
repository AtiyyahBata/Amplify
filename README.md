# Amplify — Secure Community & Messaging Android Application

**Amplify** is a feature-rich, high-performance Android application built with Java, XML layouts, and ViewBinding. It provides a robust platform for community engagement, encrypted real-time messaging, verified user identities, content sharing, AI assistance, and comprehensive administrative moderation.

---

## 🚀 What the Application Does

Amplify connects users through a secure and interactive ecosystem with the following core modules:

1. **Community Feed & Social Sharing**
   - **Home Feed (`HomeFragment`)**: Browse community posts, interact with content, and view detailed post discussions (`ViewPostActivity`).
   - **Post Creation & Management (`PostFragment`, `PostingDetailsActivity`)**: Compose and publish rich posts with media zoom (`ZoomImageView`), report inappropriate content (`ReportPostSheet`), and manage post deletion (`PostDeleter`).

2. **Secure & Real-Time Messaging**
   - **Direct & Group Chats (`MessagingFragment`, `ConversationActivity`)**: Send and receive messages in real time (`MessagingRealtime`).
   - **End-to-End Encryption (`ChatCrypto`, `ChatKeyManager`)**: Advanced cryptographic security ensuring private communications.
   - **Lexi AI Assistant (`Artificial_Intelligence`, `AiChatDetailActivity`)**: Integrated AI chat capabilities for intelligent user interaction and support.

3. **User Authentication & Profile Management**
   - **Onboarding (`WelcomeActivity`, `LoginActivity`, `RegisterActivity`, `ResetPasswordActivity`)**: Seamless authentication flows with session handling (`SessionManager`, `SessionExpiry`).
   - **Profiles & Avatars (`ProfileFragment`, `ProfileSettingsActivity`, `AvatarRepository`)**: Customizable user profiles, avatar loading & caching (`AvatarLoader`), and account settings.
   - **Identity Verification (`GetVerifiedActivity`, `VerificationRepository`)**: Secure verification workflow to build trust within the community.

4. **Search & Discovery**
   - **Search (`SearchFragment`, `SearchAdapter`)**: Fast, multi-faceted search across community posts, users, and resources.

5. **Administrative Moderation Portal**
   - **Admin Dashboard (`AdminMainActivity`)**: Dedicated control panel for administrators.
   - **Verification Review (`AdminVerifyFragment`)**: Inspect and process user verification requests.
   - **Report Moderation (`AdminReportsFragment`, `AdminReportDetailActivity`)**: Review and resolve user-submitted content reports.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Java (strict adherence to modern OOP principles)
- **UI Architecture**: Single-Activity hosting multiple Fragments (for persistent bottom navigation and tab bars) combined with targeted Activities for deep navigation flows.
- **UI Binding**: ViewBinding for type-safe view references.
- **Backend & Networking**: Supabase backend integration (`SupabaseApi`, `SupabaseConfig`) backed by modular repositories (`AccountRepository`, `MessagingRepository`, `ChatRepository`, etc.).
- **Security**: Custom cryptographic key management and message encryption (`ChatCrypto`, `ChatKeyManager`).
- **Real-Time Data**: Live messaging sync via `MessagingRealtime`.

---

## 📂 Project Structure

```
app/src/main/java/com/amplify/app/
├── adapters/               RecyclerView adapters for lists (PostAdapter, MessagePreviewAdapter,
│                           DirectMessageAdapter, ChatHistoryAdapter, VerifyRequestAdapter, ReportAdapter, etc.)
├── data/                   Backend & storage repositories (AccountRepository, MessagingRepository,
│                           ChatRepository, AdminVerificationRepository, AvatarRepository, ChatCrypto, MessagingRealtime, etc.)
├── models/                 Plain data classes (Post, Comment, MessagePreview, ChatMessage,
│                           DirectMessage, VerifyRequest, Report, SystemMessage, SearchResult)
└── ui/
    ├── onboarding/         WelcomeActivity, LoginActivity, RegisterActivity, ResetPasswordActivity, SetNewPasswordActivity, AuthCallbackActivity
    ├── main/               MainActivity (hosts bottom nav)
    │   ├── home/           HomeFragment
    │   ├── search/         SearchFragment
    │   ├── post/           PostFragment
    │   ├── messages/       MessagesFragment
    │   └── profile/        ProfileFragment
    │   └── artificial_intelligence/ Artificial_Intelligence, AiChatDetailActivity, AiChatHistoryActivity
    ├── content/            ViewPostActivity, ProfileOtherActivity, PostDeleter, ReportPostSheet, ZoomImageView
    ├── messaging/          NewMessageActivity, ConversationActivity, LexiMessagesActivity
    ├── settings/           ProfileSettingsActivity, GetVerifiedActivity
    └── admin/              AdminMainActivity, AdminLogin, AdminVerificationDetailActivity, AdminReportDetailActivity
        ├── home/           AdminHomeFragment
        ├── verify/         AdminVerifyFragment
        └── reports/        AdminReportsFragment

app/src/main/res/
├── layout/                 XML layouts for activities, fragments, and list row items (activity_*.xml, fragment_*.xml, item_*.xml)
├── drawable/               Custom shape backgrounds, vectors, and graphical assets
├── values/                 Colors, themes, and string resources
└── menu/                   Bottom navigation and option menus (bottom_nav_menu.xml, menu_post_options.xml)
```

---

## 🚀 Getting Started & Setup

1. **Open in Android Studio**:
   - Launch Android Studio and select **File → Open**.
   - Navigate to the root directory containing `settings.gradle`.
2. **Gradle Sync**:
   - Allow Android Studio to automatically sync and download required Gradle dependencies.
3. **Backend Configuration**:
   - Update Supabase configuration endpoints in [`SupabaseConfig.java`](file:///app/src/main/java/com/amplify/app/data/SupabaseConfig.java) if connecting to a live instance.
4. **Run the App**:
   - Select an emulator or physical device (Min SDK 24 / Android 7.0+).
   - Click **Run** (`Shift + F10`). `WelcomeActivity` serves as the initial launcher screen.

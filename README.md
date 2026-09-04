# Amplify — Android Studio Project (Navigation Prototype)

This is a real Android Studio project (Java, XML layouts, ViewBinding). Every
screen from the wireframe section of the master reference document has its
own Activity or Fragment **and** its own XML layout file — nothing is one
giant file. Every button navigates to the next real screen, but **no data is
saved or sent anywhere yet**. All content on screen comes from
`data/DummyData.java`, a single class that hands out placeholder lists so the
UI has something realistic to show.

This is the backbone described in the brief: wire in Firebase, real
authentication, and real Firestore reads/writes behind the buttons that
already exist, without needing to touch the layouts or navigation.

## How to open it

1. Open Android Studio → **File → Open** → select the `AmplifyAndroid` folder
   (the one containing `settings.gradle`).
2. Let Android Studio sync Gradle. It will download the correct Gradle
   version itself the first time — this project does not ship a Gradle
   wrapper jar, so if Studio shows a wrapper warning, click **"Try Again"**
   or use **File → Sync Project with Gradle Files**; Studio regenerates it
   automatically.
3. Run on an emulator or device (min SDK 24 / Android 7.0+).
4. `WelcomeActivity` is the launcher screen.

## Project structure

```
app/src/main/java/com/amplify/app/
├── ui/
│   ├── onboarding/        WelcomeActivity, LoginActivity, RegisterActivity, ResetPasswordActivity
│   ├── main/               MainActivity (hosts the bottom nav)
│   │   ├── home/           HomeFragment       — community feed
│   │   ├── search/         SearchFragment     — AI-powered resource search
│   │   ├── post/           PostFragment       — compose a new post
│   │   ├── messages/       MessagesFragment   — conversation list
│   │   └── profile/        ProfileFragment    — the logged-in user's own profile
│   ├── postflow/           PostingDetailsActivity — review/publish step
│   ├── content/            ViewPostActivity, ProfileOtherActivity
│   ├── messaging/          NewMessageActivity, ConversationActivity
│   ├── settings/           ProfileSettingsActivity, GetVerifiedActivity
│   └── admin/              AdminMainActivity (hosts the admin top tabs)
│       ├── home/           AdminHomeFragment
│       ├── verify/         AdminVerifyFragment
│       └── reports/        AdminReportsFragment
├── adapters/               One RecyclerView adapter per list (PostAdapter, MessagePreviewAdapter,
│                           SearchResultAdapter, VerifyRequestAdapter, ReportAdapter)
├── models/                 Plain data classes (Post, Comment, MessagePreview, ChatMessage,
│                           SearchResult, VerifyRequest, Report)
└── data/                   DummyData.java — the only place placeholder content lives

app/src/main/res/
├── layout/                 One XML file per screen (activity_*.xml / fragment_*.xml)
│                           and one per list row (item_*.xml)
├── drawable/                Shape backgrounds and hand-drawn vector icons
├── values/                  colors.xml, themes.xml, strings.xml
└── menu/                    bottom_nav_menu.xml (the 5-tab bottom bar)
```

### Why Fragments for the 5 bottom-nav tabs, but Activities everywhere else?

Home / Search / Post / Messages / Profile share one `BottomNavigationView`
inside `MainActivity`. If each were its own Activity, the whole nav bar would
be destroyed and rebuilt every time someone taps a tab, which flickers and
loses scroll position. Fragments solve that — the nav bar stays put and only
the content above it swaps. Every other screen (things you navigate *into*,
like a single post or a settings page) is a normal Activity, exactly as
asked, since those don't need to share a persistent nav bar.

The same pattern is used again for the Admin panel: `AdminMainActivity` hosts
three tab fragments (Home / Verify Users / Reports) behind its own top tab
bar, for the same reason.

## Where to plug in the real backend

Everything that currently only updates the screen or shows a `Toast` is
marked with a short comment in the relevant Activity/Fragment pointing to the
matching part of the reference document, for example:

- `LoginActivity` / `RegisterActivity` → Firebase Authentication (Part H.3)
- `PostFragment` / `PostingDetailsActivity` → Firestore `Posts` collection,
  enforce FR9 (verification required to publish) here
- `GetVerifiedActivity` → Cloud Storage upload + Google Cloud Vision (Part H.4)
- `SearchFragment` → Gemini API call (Part H.4)
- `NewMessageActivity` / `ConversationActivity` → Firestore `Messages` collection
- `AdminVerifyFragment` / `AdminReportsFragment` → write decisions back to
  `VerificationRequests` and the reports collection

`DummyData.java` is designed to be replaced method-by-method with real
repository/Firestore calls — the UI code calling it does not need to change
shape, since it already works off `List<Post>`, `List<MessagePreview>`, etc.

## What's intentionally not included

- No Firebase dependencies or `google-services.json` (per the "fully offline
  for now" choice) — add these yourself once you're ready to connect a real
  Firebase project.
- No data persistence of any kind — closing the app resets everything.
- No unit/UI tests yet.

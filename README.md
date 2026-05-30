# Eskerra Go

Inbox-first notes app for Android. This is step 1: a Jetpack Compose UI skeleton
with Material 3, centralized navigation, a floating-button shell, and fake data.
It proves the UI shape only.

There is intentionally no real Git, filesystem, database, Room, DataStore,
background sync, auth, or multi-module setup yet.

## Module layout (single module, package boundaries)

```
app/
  MainActivity.kt
  app/        App.kt, AppRoute.kt, AppShell.kt
  ui/theme/   Material 3 theme
  core/model/ NoteId, NoteSummary, Workspace, WikiLink
  feature/    inbox, add, note, podcasts, dashboard, menu (stateless screens)
  data/       notes, workspace, git (explicit fake data objects only)
```

Feature screens are stateless: route-level composables in `app/App.kt` read the
fake data and pass state plus callbacks into the screens.

## Building

This project uses the Gradle wrapper, but the wrapper JAR is not committed and
must be generated locally (no wrapper binaries are hand-authored).

1. Make sure you have a JDK 17 and the Android SDK installed (Android Studio
   provides both). Point the SDK via `local.properties` (`sdk.dir=...`) or the
   `ANDROID_HOME` environment variable.
2. Generate the Gradle wrapper (requires a local Gradle install, version 8.9+):

   ```bash
   gradle wrapper --gradle-version 8.11.1
   ```

   Alternatively, simply open the project in Android Studio, which provisions the
   wrapper automatically.
3. Build the debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

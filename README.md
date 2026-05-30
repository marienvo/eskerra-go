# Eskerra Go

Inbox-first notes app for Android. Step 1 proved the Compose UI shape; Step 2
added an isolated Git spike; Step 3 adds workspace setup with DataStore
persistence and an app-start gate. Inbox and Note still use fake data until Step 4.

## Module layout (single module, package boundaries)

```
app/
  MainActivity.kt
  app/        AppRoot.kt, App.kt, AppRoute.kt, AppShell.kt
  ui/theme/   Material 3 theme
  core/model/ NoteId, NoteSummary, Workspace, WorkspaceConfig, WikiLink
  feature/    inbox, add, note, podcasts, dashboard, menu, setup (stateless screens)
  data/       notes, workspace (store + setup), git
```

Feature screens are stateless: route-level composables in `app/App.kt` read the
fake data and pass state plus callbacks into the screens.

## Building

This project uses the Gradle wrapper. **Use JDK 17** to run Gradle and compile
the app. Newer JDKs (for example JDK 21 or 25) are not supported by the current
Android Gradle Plugin and will fail the build with a version error.

Set `JAVA_HOME` to a JDK 17 install before running Gradle, for example:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:testDebugUnitTest
```

If the wrapper JAR is missing locally, generate it once (requires Gradle 8.9+ on JDK 17):

```bash
gradle wrapper --gradle-version 8.11.1
```

1. Make sure you have **JDK 17** and the Android SDK installed (Android Studio
   provides both). Point the SDK via `local.properties` (`sdk.dir=...`) or the
   `ANDROID_HOME` environment variable.

   Create `local.properties` in the project root (gitignored, machine-specific):

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

   Example on Linux with the default SDK location:

   ```properties
   sdk.dir=/home/you/Android/Sdk
   ```

2. Build the debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

Run tests:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # device/emulator
```

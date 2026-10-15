# Ivy Time

Time tracker built the same way as [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet): same
architecture (data → domain → UI, MVI with Compose-runtime ViewModels, `Either` instead of
exceptions, Hilt, Room, Material3) and the same look (Ivy palette, gradient cards, round FAB).

| Ivy Wallet      | Ivy Time                          |
|-----------------|-----------------------------------|
| Transaction     | `TimeEntry` (start, end, note)    |
| Category        | `Project` (name, color)           |
| Balance header  | "Today" / running timer header    |
| Reports         | Weekly / monthly totals per project |

## Modules
- `:domain` – pure Kotlin: models, repository interfaces, use cases, summary logic (unit-tested)
- `:data` – Room database + repository implementations + Hilt module
- `:design` – Ivy colors, Material3 theme (copied from Ivy Wallet) and shared components
- `:app` – `home` (timer + entries), `projects`, `reports`, bottom-bar navigation

## Run
Open in Android Studio, or `./gradlew :app:installDebug`.
Domain tests: `./gradlew :domain:test`.

## Get an installable APK
- **GitHub Actions:** push the repo to GitHub, open *Actions → Build APK → Run workflow*, then download
  the `ivy-time-debug-apk` artifact (`app-debug.apk`, signed with the debug key).
- **Locally:** `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- **Install:** copy the APK to the phone and open it (allow "install unknown apps"), or `adb install -r app-debug.apk`.

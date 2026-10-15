# Ivy Time

Time tracker built the same way as [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet): the same
architecture (data → domain → UI, MVI with Compose-runtime ViewModels, `Either` instead of
exceptions, Hilt, Room, Material3) and the same look (Ivy palette, gradient cards, round buttons).

| Ivy Wallet       | Ivy Time                                        |
|------------------|-------------------------------------------------|
| Transaction      | `TimeEntry` — start, end, note                  |
| Category         | `Category` — name, colour, icon                 |
| Account          | `Project` — a longer effort inside a category   |
| Planned payment  | `PlannedEntry` — one-time or recurring          |
| Budget           | `Goal` — at least / at most, per week or month  |
| Balance header   | Today / running timer header                    |
| Pie chart report | Donut chart per category + per-day bars         |

## What you can do
- **Log time by hand**: pick a category, a date, a start and an end. Defaults to today and now,
  both are editable; quick chips set 15m … 8h in one tap. A night shift that ends after midnight
  is handled.
- **Run a timer**: tap a category on the home screen to start, the red button stops it.
- **Plan**: daily, weekly or monthly plans; ✓ turns a plan into a record and moves it on.
- **Goals**: at least 5h of Sport per week, at most 3h of Social, with a progress bar.
- **Reports**: donut per category, bars per day, totals per project, for a day, week, month or year.
- **Settings**: theme (light / dark / amoled), week start, 12/24-hour clock, CSV export and import.

## Modules
- `:domain` — pure Kotlin: models, repository interfaces, use cases, summaries, CSV (unit-tested)
- `:data` — Room database, repositories, DataStore settings, Hilt module
- `:design` — Ivy colours and Material3 theme (from Ivy Wallet), charts, shared components
- `:app` — `home`, `entry`, `categories`, `projects`, `planned`, `goals`, `reports`, `settings`

## Get an installable APK
- **GitHub Actions:** push, then *Actions → Build APK → Summary → Artifacts → `ivy-time-debug-apk`*.
- **Locally:** `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- **Install:** open the APK on the phone (allow "install unknown apps"), or `adb install -r`.

Domain tests: `./gradlew :domain:test`.

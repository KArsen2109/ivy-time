# Ivy Time

A day planner and time tracker for Android, built in Kotlin and Jetpack Compose.

## The idea

The day is one hour ruler with two lanes beside it: **what you planned** on the left, outlined,
and **what you actually did** on the right, filled in. The gap between the lanes is the point of
the screen. A summary line above it says how much of the plan you kept and how much time no plan
asked for.

```
      plan        actual
 08 ┌──────────┬──────────┐
    │ Work     │ Work     │
 09 │          │          │
 10 ├──────────┼──────────┤
 ───┼──────────┴──────────┤  now
 11 │ Gym      │          │
 12 └──────────┴──────────┘
```

## What you can do

- **Plan in blocks.** Tap the plan lane at any hour to drop a block there. A block has a start,
  an end, a date and a repeat rule: once, every day, chosen weekdays, or every month. Occurrences
  are worked out per day, so a past day keeps the plan it had rather than losing it when the
  series moves on.
- **Act on a block.** Tap it to start a timer from it, tick it off (which logs exactly the time
  it reserved, never time that has not passed), edit it or delete it.
- **Log time by hand.** Category, date, start and end, defaulting to the open day and now, with
  one-tap lengths from 15 minutes to 8 hours. A record that ends after midnight is handled.
- **Run a timer.** Tap a category to start; the running block is outlined in amber on the lane.
- **See the week.** Seven columns in the same two-lane language, plus weekly and monthly goals.
- **Review.** Donut per category, bars per day, totals per project, over a day, week, month or year.
- **Settings.** Theme including amoled, week start, 12/24-hour clock, CSV export and import.

## Design

The chrome is near monochrome, so the only colour on a screen belongs to the categories and a
glance reads as data rather than decoration. Amber is reserved for two things and nothing else:
the present moment and a running timer. Type is Instrument Sans with tabular figures, so times do
not jitter in a column. The hour rules are the only structural lines in the app.

## Modules

- `:domain` — pure Kotlin: models, repository interfaces, use cases, plan occurrences, plan
  versus actual, CSV. Unit-tested.
- `:data` — Room database, repositories, DataStore settings, Hilt wiring
- `:design` — tokens, theme, the timeline and week strip, charts, shared components
- `:app` — `day`, `week`, `plan`, `entry`, `categories`, `projects`, `goals`, `reports`, `settings`

Architecture follows [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet): data to domain to UI,
MVI with Compose-runtime view-models, and `Either` instead of thrown exceptions.

## Build

- **APK from CI:** push, then *Actions, Build APK, Summary, Artifacts, `ivy-time-debug-apk`*.
- **Locally:** `./gradlew :app:assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.
- **Tests:** `./gradlew :domain:test`.

Fonts are Instrument Sans under the SIL Open Font License; see `design/NOTICE-fonts.txt`.

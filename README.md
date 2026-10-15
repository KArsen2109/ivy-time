# Zham

A day planner and time tracker for Android, built in Kotlin and Jetpack Compose. The name is
ժամ, the Armenian word for hour.

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

- **Plan in blocks.** Tap the plan lane at any hour to drop a block there. A block has a title, a
  description, a category and project, a start, an end, a date and a repeat rule: once, every day,
  chosen weekdays, or every month. Occurrences are worked out per day, so a past day keeps the plan
  it had.
- **Act on a block.** Tap it to start a timer from it, tick it off (which logs exactly the time it
  reserved, never time that has not passed), edit it or delete it.
- **Log time by hand.** Category, project, date, start and end, defaulting to the open day and now.
  Times are typed on a number pad (1, 3, 2, 0 gives 13:20), with one-tap lengths from 15 minutes
  to 8 hours. A record that ends after midnight is handled.
- **Run a timer.** The ring, the digits and the button wear the colour of the category being
  tracked, and the stopped timer lands in today's list under that category.
- **Find any day.** A month calendar, with a dot on every day that has something on it, opens from
  the Day and Week screens.
- **See the week.** Seven columns in the same two-lane language, plus weekly and monthly goals.
  Open a goal to see every record its total is made of.
- **Review.** Donut per category, a bar per day, totals per project, over a day, week, month, year
  or a custom range, filtered by category. One day lists its records instead of a single bar.
- **Organise.** Categories with 46 icons and 24 colours. A project always belongs to one category,
  and picking a category narrows the projects you can pick.
- **Back up.** CSV export and import, theme (including amoled), week start, 12 or 24-hour clock.

## Mark

A hand standing on its pivot, with a violet trail that sweeps across it and fades in from nothing.
The amber dot at the pivot is the present moment, the same amber the app reserves for "now".

## Design

The chrome is near monochrome, so the only colour on a screen belongs to the categories and a
glance reads as data rather than decoration. Amber is reserved for two things and nothing else:
the present moment and a running timer. Type is Instrument Sans with tabular figures, so times do
not jitter in a column. Dates are always written in English, whatever the phone's language is.

## Modules

- `:domain` — pure Kotlin: models, repository interfaces, use cases, plan occurrences, plan
  versus actual, goal records, CSV. Unit-tested.
- `:data` — Room database (with a real 3 to 4 migration), repositories, DataStore settings, Hilt
- `:design` — tokens, theme, the timeline, week strip, charts, calendar, number pad, components
- `:app` — `day`, `week`, `timer`, `plan`, `entry`, `categories`, `projects`, `goals`, `reports`,
  `more`

Data goes through a domain layer to MVI view-models, and a function that can fail returns `Either`
instead of throwing.

## Build

- **APK from CI:** push, then *Actions, Build APK, Summary, Artifacts, `zham-debug-apk`*.
- **Locally:** `./gradlew :app:assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.
- **Tests:** `./gradlew :domain:test`.

Fonts are Instrument Sans under the SIL Open Font License; see `design/NOTICE-fonts.txt`.

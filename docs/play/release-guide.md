# Publishing Zham on Google Play

Steps for the developer. Everything the build needs is already in the repo; what is left is
accounts, a key, forms and a few images.

## 0. Before anything else

- **Licence.** The repo has no LICENSE file on purpose: choose one yourself (or keep the code
  private). No source code was copied from Ivy Wallet (checked by comparing line windows against
  its repository: no overlap), so its GPL-3.0 does not apply to this code. The Gradle wrapper
  scripts come from the Gradle project (Apache-2.0) and the Instrument Sans font is under the SIL
  Open Font License (see `design/NOTICE-fonts.txt`).
- **Name and package.** The package name `com.zham.app` is permanent once uploaded. Search Play
  for "Zham" to make sure the name is free to use.
- **Move your data.** The app on your phone is signed with a different key from the Play version,
  so the Play version cannot update it in place. Before installing from Play: More → *Back up
  everything*, install, then More → *Restore or import*.

## 1. Developer account

1. https://play.google.com/console/signup, one-off fee of 25 USD, verify your identity.
2. A *personal* account created after 13 Nov 2023 must run a closed test with at least 12
   testers who stay opted in for 14 days in a row before production access. Start recruiting
   early. An organisation account is exempt.

## 2. Create the upload key (once, on your computer)

```
keytool -genkeypair -v -keystore zham-upload.jks -alias zham-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

- Keep `zham-upload.jks` and its passwords somewhere safe **outside the repo**. Never commit it.
- Google re-signs the app with its own key (Play App Signing), so a lost upload key can be reset by
  Google support; losing it is annoying, not fatal.

In GitHub: repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
| --- | --- |
| `ZHAM_UPLOAD_KEYSTORE_BASE64` | `base64 -w0 zham-upload.jks` (on macOS: `base64 -i zham-upload.jks`) |
| `ZHAM_UPLOAD_STORE_PASSWORD` | the keystore password |
| `ZHAM_UPLOAD_KEY_ALIAS` | `zham-upload` |
| `ZHAM_UPLOAD_KEY_PASSWORD` | the key password |

## 3. Build

Every push to GitHub runs *Build APK*. Open the run → Artifacts:

- `zham-play-bundle` → `app-release.aab`, the file to upload. Signed with your upload key once the
  secrets exist; unsigned before that.
- `zham-release-check-apk` → the same shrunk code as an APK signed with the debug key. **Install it
  on your phone and go through the app before uploading**: shrinking can break things that the
  normal debug build does not show.
- `zham-mapping` → `mapping.txt`; upload it in Play Console (see step 6) for readable crash reports.
- `zham-debug-apk` → the everyday test build.

To release a new version raise `versionCode` (by at least 1 every upload) and `versionName` in
`app/build.gradle.kts`.

## 4. Create the app in Play Console

All app → *Create app*: name "Zham: Day Planner & Time Log" (see `store-listing.md`), default
language English, App, Free. Then work through *Dashboard → Set up your app*:

| Section | What to answer |
| --- | --- |
| Privacy policy | Publish `privacy-policy.md` somewhere public (for example GitHub Pages from a public repo) and paste the URL. Fill in your name and email in it first. |
| App access | All functionality is available without login. |
| Ads | No, the app has no ads. |
| Content rating | Fill the questionnaire honestly: no violence, no user-generated content shared with others, no purchases. Expect an "Everyone" rating. |
| Target audience | 18+ or "13 and over"; not designed for children. |
| Data safety | Data collected: **none**. Data shared: **none**. The app has no internet permission. Everything stays on the device. |
| Government / financial / health apps | None apply. |
| News app | No. |
| Exact alarm permission | The app declares `SCHEDULE_EXACT_ALARM` so reminders for planned blocks arrive on time. If the form asks, say: calendar-style reminders the user sets for planned events. If Google objects, delete the permission line from `AndroidManifest.xml`; reminders then still work but may run a few minutes late. |
| Store listing | Text from `store-listing.md`. Images below. |

### Images

- App icon 512×512 PNG (export the adaptive icon from `app/src/main/res/drawable`, or make it from the
  same mark on the `#0B1220` background).
- Feature graphic 1024×500 PNG.
- Phone screenshots: at least 2, up to 8 (16:9 or 9:16, 320–3840 px). Suggested: Day (plan beside
  actual), Timer, Week, Report with the grey donut, Goals, Plan a block with the reminder row.
  Take them on your phone in **light and dark**; fill the app with sample data first.

## 5. Test tracks

1. *Testing → Internal testing*: create a release, upload the `.aab`, add yourself as a tester,
   install from the Play link. Quick, no review wait.
2. *Testing → Closed testing*: same bundle, add the 12+ testers (a Google Group or email list), keep
   them opted in for 14 days.
3. *Production → Apply for access*, then create the production release.

## 6. Every release

- Upload the `.aab`; add release notes.
- *App bundle explorer / Deobfuscation files*: upload `mapping.txt` from the same build.
- Check *Pre-launch report* and *Android vitals* after publishing.

## 7. If something is rejected

Typical reasons for an app like this: missing privacy policy URL, wrong Data safety answers
(this app collects nothing), the exact-alarm declaration, or a screenshot problem. The rejection
email names the policy; fix and resubmit.

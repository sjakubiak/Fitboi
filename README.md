# Fitboi

An Android app in the style of Google Health for the Fitbit Air. It reads your activity, sleep and heart data from the
[Google Health API](https://developers.google.com/health/about) (the successor to the Fitbit Web API) and shows it on
four screens:

- **Today**: a readiness estimate, step and active-zone-minute rings, and tiles for distance, calories, resting heart
  rate, HRV, sleep and workouts.
- **Activity**: steps and active zone minutes for the last 7 days against your goals, distance, calories, and recent workouts.
- **Sleep**: last night's sleep stages, time in each stage, and time asleep for the last 7 nights.
- **Heart**: today's heart rate, plus 30-day resting heart rate and HRV trends.

Your Fitbit Air syncs to the Google Health app on your phone, and Fitboi reads the data from Google's servers. It
can't talk to the band directly.

> **Readiness** is Fitboi's own estimate. It compares last night's HRV and resting heart rate with your 14-day
> baseline and adjusts for sleep. Google's Readiness and Sleep scores aren't available through the API.

## Setup (one time, about 10 minutes)

The Google Health API only works for Google accounts you add as test users, so you need your own Google Cloud project.

1. Sign in to the **Google Health** app on your phone with the Google account your Fitbit Air uses. The API can't
   return data until you've done this.
2. In the [Google Cloud console](https://console.cloud.google.com/), create a project, then enable the
   **Google Health API** (search for it under *APIs & Services → Library*).
3. Go to *Google Auth Platform* (the OAuth consent screen):
   - **Branding**: choose any app name, for example Fitboi, and your email.
   - **Audience**: leave it as *External* and *Testing*, then add your Google account under **Test users**.
   - **Data access**: add these scopes:
     - `.../auth/googlehealth.activity_and_fitness.readonly`
     - `.../auth/googlehealth.health_metrics_and_measurements.readonly`
     - `.../auth/googlehealth.sleep.readonly`
4. Under **Clients**, create an OAuth client of type **Android** with:
   - Package name: `io.github.sjakubiak.fitboi`
   - SHA-1 certificate fingerprint: `E6:93:8E:03:F8:44:C9:42:53:51:57:21:2E:2B:F9:16:B6:DA:89:00`

   No client ID or secret goes into the app. Google matches the app by its package name and signing key.
5. Install the APK (see below), open Fitboi, tap **Connect Google Health**, and approve access. You'll see an
   "unverified app" warning because the app is in testing mode. Tap *Continue*.

In testing mode Google expires your approval after 7 days. When that happens Fitboi shows the approval screen again.

## Getting the APK

Every push runs the **Android** workflow in GitHub Actions. Open the latest run under the *Actions* tab, download the
`fitboi-apk` artifact, unzip it, and install `app-debug.apk` on your phone (allow installs from your browser or files
app when asked).

To build it yourself, run `./gradlew :app:assembleDebug` with the Android SDK installed (Android Studio is the
easiest way to get it). The APK lands in `app/build/outputs/apk/debug/`.

## Project layout

- `core/`: plain Kotlin. Contains the Google Health API client (`HealthApi`), mapping into app models
  (`HealthRepository`), and the readiness calculation. Run its tests with `./gradlew :core:test`.
- `app/`: the Android app. Uses Jetpack Compose and Google Play services `AuthorizationClient` for sign-in.

The app is signed with `app/fitboi-debug.keystore`, which is committed so that every build has the SHA-1 above. It's
a debug key for personal use. Only accounts you add as test users can grant access, so sharing it is low risk.
Generate your own key before publishing the app anywhere.

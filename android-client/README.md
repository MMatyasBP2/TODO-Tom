# TODO Tom (Android)

Android client for the TODO Tom backend, built with Kotlin + Jetpack Compose.

## Features

- Task list (To Do + Done sections)
- Create new tasks
- Edit tasks (title, description, completion state)
- Delete tasks
- Quick status toggle (To Do <-> Done)
- Priority selection (LOW / MEDIUM / HIGH)
- Due date and reminder time picker
- Reminder notifications (WorkManager)
- Category selection or AUTO categorization on the backend
- Recurring tasks (NONE / DAILY / WEEKLY / MONTHLY)

## Important network setting

Default API URL for the emulator:

- `http://10.0.2.2:8080/`

This is configured in [app/build.gradle.kts](app/build.gradle.kts) as the `BuildConfig.BASE_URL` value.

The app uses HTTP for the local backend, so Android cleartext traffic is enabled in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) (`android:usesCleartextTraffic="true"`).

If you run the app on a physical phone, change this to your machine's local IP address (for example `http://192.168.1.50:8080/`).

## Run steps in Android Studio

1. Start the backend application (`TODO Tom`) so it is available on port `:8080`.
2. Open this folder as a project in Android Studio: `TaskManagerApp/android-client`.
3. Wait for Gradle sync to finish (dependency download).
4. Start an emulator or connect a physical phone.
5. Verify the `BuildConfig.BASE_URL` setting:
   - emulátor: `http://10.0.2.2:8080/`
   - physical device: `http://<your machine's IP>:8080/`
6. Click Run on the `app` module.

## Usage

- New task: enter the title/description in the top form, then tap `Create task`.
- Delete: tap the task card or the `Delete` chip.
- Edit: `Edit` chip -> dialog -> `Save`.
- Status toggle: `Mark done` / `Move back to TODO`.

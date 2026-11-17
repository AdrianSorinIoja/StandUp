# StandUp - Move Reminder

Native Android app (Kotlin, Jetpack Compose, Material 3). No backend, no accounts, no internet permission.
Minimum Android 10 (API 29), target/compile SDK 35.

## Build & install

**Requirements:** JDK 17, Android SDK 35 (Android Studio installs both).

### Android Studio
1. *File > Open* this folder, let Gradle sync.
2. Run on a device/emulator, or *Build > Build APK(s)*.

### Command line
```bash
./gradlew testDebugUnitTest          # unit tests (active-hours logic)
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Set `ANDROID_HOME` (or create `local.properties` with `sdk.dir=...`) if Gradle cannot find the SDK.

### Release signing
Without a keystore the release APK is signed with the debug key (installable, not for the Play Store).
For a proper key run `scripts/make-keystore.sh`; it writes `keystore.properties`, which the build picks up.
Back up the generated `.jks`: updates must be signed with the same key.

### GitHub Actions
Push to GitHub; `.github/workflows/build.yml` runs the tests and builds both APKs and uploads them as the `standup-apks` artifact.

### Installing on a phone
Copy the APK over, open it, and allow "Install unknown apps" for your file manager/browser when asked.

## Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` (Android 13+) | Show the reminder notification with **Done** / **Snooze** buttons. Asked on first launch. |
| `SCHEDULE_EXACT_ALARM` (Android 12+) | Fire reminders at the exact time even in Doze. User-grantable via *Special app access > Alarms & reminders*; the app links there. Without it reminders fall back to inexact alarms and may be a few minutes late. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep the process alive while the sound repeats every 5 seconds. |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the alarm after reboot. |
| `WAKE_LOCK` | Keep the CPU awake while the sound is repeating. |
| *(no permission)* battery optimisation | The app opens the system screen so you can set StandUp to **Unrestricted**. |
| *(no permission)* custom audio | Chosen via the system file picker (Storage Access Framework); the app keeps read access to that one file only. |

The app declares **no** INTERNET permission.

## How it works

- `Scheduler` uses `AlarmManager.setExactAndAllowWhileIdle` (or `setAndAllowWhileIdle` if exact alarms are not allowed). WorkManager is not used.
- `AlarmReceiver` -> `ReminderController.onAlarm` -> starts `ReminderService` (foreground, `mediaPlayback`), which plays the sound, then the optional spoken message, every 5 seconds.
- **Done / I stood up** (notification or app) sets `ringing=false` first, stops the service (which releases `MediaPlayer` and TextToSpeech in `onDestroy`), counts the stand-up and schedules the next interval.
- **Snooze** (5 or 10 min, optional) stops the audio and schedules a new alarm without counting.
- If the OS refuses to start the foreground service, a high-priority notification with an *insistent* system sound is shown instead, with the same buttons.
- `BootReceiver` restores the alarm after reboot, app update, clock/timezone change and exact-alarm permission change. The app also re-arms the schedule every time it is opened.
- Active hours (including overnight ranges) are applied when scheduling and again when an alarm fires.
- The audio uses the alarm stream, so volume follows the *alarm* volume slider.

## Smartwatch notifications
Smartwatches (Wear OS, Galaxy Watch, Garmin, Fitbit, ...) show phone notifications through their companion app, so no watch app is needed.
When a reminder fires, StandUp posts a dedicated **non-ongoing, high-importance "Smartwatch alerts" notification** with **Done** and **Snooze** buttons (the ongoing foreground-service notification is kept phone-only because watches usually skip ongoing notifications).
- The watch vibrates when the reminder fires and, optionally, on every 5-second repeat (Settings > Smartwatch).
- Tapping Done or Snooze on the watch stops the phone audio exactly like the phone buttons; confirming on the phone removes the watch copy.
- Use *Settings > Smartwatch > Send test to watch* to verify delivery.
- If nothing arrives: enable StandUp in the watch companion app's notification list, disable "mute when phone is in use" style options, check watch Do Not Disturb, and make sure the phone's notification permission is granted. Menu names differ per brand.
- Android 15 may soften alerts when an app posts many notifications in quick succession; turn off the repeat buzz if the watch vibrations feel weak.

## Known limits
- Sounds longer than 5 s are restarted by the next 5-second cycle; use short sounds.
- Some manufacturers (Xiaomi, Huawei, Oppo, Samsung "sleeping apps") add their own battery killers; see https://dontkillmyapp.com and set StandUp to unrestricted/auto-start.
- If the device reboots while a reminder is ringing, the backup notification is shown rather than restarting audio (Android 15 forbids starting media services from the boot broadcast).
- Pausing keeps the remaining time; *Start* resumes from it.

## Manual test plan
1. Settings > **Test reminder in 5 seconds**, lock the phone: sound must start and repeat every 5 s.
2. Tap **Done** in the notification: sound stops immediately, countdown restarts at the full interval, Stats +1.
3. Repeat and tap **Snooze**: sound stops, next reminder after 5/10 min, Stats unchanged.
4. Start a 1-minute interval, reboot the phone: the reminder still fires.
5. Revoke exact alarms / notifications in system settings: the dashboard shows what to fix.
6. Set active hours that exclude now: no reminder fires until the window opens.

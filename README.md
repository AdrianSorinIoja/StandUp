**StandUp - Smart Movement Reminder**  
   
 An Android app that reminds you to stand up and move regularly.  
 Unlike traditional reminder apps, StandUp doesn't just send notifications to your smartwatch. It also uses Text-to-Speech to announce reminders through your phone speaker.  
   
 **Support the project:** [Buy me a coffee on Ko-fi](https://ko-fi.com/adrianioja "https://ko-fi.com/adrianioja")  
   
🚨 **WARNING: AI-GENERATED SOFTWARE – USE AT YOUR OWN RISK**  
This entire application was created using AI. I am not a software developer, and the code has not undergone a professional security audit or independent technical review. This is an open-source personal project provided "AS IS", without warranties of any kind. I accept no responsibility for damage, data loss, security issues, or other problems resulting from its use. Please inspect the source code before installing.  
**By installing or using this application, you accept full responsibility for doing so.**  
   
   
 **StandUp - Move Reminder**  
Native Android app (Kotlin, Jetpack Compose, Material 3). No backend, no accounts, no internet permission.  
   
 Minimum Android 10 (API 29), target/compile SDK 35.  
**Build & install**  
**Requirements:** JDK 17, Android SDK 35 (Android Studio installs both).  
```  
./gradlew testDebugUnitTest          # unit tests (active-hours logic)  
 ./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk  
 ./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk  
 adb install -r app/build/outputs/apk/debug/app-debug.apk  
   
```  
Or open the folder in Android Studio and run it. Set `ANDROID_HOME` (or `local.properties` with `sdk.dir=...`) if Gradle cannot find the SDK.  
   
 Pushing to GitHub also builds both APKs via `.github/workflows/build.yml` (artifact `standup-apks`).  
Release signing: without a keystore the release APK is signed with the debug key. For a proper key run `scripts/make-keystore.sh` (writes the git-ignored `keystore.properties`) and back up the `.jks`.  
**How it works**  
1. Start the countdown on the dashboard (Start / Pause / Resume).  
2. When it reaches zero the alert plays and **the next countdown starts immediately**. **Nothing has to be confirmed.**  
3. With *Repeat alert* on (default), the alert plays N times in total (default 3), every X seconds (default 5, 5-300 s), then stops by itself. With it off it plays once.  
4. Optional **Stop** and **Snooze** buttons (notification, smartwatch, dashboard) end the current alert early; Snooze also delays the next reminder by 5 or 10 minutes.  
Alert = sound (built-in, your own audio file, or none) plus an optional spoken message (Android text-to-speech), plus a copy of the notification for your smartwatch.  
**Settings**  
Interval (1-120 min) · active hours (overnight ranges work) · sound source and preview · volume channel and level · repeat on/off, repeat seconds, number of plays · spoken message · smartwatch alerts · snooze · light/dark theme · permission checklist · "Test reminder in 5 seconds".  
**Volume**  
Settings > Volume chooses the Android volume channel (Alarm by default, or Media / Notification) and sets that channel's real system volume. Alarm plays even in silent mode; Media follows the media volume; Notification follows ring/notification volume. While StandUp is open the phone's volume buttons control the selected channel.  
**Smartwatch**  
Watches (Wear OS, Galaxy Watch, Garmin, Fitbit...) show phone notifications through their companion app. StandUp posts a dedicated, non-ongoing, high-importance notification with Stop/Snooze buttons (the foreground-service notification itself stays phone-only). The watch vibrates when the reminder fires and, optionally, on each repeat. Use *Settings > Smartwatch > Send test to watch* to verify. If nothing arrives: allow StandUp in the watch companion app's notification list, disable "mute when phone is in use" options, check watch Do Not Disturb, and check the phone's notification permission. Android 15 may soften alerts when many are posted in quick succession; turn off the repeat buzz if they feel weak.  
**Permissions**  
  
|  |  |  
| - | - |  
| **Permission** |  **Why** |  
| `POST_NOTIFICATIONS` (Android 13+) |  Show the reminder notification and mirror it to the watch. |  
| `SCHEDULE_EXACT_ALARM` (Android 12+) |  Fire reminders at the exact time even in Doze. Grant via *Special app access > Alarms & reminders* (the app links there). Without it reminders may be a few minutes late. |  
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |  Keep the process alive while the alert repeats. |  
| `RECEIVE_BOOT_COMPLETED` |  Re-arm the alarm after reboot. |  
| `WAKE_LOCK` |  Keep the CPU awake while the alert repeats so timing stays accurate. |  
| *(none)* battery optimisation |  The app opens the system screen so you can set StandUp to **Unrestricted**. |  
| *(none)* custom audio |  Chosen via the system file picker; access is limited to that file. |  
  
   
No INTERNET permission is declared.  
**Background reliability**  
- `AlarmManager.setExactAndAllowWhileIdle` (or inexact `setAndAllowWhileIdle` if exact alarms are not allowed). WorkManager is not used.  
- Alarm -> `ReminderController.onAlarm` -> foreground `ReminderService` (mediaPlayback) plays the alert and stops itself when finished; `onDestroy` always releases audio and speech.  
- If Android refuses to start the service, a normal high-priority notification with the system sound is shown instead.  
- `BootReceiver` restores the schedule after reboot, app update, clock/timezone change and exact-alarm permission change; the app also re-arms the schedule whenever it is opened.  
- Active hours are applied when scheduling and again when an alarm fires.  
**Known limits**  
- Sounds longer than the repeat interval are restarted by the next repeat; use short sounds or a longer interval.  
- Some manufacturers (Xiaomi, Huawei, Oppo, Samsung "sleeping apps") add their own battery killers; see [https://dontkillmyapp.com.](https://dontkillmyapp.com "https://dontkillmyapp.com")  
- Pausing keeps the remaining time; Start resumes from it.  
**Manual test plan**  
1. Settings > **Test reminder in 5 seconds**, lock the phone: the alert plays the configured number of times, then stops by itself.  
2. Dashboard shows the next countdown running right after the alert starts (no confirmation).  
3. Tap **Stop** in the notification during a repeat: audio stops immediately; the countdown is unaffected. Tap **Snooze**: next reminder after 5/10 min.  
4. Turn Repeat off: the alert plays once.  
5. Reboot with a running countdown: the reminder still fires.  
6. Set active hours that exclude now: nothing fires until the window opens.  

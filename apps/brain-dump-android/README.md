# Brain Dump for Android

A phone-only native app: Kotlin + Jetpack Compose, all data on the phone, no internet permission.
Design: [`docs/brain-dump-android/system-design.md`](../../docs/brain-dump-android/system-design.md).

## Build locally (Windows)

Tools (free, outside the repo):

| Tool | Where |
|---|---|
| JDK 17 | `C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot` |
| Android SDK | `D:\Android\sdk` (set in `local.properties`, which is git-ignored) |
| Emulator | `D:\Android\sdk\emulator` (AVD `bd35`) |

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # tests + debug APK
./gradlew assembleRelease                              # signed release APK (needs the key, below)
```

Debug builds are installed as `io.github.nithv.braindump.debug`, next to the real app, so testing never touches your real data.

## Releases

1. Bump `versionCode` (always up, never down) and `versionName` in `app/build.gradle.kts`.
2. Commit, then tag and push: `git tag android-v0.2.0 && git push origin android-v0.2.0`.
3. GitHub Actions runs the tests, builds a signed APK, checks the signature and attaches it to a GitHub Release.
4. On the phone: open the release page and tap the `.apk`.

There is no rollback. Android won't install a lower `versionCode` over a higher one, so a bad release is fixed by shipping a new one ("roll forward").

## ⚠️ The signing key

Android only installs an update if it's signed with the **same key** as the installed app.

- Key: `D:\Metamorphosis\keys\brain-dump-android.jks`, password in `D:\Metamorphosis\keys\keystore.properties`. **Never commit either.**
- CI gets them as the GitHub secrets `ANDROID_KEYSTORE_B64` and `ANDROID_KEYSTORE_PASSWORD`.
- **Keep a second copy of the `keys` folder somewhere safe** (a USB stick or a private cloud folder). If the key is lost, the app can't be updated without uninstalling it, which deletes its data.

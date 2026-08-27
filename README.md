# andSri

andSri is a minimal Android 16+ launcher designed for low battery use, low CPU overhead, and a quiet home screen. It provides one scrolling screen with the date and time, a configurable favorites grid, and all visible apps alphabetically.

## Download

The signed APK is available at [releases/andSri-0.3.0.apk](releases/andSri-0.3.0.apk). Verify it against [releases/SHA256SUMS](releases/SHA256SUMS) before installation.

Android may require permission to install applications from your browser or file manager. Existing andSri installations signed with the official release certificate can be upgraded without losing launcher configuration.

Official signing certificate SHA-256:

```text
05eab865f1f91c995c59677ff09f777e854e84e9c2ee9e16d868972f052680ea
```

## Highlights

- No internet permission, telemetry, analytics, background service, worker, or polling
- Favorites in a responsive icon grid, followed by every visible app alphabetically
- Text-only and icon-with-text app rows
- Bundled Lawnicons, Arcticons, Mondstern, Cuscon, Delta, Dollphone, and Snow subsets
- Bundled Atkinson Hyperlegible Next, Newsreader, and Maple Mono typography
- Gallery or system wallpaper, automatic contrast, dark mode, and pure-black background
- Favorite ordering, custom app labels, and biometric-protected hidden apps
- English, Dutch, and Hindi localization
- Android-managed backup for lightweight launcher configuration

## Build the source

Android Studio with Android SDK 36 and Java 21 is required.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The public repository intentionally contains no signing key. Locally built debug APKs cannot update an official release installation without uninstalling it first, and uninstalling clears local launcher data.

## Licensing

No license has yet been granted for andSri's original source code. Third-party fonts and icon artwork retain their respective licenses. Complete provenance and license texts are available in [third_party](third_party/README.md) and are also bundled in the application.

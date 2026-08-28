# andSri

andSri is a minimal Android 16+ launcher designed for low battery use, low CPU overhead, and a quiet home screen. One scrolling screen combines time, optional tap-to-refresh weather, favorites, and every visible app.

## Download

The current signed APK is [andSri 0.4.0](releases/andSri-0.4.0.apk). Verify it against [SHA256SUMS](releases/SHA256SUMS) before installation.

Android may require permission to install applications from your browser or file manager. Existing andSri installations signed with the official release certificate can be upgraded without losing launcher configuration.

Official signing certificate SHA-256:

```text
05eab865f1f91c995c59677ff09f777e854e84e9c2ee9e16d868972f052680ea
```

## Highlights

- No telemetry, analytics, background service, worker, alarm, wake lock, or polling
- Optional cached weather with manual location and tap-only networking
- Favorites in a responsive icon grid, followed by every visible app alphabetically
- Text-only and icon-with-text app rows
- Bundled Lawnicons, Arcticons, Appstract, Cuscon, Delta, Dollphone, and Snow subsets
- Bundled Atkinson Hyperlegible Next, Newsreader, and Maple Mono typography
- Gallery or system wallpaper, light/dark modes, static fade, and adaptive solid background
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

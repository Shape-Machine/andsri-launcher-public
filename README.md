# andSri

Minimal Android launcher. One scrolling screen. No clutter. Android 13+.

## Screenshots

| Screen | Light | Dark |
|---|---|---|
| Home, weather, 4 favorites | <img src="docs/screenshots/light-home-weather.png" width="210" alt="Home with weather and four favorites in light mode"> | <img src="docs/screenshots/dark-home-weather.png" width="210" alt="Home with weather and four favorites in dark mode"> |
| Home, weather, 8 favorites | <img src="docs/screenshots/light-home-eight-favorites.png" width="210" alt="Home with weather and eight favorites in light mode"> | <img src="docs/screenshots/dark-home-eight-favorites.png" width="210" alt="Home with weather and eight favorites in dark mode"> |
| Newsreader serif home | <img src="docs/screenshots/light-home-serif.png" width="210" alt="Home using Newsreader serif typography in light mode"> | <img src="docs/screenshots/dark-home-serif.png" width="210" alt="Home using Newsreader serif typography in dark mode"> |
| All apps | <img src="docs/screenshots/light-all-apps.png" width="210" alt="All apps in light mode"> | <img src="docs/screenshots/dark-all-apps.png" width="210" alt="All apps in dark mode"> |
| App actions | <img src="docs/screenshots/light-app-actions.png" width="210" alt="App actions in light mode"> | <img src="docs/screenshots/dark-app-actions.png" width="210" alt="App actions in dark mode"> |
| Organization and weather | <img src="docs/screenshots/light-settings-organize.png" width="210" alt="Organization and weather settings in light mode"> | <img src="docs/screenshots/dark-settings-organize.png" width="210" alt="Organization and weather settings in dark mode"> |
| Appearance and icons | <img src="docs/screenshots/light-settings-appearance.png" width="210" alt="Appearance and icon settings in light mode"> | <img src="docs/screenshots/dark-settings-appearance.png" width="210" alt="Appearance and icon settings in dark mode"> |
| Serif font selection | <img src="docs/screenshots/light-settings-serif-font.png" width="210" alt="Newsreader serif font selected in light settings"> | <img src="docs/screenshots/dark-settings-serif-font.png" width="210" alt="Newsreader serif font selected in dark settings"> |

Weather data shown is representative.

## Download

**[Download the latest signed APK](https://github.com/Shape-Machine/andsri-launcher-public/releases/latest/download/andSri.apk)** · [Release notes](https://github.com/Shape-Machine/andsri-launcher-public/releases/latest) · [SHA-256 checksums](https://github.com/Shape-Machine/andsri-launcher-public/releases/latest/download/SHA256SUMS)

Requires Android 13+. Android may request permission to install the APK. Release certificate SHA-256: `05eab865f1f91c995c59677ff09f777e854e84e9c2ee9e16d868972f052680ea`. Signed updates preserve settings.

## Principles

- **Focus:** no search, suggestions, badges, feeds, widgets, or custom animation.
- **Efficiency:** no telemetry, analytics, polling, background services, scheduled work, or idle networking.
- **Predictability:** favorites and the alphabetical app list share one vertical screen.
- **Privacy:** weather is user-triggered; hidden apps require device authentication.
- **Durability:** Kotlin, classic Android Views, platform APIs, zero production runtime dependencies.

## Benefits

- Fewer steps from Home to app.
- Exceptionally low idle battery, CPU, memory, and network use.
- Configurable icons, typography, density, wallpaper, and appearance.
- Fast favorites plus a complete alphabetical list.
- English, Dutch, and Hindi; Android-managed configuration backup.

## Build

Requires Android SDK 36 and Java 21.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

No signing key is included. Debug builds require uninstalling an official installation first.

## Licensing

andSri is source-available. Use, modify, and redistribute it free of charge. Monetization is prohibited. Distributed forks must remain free and publish their source under the same terms. See [LICENSE](LICENSE).

Third-party fonts and icons retain their licenses: [third_party](third_party/README.md).

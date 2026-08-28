# Verification

## Automated checks

- Unit tests, Android lint, and debug assembly: `./gradlew testDebugUnitTest lintDebug assembleDebug`
- Connected tests: `./gradlew connectedDebugAndroidTest` on a disposable Android 16 emulator.
- Maintainer releases add R8, resource shrinking, release lint, permanent-signature verification, backup validation, and physical-device benchmarks.

## 2026-08-25 physical-device result

All four instrumented tests passed on both the Android 16 Sony XQ-FE54 and the `Fairphone_6_Plus_API_36` emulator. Android system backup completed successfully. A ten-run debug cold-start sample measured a 520 ms median (510–533 ms) on the sleeping/locked Sony and 482 ms (314–554 ms) on the headless software-rendered emulator. These are regression baselines, not acceptance of the Fairphone target: debug startup timing, locked-screen frame data, software graphics, and different hardware cannot establish the sub-300 ms physical-Fairphone criterion.

Fairphone hardware measurement remains mandatory for release acceptance.

## 2026-08-25 optimized Sony baseline

Before the reload and clock-binding optimizations, a ten-run debug sample on the Sony XQ-FE54 measured a 327 ms median (309–346 ms), with idle CPU sampled at 0%. Post-change samples varied with the device's shade/lock state (three clean runs had a 295 ms median), so they are regression signals rather than proof of a speedup. Android recorded zero launcher frames while the system shade retained focus, so no scrolling percentile is claimed. Arcticons and Lawnicons now cover every launcher-visible package on this test device.

## 2026-08-28 release baseline

Sony XQ-FE54 release measurements: 134 ms median cold launch, 0.38% modern frame-deadline misses across 265 aggressive-scroll frames, zero measurable idle CPU ticks over ten seconds, zero attributed wake locks, and 47.5 MB steady-state PSS. Results are device-specific regression baselines, not universal guarantees.

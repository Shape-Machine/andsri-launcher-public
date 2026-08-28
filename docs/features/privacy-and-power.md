# Privacy and power

andSri minimizes battery use and data exposure by doing nothing unless the user or Android requests it.

## Power model

- No background services, workers, jobs, alarms, polling, wake locks, or boot/connectivity receivers.
- Update the clock once per minute only while Home is visible.
- Refresh installed apps only after Android reports a package change.
- Load and cache icons near display size with fixed memory limits.
- Decode custom wallpaper once at screen resolution, off the main thread.
- Make weather requests only after explicit taps; never retry automatically.
- Disable wallpaper rendering when Solid background is active.

## Privacy

- No telemetry, analytics, advertising, remote crash reporting, accounts, or usage profiling.
- No notification access or device-location permission.
- Protect hidden-app management with Android authentication.
- Send only a manually configured place to Open-Meteo during explicit weather actions.

## Targets

- Effectively zero idle CPU, background traffic, and periodic wakeups.
- Cold launch below 300 ms on Fairphone 6.
- Smooth 120 Hz scrolling when enabled by the device.

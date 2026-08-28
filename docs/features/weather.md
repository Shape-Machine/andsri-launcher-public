# Weather

Weather provides an optional, low-power glance without tracking location or running in the background.

## Experience

- Appears between the clock and favorites after the user saves a location.
- Shows cached conditions immediately.
- Refreshes once when tapped; opening Home never triggers a request.
- Presents humanized age: just now, minutes, hours, or days ago.
- Offers Compact, Standard, and Emphasized layouts plus system, Celsius, and Fahrenheit units.
- Retains the last successful result after network failure.

## Controls

- Settings manages location, layout, units, provider attribution, and removal.
- Compact uses one row. Larger layouts add location and a separate Open-Meteo information control.
- Clearing location hides weather and deletes its cache.

## Boundaries

- No GPS, location permission, polling, retries, alerts, forecasts, maps, radar, animation, services, or scheduled work.
- Open-Meteo receives only the configured location after an explicit lookup or refresh.
- Open-Meteo’s free API is for personal, non-commercial use; public distribution requires a terms review.

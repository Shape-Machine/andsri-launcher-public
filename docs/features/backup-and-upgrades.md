# Backup and upgrades

Launcher configuration survives routine upgrades and participates in Android backup.

## In-place upgrades

- Favorites and order
- Custom app names
- Hidden-app identifiers
- Weather configuration and cached result
- Appearance, typography, icons, density, and app-list state

## Android backup

- Restores lightweight launcher configuration when the platform permits it.
- Excludes cached weather and wallpaper image bytes.
- Retains only the wallpaper’s platform URI.

## Release model

- Installs updates without clearing configuration.
- Uses one permanent signing identity for every release.
- Reset launcher removes configuration only after confirmation.

## Boundaries

- No manual export/import, launcher account, cloud service, background sync, or secret credential backup.
- Debug and release signatures are separate; replacing one with the other may require uninstalling and losing local data.

# VideoSaver

Android app for downloading videos from TikTok, Twitter/X, and Instagram.

## Build

Requires JDK 17 and the Android SDK (API 35).

```bash
./gradlew assembleDebug
./gradlew lintDebug
./gradlew test
./gradlew assembleRelease   # requires keystore.properties
```

Release signing reads credentials from `keystore.properties` (not committed).

## Architecture

- `app/` — Android application
- `gradle/libs.versions.toml` — dependency version catalog
- Room database for download history (migrated automatically from legacy SharedPreferences)
- WorkManager for background downloads with progress tracking
- Custom save folder via Storage Access Framework (Settings)

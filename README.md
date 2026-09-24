# VideoSaver

VideoSaver is a robust Android application designed to seamlessly download and save videos from popular social media platforms including TikTok, Twitter/X, and Instagram. 

## Features

- Multi-Platform Support: Extract and download videos from TikTok, Twitter/X, and Instagram effortlessly.
- Watermark Removal: Option to remove TikTok watermarks when downloading videos.
- Background Downloads: Robust background downloading utilizing Android WorkManager, ensuring downloads continue even if the app is closed.
- Download History: Keeps track of your downloaded videos with a Room database.
- Custom Save Locations: Choose your preferred storage directory using the Android Storage Access Framework (SAF).
- Material Design UI: Clean, modern interface supporting both Light and Dark themes.
- Progress Tracking: Real-time download progress tracking and notifications.
- Integrated Video Player: Play downloaded videos directly within the app.

## Architecture and Tech Stack

The application is built using modern Android development practices and Kotlin:

- Language: 100% Kotlin
- Architecture: MVVM (Model-View-ViewModel) architecture for clear separation of concerns.
- Local Storage: Room Database for persistent and structured history tracking.
- Asynchronous Tasks: Kotlin Coroutines and WorkManager for robust background processing.
- Dependency Management: Version Catalog (libs.versions.toml) for centralized dependency management.
- Networking: Custom HTTP client provider for handling network requests and parsing JSON responses.

## Prerequisites

To build and run this project, you will need:
- JDK 17
- Android SDK (API 35)
- Android Studio (recommended for the best development experience)

## Build Instructions

You can build the project from the command line using the Gradle wrapper.

To build the debug APK:
```bash
./gradlew assembleDebug
```

To run lint checks:
```bash
./gradlew lintDebug
```

To run unit tests:
```bash
./gradlew test
```

To build a release APK:
```bash
./gradlew assembleRelease
```
Note: Building a release APK requires a `keystore.properties` file containing your release signing credentials (which is intentionally not committed to the repository).

## Usage

1. Copy the link of the video from TikTok, Twitter/X, or Instagram.
2. Open the VideoSaver app.
3. Paste the link into the designated input field.
4. Tap the download button. You can monitor the progress in the history tab or via system notifications.
5. Once completed, access the video directly from the app or your chosen custom save directory.

## Privacy

The app respects user privacy and only requests necessary permissions to write files to storage. A privacy policy is included in the application assets.

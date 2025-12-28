# GPS Map Camera (V1)

A minimal Android app that captures a photo and bakes a "GPS Map Camera"-style overlay into the saved image.

## Features
- Configure overlay text and pick a map thumbnail from the gallery.
- Camera preview with bottom overlay panel.
- Capture saves a composited image to the system gallery (MediaStore).

## Requirements
- Android Studio Hedgehog or newer
- Android SDK 34
- Device running Android 7.0+ (API 24)

## How to Run
1. Open the project folder in Android Studio.
2. Sync Gradle.
3. Run the app on a device or emulator with a camera.

## How to Test
1. Tap **Configure Overlay** and enter text fields.
2. Tap **Upload Map Thumbnail** and pick an image.
3. Save the config.
4. Tap **Open Camera** and capture a photo.
5. Open the device gallery and verify the saved image includes the overlay panel.

## Notes
- The overlay is drawn into the bitmap before saving.
- Images are saved to `Pictures/GpsMapCamera/`.
- No Google Maps API is used in V1.

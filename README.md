# ZipGPT

Android helper app for selecting photos and sharing them as a single ZIP without resizing, JPEG recompression, EXIF rewriting, or any other image transformation.

## What it does

1. Select one or more images from Android's document/gallery picker.
2. For MediaStore-backed images, ZipGPT tries to reopen the canonical media item and requests the original representation with `MediaStore.setRequireOriginal()` when `ACCESS_MEDIA_LOCATION` is granted.
3. Each selected file is streamed directly into a ZIP entry. ZipGPT never decodes the image.
4. The ZIP is created in app cache and Android's share sheet opens automatically.
5. Cached ZIP files older than 24 hours are deleted on the next ZIP creation.

## What "no compression" means

The source image bytes are not modified. The ZIP stream uses deflate level 0 because JPEG/HEIC files are already compressed and additional ZIP compression mostly wastes CPU. After extraction, each entry contains the exact bytes read from its source URI.

## EXIF / GPS

ZipGPT does not parse or reconstruct EXIF. It attempts to copy the original media stream so embedded EXIF remains untouched.

Android can redact location metadata for some picker/provider URIs. ZipGPT therefore prefers a canonical MediaStore URI and `MediaStore.setRequireOriginal()` when possible. If that path cannot be opened, it falls back to the selected URI and reports the fallback count on screen.

Permissions used:

- `READ_MEDIA_IMAGES` on Android 13+
- `READ_EXTERNAL_STORAGE` on Android 12L and below
- `ACCESS_MEDIA_LOCATION` on Android 10+ for original unredacted photo metadata
- `READ_MEDIA_VISUAL_USER_SELECTED` declaration for Android 14+ selected-photo access behavior

ZipGPT does **not** request current device location (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`).

## Build

Open the project in Android Studio, or push it to GitHub. The included GitHub Actions workflow builds a debug APK and uploads it as the `zipgpt-debug-apk` artifact.

Command-line build with Gradle 8.9 + JDK 17:

```bash
gradle testDebugUnitTest
gradle lintDebug
gradle assembleDebug
```

## Device test

Recommended test on Samsung Galaxy / Android 16:

1. Pick a Samsung Camera JPEG known to contain GPS EXIF.
2. Create the ZIP.
3. Confirm the status shows `원본 MediaStore 경로` for the photo when possible.
4. Upload the ZIP to ChatGPT.
5. Ask it to inspect the image and read `DateTimeOriginal`, GPS latitude/longitude and altitude from the file inside the ZIP.

If the app reports `선택 URI 폴백`, Android/provider redaction may already have happened before ZipGPT received the stream.

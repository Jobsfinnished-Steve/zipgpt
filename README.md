# ZipGPT

ZipGPT is an Android ChatGPT WebView wrapper that automatically converts photo attachments into one ZIP before returning the attachment to the ChatGPT web file input.

## Intended flow

```
Open ZipGPT
→ ChatGPT loads inside the app
→ tap ChatGPT + / photo attachment
→ choose one or more normal photos
→ ZipGPT intercepts the image file chooser
→ photos are copied byte-for-byte into one ZIP
→ the ZIP URI is returned to the same ChatGPT WebView upload callback
→ ChatGPT receives one .zip attachment
```

ZipGPT is **not** a standalone compression/share utility.

## Image preservation

ZipGPT does not:

- resize images
- decode/re-encode JPEG/HEIC
- rewrite EXIF
- strip metadata
- create thumbnails

`ZipBuilder` streams the selected image bytes directly into ZIP entries using deflate level 0.

For MediaStore-backed images, ZipGPT tries to use a canonical MediaStore URI and `MediaStore.setRequireOriginal()` when `ACCESS_MEDIA_LOCATION` is available. If Android/provider restrictions prevent that, it falls back to the selected document URI.

## Other file uploads

The interception is limited to WebView file chooser requests whose accept types indicate images. Normal PDF/TXT/etc file chooser requests are passed through unchanged.

## Web compatibility

ZipGPT intentionally does not maintain a domain allowlist for ChatGPT subresources. HTTPS resources, OpenAI authentication, Cloudflare challenges, and future ChatGPT support domains are allowed to load normally inside the WebView.

## Current experiment

Whether the current ChatGPT web client accepts an `application/zip` URI returned to an image-oriented file input must be confirmed on-device. The Android bridge now performs the requested behavior; if ChatGPT rejects the ZIP because of its input `accept` policy, the next step is to route the generated ZIP through ChatGPT's generic-file attachment input rather than changing the ZIP contents.

## Build

GitHub Actions runs:

- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- uploads `zipgpt-debug-apk`

Recommended test device: Samsung Galaxy / Android 16.

# Architecture

`MainActivity` selects images and starts ZIP creation on a background thread.

`OriginalMediaResolver` attempts this source order:

1. direct `content://media/...` URI
2. Media DocumentsProvider `image:<id>` mapped to `MediaStore.Images.Media`
3. `MediaStore.setRequireOriginal()` on Android 10+ when `ACCESS_MEDIA_LOCATION` is granted
4. selected picker/document URI fallback

`ZipBuilder` streams the chosen URI directly into `ZipOutputStream`; no `Bitmap`, image decoder, encoder, resize, EXIF writer or metadata reconstruction is used.

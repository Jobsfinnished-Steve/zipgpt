package com.jobsfinnished.zipgpt;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.core.content.ContextCompat;

final class OriginalMediaResolver {

    enum Status {
        REQUIRE_ORIGINAL,
        DIRECT_MEDIASTORE,
        MEDIA_DOCUMENT_RESOLVED,
        FALLBACK_SELECTED_URI
    }

    static final class Result {
        final Uri selectedUri;
        final Uri preferredUri;
        final Status status;

        Result(Uri selectedUri, Uri preferredUri, Status status) {
            this.selectedUri = selectedUri;
            this.preferredUri = preferredUri;
            this.status = status;
        }
    }

    private OriginalMediaResolver() {}

    static Result resolve(Context context, Uri selectedUri) {
        Uri mediaStoreUri = canonicalMediaStoreUri(context, selectedUri);
        if (mediaStoreUri == null) {
            return new Result(selectedUri, selectedUri, Status.FALLBACK_SELECTED_URI);
        }

        Status resolvedStatus = isDirectMediaStore(selectedUri)
                ? Status.DIRECT_MEDIASTORE : Status.MEDIA_DOCUMENT_RESOLVED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                return new Result(selectedUri, MediaStore.setRequireOriginal(mediaStoreUri), Status.REQUIRE_ORIGINAL);
            } catch (RuntimeException ignored) {
            }
        }
        return new Result(selectedUri, mediaStoreUri, resolvedStatus);
    }

    private static Uri canonicalMediaStoreUri(Context context, Uri uri) {
        if (isDirectMediaStore(uri)) return uri;

        if ("content".equals(uri.getScheme())
                && "com.android.providers.media.documents".equals(uri.getAuthority())
                && DocumentsContract.isDocumentUri(context, uri)) {
            try {
                Long id = MediaDocumentIdParser.parseImageId(DocumentsContract.getDocumentId(uri));
                if (id != null) {
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                }
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isDirectMediaStore(Uri uri) {
        return "content".equals(uri.getScheme())
                && "media".equals(uri.getAuthority());
    }
}

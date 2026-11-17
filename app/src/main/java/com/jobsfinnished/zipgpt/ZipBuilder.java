package com.jobsfinnished.zipgpt;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ZipBuilder {
    private static final String DIRECTORY = "zips";
    private static final long MAX_AGE_MS = TimeUnit.HOURS.toMillis(24);

    static final class Result {
        final File file;
        final int fileCount;
        final int preferredSourceCount;
        final int fallbackCount;

        Result(File file, int fileCount, int preferredSourceCount, int fallbackCount) {
            this.file = file;
            this.fileCount = fileCount;
            this.preferredSourceCount = preferredSourceCount;
            this.fallbackCount = fallbackCount;
        }
    }

    private ZipBuilder() {}

    static Result create(Context context, List<Uri> selectedUris) throws IOException {
        File root = new File(context.getCacheDir(), DIRECTORY);
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create ZIP cache directory");
        cleanup(root);

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File zipFile = new File(root, "zipgpt_" + stamp + ".zip");
        ContentResolver resolver = context.getContentResolver();
        Set<String> usedNames = new HashSet<>();
        int preferred = 0;
        int fallback = 0;

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zip.setLevel(Deflater.NO_COMPRESSION);
            byte[] buffer = new byte[128 * 1024];

            for (int i = 0; i < selectedUris.size(); i++) {
                Uri selected = selectedUris.get(i);
                OriginalMediaResolver.Result resolution = OriginalMediaResolver.resolve(context, selected);
                InputStream raw = null;

                if (!resolution.preferredUri.equals(selected)) {
                    try {
                        raw = resolver.openInputStream(resolution.preferredUri);
                    } catch (RuntimeException ignored) {
                        raw = null;
                    }
                }

                if (raw == null) {
                    raw = resolver.openInputStream(selected);
                    fallback++;
                } else {
                    preferred++;
                }
                if (raw == null) throw new IOException("Unable to open selected image " + (i + 1));

                String name = uniqueName(safeDisplayName(resolver, selected, i), usedNames);
                ZipEntry entry = new ZipEntry(name);
                zip.putNextEntry(entry);
                try (InputStream in = new BufferedInputStream(raw)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            zipFile.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("ZIP creation failed", e);
        }

        return new Result(zipFile, selectedUris.size(), preferred, fallback);
    }

    private static String safeDisplayName(ContentResolver resolver, Uri uri, int index) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) name = cursor.getString(column);
            }
        } catch (RuntimeException ignored) {
        }
        if (name == null || name.trim().isEmpty()) name = String.format(Locale.US, "image_%03d", index + 1);
        name = name.replace('\\', '_').replace('/', '_').replace('\u0000', '_');
        if (name.equals(".") || name.equals("..")) name = String.format(Locale.US, "image_%03d", index + 1);
        return name;
    }

    private static String uniqueName(String original, Set<String> used) {
        if (used.add(original)) return original;
        int dot = original.lastIndexOf('.');
        String stem = dot > 0 ? original.substring(0, dot) : original;
        String ext = dot > 0 ? original.substring(dot) : "";
        int suffix = 2;
        String candidate;
        do {
            candidate = stem + "_" + suffix++ + ext;
        } while (!used.add(candidate));
        return candidate;
    }

    private static void cleanup(File root) {
        File[] files = root.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                file.delete();
            }
        }
    }
}

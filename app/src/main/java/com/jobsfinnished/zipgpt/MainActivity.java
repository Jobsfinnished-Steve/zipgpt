package com.jobsfinnished.zipgpt;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_IMAGES = 1001;
    private static final int REQUEST_MEDIA_PERMISSIONS = 1002;

    private Button selectButton;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        selectButton = findViewById(R.id.selectButton);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        selectButton.setOnClickListener(v -> prepareAndOpenPicker());
    }

    private void prepareAndOpenPicker() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= 29
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQUEST_MEDIA_PERMISSIONS);
        } else {
            openPicker();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MEDIA_PERMISSIONS) {
            openPicker();
        }
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = collectUris(data);
        if (uris.isEmpty()) return;
        buildAndShare(uris);
    }

    private List<Uri> collectUris(Intent data) {
        Set<Uri> ordered = new LinkedHashSet<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) ordered.add(uri);
            }
        }
        if (data.getData() != null) ordered.add(data.getData());
        return new ArrayList<>(ordered);
    }

    private void buildAndShare(List<Uri> uris) {
        setBusy(true);
        status.setText("ZIP 생성 중: " + uris.size() + "장");
        new Thread(() -> {
            try {
                ZipBuilder.Result result = ZipBuilder.create(this, uris);
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("완료: " + result.fileCount + "장\n"
                            + "원본 MediaStore 경로: " + result.preferredSourceCount + "장\n"
                            + "선택 URI 폴백: " + result.fallbackCount + "장\n"
                            + "ZIP: " + result.file.getName());
                    share(result);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("실패: " + e.getClass().getSimpleName());
                    Toast.makeText(this, "ZIP 생성 실패", Toast.LENGTH_LONG).show();
                });
            }
        }, "zipgpt-builder").start();
    }

    private void share(ZipBuilder.Result result) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", result.file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/zip");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.putExtra(Intent.EXTRA_SUBJECT, result.file.getName());
        startActivity(Intent.createChooser(send, "ZIP 공유"));
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        selectButton.setEnabled(!busy);
    }
}

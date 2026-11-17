package com.jobsfinnished.zipgpt;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String CHATGPT_URL = "https://chatgpt.com/";

    private static final int REQUEST_IMAGE_PICKER = 4101;
    private static final int REQUEST_GENERIC_CHOOSER = 4102;
    private static final int REQUEST_MEDIA_PERMISSIONS = 4103;

    private WebView chatWebView;
    private View zipStatusPanel;
    private ProgressBar zipProgress;
    private TextView zipStatusText;

    private ValueCallback<Uri[]> pendingFileCallback;
    private boolean pendingZipImageRequest;
    private String lastAcceptTypes = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatWebView = findViewById(R.id.chatWebView);
        zipStatusPanel = findViewById(R.id.zipStatusPanel);
        zipProgress = findViewById(R.id.zipProgress);
        zipStatusText = findViewById(R.id.zipStatusText);

        configureWebView();
        chatWebView.loadUrl(CHATGPT_URL);
    }

    private void configureWebView() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(chatWebView, true);

        WebSettings settings = chatWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        chatWebView.setBackgroundColor(Color.WHITE);
        chatWebView.setWebViewClient(new WebViewClient());

        chatWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                cancelPendingFileCallback();

                pendingFileCallback = filePathCallback;
                lastAcceptTypes = joinAcceptTypes(fileChooserParams.getAcceptTypes());

                if (shouldZipImageRequest(fileChooserParams)) {
                    pendingZipImageRequest = true;
                    requestMediaPermissionsThenOpenPicker();
                } else {
                    pendingZipImageRequest = false;
                    openGenericChooser(fileChooserParams);
                }
                return true;
            }
        });
    }

    private boolean shouldZipImageRequest(WebChromeClient.FileChooserParams params) {
        if (params == null || params.isCaptureEnabled()) return false;

        String[] acceptTypes = params.getAcceptTypes();
        if (acceptTypes == null || acceptTypes.length == 0) return false;

        for (String value : acceptTypes) {
            if (value == null) continue;
            String[] tokens = value.toLowerCase(Locale.ROOT).split(",");
            for (String token : tokens) {
                String t = token.trim();
                if (t.startsWith("image/")
                        || t.equals(".jpg")
                        || t.equals(".jpeg")
                        || t.equals(".png")
                        || t.equals(".webp")
                        || t.equals(".heic")
                        || t.equals(".heif")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String joinAcceptTypes(String[] acceptTypes) {
        if (acceptTypes == null || acceptTypes.length == 0) return "(none)";
        StringBuilder out = new StringBuilder();
        for (String type : acceptTypes) {
            if (out.length() > 0) out.append(" | ");
            out.append(type == null || type.isEmpty() ? "(empty)" : type);
        }
        return out.toString();
    }

    private void requestMediaPermissionsThenOpenPicker() {
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

        if (missing.isEmpty()) {
            openImagePicker();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    missing.toArray(new String[0]),
                    REQUEST_MEDIA_PERMISSIONS
            );
        }
    }

    private void openImagePicker() {
        if (pendingFileCallback == null || !pendingZipImageRequest) return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        try {
            startActivityForResult(intent, REQUEST_IMAGE_PICKER);
        } catch (RuntimeException e) {
            failPendingUpload("사진 선택기를 열 수 없음");
        }
    }

    private void openGenericChooser(WebChromeClient.FileChooserParams params) {
        try {
            startActivityForResult(params.createIntent(), REQUEST_GENERIC_CHOOSER);
        } catch (RuntimeException e) {
            failPendingUpload("파일 선택기를 열 수 없음");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MEDIA_PERMISSIONS
                && pendingFileCallback != null
                && pendingZipImageRequest) {
            // Even if broad/original-media access was denied, ACTION_OPEN_DOCUMENT still
            // grants access to the selected item. ZipBuilder will use that as a fallback.
            openImagePicker();
        }
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_GENERIC_CHOOSER) {
            ValueCallback<Uri[]> callback = pendingFileCallback;
            clearPendingState();
            if (callback != null) {
                callback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                );
            }
            return;
        }

        if (requestCode != REQUEST_IMAGE_PICKER) return;

        if (resultCode != RESULT_OK || data == null) {
            completePendingCallback(null);
            return;
        }

        List<Uri> selected = collectUris(data);
        if (selected.isEmpty()) {
            completePendingCallback(null);
            return;
        }

        buildZipAndReturnToChatGpt(selected);
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

    private void buildZipAndReturnToChatGpt(List<Uri> selected) {
        showZipStatus("사진 " + selected.size() + "장 → ZIP 생성 중", true);

        new Thread(() -> {
            try {
                ZipBuilder.Result result = ZipBuilder.create(this, selected);
                Uri zipUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".files",
                        result.file
                );

                runOnUiThread(() -> {
                    showZipStatus(
                            "ZIP 첨부 반환: " + result.fileCount + "장"
                                    + " · 원본경로 " + result.preferredSourceCount
                                    + " · 폴백 " + result.fallbackCount,
                            false
                    );
                    completePendingCallback(new Uri[]{zipUri});
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showZipStatus("ZIP 생성 실패: " + e.getClass().getSimpleName(), false);
                    completePendingCallback(null);
                    Toast.makeText(
                            this,
                            "사진을 ZIP으로 첨부하지 못했습니다.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }, "zipgpt-chat-upload").start();
    }

    private void showZipStatus(String message, boolean busy) {
        zipStatusText.setText(message);
        zipProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        zipStatusPanel.setVisibility(View.VISIBLE);

        mainHandler.removeCallbacksAndMessages("hideZipStatus");
        if (!busy) {
            Runnable hide = () -> zipStatusPanel.setVisibility(View.GONE);
            android.os.Message msg = android.os.Message.obtain(mainHandler, hide);
            msg.obj = "hideZipStatus";
            mainHandler.sendMessageDelayed(msg, 5000L);
        }
    }

    private void failPendingUpload(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        completePendingCallback(null);
    }

    private void completePendingCallback(Uri[] uris) {
        ValueCallback<Uri[]> callback = pendingFileCallback;
        clearPendingState();
        if (callback != null) callback.onReceiveValue(uris);
    }

    private void cancelPendingFileCallback() {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
        }
        clearPendingState();
    }

    private void clearPendingState() {
        pendingFileCallback = null;
        pendingZipImageRequest = false;
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelPendingFileCallback();
        if (chatWebView != null) {
            chatWebView.stopLoading();
            chatWebView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (chatWebView != null && chatWebView.canGoBack()) {
            chatWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

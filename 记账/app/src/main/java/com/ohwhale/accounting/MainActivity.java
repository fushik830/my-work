package com.ohwhale.accounting;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

public class MainActivity extends Activity {

    private static final String TAG = "OhWhale";
    private static final String CHANNEL_ID = "budget_alerts";
    private static final int REQ_IMPORT = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;
    private static final int MAX_STATE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private WebView webView;
    private NativeBridge nativeBridge;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Object stateLock = new Object();
    private long lastBackAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        createNotificationChannel();

        webView = new WebView(this);
        webView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.parseColor("#F3F9FB"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebView.setWebContentsDebuggingEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);

        nativeBridge = new NativeBridge();
        webView.addJavascriptInterface(nativeBridge, "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUri(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("window.appOnResume && window.appOnResume();", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "web: " + consoleMessage.message() + " @" + consoleMessage.lineNumber());
                return true;
            }
        });

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#F3F9FB"));
        window.setNavigationBarColor(Color.WHITE);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private boolean handleUri(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            return !uri.toString().startsWith("file:///android_asset/");
        }
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                toast("没有可以打开这个链接的应用");
            }
            return true;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return window.appHandleBack?!!window.appHandleBack():false}catch(e){return false}})()",
                value -> {
                    if ("true".equals(value)) return;
                    long now = System.currentTimeMillis();
                    if (now - lastBackAt < 2000) {
                        finish();
                    } else {
                        lastBackAt = now;
                        toast("再按一次退出");
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.evaluateJavascript("window.appOnResume && window.appOnResume();", null);
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidNative");
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (nativeBridge != null) nativeBridge.deliverImport(false, "已取消导入");
            return;
        }
        if (nativeBridge != null) nativeBridge.readImport(data.getData());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS && nativeBridge != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            nativeBridge.notifyPermissionResult(granted);
        }
    }

    private void toast(final String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_budget_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(getString(R.string.channel_budget_desc));
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 120, 70, 120});
            channel.setShowBadge(true);
            nm.createNotificationChannel(channel);
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private boolean postBudgetNotification(String title, String body) {
        if (!hasNotificationPermission()) return false;
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 7001, intent, piFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_whale)
                .setContentTitle(title == null ? "哦鲸鲸记账" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setColor(Color.parseColor("#3E9E9A"))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_REMINDER);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        try {
            nm.notify(7001, builder.build());
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "notification permission revoked", e);
            return false;
        }
    }

    private void callJs(final String script) {
        mainHandler.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private File stateFile() {
        return new File(getFilesDir(), "ohwhale-state.json");
    }

    private File stateBackupFile() {
        return new File(getFilesDir(), "ohwhale-state.bak.json");
    }

    private String readFile(File file) {
        if (file == null || !file.exists() || file.length() <= 0 || file.length() > MAX_STATE_BYTES) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "read state failed", e);
            return null;
        }
    }

    private boolean isValidJsonObject(String json) {
        if (json == null) return false;
        String t = json.trim();
        if (!t.startsWith("{") || !t.endsWith("}")) return false;
        try {
            new JSONObject(t);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private boolean writeFileSafely(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            Log.w(TAG, "write temp failed", e);
            return false;
        }
        if (file.exists()) {
            File bak = stateBackupFile();
            try (FileInputStream in = new FileInputStream(file);
                 FileOutputStream out = new FileOutputStream(bak)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
                out.getFD().sync();
            } catch (IOException e) {
                Log.w(TAG, "backup state failed", e);
            }
        }
        if (file.exists() && !file.delete()) return false;
        if (tmp.renameTo(file)) return true;
        // rename can fail on some file systems; fall back to copying.
        try (FileInputStream in = new FileInputStream(tmp);
             FileOutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            out.getFD().sync();
            return true;
        } catch (IOException e) {
            Log.w(TAG, "write state failed", e);
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private String writeExport(String json, String filename) throws IOException {
        String safe = filename == null ? "" : filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) safe = "ohwhale-backup.json";
        if (!safe.toLowerCase().endsWith(".json")) safe += ".json";
        safe = safe.replaceAll("[\\r\\n]", "_");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/哦鲸鲸记账");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("无法创建下载文件");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("无法写入下载文件");
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return "已保存到「下载/哦鲸鲸记账/" + safe + "」";
        } else {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "哦鲸鲸记账");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建备份目录");
            File file = new File(dir, safe);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return "已保存到 " + file.getAbsolutePath();
        }
    }

    private String readImport(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("无法读取文件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_IMPORT_BYTES) throw new IOException("备份文件太大了");
                out.write(buf, 0, n);
            }
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (!isValidJsonObject(text)) throw new IOException("这不是有效的哦鲸鲸备份文件");
            return text;
        }
    }

    private String callAi(String endpoint, String apiKey, String model, String messagesJson,
                          int maxTokens, double temperature) throws Exception {
        URL url = new URL(endpoint);
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IOException("只支持 http/https 接口");
        }
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", new JSONArray(messagesJson));
        body.put("stream", false);
        if (maxTokens > 0) body.put("max_tokens", maxTokens);
        if (temperature >= 0) body.put("temperature", temperature);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(payload.length);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "OhWhale/1.0 (Android)");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
                out.flush();
            }
            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = readLimited(stream, 4 * 1024 * 1024);
            if (code < 200 || code >= 300) {
                String detail = extractApiError(text);
                throw new IOException("HTTP " + code + (detail.isEmpty() ? "" : "：" + detail));
            }
            JSONObject obj = new JSONObject(text);
            JSONArray choices = obj.optJSONArray("choices");
            String content = null;
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.optJSONObject(0);
                if (choice != null) {
                    JSONObject message = choice.optJSONObject("message");
                    if (message != null) {
                        Object raw = message.opt("content");
                        if (raw instanceof String) {
                            content = (String) raw;
                        } else if (raw instanceof JSONArray) {
                            StringBuilder sb = new StringBuilder();
                            JSONArray parts = (JSONArray) raw;
                            for (int i = 0; i < parts.length(); i++) {
                                JSONObject part = parts.optJSONObject(i);
                                if (part != null && part.has("text")) sb.append(part.optString("text"));
                            }
                            content = sb.toString();
                        }
                        if ((content == null || content.trim().isEmpty()) && message.has("reasoning_content")) {
                            content = message.optString("reasoning_content");
                        }
                    }
                    if ((content == null || content.trim().isEmpty()) && choice.has("text")) {
                        content = choice.optString("text");
                    }
                }
            }
            if ((content == null || content.trim().isEmpty()) && obj.has("output_text")) {
                content = obj.optString("output_text");
            }
            if (content == null || content.trim().isEmpty()) {
                throw new IOException("AI 没有返回内容");
            }
            return content.trim();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readLimited(InputStream in, int limit) throws IOException {
        if (in == null) return "";
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = stream.read(buf)) > 0) {
                total += n;
                if (total > limit) throw new IOException("接口返回内容过大");
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String extractApiError(String text) {
        if (text == null) return "";
        try {
            JSONObject obj = new JSONObject(text);
            JSONObject err = obj.optJSONObject("error");
            if (err != null) {
                String msg = err.optString("message", "");
                if (msg.isEmpty()) msg = err.optString("type", "");
                if (!msg.isEmpty()) return trimForUi(msg, 300);
            }
            String msg = obj.optString("message", "");
            if (!msg.isEmpty()) return trimForUi(msg, 300);
        } catch (JSONException ignored) {
        }
        return trimForUi(text.replaceAll("\\s+", " ").trim(), 240);
    }

    private String trimForUi(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private String friendlyError(Exception e) {
        if (e instanceof SocketTimeoutException) return "请求超时，AI 可能正在忙，请稍后再试";
        if (e instanceof UnknownHostException) return "找不到接口地址，请检查网络或 Base URL";
        if (e instanceof SSLException) return "HTTPS 连接失败，请检查接口地址";
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
        return trimForUi(msg, 500);
    }

    @SuppressWarnings("unused")
    private class NativeBridge {

        @JavascriptInterface
        public String getVersionName() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1.0.0";
            }
        }

        @JavascriptInterface
        public String getPlatform() {
            return "android";
        }

        @JavascriptInterface
        public int getSdkInt() {
            return Build.VERSION.SDK_INT;
        }

        @JavascriptInterface
        public void toast(String message) {
            if (message != null) toast(trimForUi(message, 200));
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                int duration = Math.max(1, Math.min(ms, 60));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(duration);
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String loadState() {
            synchronized (stateLock) {
                String json = readFile(stateFile());
                if (!isValidJsonObject(json)) json = readFile(stateBackupFile());
                return isValidJsonObject(json) ? json : "";
            }
        }

        @JavascriptInterface
        public boolean saveState(String json) {
            if (!isValidJsonObject(json) || json.length() > MAX_STATE_BYTES) return false;
            synchronized (stateLock) {
                return writeFileSafely(stateFile(), json);
            }
        }

        @JavascriptInterface
        public boolean hasNotificationPermission() {
            return MainActivity.this.hasNotificationPermission();
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(MainActivity.this::requestNotificationPermission);
        }

        @JavascriptInterface
        public boolean notifyBudget(final String title, final String body) {
            if (!hasNotificationPermission()) {
                runOnUiThread(MainActivity.this::requestNotificationPermission);
                return false;
            }
            mainHandler.post(() -> postBudgetNotification(trimForUi(title, 80), trimForUi(body, 500)));
            return true;
        }

        @JavascriptInterface
        public void copyText(String label, String text) {
            if (text == null) return;
            mainHandler.post(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText(label == null ? "哦鲸鲸记账" : label, text));
                    toast("已复制到剪贴板");
                }
            });
        }

        @JavascriptInterface
        public void shareText(String text) {
            if (text == null) return;
            mainHandler.post(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text);
                try {
                    startActivity(Intent.createChooser(send, "分享哦鲸鲸记账"));
                } catch (Exception e) {
                    toast("没有可以分享的应用");
                }
            });
        }

        @JavascriptInterface
        public void openUrl(String url) {
            if (url == null) return;
            try {
                handleUri(Uri.parse(url));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void aiChat(final String requestId, final String endpoint, final String apiKey,
                           final String model, final String messagesJson, final int maxTokens,
                           final double temperature) {
            if (requestId == null || endpoint == null || messagesJson == null) {
                deliverAi(requestId, false, null, "请求参数不完整");
                return;
            }
            pool.execute(() -> {
                try {
                    String content = callAi(endpoint, apiKey, model, messagesJson, maxTokens, temperature);
                    deliverAi(requestId, true, content, null);
                } catch (Exception e) {
                    Log.w(TAG, "AI request failed: " + e.getMessage());
                    deliverAi(requestId, false, null, friendlyError(e));
                }
            });
        }

        @JavascriptInterface
        public void exportBackup(final String json, final String filename) {
            if (!isValidJsonObject(json)) {
                deliverExport(false, "数据格式异常，导出失败");
                return;
            }
            pool.execute(() -> {
                try {
                    String message = writeExport(json, filename);
                    deliverExport(true, message);
                } catch (Exception e) {
                    deliverExport(false, "导出失败：" + friendlyError(e));
                }
            });
        }

        @JavascriptInterface
        public void importBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json", "text/plain", "application/octet-stream"
                });
                try {
                    startActivityForResult(intent, REQ_IMPORT);
                } catch (Exception e) {
                    deliverImport(false, "无法打开文件选择器");
                }
            });
        }

        void readImport(final Uri uri) {
            pool.execute(() -> {
                try {
                    String text = MainActivity.this.readImport(uri);
                    deliverImport(true, text);
                } catch (Exception e) {
                    deliverImport(false, "导入失败：" + friendlyError(e));
                }
            });
        }

        void deliverImport(boolean ok, String payload) {
            JSONObject o = new JSONObject();
            try {
                o.put("ok", ok);
                o.put("payload", payload == null ? "" : payload);
            } catch (JSONException ignored) {
            }
            callJs("window.__nativeImportResult(JSON.parse(" + JSONObject.quote(o.toString()) + "));");
        }

        void notifyPermissionResult(boolean granted) {
            callJs("window.__nativeNotifyPermission && window.__nativeNotifyPermission(" + granted + ");");
        }

        private void deliverAi(String requestId, boolean ok, String content, String error) {
            JSONObject o = new JSONObject();
            try {
                o.put("ok", ok);
                o.put("content", content == null ? "" : content);
                o.put("error", error == null ? "" : error);
            } catch (JSONException ignored) {
            }
            callJs("window.__nativeAIResult(" + JSONObject.quote(requestId == null ? "" : requestId) +
                    ", JSON.parse(" + JSONObject.quote(o.toString()) + "));");
        }

        private void deliverExport(boolean ok, String message) {
            callJs("window.__nativeExportResult(" + ok + ", " + JSONObject.quote(message == null ? "" : message) + ");");
        }
    }
}

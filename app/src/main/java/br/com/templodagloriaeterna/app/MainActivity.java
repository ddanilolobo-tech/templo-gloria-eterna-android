package br.com.templodagloriaeterna.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Base64;

import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_FILE = 4101;
    private static final int REQUEST_STORAGE = 4102;
    private static final int REQUEST_NOTIFICATIONS = 4103;
    private static final String CHURCH_HOST = "www.templodagloriaeterna.com.br";
    private static final String SITE_ORIGIN = "https://www.templodagloriaeterna.com.br";
    static final String EXTRA_NOTIFICATION_URL = "notification_url";

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private DownloadData pendingDownload;
    private String appUserAgent = "TemploGloriaEternaApp/1.0";
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.gravity = android.view.Gravity.TOP;
        root.addView(progressBar, progressParams);
        setContentView(root);

        configureWebView();
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(notificationUrlOrDefault(getIntent()));
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        appUserAgent = settings.getUserAgentString()
                + " TemploGloriaEternaApp/" + getVersionName();
        settings.setUserAgentString(appUserAgent);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebView.setWebContentsDebuggingEnabled(false);
        webView.setWebViewClient(new ChurchWebViewClient());
        webView.setWebChromeClient(new ChurchWebChromeClient());
        webView.setDownloadListener(new ChurchDownloadListener());
        webView.addJavascriptInterface(new NotificationBridge(), "TemploAndroid");
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "1.0";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isChurchUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return CHURCH_HOST.equalsIgnoreCase(host)
                || "templodagloriaeterna.com.br".equalsIgnoreCase(host);
    }

    private boolean openInsideOrOutside(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isChurchUrl(uri)) return false;
            openExternal(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }
        if ("intent".equals(scheme)) {
            try {
                openExternal(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME));
            } catch (Exception ignored) {
                Toast.makeText(this, "Não foi possível abrir este link.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if ("tel".equals(scheme) || "mailto".equals(scheme) || "sms".equals(scheme)
                || "market".equals(scheme) || "whatsapp".equals(scheme)) {
            openExternal(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }
        return false;
    }

    private void openExternal(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Nenhum aplicativo disponível para abrir este conteúdo.", Toast.LENGTH_SHORT).show();
        }
    }

    private final class ChurchWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return request.isForMainFrame() && openInsideOrOutside(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return openInsideOrOutside(Uri.parse(url));
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
            if (isChurchUrl(Uri.parse(url))) syncEnabledNotificationToken();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showOfflinePage();
        }
    }

    private void showOfflinePage() {
        String html = "<!doctype html><html lang='pt-BR'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;background:#071a33;color:white;font-family:sans-serif;display:grid;place-items:center;"
                + "min-height:100vh;text-align:center;padding:28px;box-sizing:border-box}div{max-width:360px}"
                + "h1{font-size:24px}p{color:#cbd5e1;line-height:1.5}button{border:0;border-radius:12px;padding:14px 22px;"
                + "font-size:16px;font-weight:700;background:#d5ad55;color:#071a33}</style><div>"
                + "<h1>Sem conexão com a internet</h1><p>Conecte o celular e tente abrir novamente.</p>"
                + "<button onclick='location.href=\"" + getString(R.string.launch_url) + "\"'>Tentar novamente</button>"
                + "</div></html>";
        webView.loadDataWithBaseURL("https://" + CHURCH_HOST, html, "text/html", "UTF-8", null);
    }

    private final class ChurchWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            progressBar.setProgress(progress);
            progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams parameters
        ) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            cameraUri = null;

            Intent contentIntent;
            try {
                contentIntent = parameters.createIntent();
            } catch (ActivityNotFoundException error) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "Não foi possível abrir seus arquivos.", Toast.LENGTH_SHORT).show();
                return false;
            }

            ArrayList<Intent> extraIntents = new ArrayList<>();
            if (parameters.isCaptureEnabled() || acceptsImage(parameters.getAcceptTypes())) {
                Intent cameraIntent = buildCameraIntent();
                if (cameraIntent != null) extraIntents.add(cameraIntent);
            }

            Intent chooser = new Intent(Intent.ACTION_CHOOSER);
            chooser.putExtra(Intent.EXTRA_INTENT, contentIntent);
            chooser.putExtra(Intent.EXTRA_TITLE, "Escolher arquivo ou tirar foto");
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Intent[0]));

            try {
                startActivityForResult(chooser, REQUEST_FILE);
                return true;
            } catch (ActivityNotFoundException error) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "Nenhum aplicativo disponível para escolher arquivos.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private boolean acceptsImage(String[] acceptedTypes) {
        if (acceptedTypes == null) return false;
        for (String type : acceptedTypes) {
            if (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/")) return true;
        }
        return false;
    }

    private Intent buildCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) return null;

        try {
            File directory = new File(getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) return null;
            File image = File.createTempFile("foto-", ".jpg", directory);
            cameraUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    image
            );
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("foto", cameraUri));
            return intent;
        } catch (IOException error) {
            cameraUri = null;
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILE || fileCallback == null) return;

        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if ((data == null || data.getData() == null) && cameraUri != null) {
                result = new Uri[]{cameraUri};
            } else {
                result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
        }

        fileCallback.onReceiveValue(result);
        fileCallback = null;
        cameraUri = null;
    }

    private final class ChurchDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType,
                long contentLength
        ) {
            DownloadData download = new DownloadData(url, userAgent, contentDisposition, mimeType);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = download;
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE
                );
                return;
            }
            enqueueDownload(download);
        }
    }

    private void enqueueDownload(DownloadData data) {
        Uri uri = Uri.parse(data.url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, "Download bloqueado por segurança.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = URLUtil.guessFileName(data.url, data.contentDisposition, data.mimeType);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(fileName);
            request.setDescription("Baixando pelo aplicativo da igreja");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            String cookie = CookieManager.getInstance().getCookie(data.url);
            if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);
            if (data.userAgent != null) request.addRequestHeader("User-Agent", data.userAgent);

            String mime = data.mimeType;
            if ((mime == null || mime.isEmpty()) && fileName.contains(".")) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            if (mime != null && !mime.isEmpty()) request.setMimeType(mime);

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download iniciado. Veja a pasta Downloads.", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            openExternal(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(TemploMessagingService.PREF_ENABLED, granted).apply();
            if (granted) syncEnabledNotificationToken();
            notifyNotificationStateChanged();
            return;
        }
        if (requestCode == REQUEST_STORAGE && pendingDownload != null) {
            DownloadData download = pendingDownload;
            pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enqueueDownload(download);
            } else {
                Toast.makeText(this, "Permita salvar arquivos para concluir o download.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        webView.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (webView != null) {
            webView.removeJavascriptInterface("TemploAndroid");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        notificationExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String target = notificationUrlOrDefault(intent);
        if (webView != null && isChurchUrl(Uri.parse(target))) webView.loadUrl(target);
    }

    private String notificationUrlOrDefault(Intent intent) {
        String target = intent == null ? null : intent.getStringExtra(EXTRA_NOTIFICATION_URL);
        if (target != null && isChurchUrl(Uri.parse(target))) return target;
        Uri deepLink = intent == null ? null : intent.getData();
        if (isChurchUrl(deepLink)) return deepLink.toString();
        return getString(R.string.launch_url);
    }

    private boolean notificationPermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void enableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted()) {
            getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(TemploMessagingService.PREF_ASKED, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                .edit().putBoolean(TemploMessagingService.PREF_ENABLED, true).apply();
        syncEnabledNotificationToken();
        notifyNotificationStateChanged();
    }

    private void disableNotifications() {
        getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                .edit().putBoolean(TemploMessagingService.PREF_ENABLED, false).apply();
        syncNotificationToken(true);
        notifyNotificationStateChanged();
    }

    private void notifyNotificationStateChanged() {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
                "window.dispatchEvent(new Event('templo-notification-state'));",
                null
        ));
    }

    private void syncEnabledNotificationToken() {
        boolean enabled = getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                .getBoolean(TemploMessagingService.PREF_ENABLED, false);
        if (enabled && notificationPermissionGranted()) syncNotificationToken(false);
    }

    private void syncNotificationToken(boolean remove) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;
            String token = task.getResult();
            getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                    .edit().putString(TemploMessagingService.PREF_TOKEN, token).apply();
            notificationExecutor.execute(() -> sendTokenToSite(token, remove));
        });
    }

    private void sendTokenToSite(String token, boolean remove) {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(SITE_ORIGIN + "/api/membros/push-nativo");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod(remove ? "DELETE" : "PUT");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", appUserAgent);
            String cookie = CookieManager.getInstance().getCookie(SITE_ORIGIN);
            if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
            connection.setDoOutput(true);
            String preferences = getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                    .getString(TemploMessagingService.PREF_PREFERENCES, TemploMessagingService.DEFAULT_PREFERENCES);
            String body = "{\"token\":\"" + jsonEscape(token) + "\",\"preferencias\":" + preferences + "}";
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            connection.getResponseCode();
        } catch (Exception ignored) {
            // O próximo carregamento de uma página da igreja tenta sincronizar novamente.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private final class NotificationBridge {
        @JavascriptInterface
        public String getNotificationStatus() {
            boolean enabled = getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                    .getBoolean(TemploMessagingService.PREF_ENABLED, false);
            boolean asked = getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                    .getBoolean(TemploMessagingService.PREF_ASKED, false);
            if (!notificationPermissionGranted()) return asked ? "denied" : "disabled";
            return enabled ? "granted" : "disabled";
        }

        @JavascriptInterface
        public String getAppVersion() {
            try {
                int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                return "android-" + versionCode;
            } catch (PackageManager.NameNotFoundException error) {
                return "";
            }
        }

        @JavascriptInterface
        public void requestNotifications() {
            runOnUiThread(MainActivity.this::enableNotifications);
        }

        @JavascriptInterface
        public void saveNotificationPreferences(String preferencesJson) {
            if (preferencesJson == null || preferencesJson.length() > 3000 || !preferencesJson.trim().startsWith("{")) return;
            getSharedPreferences(TemploMessagingService.PREFS, MODE_PRIVATE)
                    .edit().putString(TemploMessagingService.PREF_PREFERENCES, preferencesJson).apply();
            syncEnabledNotificationToken();
        }

        @JavascriptInterface
        public void disableNotifications() {
            runOnUiThread(MainActivity.this::disableNotifications);
        }

        @JavascriptInterface
        public void openPdf(String base64, String fileName) {
            if (base64 == null || base64.isEmpty() || base64.length() > 16 * 1024 * 1024) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "O PDF não pôde ser aberto.", Toast.LENGTH_LONG).show());
                return;
            }
            notificationExecutor.execute(() -> {
                try {
                    String safeName = fileName == null ? "carteirinha.pdf" : fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
                    if (!safeName.toLowerCase(Locale.ROOT).endsWith(".pdf")) safeName += ".pdf";
                    File directory = new File(getCacheDir(), "pdfs");
                    if (!directory.exists() && !directory.mkdirs()) throw new IOException("PDF directory unavailable");
                    File pdfFile = new File(directory, safeName);
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    try (FileOutputStream output = new FileOutputStream(pdfFile)) {
                        output.write(bytes);
                    }
                    Uri pdfUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            getPackageName() + ".fileprovider",
                            pdfFile
                    );
                    runOnUiThread(() -> {
                        Intent viewPdf = new Intent(Intent.ACTION_VIEW);
                        viewPdf.setDataAndType(pdfUri, "application/pdf");
                        viewPdf.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(viewPdf);
                        } catch (ActivityNotFoundException error) {
                            Intent sharePdf = new Intent(Intent.ACTION_SEND);
                            sharePdf.setType("application/pdf");
                            sharePdf.putExtra(Intent.EXTRA_STREAM, pdfUri);
                            sharePdf.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try {
                                startActivity(Intent.createChooser(sharePdf, "Abrir ou salvar a carteirinha"));
                            } catch (ActivityNotFoundException ignored) {
                                Toast.makeText(MainActivity.this, "Nenhum aplicativo disponível para abrir PDF.", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível abrir o PDF.", Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    private static final class DownloadData {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimeType;

        DownloadData(String url, String userAgent, String contentDisposition, String mimeType) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }
}

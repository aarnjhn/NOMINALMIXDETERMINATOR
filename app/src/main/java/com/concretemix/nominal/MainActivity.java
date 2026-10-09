package com.concretemix.nominal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_SAVE = 1001;
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";

    // The page exports CSV via <a download href="blob:..."> .click(), which WebView ignores.
    // This hook catches that and hands the file to Android so the user can save it.
    private static final String HOOK_JS = """
        (function(){
          if (window.__apkHooked) return; window.__apkHooked = true;
          var orig = HTMLAnchorElement.prototype.click;
          HTMLAnchorElement.prototype.click = function(){
            var a = this;
            if (a.download && a.href && a.href.indexOf('blob:') === 0) {
              fetch(a.href).then(function(r){ return r.blob(); }).then(function(b){
                var fr = new FileReader();
                fr.onloadend = function(){
                  AndroidBridge.saveFile(fr.result, b.type || 'application/octet-stream', a.download);
                };
                fr.readAsDataURL(b);
              });
              return;
            }
            return orig.apply(this, arguments);
          };
        })();
        """;

    private WebView webView;
    private byte[] pendingBytes;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage (mix history)
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(HOOK_JS, null);
            }
        });

        // The page uses alert() and confirm(); WebView needs these to show them.
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void saveFile(String dataUrl, String mime, String name) {
            try {
                int comma = dataUrl.indexOf(',');
                final byte[] data = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                final String type = mime.contains(";") ? mime.substring(0, mime.indexOf(';')) : mime;
                final String fileName = (name == null || name.isEmpty()) ? "export.csv" : name;
                runOnUiThread(() -> {
                    pendingBytes = data;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(type.isEmpty() ? "*/*" : type);
                    i.putExtra(Intent.EXTRA_TITLE, fileName);
                    startActivityForResult(i, REQ_SAVE);
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Could not export file", Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE) return;
        byte[] bytes = pendingBytes;
        pendingBytes = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || bytes == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
            if (os == null) throw new IllegalStateException("no stream");
            os.write(bytes);
            Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save file", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
        }
        super.onDestroy();
    }
}

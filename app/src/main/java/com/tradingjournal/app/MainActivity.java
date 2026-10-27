package com.tradingjournal.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQ_SAVE = 1;
    private static final int REQ_OPEN = 2;

    private WebView web;
    private String pendingBackup = "";

    /** Called from the web page: window.AndroidBridge.saveBackup(...) / restoreBackup() */
    private class Bridge {
        @JavascriptInterface
        public void saveBackup(String json, String filename) {
            pendingBackup = json;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, filename);
                startActivityForResult(i, REQ_SAVE);
            });
        }

        @JavascriptInterface
        public void restoreBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_OPEN);
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0b1220"));
        getWindow().setNavigationBarColor(Color.parseColor("#0b1220"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b1220"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // trades are saved in localStorage
        s.setAllowFileAccess(true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return; // the user cancelled the picker
        }
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_SAVE) {
                try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                    os.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                }
                runJs("window.nativeSaved && window.nativeSaved(true)");
            } else if (requestCode == REQ_OPEN) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                }
                String text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                runJs("window.nativeRestore && window.nativeRestore(" + JSONObject.quote(text) + ")");
            }
        } catch (Exception e) {
            runJs("window.nativeSaved && window.nativeSaved(false)");
        }
    }

    private void runJs(String js) {
        web.evaluateJavascript(js, null);
    }
}

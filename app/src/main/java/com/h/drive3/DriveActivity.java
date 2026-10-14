package com.h.drive3;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Một hồ sơ Drive. Mỗi lớp con chạy trong process riêng (xem AndroidManifest)
 * và dùng thư mục dữ liệu WebView riêng => tài khoản Google tách biệt.
 */
public abstract class DriveActivity extends Activity {

    protected abstract String suffix();
    protected abstract String startUrl();
    protected abstract String pickType();
    protected abstract String label();

    private static final int RC_PICK = 11;
    private static final int RC_CHOOSER = 12;

    private WebView web;
    private ProgressBar bar;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private ValueCallback<Uri[]> chooserCb;
    private Uri[] pending;
    private boolean triedAuto;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            WebView.setDataDirectorySuffix(suffix());
        } catch (IllegalStateException ignored) { }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildUi();
        setupWebView();

        web.loadUrl(startUrl());
        if (getIntent().getBooleanExtra("sync", false)) pickFiles();
    }

    private void buildUi() {
        root = new FrameLayout(this);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setBackgroundColor(0xFF202124);

        // Thanh dọc bên trái: tận dụng chiều ngang, nhường tối đa chiều cao cho Drive
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setBackgroundColor(0xFF202124);
        main.addView(rail, new LinearLayout.LayoutParams(dp(64), -1));

        TextView tag = new TextView(this);
        tag.setText(label());
        tag.setTextColor(0xFF8AB4F8);
        tag.setTextSize(12);
        tag.setGravity(Gravity.CENTER);
        rail.addView(tag, new LinearLayout.LayoutParams(-1, dp(28)));

        rail.addView(railBtn("⌂", "Chính", v -> finish()));
        rail.addView(railBtn("◀", "Lùi", v -> onBackPressed()));
        rail.addView(railBtn("⟳", "Tải lại", v -> web.reload()));
        rail.addView(railBtn("⬆", "Đồng bộ", v -> pickFiles()));

        FrameLayout content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(0, -1, 1f));

        web = new WebView(this);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        content.addView(bar, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));

        root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private View railBtn(String icon, String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(icon + "\n" + text);
        t.setTextColor(0xFFE8EAED);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        return t;
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        // Giữ nguyên User-Agent thật của WebView, không giả mạo.

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sch = r.getUrl().getScheme();
                return !("http".equals(sch) || "https".equals(sch));
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                bar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                bar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                if (pending != null && !triedAuto && url != null
                        && url.contains("drive.google.com")) {
                    triedAuto = true;
                    web.postDelayed(() -> tryAutoUpload(), 1500);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (chooserCb != null) chooserCb.onReceiveValue(null);
                chooserCb = null;
                if (pending != null) {
                    cb.onReceiveValue(pending);
                    pending = null;
                    toast("Đang tải lên Drive…");
                    return true;
                }
                chooserCb = cb;
                try {
                    startActivityForResult(params.createIntent(), RC_CHOOSER);
                } catch (Exception e) {
                    chooserCb = null;
                    cb.onReceiveValue(null);
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customCb != null) customCb.onCustomViewHidden();
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) r.addRequestHeader("Cookie", ck);
                r.addRequestHeader("User-Agent", ua);
                r.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, cd, mime));
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                toast("Đang tải xuống…");
            } catch (Exception e) {
                toast("Không tải xuống được");
            }
        });
    }

    /** Chọn tệp trong máy (lọc theo mục: ảnh / video / tệp). */
    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(pickType());
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, RC_PICK);
        } catch (Exception e) {
            toast("Không mở được trình chọn tệp");
        }
    }

    /**
     * Cố gắng tự bấm "Mới → Tải tệp lên" trên giao diện Drive.
     * Nếu Drive đổi giao diện/ngôn ngữ không khớp thì bấm tay: tệp đã chọn vẫn được
     * trao cho hộp thoại chọn tệp ngay khi Drive mở nó.
     */
    private void tryAutoUpload() {
        String js = "(function(){"
                + "function clk(labels){var n=document.querySelectorAll("
                + "'[aria-label],[data-tooltip],div[role=button],div[role=menuitem],button');"
                + "for(var i=0;i<n.length;i++){var e=n[i];"
                + "var t=(e.getAttribute('aria-label')||e.getAttribute('data-tooltip')||e.innerText||'')"
                + ".trim().toLowerCase();"
                + "if(labels.indexOf(t)>=0){e.click();return true;}}return false;}"
                + "if(clk(['new','mới','upload','tải lên'])){"
                + "setTimeout(function(){clk(['file upload','tải tệp lên','upload files','upload file','tải lên tệp']);},700);}"
                + "})();";
        web.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == RC_PICK) {
            if (res == RESULT_OK && data != null) {
                Uri[] u = parse(data);
                if (u != null && u.length > 0) {
                    pending = u;
                    triedAuto = false;
                    toast("Đã chọn " + u.length + " tệp – đang mở hộp tải lên của Drive");
                    tryAutoUpload();
                }
            }
        } else if (rc == RC_CHOOSER && chooserCb != null) {
            chooserCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            chooserCb = null;
        }
    }

    private Uri[] parse(Intent d) {
        ClipData c = d.getClipData();
        if (c != null) {
            Uri[] u = new Uri[c.getItemCount()];
            for (int i = 0; i < u.length; i++) u[i] = c.getItemAt(i).getUri();
            return u;
        }
        return d.getData() != null ? new Uri[]{d.getData()} : null;
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            web.getWebChromeClient().onHideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}

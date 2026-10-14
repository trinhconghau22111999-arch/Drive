package com.h.drive3;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
    protected abstract String label();
    protected abstract int accent();

    private static final int RC_CHOOSER = 12;
    private static final int RAIL_BG = 0xFF16181D;

    private WebView web;
    private ProgressBar bar;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private ValueCallback<Uri[]> chooserCb;
    private boolean autoNew;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            WebView.setDataDirectorySuffix(suffix());
        } catch (IllegalStateException ignored) { }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(RAIL_BG);
        autoNew = getIntent().getBooleanExtra("sync", false);

        buildUi();
        setupWebView();
        web.loadUrl(startUrl());
    }

    private void buildUi() {
        root = new FrameLayout(this);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setBackgroundColor(RAIL_BG);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(4), dp(8), dp(4), dp(8));
        rail.setBackgroundColor(RAIL_BG);
        main.addView(rail, new LinearLayout.LayoutParams(dp(68), -1));

        TextView tag = new TextView(this);
        tag.setText(label());
        tag.setTextColor(Color.WHITE);
        tag.setTextSize(11);
        tag.setLetterSpacing(0.1f);
        tag.setGravity(Gravity.CENTER);
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(accent());
        chip.setCornerRadius(dp(10));
        tag.setBackground(chip);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(26));
        tp.setMargins(dp(4), 0, dp(4), dp(6));
        rail.addView(tag, tp);

        rail.addView(railBtn("⌂", "Chính", false, v -> finish()));
        rail.addView(railBtn("◀", "Lùi", false, v -> onBackPressed()));
        rail.addView(railBtn("⟳", "Tải lại", false, v -> web.reload()));
        rail.addView(railBtn("⬆", "Đồng bộ", true, v -> clickNew(3)));

        FrameLayout content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(0, -1, 1f));

        web = new WebView(this);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(accent()));
        content.addView(bar, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));

        root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private View railBtn(String icon, String text, boolean primary, View.OnClickListener l) {
        TextView t = new TextView(this);
        SpannableString sp = new SpannableString(icon + "\n" + text);
        sp.setSpan(new RelativeSizeSpan(1.7f), 0, icon.length(), 0);
        t.setText(sp);
        t.setTextColor(primary ? Color.WHITE : 0xFFC4C7CC);
        t.setTextSize(10);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setOnClickListener(l);

        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(16));
        fill.setColor(primary ? accent() : Color.TRANSPARENT);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(16));
        Drawable bg = new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), fill, mask);
        t.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.setMargins(dp(4), dp(3), dp(4), dp(3));
        t.setLayoutParams(lp);
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
                // Bấm "Đồng bộ" từ màn hình chính: khi vào tới trang Drive thì tự bấm nút "Mới".
                if (autoNew && url != null
                        && "drive.google.com".equals(Uri.parse(url).getHost())) {
                    autoNew = false;
                    web.postDelayed(() -> clickNew(6), 1000);
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

    /**
     * "Đồng bộ" = bấm nút "Mới" trên trang Google Drive (menu Tải tệp lên / Tạo thư mục…).
     * Thử lại nhiều lần vì Drive là trang tải động.
     */
    private void clickNew(final int tries) {
        String js = "(function(){"
                + "var L=['new','mới','tạo mới'];"
                + "var n=document.querySelectorAll("
                + "'[aria-label],[data-tooltip],div[role=button],button');"
                + "for(var i=0;i<n.length;i++){var e=n[i];"
                + "var t=(e.getAttribute('aria-label')||e.getAttribute('data-tooltip')||e.innerText||'')"
                + ".trim().toLowerCase();"
                + "for(var j=0;j<L.length;j++){"
                + "if(t===L[j]||t.endsWith('\\n'+L[j])){e.click();return true;}}}"
                + "return false;})();";
        web.evaluateJavascript(js, v -> {
            if ("true".equals(v)) return;
            if (tries > 0) {
                web.postDelayed(() -> clickNew(tries - 1), 1000);
            } else {
                toast("Chưa thấy nút Mới – hãy đăng nhập hoặc bấm tay");
            }
        });
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == RC_CHOOSER && chooserCb != null) {
            chooserCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            chooserCb = null;
        }
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

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
        root.setBackgroundColor(0xFF000000);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(accent()));
        root.addView(bar, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));

        setContentView(root);
    }

    /**
     * Chạy trên trang Drive: tự chuyển sang dạng lưới và ẩn phần "Thư mục",
     * chỉ để lại phần "Tệp". Tìm theo chữ tiêu đề nên không phụ thuộc tên class của Drive.
     */
    private static final String TIDY_JS = """
(function(){
  if(window.__d3) return; window.__d3=true;
  var gridDone=false;
  function txt(e){return (e.innerText||'').trim().toLowerCase();}
  function leaf(labels, after){
    var all=document.body.getElementsByTagName('*');
    for(var i=0;i<all.length;i++){
      var e=all[i];
      if(e.children.length!==0) continue;
      if(labels.indexOf(txt(e))<0) continue;
      if(after && !(after.compareDocumentPosition(e) & 4)) continue;
      return e;
    }
    return null;
  }
  function hideFolders(){
    var f=leaf(['folders','thư mục']);
    if(!f) return;
    var g=leaf(['files','tệp'], f);
    if(!g) return;
    var p=f;
    while(p.parentElement && !p.parentElement.contains(g)) p=p.parentElement;
    if(p!==document.body && !p.contains(g)) p.style.setProperty('display','none','important');
  }
  function ensureGrid(){
    if(gridDone) return;
    var n=document.querySelectorAll('[aria-label],[data-tooltip]');
    for(var i=0;i<n.length;i++){
      var e=n[i];
      var t=(e.getAttribute('aria-label')||e.getAttribute('data-tooltip')||'').toLowerCase();
      var toGrid=(t.indexOf('grid')>=0&&t.indexOf('switch')>=0)||(t.indexOf('lưới')>=0&&t.indexOf('chuyển')>=0);
      if(toGrid){e.click();gridDone=true;return;}
    }
  }
  setInterval(function(){
    if(!document.body) return;
    try{hideFolders();ensureGrid();}catch(x){}
  },800);
})();
""";

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
                if (url != null && "drive.google.com".equals(Uri.parse(url).getHost())) {
                    web.evaluateJavascript(TIDY_JS, null);
                }
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

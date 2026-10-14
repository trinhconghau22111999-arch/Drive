package com.h.drive3;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
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
    protected abstract String pickType();

    private static final int RC_CHOOSER = 12;
    private static final int RAIL_BG = 0xFF16181D;

    private WebView web;
    private ProgressBar bar;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private ValueCallback<Uri[]> chooserCb;
    private Uri[] pending;
    private boolean pageReady;
    private boolean uploadStarted;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            WebView.setDataDirectorySuffix(suffix());
        } catch (IllegalStateException ignored) { }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(RAIL_BG);

        buildUi();
        setupWebView();
        web.loadUrl(startUrl());
        if (getIntent().getBooleanExtra("sync", false)) pickFiles();
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
  var gridDone=false, promoClicked=false;
  function norm(s){return (s||'').replace(/\\s+/g,' ').trim().toLowerCase();}
  function txtOf(e){return norm(e.innerText||e.textContent);}
  // Tìm phần tử chứa đúng đoạn chữ (duyệt theo text node, không phụ thuộc class của Drive)
  function find(labels, after){
    var w=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var n;
    while((n=w.nextNode())){
      if(labels.indexOf(norm(n.nodeValue))<0) continue;
      var e=n.parentElement;
      if(!e) continue;
      if(after && !(after.compareDocumentPosition(e) & 4)) continue;
      return e;
    }
    return null;
  }
  function hide(e){
    if(e && e!==document.body && e!==document.documentElement)
      e.style.setProperty('display','none','important');
  }
  // Ẩn nguyên 1 dòng menu có chữ này
  function hideRow(labels){
    var e=find(labels);
    if(!e) return;
    var t=txtOf(e), p=e;
    while(p.parentElement && p.parentElement!==document.body && txtOf(p.parentElement)===t) p=p.parentElement;
    hide(p);
  }
  // Bảng đề xuất "Tải ứng dụng Drive": ẩn và bấm "Không, cảm ơn" một lần
  function hidePromo(){
    var btn=find(['không, cảm ơn','no, thanks','no thanks']);
    if(!btn) return;
    var box=null, el=btn;
    while(el && el!==document.body){
      var role=el.getAttribute('role');
      if(getComputedStyle(el).position==='fixed'||role==='dialog'||role==='alertdialog'){box=el;break;}
      el=el.parentElement;
    }
    if(box && txtOf(box).length>=300) box=null;
    if(!box){
      var h=find(['tải ứng dụng drive','get the drive app']);
      if(h){
        var p=h;
        while(p.parentElement && !p.parentElement.contains(btn)) p=p.parentElement;
        box=p.parentElement;
        if(box && txtOf(box).length>=300) box=null;
      }
    }
    hide(box);
    if(!promoClicked){promoClicked=true;try{btn.click();}catch(x){}}
  }
  function hideFolders(){
    var f=find(['folders','thư mục']);
    if(!f) return;
    var g=find(['files','tệp'], f);
    if(!g) return;
    var p=f;
    while(p.parentElement && !p.parentElement.contains(g)) p=p.parentElement;
    if(p!==document.body && !p.contains(g)) hide(p);
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
    try{
      hidePromo();
      hideRow(['tải ứng dụng google drive','get the drive app','get drive for desktop']);
      hideFolders();
      ensureGrid();
    }catch(x){}
  },900);
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
                    pageReady = true;
                    // Đã chọn tệp từ máy: vào tới trang Drive thì tự bấm Mới -> Tải tệp lên.
                    if (pending != null && !uploadStarted) {
                        uploadStarted = true;
                        web.postDelayed(() -> runUpload(), 1500);
                    }
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

    private static final int RC_PICK = 11;

    private static final String CLICK_NEW_JS = """
(function(){
  var L=['new','mới','tạo mới'];
  var n=document.querySelectorAll('[aria-label],[data-tooltip],div[role=button],button');
  for(var i=0;i<n.length;i++){
    var e=n[i];
    var t=(e.getAttribute('aria-label')||e.getAttribute('data-tooltip')||e.innerText||'').trim().toLowerCase();
    for(var j=0;j<L.length;j++){
      if(t===L[j]||t.endsWith('\\n'+L[j])){e.click();return true;}
    }
  }
  return false;
})();
""";

    private static final String CLICK_UPLOAD_JS = """
(function(){
  var L=['file upload','tải tệp lên','upload files','upload file','tải lên tệp','upload','tải lên'];
  var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null),n;
  while((n=w.nextNode())){
    var t=(n.nodeValue||'').replace(/\\s+/g,' ').trim().toLowerCase();
    if(L.indexOf(t)<0) continue;
    var e=n.parentElement; if(!e) continue;
    var c=e.closest('[role=menuitem],[role=button],button,li')||e;
    c.click(); return true;
  }
  return false;
})();
""";

    /** Chọn tệp trong máy, lọc theo mục: ảnh / video / tệp. */
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

    /** Tự bấm "Mới" rồi "Tải tệp lên"; Drive gọi hộp chọn tệp thì ta đưa tệp đã chọn vào. */
    private void runUpload() {
        clickStep(CLICK_NEW_JS, 8, () -> clickStep(CLICK_UPLOAD_JS, 6, null));
    }

    private void clickStep(final String js, final int tries, final Runnable next) {
        if (pending == null) return; // tệp đã được giao cho Drive
        web.evaluateJavascript(js, v -> {
            if ("true".equals(v)) {
                if (next != null) web.postDelayed(next, 800);
                return;
            }
            if (tries > 0) {
                web.postDelayed(() -> clickStep(js, tries - 1, next), 800);
            } else if (pending != null) {
                toast("Chưa tự bấm được – hãy bấm Mới → Tải tệp lên, tệp đã chọn sẽ được gửi");
            }
        });
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == RC_PICK) {
            if (res == RESULT_OK && data != null) {
                Uri[] u = parse(data);
                if (u != null && u.length > 0) {
                    pending = u;
                    uploadStarted = false;
                    toast("Đã chọn " + u.length + " tệp – đang gửi lên Drive");
                    if (pageReady) {
                        uploadStarted = true;
                        runUpload();
                    }
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

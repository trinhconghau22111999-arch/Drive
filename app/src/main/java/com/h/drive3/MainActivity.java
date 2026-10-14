package com.h.drive3;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.setPadding(dp(16), dp(10), dp(16), dp(14));

        TextView head = new TextView(this);
        head.setText("Drive – 3 hồ sơ cố định");
        head.setTextColor(0xFFBBBBBB);
        head.setTextSize(15);
        head.setGravity(Gravity.CENTER);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // Hàng ngang 3 thẻ – hợp với màn hình ngang
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));

        row.addView(card("🖼", "Ảnh", "Hồ sơ 1", 0xFF1E88E5, PhotoActivity.class));
        row.addView(card("🎬", "Video", "Hồ sơ 2", 0xFFE53935, VideoActivity.class));
        row.addView(card("📁", "Tệp", "Hồ sơ 3", 0xFF43A047, FileActivity.class));

        setContentView(root);
    }

    private View card(String icon, String title, String sub, int color, final Class<?> target) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(20));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(dp(8), dp(10), dp(8), 0);
        c.setLayoutParams(lp);
        c.setClickable(true);
        c.setOnClickListener(v -> open(target, false));

        TextView i = new TextView(this);
        i.setText(icon);
        i.setTextSize(48);
        i.setGravity(Gravity.CENTER);
        c.addView(i);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        c.addView(t);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(0xCCFFFFFF);
        s.setTextSize(13);
        s.setGravity(Gravity.CENTER);
        c.addView(s);

        Button sync = new Button(this);
        sync.setText("⬆ Đồng bộ");
        sync.setAllCaps(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(dp(10), dp(12), dp(10), 0);
        sync.setOnClickListener(v -> open(target, true));
        c.addView(sync, bp);
        return c;
    }

    private void open(Class<?> target, boolean sync) {
        Intent it = new Intent(this, target);
        it.putExtra("sync", sync);
        startActivity(it);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}

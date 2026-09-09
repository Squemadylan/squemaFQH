package com.byterax.phoenix.read.quark;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.byterax.phoenix.read.Constants;
import com.byterax.phoenix.read.ScopeStatus;

/**
 * Quark feature panel — writes the same cfg file as original QuarkHook.
 */
public class QuarkSettingsActivity extends Activity {

    private static final String SUPPORT_VERSION = "10.15.5.1130";

    private TextView statusText;
    private TextView statusSub;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Config.ensureDefaults();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, dp(20));

        TextView title = new TextView(this);
        title.setText("夸克");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(0xFFF2EDE4);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("配置与原版 QuarkHook 相同（Download/QuarkHook/quark_bypass.cfg）\n"
                + "改开关后请强停夸克再开 · 建议版本 " + SUPPORT_VERSION);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTextColor(0xFFA89F8E);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        root.addView(buildStatusCard());

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(buildSwitch("总开关", "关闭后全部功能不生效", Config.K_ENABLE));
        list.addView(buildSwitch("违规域名拦截绕过", "绕过 URL 安全扫描 / 封禁页", Config.K_BLOCK_SECURITY));
        list.addView(buildSwitch("禁用书城入口", "移除书城 / 小说相关入口", Config.K_BLOCK_BOOK));
        list.addView(buildSwitch("首页模块过滤", "过滤创作 / 办公 / 学习等分组", Config.K_BLOCK_NAVI));
        list.addView(buildSwitch("我的页面广告清除", "清理我的页横幅与福利模块", Config.K_BLOCK_USER_CENTER));
        list.addView(buildSwitch("锁定设置项", "锁定导航 / 推送 / 推荐等开关", Config.K_LOCK_SETTINGS));
        list.addView(buildSwitch("拦截网盘流畅播提示", "隐藏存网盘流畅播相关提示", Config.K_BLOCK_SNIFF));
        list.addView(buildSwitch("禁止夸克更新", "拦截升级弹窗与组件热更新", Config.K_BLOCK_UPDATE));
        list.addView(buildSwitch("解锁画质音效", "解锁高清画质与智能音效", Config.K_UNLOCK_AV));
        list.addView(buildSwitch("移除夏日任务悬浮", "隐藏夏日任务进度悬浮窗", Config.K_BLOCK_SUMMER_TASK));
        list.addView(buildSwitch("日志", "输出调试日志", Config.K_ENABLE_LOG));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config.invalidate();
        refreshStatus();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private View buildStatusCard() {
        LinearLayout card = glassCard();
        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        card.addView(statusText);
        statusSub = new TextView(this);
        statusSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusSub.setTextColor(0xFFA89F8E);
        statusSub.setPadding(0, dp(4), 0, 0);
        card.addView(statusSub);
        refreshStatus();
        return card;
    }

    private void refreshStatus() {
        if (statusText == null) {
            return;
        }
        // Trust ScopeStatus only — do not OR stale tmp/prefs when out of scope.
        ScopeStatus.State state = ScopeStatus.resolve(this, Constants.PKG_QUARK);

        if (state == ScopeStatus.State.LIVE) {
            statusText.setText("运行中");
            statusText.setTextColor(0xFFE8D5A3);
            statusSub.setText("夸克进程已加载 Hook");
        } else if (!isQuarkInstalled()) {
            statusText.setText("未安装夸克");
            statusText.setTextColor(0xFFA89F8E);
            statusSub.setText("未找到 com.quark.browser");
        } else if (state == ScopeStatus.State.SCOPED) {
            statusText.setText("已勾选");
            statusText.setTextColor(0xFFE8D5A3);
            statusSub.setText("请打开夸克；成功会 Toast「夸克 Hook 成功」");
        } else {
            statusText.setText("未勾选");
            statusText.setTextColor(0xFFA89F8E);
            statusSub.setText("请在 LSPosed 勾选夸克浏览器");
        }
    }

    private boolean isQuarkInstalled() {
        try {
            getPackageManager().getPackageInfo(Constants.PKG_QUARK, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private View buildSwitch(final String title, String desc, final String key) {
        LinearLayout card = glassCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(0xFFF2EDE4);
        texts.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        d.setTextColor(0xFF8F846C);
        d.setPadding(0, dp(3), 0, 0);
        texts.addView(d);
        row.addView(texts);

        Switch sw = new Switch(this);
        sw.setChecked(Config.isEnabled(key));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Config.set(key, isChecked);
                Toast.makeText(QuarkSettingsActivity.this,
                        title + (isChecked ? " 已开" : " 已关") + " · 重启夸克生效",
                        Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(sw);
        card.addView(row);
        return card;
    }

    private LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0x22FFFFFF);
        bg.setStroke(Math.max(1, dp(1) / 2), 0x40C9A84B);
        card.setBackground(bg);
        int p = dp(14);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

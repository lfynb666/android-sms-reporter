package com.gamify.smsreporter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMS Reporter - 原生单页面，视觉风格对齐gamify前端
 * 功能：权限申请 + 服务器连接配置 + 服务状态显示
 */
public class MainActivity extends Activity {
    private static final String TAG = "SMSReporter";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String DEFAULT_SERVER = "https://api.666-lufengyuan-nb.top";

    // 颜色常量（对齐gamify前端设计语言）
    private static final String C_BG = "#141414";
    private static final String C_CARD = "#1a1a1a";
    private static final String C_HEADER = "#1f1f1f";
    private static final String C_ACCENT = "#fffa00";
    private static final String C_TEXT = "#eeeeee";
    private static final String C_TEXT_SEC = "#858585";
    private static final String C_TEXT_DIM = "#555555";
    private static final String C_BORDER = "#262626";
    private static final String C_BTN = "#383838";
    private static final String C_GREEN = "#00ffa2";
    private static final String C_PINK = "#ff00f0";
    private static final String C_RED = "#ff6464";

    private TextView statusSms;
    private TextView statusNotification;
    private TextView statusService;
    private TextView statusServer;
    private TextView statusStats;
    private EditText serverUrlInput;
    private EditText tokenInput;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.parseColor(C_BG));
            getWindow().setNavigationBarColor(Color.parseColor(C_BG));
        }
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // 根布局
        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Color.parseColor(C_BG));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ── 顶部Header Bar（模拟 .page-header-bar） ──
        root.addView(buildHeaderBar());

        // ── 三色装饰线（模拟 .deco-line） ──
        root.addView(buildDecoLine());

        // ── 主内容区 ──
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(48));

        // ── 权限状态区 ──
        content.addView(buildSectionTitle("PERMISSION", "权限状态"));
        content.addView(buildCardGroup(() -> {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);

            statusSms = buildStatusItem("SMS RECEIVE / READ");
            group.addView(statusSms);
            group.addView(buildDivider());

            statusNotification = buildStatusItem("NOTIFICATION");
            group.addView(statusNotification);

            return group;
        }), matchWrap(dp(12)));

        content.addView(buildAccentButton("REQUEST ALL PERMISSIONS", v -> requestAllPermissions()), matchWrap(dp(16)));

        // ── 服务状态区 ──
        content.addView(buildSectionTitle("SERVICE STATUS", "服务状态"), matchWrap(dp(32)));
        content.addView(buildCardGroup(() -> {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);

            statusService = buildStatusItem("FOREGROUND SERVICE");
            group.addView(statusService);
            group.addView(buildDivider());

            statusServer = buildStatusItem("SERVER CONNECTION");
            group.addView(statusServer);
            group.addView(buildDivider());

            statusStats = buildStatusItem("REPORT STATS");
            group.addView(statusStats);

            return group;
        }), matchWrap(dp(12)));

        // ── 服务器配置区 ──
        content.addView(buildSectionTitle("CONFIGURATION", "服务器配置"), matchWrap(dp(32)));
        content.addView(buildCardGroup(() -> {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setPadding(dp(16), dp(16), dp(16), dp(16));

            group.addView(buildFieldLabel("SERVER URL"));
            serverUrlInput = buildInput("https://...");
            group.addView(serverUrlInput, matchWrap(dp(6)));

            group.addView(buildFieldLabel("AUTH TOKEN"), matchWrap(dp(16)));
            tokenInput = buildInput("JWT Token");
            tokenInput.setSingleLine(false);
            tokenInput.setMaxLines(3);
            group.addView(tokenInput, matchWrap(dp(6)));

            return group;
        }), matchWrap(dp(12)));

        // 按钮行
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        btnRow.addView(buildButton("TEST CONNECTION", C_BTN, C_ACCENT, v -> testConnection()));
        View spacer = new View(this);
        btnRow.addView(spacer, new LinearLayout.LayoutParams(dp(12), 0));
        btnRow.addView(buildButton("SAVE", C_BTN, C_TEXT, v -> saveConfig()));

        content.addView(btnRow, matchWrap(dp(16)));

        // ── 操作区 ──
        content.addView(buildSectionTitle("ACTIONS", "操作"), matchWrap(dp(32)));
        content.addView(buildButton("RESTART SERVICE", C_BTN, C_TEXT, v -> restartService()), matchWrap(dp(12)));

        root.addView(content);
        scroll.addView(root);
        rootFrame.addView(scroll);
        setContentView(rootFrame);

        // 初始化
        loadConfig();
        refreshStatus();
        startSmsMonitorService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    // ── 顶部Header（模拟 .page-header-bar） ──────────────────────

    private View buildHeaderBar() {
        FrameLayout header = new FrameLayout(this);
        header.setBackgroundColor(Color.parseColor(C_HEADER));
        int headerH = dp(72);

        // 背景大字（模拟 .header-deco）
        TextView deco = new TextView(this);
        deco.setText("SMS REPORTER");
        deco.setTextColor(Color.parseColor("#0d0d0d"));
        deco.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        deco.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        deco.setLetterSpacing(0.25f);
        deco.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams decoLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        decoLp.gravity = Gravity.CENTER;
        header.addView(deco, decoLp);

        // 前景标题（模拟 .header-title）
        TextView title = new TextView(this);
        title.setText("SMS REPORTER");
        title.setTextColor(Color.parseColor("#ffffff"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(0.1f);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        header.addView(title, titleLp);

        header.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, headerH));
        return header;
    }

    // ── 三色装饰线（模拟 .deco-line） ───────────────────────────

    private View buildDecoLine() {
        View line = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                Paint p = new Paint();
                // 粉色段
                p.setColor(Color.parseColor(C_PINK));
                canvas.drawRect(0, 0, w / 3f, h, p);
                // 黄色段
                p.setColor(Color.parseColor(C_ACCENT));
                canvas.drawRect(w / 3f, 0, w * 2f / 3f, h, p);
                // 绿色段
                p.setColor(Color.parseColor(C_GREEN));
                canvas.drawRect(w * 2f / 3f, 0, w, h, p);
            }
        };
        line.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        return line;
    }

    // ── Section标题（模拟 .section-title） ───────────────────────

    private View buildSectionTitle(String enText, String cnText) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(8), 0, 0);

        // 英文小标签（灰色底白色斜切块）
        TextView en = new TextView(this);
        en.setText(enText);
        en.setTextColor(Color.parseColor(C_TEXT_SEC));
        en.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        en.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        en.setLetterSpacing(0.15f);
        section.addView(en);

        // 中文大标题
        TextView cn = new TextView(this);
        cn.setText(cnText);
        cn.setTextColor(Color.parseColor("#ffffff"));
        cn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        cn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        cn.setPadding(0, dp(2), 0, 0);
        section.addView(cn);

        return section;
    }

    // ── 卡片容器（模拟 .interact-card / .checkin-card 左侧黄条） ─

    private interface CardContentBuilder {
        View build();
    }

    private View buildCardGroup(CardContentBuilder builder) {
        FrameLayout card = new FrameLayout(this);

        // 卡片背景+边框
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor(C_CARD));
        cardBg.setStroke(1, Color.parseColor(C_BORDER));
        card.setBackground(cardBg);

        // 左侧黄色竖条（4dp宽，模拟 ::before）
        View accent = new View(this);
        accent.setBackgroundColor(Color.parseColor(C_ACCENT));
        FrameLayout.LayoutParams accentLp = new FrameLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT);
        accentLp.gravity = Gravity.START;
        card.addView(accent, accentLp);

        // 内容区（左侧留出4dp给黄条）
        View content = builder.build();
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.leftMargin = dp(4);
        card.addView(content, contentLp);

        return card;
    }

    // ── 状态行 ──────────────────────────────────────────────────

    private TextView buildStatusItem(String label) {
        TextView tv = new TextView(this);
        tv.setText(label + "  --");
        tv.setTextColor(Color.parseColor(C_TEXT_SEC));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        return tv;
    }

    private View buildDivider() {
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(C_BORDER));
        div.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return div;
    }

    // ── 按钮（模拟 .btn 带左侧黄色小竖条） ─────────────────────

    private View buildAccentButton(String text, View.OnClickListener listener) {
        return buildButton(text, C_ACCENT, C_BG, listener);
    }

    private View buildButton(String text, String bgColor, String textColor, View.OnClickListener listener) {
        FrameLayout wrapper = new FrameLayout(this);

        // 按钮背景
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor(bgColor));
        wrapper.setBackground(btnBg);

        // 左侧黄色小竖条（模拟 .btn::after）
        if (!bgColor.equals(C_ACCENT)) {
            View bar = new View(this);
            bar.setBackgroundColor(Color.parseColor(C_ACCENT));
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(dp(3), dp(18));
            barLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            barLp.leftMargin = dp(6);
            wrapper.addView(bar, barLp);
        }

        // 按钮文字
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(textColor));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setLetterSpacing(0.05f);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(20), dp(14), dp(20), dp(14));
        wrapper.addView(btn);

        wrapper.setClickable(true);
        wrapper.setOnClickListener(listener);
        return wrapper;
    }

    // ── 字段标签 ────────────────────────────────────────────────

    private TextView buildFieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(C_TEXT_DIM));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        tv.setLetterSpacing(0.15f);
        return tv;
    }

    // ── 输入框（模拟 .ip-input / .sms-textarea） ────────────────

    private EditText buildInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(Color.parseColor("#d9d9d9"));
        et.setHintTextColor(Color.parseColor(C_TEXT_DIM));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        et.setSingleLine(true);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));

        // 模拟border: 1px solid rgba(255,255,255,.08); focus时border-color: rgba(255,250,0,.3)
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor(C_BG));
        inputBg.setStroke(1, Color.parseColor("#1f1f1f"));
        et.setBackground(inputBg);

        et.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(C_BG));
            bg.setStroke(1, hasFocus ? Color.parseColor("#4d4d00") : Color.parseColor("#1f1f1f"));
            et.setBackground(bg);
        });

        return et;
    }

    // ── 权限 ──────────────────────────────────────────────────────

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            String[] perms;
            if (Build.VERSION.SDK_INT >= 33) {
                perms = new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.POST_NOTIFICATIONS
                };
            } else {
                perms = new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                };
            }
            requestPermissions(perms, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshStatus();
        }
    }

    // ── 状态刷新 ──────────────────────────────────────────────────

    private void refreshStatus() {
        // SMS权限
        boolean hasSms = true;
        if (Build.VERSION.SDK_INT >= 23) {
            hasSms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                  && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        }
        setStatusText(statusSms, "SMS RECEIVE / READ", hasSms ? "GRANTED" : "DENIED", hasSms);

        // 通知权限
        boolean hasNotif = true;
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        setStatusText(statusNotification, "NOTIFICATION", hasNotif ? "GRANTED" : "DENIED", hasNotif);

        // 前台服务
        setStatusText(statusService, "FOREGROUND SERVICE", "RUNNING", true);

        // 上报统计
        SharedPreferences log = getSharedPreferences("sms_log", MODE_PRIVATE);
        int total = log.getInt("total_count", 0);
        String lastTime = log.getString("last_time", "-");
        String lastStatus = log.getString("last_status", "-");
        statusStats.setText("REPORT STATS  " + total + " total  |  " + lastTime + "  " + lastStatus);
        statusStats.setTextColor(Color.parseColor(C_TEXT_SEC));

        // 服务器连接
        SharedPreferences config = getSharedPreferences("config", MODE_PRIVATE);
        String token = config.getString("auth_token", "");
        if (token.isEmpty()) {
            setStatusText(statusServer, "SERVER CONNECTION", "NO TOKEN", false);
        } else {
            setStatusText(statusServer, "SERVER CONNECTION", "TOKEN SET", true);
        }
    }

    private void setStatusText(TextView tv, String label, String status, boolean ok) {
        tv.setText(label + "  " + status);
        tv.setTextColor(ok ? Color.parseColor(C_GREEN) : Color.parseColor(C_RED));
    }

    // ── 配置 ──────────────────────────────────────────────────────

    private void loadConfig() {
        SharedPreferences config = getSharedPreferences("config", MODE_PRIVATE);
        serverUrlInput.setText(config.getString("server_url", DEFAULT_SERVER));
        tokenInput.setText(config.getString("auth_token", ""));
    }

    private void saveConfig() {
        String url = serverUrlInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (url.isEmpty()) url = DEFAULT_SERVER;

        SharedPreferences config = getSharedPreferences("config", MODE_PRIVATE);
        config.edit()
            .putString("server_url", url)
            .putString("auth_token", token)
            .apply();

        showToast("Configuration saved");
        refreshStatus();
    }

    // ── 测试连接 ──────────────────────────────────────────────────

    private void testConnection() {
        setStatusText(statusServer, "SERVER CONNECTION", "TESTING...", false);
        statusServer.setTextColor(Color.parseColor(C_ACCENT));

        SharedPreferences config = getSharedPreferences("config", MODE_PRIVATE);
        String serverUrl = config.getString("server_url", DEFAULT_SERVER);
        String token = config.getString("auth_token", "");

        executor.execute(() -> {
            try {
                URL url = new URL(serverUrl + "/api/user/profile");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (!token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                conn.disconnect();

                handler.post(() -> {
                    if (code == 200) {
                        setStatusText(statusServer, "SERVER CONNECTION", "OK (" + code + ")", true);
                    } else if (code == 401) {
                        setStatusText(statusServer, "SERVER CONNECTION", "INVALID TOKEN (401)", false);
                    } else {
                        setStatusText(statusServer, "SERVER CONNECTION", "ERROR (" + code + ")", false);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    setStatusText(statusServer, "SERVER CONNECTION", "FAILED", false);
                });
            }
        });
    }

    // ── 服务管理 ──────────────────────────────────────────────────

    private void startSmsMonitorService() {
        Intent intent = new Intent(this, SMSMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void restartService() {
        stopService(new Intent(this, SMSMonitorService.class));
        handler.postDelayed(() -> {
            startSmsMonitorService();
            showToast("Service restarted");
            refreshStatus();
        }, 500);
    }

    // ── Toast（模拟前端toast样式） ───────────────────────────────

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ── 工具 ────────────────────────────────────────────────────

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = topMargin;
        return p;
    }
}

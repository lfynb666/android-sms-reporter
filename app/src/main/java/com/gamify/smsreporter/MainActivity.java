package com.gamify.smsreporter;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMS Reporter - 原生单页面（不可滚动），视觉风格对齐gamify前端
 * 功能：权限申请（含电池优化+自启动） + 服务状态 + 登录
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
    private TextView statusBattery;
    private TextView statusAutoStart;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView loginStatusText;
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

        // 根布局（不使用ScrollView，真正的单页）
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(C_BG));

        // ── 顶部Header Bar ──
        root.addView(buildHeaderBar());

        // ── 三色装饰线 ──
        root.addView(buildDecoLine());

        // ── 主内容区 ──
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(16));

        // ── 权限状态区（每行带独立申请按钮） ──
        content.addView(buildSectionTitle("PERMISSION", "权限状态"));
        content.addView(buildCardGroup(() -> {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);

            group.addView(buildPermissionRow("SMS", statusSms = new TextView(this), v -> requestSmsPermission()));
            group.addView(buildDivider());
            group.addView(buildPermissionRow("NOTIFICATION", statusNotification = new TextView(this), v -> requestNotificationPermission()));
            group.addView(buildDivider());
            group.addView(buildPermissionRow("BATTERY", statusBattery = new TextView(this), v -> requestIgnoreBatteryOptimization()));
            group.addView(buildDivider());
            group.addView(buildPermissionRow("AUTO START", statusAutoStart = new TextView(this), v -> openAutoStartSettings()));

            return group;
        }), matchWrap(dp(12)));

        // ── 登录区 ──
        content.addView(buildSectionTitle("LOGIN", "账号登录"), matchWrap(dp(24)));

        // 登录状态
        loginStatusText = new TextView(this);
        loginStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        loginStatusText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        loginStatusText.setPadding(0, dp(4), 0, dp(8));
        content.addView(loginStatusText, matchWrap(dp(4)));

        content.addView(buildCardGroup(() -> {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setPadding(dp(16), dp(14), dp(16), dp(14));

            group.addView(buildFieldLabel("USERNAME"));
            usernameInput = buildInput("用户名");
            group.addView(usernameInput, matchWrap(dp(6)));

            group.addView(buildFieldLabel("PASSWORD"), matchWrap(dp(12)));
            passwordInput = buildInput("密码");
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            group.addView(passwordInput, matchWrap(dp(6)));

            return group;
        }), matchWrap(dp(6)));

        // 登录按钮
        content.addView(buildAccentButton("LOGIN", v -> login()), matchWrap(dp(16)));

        root.addView(content, matchWrap(0));

        setContentView(root);

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

    // ── 顶部Header ──────────────────────────────────────────────

    private View buildHeaderBar() {
        FrameLayout header = new FrameLayout(this);
        header.setBackgroundColor(Color.parseColor(C_HEADER));
        int headerH = dp(52);

        // 背景大字
        TextView deco = new TextView(this);
        deco.setText("SMS REPORTER");
        deco.setTextColor(Color.parseColor("#0d0d0d"));
        deco.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        deco.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        deco.setLetterSpacing(0.25f);
        deco.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams decoLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        decoLp.gravity = Gravity.CENTER;
        header.addView(deco, decoLp);

        // 前景标题
        TextView title = new TextView(this);
        title.setText("SMS REPORTER");
        title.setTextColor(Color.parseColor("#ffffff"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
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

    // ── 三色装饰线 ───────────────────────────────────────────────

    private View buildDecoLine() {
        View line = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                Paint p = new Paint();
                p.setColor(Color.parseColor(C_PINK));
                canvas.drawRect(0, 0, w / 3f, h, p);
                p.setColor(Color.parseColor(C_ACCENT));
                canvas.drawRect(w / 3f, 0, w * 2f / 3f, h, p);
                p.setColor(Color.parseColor(C_GREEN));
                canvas.drawRect(w * 2f / 3f, 0, w, h, p);
            }
        };
        line.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        return line;
    }

    // ── Section标题 ──────────────────────────────────────────────

    private View buildSectionTitle(String enText, String cnText) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(4), 0, 0);

        TextView en = new TextView(this);
        en.setText(enText);
        en.setTextColor(Color.parseColor(C_TEXT_SEC));
        en.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        en.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        en.setLetterSpacing(0.15f);
        section.addView(en);

        TextView cn = new TextView(this);
        cn.setText(cnText);
        cn.setTextColor(Color.parseColor("#ffffff"));
        cn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        cn.setPadding(0, dp(1), 0, 0);
        section.addView(cn);

        return section;
    }

    // ── 卡片容器 ─────────────────────────────────────────────────

    private interface CardContentBuilder {
        View build();
    }

    private View buildCardGroup(CardContentBuilder builder) {
        FrameLayout card = new FrameLayout(this);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor(C_CARD));
        cardBg.setStroke(1, Color.parseColor(C_BORDER));
        card.setBackground(cardBg);

        View accent = new View(this);
        accent.setBackgroundColor(Color.parseColor(C_ACCENT));
        FrameLayout.LayoutParams accentLp = new FrameLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT);
        accentLp.gravity = Gravity.START;
        card.addView(accent, accentLp);

        View contentView = builder.build();
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.leftMargin = dp(3);
        card.addView(contentView, contentLp);

        return card;
    }

    // ── 状态行 ───────────────────────────────────────────────────

    // 权限行：左边状态文字 + 右边小申请按钮
    private View buildPermissionRow(String label, TextView statusTv, View.OnClickListener onRequest) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(8), dp(10));
        row.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 状态文字（占满左侧剩余空间）
        statusTv.setText(label + "  --");
        statusTv.setTextColor(Color.parseColor(C_TEXT_SEC));
        statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusTv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        row.addView(statusTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧小按钮
        TextView btn = new TextView(this);
        btn.setText("REQUEST");
        btn.setTextColor(Color.parseColor(C_BG));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setLetterSpacing(0.05f);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor(C_ACCENT));
        btn.setBackground(btnBg);
        btn.setClickable(true);
        btn.setOnClickListener(onRequest);
        row.addView(btn);

        return row;
    }

    private View buildDivider() {
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(C_BORDER));
        div.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return div;
    }

    // ── 按钮 ─────────────────────────────────────────────────────

    private View buildAccentButton(String text, View.OnClickListener listener) {
        return buildButton(text, C_ACCENT, C_BG, listener);
    }

    private View buildButton(String text, String bgColor, String textColor, View.OnClickListener listener) {
        FrameLayout wrapper = new FrameLayout(this);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor(bgColor));
        wrapper.setBackground(btnBg);

        if (!bgColor.equals(C_ACCENT)) {
            View bar = new View(this);
            bar.setBackgroundColor(Color.parseColor(C_ACCENT));
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(dp(3), dp(14));
            barLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            barLp.leftMargin = dp(6);
            wrapper.addView(bar, barLp);
        }

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

    // ── 字段标签 ─────────────────────────────────────────────────

    private TextView buildFieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(C_TEXT_DIM));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        tv.setLetterSpacing(0.15f);
        return tv;
    }

    // ── 输入框 ───────────────────────────────────────────────────

    private EditText buildInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(Color.parseColor("#d9d9d9"));
        et.setHintTextColor(Color.parseColor(C_TEXT_DIM));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        et.setSingleLine(true);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));

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

    // ── 权限申请 ─────────────────────────────────────────────────

    private void requestSmsPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            }, PERMISSION_REQUEST_CODE);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                Manifest.permission.POST_NOTIFICATIONS
            }, PERMISSION_REQUEST_CODE);
        } else {
            showToast("Android 13以下无需单独申请通知权限");
        }
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    // 部分ROM不支持，降级到电池优化列表
                    try {
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    // 尝试打开厂商自启动/关联启动/后台活动设置页面
    private void openAutoStartSettings() {
        // 各厂商自启动管理页面的Intent（覆盖主流国产ROM）
        Intent[][] intents = {
            // OPPO / Realme / OnePlus (ColorOS)
            { new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")) },
            { new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")) },
            { new Intent().setComponent(new ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")) },
            // 小米 (MIUI)
            { new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")) },
            // 华为 (EMUI)
            { new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")) },
            { new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")) },
            // Vivo (OriginOS / FuntouchOS)
            { new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")) },
            { new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")) },
            // 三星
            { new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")) },
        };

        for (Intent[] group : intents) {
            for (Intent intent : group) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (getPackageManager().resolveActivity(intent, 0) != null) {
                        startActivity(intent);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
        // 所有厂商页面都无法打开时，Toast提示用户手动设置
        showToast("请在系统设置中手动开启自启动/关联启动/后台活动权限");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshStatus();
        }
    }

    // ── 状态刷新 ─────────────────────────────────────────────────

    private void refreshStatus() {
        // SMS权限
        boolean hasSms = true;
        if (Build.VERSION.SDK_INT >= 23) {
            hasSms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                  && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        }
        setStatusText(statusSms, "SMS", hasSms ? "GRANTED" : "DENIED", hasSms);

        // 通知权限
        boolean hasNotif = true;
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        setStatusText(statusNotification, "NOTIFICATION", hasNotif ? "GRANTED" : "DENIED", hasNotif);

        // 电池优化
        boolean batteryIgnored = false;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            batteryIgnored = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        setStatusText(statusBattery, "BATTERY", batteryIgnored ? "IGNORED" : "NOT IGNORED", batteryIgnored);

        // 自启动（无标准API可检测，只能提示用户确认）
        setStatusText(statusAutoStart, "AUTO START", "OPEN SETTINGS", false);
        statusAutoStart.setTextColor(Color.parseColor(C_TEXT_SEC));
    }

    private void setStatusText(TextView tv, String label, String status, boolean ok) {
        tv.setText(label + "  " + status);
        tv.setTextColor(ok ? Color.parseColor(C_GREEN) : Color.parseColor(C_RED));
    }

    // ── 配置 ─────────────────────────────────────────────────────

    private void loadConfig() {
        SharedPreferences config = getSharedPreferences("config", MODE_PRIVATE);
        String loggedUser = config.getString("logged_username", "");
        String token = config.getString("auth_token", "");
        if (!token.isEmpty() && !loggedUser.isEmpty()) {
            loginStatusText.setText("已登录: " + loggedUser);
            loginStatusText.setTextColor(Color.parseColor(C_GREEN));
        } else {
            loginStatusText.setText("未登录");
            loginStatusText.setTextColor(Color.parseColor(C_RED));
        }
    }

    private void login() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        if (username.isEmpty() || password.isEmpty()) {
            showToast("请填写用户名和密码");
            return;
        }

        loginStatusText.setText("登录中...");
        loginStatusText.setTextColor(Color.parseColor(C_ACCENT));

        SharedPreferences config = getSharedPreferences("config", MODE_PRIVATE);
        String serverUrl = config.getString("server_url", DEFAULT_SERVER);

        executor.execute(() -> {
            try {
                URL url = new URL(serverUrl + "/api/auth/login");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                String json = "{\"username\":" + escapeJson(username) + ",\"password\":" + escapeJson(password) + "}";
                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    byte[] buf = new byte[4096];
                    int len = is.read(buf);
                    is.close();
                    String resp = new String(buf, 0, len, java.nio.charset.StandardCharsets.UTF_8);
                    String token = extractJsonValue(resp, "token");
                    if (token != null && !token.isEmpty()) {
                        config.edit()
                            .putString("auth_token", token)
                            .putString("logged_username", username)
                            .apply();
                        handler.post(() -> {
                            loginStatusText.setText("已登录: " + username);
                            loginStatusText.setTextColor(Color.parseColor(C_GREEN));
                            passwordInput.setText("");
                            showToast("登录成功");
                            refreshStatus();
                        });
                    } else {
                        handler.post(() -> {
                            loginStatusText.setText("登录失败: 响应异常");
                            loginStatusText.setTextColor(Color.parseColor(C_RED));
                        });
                    }
                } else {
                    java.io.InputStream es = conn.getErrorStream();
                    String errMsg = "HTTP " + code;
                    if (es != null) {
                        byte[] buf = new byte[2048];
                        int len = es.read(buf);
                        if (len > 0) {
                            String errResp = new String(buf, 0, len, java.nio.charset.StandardCharsets.UTF_8);
                            String errDetail = extractJsonValue(errResp, "error");
                            if (errDetail != null) errMsg = errDetail;
                        }
                        es.close();
                    }
                    final String msg = errMsg;
                    handler.post(() -> {
                        loginStatusText.setText("登录失败: " + msg);
                        loginStatusText.setTextColor(Color.parseColor(C_RED));
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                handler.post(() -> {
                    loginStatusText.setText("登录失败: " + e.getMessage());
                    loginStatusText.setTextColor(Color.parseColor(C_RED));
                });
            }
        });
    }

    // 简单JSON值提取（避免引入外部JSON库）
    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return null;
        int start = json.indexOf('"', colonIdx + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        while (end > 0 && json.charAt(end - 1) == '\\') {
            end = json.indexOf('"', end + 1);
        }
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    // ── 服务管理 ─────────────────────────────────────────────────

    private void startSmsMonitorService() {
        Intent intent = new Intent(this, SMSMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ── Toast ────────────────────────────────────────────────────

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ── 工具 ─────────────────────────────────────────────────────

    private String escapeJson(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

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

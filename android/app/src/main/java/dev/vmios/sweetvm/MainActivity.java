package dev.vmios.sweetvm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int REQ_ROOT = 100;
    private static final int REQ_PAYLOAD = 101;

    private static final int BG = 0xFF08080C;
    private static final int SURFACE = 0xFF14141B;
    private static final int SURFACE_2 = 0xFF1D1D26;
    private static final int TEXT = 0xFFF5F5F7;
    private static final int DIM = 0xFF9898A6;
    private static final int ACCENT = 0xFF0A84FF;
    private static final int GOOD = 0xFF30D158;
    private static final int BAD = 0xFFFF453A;
    private static final int WARN = 0xFFFF9F0A;
    private static final int STROKE = 0xFF262631;

    private BootConfig cfg;
    private VmEngine engine;
    private PowerManager.WakeLock wake;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout rootView;
    private FrameLayout vmScreen;
    private FramebufferView fbView;
    private TouchSender touch;
    private TextView vmStatus;
    private TextView vmTimer;
    private TextView vmCpu;
    private TextView vmHint;
    private LinearLayout vmTop;
    private TextView vmPill;
    private boolean vmActive;
    private boolean vmStopping;
    private String lastCmdline = "";
    private long vmStartedAt;
    private long lastFrames;
    private long lastFrameTime;
    private boolean controlsVisible = true;
    private int displayMode = 1; // 0=1:1, 1=shrink, 2=fullscreen
    private boolean immersiveUi;
    private TextView hostClock;
    private TextView hostCpu;
    private TextView hostRam;
    private TextView startButton;
    private TextView modeOne;
    private TextView modeShrink;
    private TextView modeFull;
    private TextView immersiveButton;
    private FrameLayout displayArea;
    private long previousHostTotal = -1;
    private long previousHostIdle = -1;

    private final Runnable hideControls = () -> {
        if (!vmActive || vmTop == null) {
            return;
        }
        controlsVisible = false;
        vmTop.animate().alpha(0f).setDuration(220).start();
        if (vmPill != null) {
            vmPill.animate().alpha(0f).setDuration(220).start();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!vmActive) {
                return;
            }
            long now = System.currentTimeMillis();
            long s = Math.max(0, (now - vmStartedAt) / 1000);
            long f = engine.frames();
            long df = f - lastFrames;
            long dt = Math.max(1, now - lastFrameTime);
            long fps = df * 1000 / dt;
            lastFrames = f;
            lastFrameTime = now;
            if (s % 5 == 0) {
                long copies = engine.frames();
                long avgUs = copies > 0 ? engine.copyNanos() / copies / 1000 : 0;
                android.util.Log.i("sweetvm", "fps=" + fps + " ticks=" + engine.ticks()
                        + " copies=" + copies + " copy_avg_us=" + avgUs
                        + " copy_max_us=" + engine.copyMaxNanos() / 1000 + " uptime=" + s);
            }
            if (vmStatus != null) {
                vmStatus.setText(engine.isRunning() ? "运行中 · 软件 CPU" : "正在停止…");
            }
            if (vmHint != null && fbView != null && fbView.hasFrame()) {
                vmHint.setVisibility(View.GONE);
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        cfg = BootConfig.load(this);
        if (getIntent().getBooleanExtra("gpu_wire", false)) {
            // Test hook: VmEngine starts this too when the payload cmdline has
            // gpu_wire=1; the extra lets the host server be exercised alone.
            GpuWireServer.start(5141);
        }
        engine = new VmEngine(this);
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(BG);
        setContentView(rootView);
        adoptExistingFiles();
        showHome();
        beginBundledPayloadInstall();
    }

    private void beginBundledPayloadInstall() {
        final File bundled = BootAssets.payloadFile(this);
        new Thread(() -> {
            try {
                BootAssets.ensure(getApplicationContext(), getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionCode);
                runOnUiThread(() -> {
                    cfg.payloadPath = bundled.getAbsolutePath();
                    cfg.save(this);
                    showHome();
                    toast("内置 Host Raster payload 已就绪");
                });
            } catch (Exception e) {
                android.util.Log.e("sweetvm", "bundled payload extraction failed", e);
                runOnUiThread(() -> toast("内置 payload 释放失败：" + e.getMessage()));
            }
        }, "sweetvm-payload-assets").start();
    }

    private void adoptExistingFiles() {
        File root = BootConfig.defaultRoot(this);
        File payload = BootConfig.defaultPayload(this);
        if (!cfg.rootPath.isEmpty()) {
            File f = new File(cfg.rootPath);
            if (!f.isFile()) {
                cfg.rootPath = "";
            }
        }
        if (!cfg.payloadPath.isEmpty()) {
            File f = new File(cfg.payloadPath);
            if (!f.isFile()) {
                cfg.payloadPath = "";
            }
        }
        if (cfg.rootPath.isEmpty() && root.isFile()) {
            cfg.rootPath = root.getAbsolutePath();
        }
        if (cfg.payloadPath.isEmpty() && payload.isFile()) {
            cfg.payloadPath = payload.getAbsolutePath();
        }
        cfg.save(this);
    }

    private int insetTop() {
        if (Build.VERSION.SDK_INT >= 23) {
            android.view.WindowInsets i = getWindow().getDecorView().getRootWindowInsets();
            if (i != null) {
                return i.getSystemWindowInsetTop();
            }
        }
        return 0;
    }

    private void setVmSystemBars(boolean vm) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (vm) {
            // Hide only the Android status bar. Keep system navigation available.
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (vm) {
                    c.hide(android.view.WindowInsets.Type.statusBars());
                    c.show(android.view.WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(android.view.WindowInsetsController
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    c.show(android.view.WindowInsets.Type.statusBars()
                            | android.view.WindowInsets.Type.navigationBars());
                }
            }
        }
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int h = id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
        return Math.max(h, insetTop());
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int h = id > 0 ? getResources().getDimensionPixelSize(id) : dp(16);
        return Math.max(h, insetBottom());
    }

    private int insetBottom() {
        if (Build.VERSION.SDK_INT >= 23) {
            android.view.WindowInsets i = getWindow().getDecorView().getRootWindowInsets();
            if (i != null) {
                return i.getSystemWindowInsetBottom();
            }
        }
        return 0;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int sp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setLineSpacing(0, 1.18f);
        return t;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable outline(int color, int strokeColor, float radiusDp, int strokeDp) {
        GradientDrawable d = round(color, radiusDp);
        d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(outline(SURFACE, STROKE, 20, 1));
        return c;
    }

    private TextView action(String label, boolean primary, View.OnClickListener l) {
        TextView b = text(label, 15, primary ? Color.WHITE : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(15), dp(16), dp(15));
        b.setBackground(primary ? round(ACCENT, 15) : outline(SURFACE_2, STROKE, 15, 1));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    private Space gap(int dp) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return s;
    }

    private void clearRoot() {
        rootView.removeAllViews();
        vmScreen = null;
        fbView = null;
        vmStatus = null;
        vmTimer = null;
        vmCpu = null;
        vmHint = null;
        vmTop = null;
        vmPill = null;
    }

    private void showHome() {
        vmActive = false;
        ui.removeCallbacks(ticker);
        ui.removeCallbacks(hideControls);
        ui.removeCallbacks(hostStatsTicker);
        if (touch != null) {
            touch.close();
            touch = null;
        }
        releaseWake();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersiveUi = false;
        displayMode = 1;
        showMainScreen(false);
    }

    private int U() { return dp(36); }

    private void showMainScreen(boolean running) {
        setVmSystemBars(false);
        clearRoot();
        rootView.setBackgroundColor(Color.BLACK);
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        rootView.addView(screen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        top.setPadding(dp(8), 0, dp(8), 0);
        top.setBackgroundColor(0xFF111111);
        hostClock = text("⏱ 00:00:00", 12, TEXT, true);
        hostCpu = text("CPU --%", 12, TEXT, true);
        hostRam = text("RAM --/-- GB", 12, TEXT, true);
        top.addView(hostClock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addMetricGap(top);
        top.addView(hostCpu, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addMetricGap(top);
        top.addView(hostRam, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top.setOnClickListener(v -> showAdvanced());
        top.setOnLongClickListener(v -> { showLogs(); return true; });
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, U(), Gravity.TOP);
        topLp.leftMargin = dp(4); topLp.rightMargin = dp(4);
        screen.addView(top, topLp);
        vmTimer = hostClock;
        vmCpu = hostCpu;

        displayArea = new FrameLayout(this);
        displayArea.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams areaLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(displayArea, areaLp);

        fbView = new FramebufferView(this);
        fbView.setTouchListener((type, x, y) -> {
            TouchSender sender = touch;
            if (sender != null) sender.send(type, x, y);
        });
        displayArea.addView(fbView);
        if (running) engine.setView(fbView);
        updateDisplayGeometry();
        displayArea.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> updateDisplayGeometry());

        LinearLayout volume = new LinearLayout(this);
        volume.setOrientation(LinearLayout.VERTICAL);
        volume.setGravity(Gravity.CENTER);
        TextView volUp = placeholderControl("+", "音量增加（预留）", 18);
        TextView volDown = placeholderControl("−", "音量减少（预留）", 18);
        LinearLayout.LayoutParams sideHalf = new LinearLayout.LayoutParams(U(), (int)(U()*1.5f));
        volume.addView(volUp, sideHalf);
        volume.addView(volDown, new LinearLayout.LayoutParams(U(), (int)(U()*1.5f)));
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(U(), U()*3,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        vlp.leftMargin = dp(4);
        screen.addView(volume, vlp);

        TextView power = placeholderControl("⏻", "电源按钮（预留）", 20);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(U(), U()*3,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        plp.rightMargin = dp(4);
        screen.addView(power, plp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(0, 0, 0, 0);
        bottom.setBackgroundColor(0xFF111111);
        startButton = bottomButton("▶", "启动", true, () -> {
            if (!vmActive && cfg.rootFile() != null) startIos();
        });
        bottom.addView(startButton, new LinearLayout.LayoutParams(U(), U()));
        modeOne = bottomButton("1:1", "原始尺寸", !running, () -> setDisplayMode(0));
        bottom.addView(modeOne, new LinearLayout.LayoutParams(U()*3, U()));
        modeShrink = bottomButton("缩小", "缩小到 80%", !running, () -> setDisplayMode(1));
        bottom.addView(modeShrink, new LinearLayout.LayoutParams(U()*3, U()));
        modeFull = bottomButton("全屏", "等比铺满", !running, () -> setDisplayMode(2));
        bottom.addView(modeFull, new LinearLayout.LayoutParams(U()*3, U()));
        immersiveButton = bottomButton("◉", "沉浸", true, () -> toggleImmersiveUi(true));
        bottom.addView(immersiveButton, new LinearLayout.LayoutParams(U(), U()));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                U()*11, U(), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomLp.bottomMargin = dp(4);
        screen.addView(bottom, bottomLp);

        View.OnClickListener chooseRoot = v -> {
            if (!vmActive && (cfg.rootFile() == null || !cfg.rootFile().isFile())) pick(REQ_ROOT);
        };
        if (cfg.rootFile() == null || !cfg.rootFile().isFile()) {
            TextView overlay = text("点击选择系统镜像", 18, Color.WHITE, true);
            overlay.setGravity(Gravity.CENTER);
            overlay.setBackgroundColor(0x99000000);
            screen.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.setOnClickListener(chooseRoot);
            screen.setOnClickListener(chooseRoot);
        }
        refreshMainControls(running);
        if (immersiveUi) applyImmersiveVisibility();
        ui.removeCallbacks(hostStatsTicker);
        ui.post(hostStatsTicker);
    }

    private void addMetricGap(LinearLayout row) {
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
    }

    private TextView bottomButton(String glyph, String label, boolean enabled, Runnable action) {
        TextView v = text(glyph, 13, enabled ? TEXT : 0xFF666666, true);
        v.setGravity(Gravity.CENTER);
        v.setContentDescription(label);
        v.setBackground(outline(SURFACE_2, STROKE, 8, 1));
        v.setEnabled(enabled);
        v.setOnClickListener(view -> { if (v.isEnabled()) action.run(); });
        return v;
    }

    private void refreshMainControls(boolean running) {
        if (startButton != null) {
            boolean ready = cfg.rootFile() != null && cfg.rootFile().isFile()
                    && BootAssets.payloadFile(this).isFile();
            startButton.setEnabled(!running && ready);
            startButton.setAlpha(startButton.isEnabled() ? 1f : 0.35f);
        }
        if (modeOne != null) {
            boolean canOne = rootView != null && rootView.getWidth() > 1125
                    && rootView.getHeight() > 2436;
            modeOne.setEnabled(canOne);
            modeOne.setAlpha(modeOne.isEnabled() ? 1f : 0.35f);
        }
        if (modeShrink != null) { modeShrink.setEnabled(true); modeShrink.setAlpha(1f); }
        if (modeFull != null) { modeFull.setEnabled(true); modeFull.setAlpha(1f); }
        if (immersiveButton != null) immersiveButton.setEnabled(true);
    }

    private void setDisplayMode(int mode) {
        if (mode == 0 && (displayArea == null || displayArea.getWidth() <= 1125
                || displayArea.getHeight() <= 2436)) return;
        displayMode = mode;
        updateDisplayGeometry();
    }

    private void updateDisplayGeometry() {
        if (displayArea == null || fbView == null) return;
        int w = rootView != null ? rootView.getWidth() : displayArea.getWidth();
        int h = rootView != null ? rootView.getHeight() : displayArea.getHeight();
        if (w <= 0 || h <= 0) return;
        float scale;
        if (displayMode == 0) scale = 1f;
        else if (displayMode == 1) scale = (w * .80f) / 1125f;
        else scale = Math.min(w / 1125f, h / 2436f);
        int fw = Math.max(1, Math.round(1125f * scale));
        int fh = Math.max(1, Math.round(2436f * scale));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(fw, fh, Gravity.CENTER);
        fbView.setLayoutParams(lp);
        if (modeOne != null) {
            boolean allowed = w > 1125 && h > 2436;
            modeOne.setEnabled(allowed);
            modeOne.setAlpha(modeOne.isEnabled() ? 1f : .35f);
        }
    }

    private void toggleImmersiveUi(boolean enabled) {
        immersiveUi = enabled;
        applyImmersiveVisibility();
        if (enabled) toast("点击相同位置即可呼出按钮");
    }

    private void applyImmersiveVisibility() {
        if (rootView == null || rootView.getChildCount() == 0) return;
        View screen = rootView.getChildAt(0);
        if (!(screen instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) screen;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setVisibility(immersiveUi ? View.GONE : View.VISIBLE);
        }
        if (immersiveUi) {
            screen.setOnClickListener(v -> toggleImmersiveUi(false));
            if (displayArea != null) displayArea.setOnClickListener(v -> toggleImmersiveUi(false));
            if (fbView != null) fbView.setOnClickListener(v -> toggleImmersiveUi(false));
        } else {
            screen.setOnClickListener(null);
            if (displayArea != null) displayArea.setOnClickListener(null);
            if (fbView != null) fbView.setOnClickListener(null);
        }
    }

    private int readHostCpuPercent() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/stat"))) {
            String line = r.readLine();
            if (line == null || !line.startsWith("cpu ")) return -1;
            String[] p = line.trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);
            long idle = Long.parseLong(p[4]) + (p.length > 5 ? Long.parseLong(p[5]) : 0);
            int percent = -1;
            if (previousHostTotal >= 0 && total > previousHostTotal) {
                long dt = total - previousHostTotal, di = idle - previousHostIdle;
                percent = (int)Math.max(0, Math.min(100, (dt-di)*100/dt));
            }
            previousHostTotal = total; previousHostIdle = idle;
            return percent;
        } catch (Exception e) { return -1; }
    }

    private String readHostRam() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"))) {
            long total = -1, available = -1; String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) total = Long.parseLong(line.replaceAll("[^0-9]", ""));
                else if (line.startsWith("MemAvailable:")) available = Long.parseLong(line.replaceAll("[^0-9]", ""));
            }
            if (total > 0 && available >= 0) {
                long used = (total-available)/1024;
                return String.format(Locale.US, "RAM %.0f/%.0f GB", used/1024.0/1024.0, total/1024.0/1024.0);
            }
        } catch (Exception ignored) { }
        return "RAM --/-- GB";
    }

    private final Runnable hostStatsTicker = new Runnable() {
        @Override public void run() {
            if (hostClock != null) {
                long seconds = vmActive ? Math.max(0,(System.currentTimeMillis()-vmStartedAt)/1000) : 0;
                hostClock.setText(String.format(Locale.US,"⏱ %02d:%02d:%02d",
                        seconds/3600,(seconds/60)%60,seconds%60));
            }
            int cpu = readHostCpuPercent();
            if (hostCpu != null) hostCpu.setText(cpu < 0 ? "CPU --%" : "CPU " + cpu + "%");
            if (hostRam != null) hostRam.setText(readHostRam());
            ui.postDelayed(this,1000);
        }
    };

    private LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View sectionLabel(String s) {
        TextView t = text(s, 12, DIM, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private View infoRow(String label, String value, boolean ok) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView a = text(label, 11, DIM, false);
        l.addView(a);
        TextView b = text(value, 14, TEXT, false);
        b.setPadding(0, dp(3), 0, 0);
        b.setTextIsSelectable(true);
        l.addView(b);
        if (!ok && !"未选择".equals(value)) {
            TextView w = text("格式未验证或文件缺失", 10, WARN, false);
            w.setPadding(0, dp(3), 0, 0);
            l.addView(w);
        }
        return l;
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            toast("无法打开文件选择器: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQ_ROOT) {
            importFile(data.getData(), true);
        } else if (requestCode == REQ_PAYLOAD) {
            importFile(data.getData(), false);
        }
    }

    private void importFile(Uri uri, boolean root) {
        File target = root ? BootConfig.defaultRoot(this) : BootConfig.defaultPayload(this);
        long size = querySize(uri);
        if (size > 0 && target.getParentFile() != null
                && target.getParentFile().getUsableSpace() < size + (256L << 20)) {
            dialog("空间不足", "目标目录可用空间不足。\n需要 " + BootConfig.human(size)
                    + "，可用 " + BootConfig.human(target.getParentFile().getUsableSpace()) + "。");
            return;
        }
        if (target.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("覆盖现有文件？")
                    .setMessage(target.getName() + " 已存在，导入会替换它。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("覆盖", (d, w) -> doImport(uri, target, size, root))
                    .show();
        } else {
            doImport(uri, target, size, root);
        }
    }

    private void doImport(Uri uri, File target, long size, boolean isRoot) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        TextView msg = text(size > 0 ? "0 / " + BootConfig.human(size) : "准备中…", 12, DIM, false);
        msg.setPadding(0, dp(10), 0, 0);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), dp(6));
        box.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        box.addView(msg);
        AtomicBoolean cancel = new AtomicBoolean(false);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("导入 " + target.getName())
                .setView(box)
                .setNegativeButton("取消", (d, w) -> cancel.set(true))
                .setCancelable(false)
                .create();
        dlg.show();
        new Thread(() -> {
            File tmp = new File(target.getAbsolutePath() + ".part");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                if (in == null) {
                    throw new IOException("无法读取文件");
                }
                byte[] buf = new byte[1 << 20];
                long done = 0;
                int last = -1;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel.get()) {
                        throw new IOException("已取消");
                    }
                    out.write(buf, 0, n);
                    done += n;
                    if (size > 0) {
                        int p = (int) Math.min(1000, done * 1000 / size);
                        if (p != last) {
                            last = p;
                            long fd = done;
                            ui.post(() -> {
                                bar.setProgress(p);
                                msg.setText(BootConfig.human(fd) + " / " + BootConfig.human(size));
                            });
                        }
                    }
                }
                out.getFD().sync();
                if (target.exists() && !target.delete()) {
                    throw new IOException("无法替换旧文件");
                }
                if (!tmp.renameTo(target)) {
                    throw new IOException("无法保存文件");
                }
                if (cancel.get()) {
                    throw new IOException("已取消");
                }
                ui.post(() -> {
                    dlg.dismiss();
                    if (isRoot) {
                        cfg.rootPath = target.getAbsolutePath();
                        if (!BootConfig.looksLikeHfs(target)) {
                            toast("已导入，但没有识别到 HFS+ 卷头，请确认这是 raw 根镜像");
                        }
                    } else {
                        cfg.payloadPath = target.getAbsolutePath();
                        if (!BootConfig.looksLikePayload(target)) {
                            toast("已导入，但不是 SWEETXNU payload");
                        }
                    }
                    cfg.save(this);
                    showHome();
                });
            } catch (Exception e) {
                try {
                    tmp.delete();
                } catch (Exception ignored) {
                }
                ui.post(() -> {
                    dlg.dismiss();
                    dialog("导入失败", e.getMessage() == null ? "未知错误" : e.getMessage());
                });
            }
        }, "sweetvm-import").start();
    }

    private long querySize(Uri uri) {
        ContentResolver cr = getContentResolver();
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) {
                    return c.getLong(i);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private boolean canResume() {
        File root = cfg.rootFile();
        File payload = cfg.payloadFile();
        if (root == null || !root.isFile() || payload == null || !payload.isFile()) {
            return false;
        }
        String engineVersion;
        try {
            engineVersion = EngineAssets.currentVersion(this);
        } catch (IOException e) {
            return false;
        }
        return Snapshot.isUsable(this, payload, root, iosCmdline(payload), engineVersion);
    }

    private void startIos() {
        startIos(canResume());
    }

    private void startIos(boolean resume) {
        File bundledPayload = BootAssets.payloadFile(this);
        if (!bundledPayload.isFile() || bundledPayload.length() == 0) {
            toast("内置 Host Raster payload 正在释放或不可用，请稍后重试");
            return;
        }
        if (!bundledPayload.getAbsolutePath().equals(cfg.payloadPath)) {
            cfg.payloadPath = bundledPayload.getAbsolutePath();
            cfg.save(this);
        }
        File root = cfg.rootFile();
        File payload = cfg.payloadFile();
        if (root == null || !root.isFile()) {
            dialog("需要 iOS 根镜像",
                    "请先导入由 tools/mkiosroot.sh 生成的 HFS+ raw 根镜像。\n\n"
                            + "App 不附带 iOS 系统文件；这是你自备的 IPSW 生成的镜像。");
            return;
        }
        if (payload == null || !payload.isFile()) {
            dialog("需要内核 payload", "请先导入用 tools/mkpayload.py 生成的 SWEETXNU payload。");
            return;
        }
        if (!BootConfig.looksLikeHfs(root)) {
            dialog("根镜像格式未确认",
                    "在 offset 1024 没有找到 HFS+ 卷头（H+ / HX）。继续启动可能失败。",
                    "仍然启动", () -> launch(payload, root, iosCmdline(payload), resume));
            return;
        }
        if (!BootConfig.looksLikePayload(payload)) {
            dialog("payload 格式错误", "文件不是 SWEETXNU payload，请重新生成。");
            return;
        }
        launch(payload, root, iosCmdline(payload), resume);
    }

    private String iosCmdline(File payload) {
        String cmd = cfg.cmdline.trim();
        if (cmd.isEmpty()) {
            try {
                cmd = Payload.load(payload).cmdline().trim();
            } catch (Exception e) {
                cmd = "";
            }
        }
        if (!cmd.contains("rd=")) {
            cmd = (cmd + " rd=ufsroot0 ufsrw=1 rootrw=1").trim();
        }
        cmd = ensureQemuArgs(cmd);
        cmd = enableTouch(cmd);
        // Keep QEMU -smp and XNU-visible CPU topology at three.
        cmd = setBootArg(cmd, "cpus", "3");
        if (cfg.mttcg) {
            if (!cmd.contains("tcgmulti=1")) {
                cmd = (cmd + " tcgmulti=1").trim();
            }
        }
        // Keep the ticket-lock timeout explicit in the final boot-args string.
        // VmEngine used to append it later, where a 607-byte truncation or a
        // pre-existing tlto_us value could silently leave XNU at 125 ms.
        if (cfg.offload && !cmd.contains("sweetfb_offload=")) {
            cmd = (cmd + " sweetfb_offload=1").trim();
        } else if (!cfg.offload) {
            cmd = setBootArg(cmd, "sweetfb_offload", "0");
        }
        if (cfg.mttcg || cmd.contains("tcgmulti=1")) {
            cmd = setBootArg(cmd, "tlto_us", "2000000");
            cmd = setBootArg(cmd, "slto_us", "2000000");
        }
        return truncateCmdline(cmd);
    }

    private String enableTouch(String cmd) {
        cmd = cmd.replace("sweettouch=0", "sweettouch=1");
        if (!cmd.contains("sweettouch=")) {
            cmd = (cmd + " sweettouch=1").trim();
        }
        if (!cmd.contains("sweettouchdelay=")) {
            cmd = (cmd + " sweettouchdelay=3").trim();
        }
        if (!cmd.contains("sweetfb_noconsole=")) {
            cmd = (cmd + " sweetfb_noconsole=1").trim();
        }
        return cmd;
    }

    private String demoCmdline(File payload) {
        String cmd = cfg.cmdline.trim();
        if (cmd.isEmpty() || cmd.contains("rd=ufsroot0")) {
            try {
                cmd = Payload.load(payload).cmdline().trim();
            } catch (Exception e) {
                cmd = "";
            }
        }
        if (!cmd.contains("rd=")) {
            cmd = (cmd + " rd=md0").trim();
        }
        return truncateCmdline(ensureQemuArgs(cmd));
    }

    private String ensureQemuArgs(String cmd) {
        StringBuilder b = new StringBuilder(cmd);
        String[] need = {"sweetkeys=0", "sweetusb=0",
                "wdt=-1", "wdt_test=no_user_checkin",
                "sweetiolog=0", "sweetsample=0", "sweetnull=0"};
        for (String s : need) {
            String key = s.split("=")[0];
            if (!b.toString().contains(key + "=")) {
                b.append(' ').append(s);
            }
        }
        return b.toString().trim();
    }

    private String setBootArg(String cmd, String key, String value) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern
                .compile("(^|\\s)" + key + "=\\S+");
        java.util.regex.Matcher matcher = pattern.matcher(cmd);
        if (matcher.find()) {
            return matcher.replaceAll("$1" + key + "=" + value);
        }
        return (cmd + " " + key + "=" + value).trim();
    }

    private String truncateCmdline(String cmd) {
        final int limit = 607;
        boolean multi = cfg.mttcg || cmd.contains("tcgmulti=1");
        if (!multi) {
            if (cmd.length() <= limit) return cmd;
            int cut = cmd.lastIndexOf(' ', limit);
            return cmd.substring(0, cut < 0 ? limit : cut);
        }

        // Preserve the lock-timeout arguments at the end of the fixed-size
        // boot_args field; truncate optional earlier arguments first.
        String suffix = " tlto_us=2000000 slto_us=2000000";
        cmd = cmd.replaceAll("(^|\\s)tlto_us=\\S+", "")
                 .replaceAll("(^|\\s)slto_us=\\S+", "").trim();
        int prefixLimit = limit - suffix.length();
        if (cmd.length() > prefixLimit) {
            int cut = cmd.lastIndexOf(' ', prefixLimit);
            cmd = cmd.substring(0, cut < 0 ? prefixLimit : cut);
        }
        return cmd + suffix;
    }

    private void startDemo() {
        File payload;
        try {
            payload = BootConfig.demoPayload(this);
        } catch (IOException e) {
            dialog("演示 payload 缺失", "APK 中没有 assets/demo/payload.bin。\n" + e.getMessage());
            return;
        }
        if (!BootConfig.looksLikePayload(payload)) {
            dialog("演示 payload 损坏", "assets/demo/payload.bin 不是 SWEETXNU payload。");
            return;
        }
        launch(payload, null, demoCmdline(payload), false);
    }

    private void launch(File payload, File root, String cmd, boolean resume) {
        try {
            Payload p = Payload.load(payload);
            if (p.physBase != 0xb0000000L) {
                dialog("payload 不适用于 App",
                        "App 的 QEMU 使用 0xb0000000 低内存布局；请在构建时使用 "
                                + "mkadt.py --qemu（不要 --highmem）。");
                return;
            }
        } catch (Exception e) {
            dialog("无法读取 payload", e.getMessage() == null ? "未知错误" : e.getMessage());
            return;
        }
        Payload.configure(cmd);
        showVmScreen();
        lastCmdline = cmd;
        touch = new TouchSender(new File(getFilesDir(), "vm/touch.bin"));
        touch.start();
        acquireWake();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        vmActive = true;
        vmStopping = false;
        vmStartedAt = System.currentTimeMillis();
        lastFrames = 0;
        lastFrameTime = vmStartedAt;
        ui.removeCallbacks(ticker);
        ui.post(ticker);
        new Thread(() -> {
            try {
                engine.start(payload, root, cmd, cfg.fps, resume, new VmEngine.Listener() {
                    @Override
                    public void onExit(int code, String logTail) {
                        ui.post(() -> {
                            if (!vmActive || vmStopping) {
                                return;
                            }
                            vmActive = false;
                            ui.removeCallbacks(ticker);
                            releaseWake();
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            showExit(code, logTail);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        ui.post(() -> toast(message));
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    vmActive = false;
                    ui.removeCallbacks(ticker);
                    releaseWake();
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    showExit(-1, e.getMessage() == null ? "无法启动 QEMU" : e.getMessage());
                });
            }
        }, "sweetvm-start").start();
    }

    private void showVmScreen() {
        immersiveUi = false;
        showMainScreen(true);
    }

    private TextView placeholderControl(String glyph, String description, int sizeSp) {
        TextView control = text(glyph, sizeSp, TEXT, true);
        control.setGravity(Gravity.CENTER);
        control.setBackground(outline(SURFACE_2, STROKE, 8, 1));
        control.setContentDescription(description);
        control.setClickable(true);
        control.setFocusable(false);
        control.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.55f);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
            }
            return true;
        });
        return control;
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        if (vmTop != null) {
            vmTop.animate().alpha(controlsVisible ? 1f : 0f).setDuration(180).start();
        }
        if (vmPill != null) {
            vmPill.animate().alpha(controlsVisible ? 1f : 0f).setDuration(180).start();
        }
        ui.removeCallbacks(hideControls);
        if (controlsVisible) {
            ui.postDelayed(hideControls, 4500);
        }
    }

    private void confirmStop() {
        if (!vmActive) {
            showHome();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("停止虚拟机？")
                .setMessage("保存快照会把当前 RAM 和设备状态写入磁盘，下次启动只需几秒；"
                        + "直接停止会丢失未保存的 iOS 状态（HFS+ 已写入的部分保留）。")
                .setNegativeButton("继续运行", null)
                .setNeutralButton("直接停止", (d, w) -> stopVm(true))
                .setPositiveButton("保存快照并退出", (d, w) -> saveAndStop())
                .show();
    }

    private void saveAndStop() {
        if (!vmActive) {
            showHome();
            return;
        }
        final File payload = cfg.payloadFile();
        final File root = cfg.rootFile();
        final String cmd = lastCmdline;
        final String engineVersion;
        try {
            engineVersion = EngineAssets.currentVersion(this);
        } catch (IOException e) {
            toast("保存快照失败: " + e.getMessage());
            return;
        }
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("正在保存快照")
                .setMessage("写入客户机内存与设备状态，请稍候…")
                .setCancelable(false)
                .show();
        vmStopping = true;
        vmActive = false;
        ui.removeCallbacks(ticker);
        new Thread(() -> {
            String err = null;
            try {
                Snapshot.save(engine.qmpSocket(), Snapshot.stateFile(this));
                Snapshot.writeMeta(this, payload, root, cmd, engineVersion);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            engine.stop();
            final String fe = err;
            ui.post(() -> {
                progress.dismiss();
                releaseWake();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                showHome();
                toast(fe == null ? "快照已保存，下次可秒开" : "保存快照失败: " + fe);
            });
        }, "sweetvm-snapshot").start();
    }

    private void stopVm(boolean home) {
        vmStopping = true;
        vmActive = false;
        ui.removeCallbacks(ticker);
        engine.stop();
        releaseWake();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (home) {
            showHome();
        }
    }

    private void showExit(int code, String log) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        String tail = log == null ? "" : log;
        if (tail.length() > 2400) {
            tail = tail.substring(tail.length() - 2400);
        }
        String msg = "退出码：" + code + "\n\n" + tail;
        if (cfg.mttcg && (tail.contains("panic(") || tail.contains("Ticket spinlock"))) {
            msg = "检测到内核 panic：多线程 TCG 可能不稳定。\n"
                    + "请在高级选项关闭「多线程 TCG」后重试，并复制日志。\n\n" + msg;
        }
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(code == 0 ? "虚拟机已退出" : "虚拟机异常退出")
                .setMessage(msg)
                .setNegativeButton("复制日志", (dd, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("vmios log", log == null ? "" : log));
                    toast("日志已复制");
                })
                .setPositiveButton("返回", (dd, w) -> showHome())
                .setCancelable(false)
                .create();
        d.show();
    }

    private void showAdvanced() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(14), dp(22), dp(4));
        box.addView(text("启动参数（留空 = 使用 payload 自带）", 11, DIM, false));
        EditText et = new EditText(this);
        et.setText(cfg.cmdline);
        et.setHint("rd=ufsroot0 ufsrw=1 rootrw=1 …");
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        et.setTypeface(Typeface.MONOSPACE);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setMinLines(4);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setPadding(dp(10), dp(10), dp(10), dp(10));
        et.setBackground(outline(SURFACE_2, STROKE, 10, 1));
        box.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        box.addView(gap(14));
        android.widget.CheckBox mttcgBox = new android.widget.CheckBox(this);
        mttcgBox.setText("多线程 TCG（启用 3 个 vCPU 真并行）");
        mttcgBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mttcgBox.setTextColor(TEXT);
        mttcgBox.setChecked(cfg.mttcg);
        box.addView(mttcgBox);
        android.widget.CheckBox offloadBox = new android.widget.CheckBox(this);
        offloadBox.setText("原生 present（零拷贝，出问题可关）");
        offloadBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        offloadBox.setTextColor(TEXT);
        offloadBox.setChecked(cfg.offload);
        box.addView(offloadBox);
        TextView fpsLabel = text("画面采样：" + cfg.fps + " fps", 12, DIM, false);
        SeekBar seek = new SeekBar(this);
        // 15..60 fps in steps of 5. The old 1..11 range capped the whole VM
        // at 11 fps regardless of how fast the guest rendered.
        seek.setMax(9);
        seek.setProgress(Math.max(0, Math.min(9, (cfg.fps - 15) / 5)));
        seek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                fpsLabel.setText("画面采样：" + (15 + p * 5) + " fps");
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(fpsLabel);
        box.addView(seek);
        box.addView(text("QEMU 固定 3 GB 客户机内存；vCPU 数取自 cpus=N，默认单线程 TCG。"
                + "勾选多线程 TCG 会自动设置 cpus=3 和 tcgmulti=1。参数长度上限 607 字节。",
                10, DIM, false));
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("高级选项")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除", (dd, w) -> {
                    cfg.cmdline = "";
                    cfg.save(this);
                    showHome();
                })
                .setPositiveButton("保存", (dd, w) -> {
                    cfg.cmdline = et.getText().toString().trim();
                    cfg.mttcg = mttcgBox.isChecked();
                    cfg.offload = offloadBox.isChecked();
                    cfg.fps = 15 + seek.getProgress() * 5;
                    cfg.save(this);
                    showHome();
                })
                .create();
        d.show();
    }

    private void showLogs() {
        StringBuilder sb = new StringBuilder();
        appendFile(sb, new File(getFilesDir(), "vm/qemu.log"));
        appendFile(sb, new File(getFilesDir(), "vm/serial.log"));
        String guest = SweetLog.tail(cfg.rootFile(), 64 * 1024);
        if (!guest.isEmpty()) {
            sb.append("--- SWEETLOG ---\n").append(guest).append('\n');
        }
        String content = sb.toString();
        if (content.trim().isEmpty()) {
            content = "还没有运行日志。";
        }
        final String shown = content;
        TextView tv = text(content, 11, TEXT, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(14), dp(12), dp(14), dp(12));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("运行日志")
                .setView(sv)
                .setNegativeButton("关闭", null)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("vmios log", shown));
                    toast("已复制");
                })
                .show();
    }

    private void appendFile(StringBuilder sb, File f) {
        if (!f.isFile()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 256 * 1024)];
            int n = in.read(b);
            if (n > 0) {
                sb.append(new String(b, 0, n, java.nio.charset.StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException ignored) {
        }
    }

    private void showHelp() {
        dialog("构建镜像与 payload",
                "1. 在 Linux/macOS 上用 tools/mkiosroot.sh 把 iPhone X 16.7.16 IPSW 的 "
                        + "OS/SystemOS/AppOS 合成 16 GB HFS+ 根镜像。\n\n"
                        + "2. 生成 QEMU 设备树：python3 tools/mkadt.py q.adt --qemu\n\n"
                        + "3. 打包 payload：python3 tools/mkpayload.py kernel.development.sweet "
                        + "q.adt payload.bin --cmdline \"debug=0xc serial=3 cpus=1 keepsyms=1 "
                        + "-enable_kprintf_spam -noprogress rd=ufsroot0 ufsrw=1 rootrw=1 "
                        + "unentitled_ios_sim_launch=1 ignition_level=1 wdt_test=no_user_checkin "
                        + "launchd_unsecure_cache=1 wdt=-1 sweetfb_hz=30\"\n\n"
                        + "4. 在 App 里导入 payload 和根镜像。根镜像也可放到 "
                        + "Android/data/dev.vmios.sweetvm/files/ios16-root.img。\n\n"
                        + "注意：kernel 必须用 QEMU ADT（--qemu），phys_base 必须是 0xb0000000，"
                        + "不能使用 --highmem。");
    }

    private void dialog(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("知道了", null).show();
    }

    private void dialog(String title, String msg, String positive, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton(positive, (d, w) -> action.run())
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void acquireWake() {
        if (wake == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vmios:vm");
            wake.setReferenceCounted(false);
        }
        wake.acquire(12L * 60 * 60 * 1000);
    }

    private void releaseWake() {
        if (wake != null && wake.isHeld()) {
            wake.release();
        }
    }

    @Override
    public void onBackPressed() {
        if (vmActive) {
            confirmStop();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        vmActive = false;
        vmStopping = true;
        ui.removeCallbacks(ticker);
        engine.stop();
        if (touch != null) {
            touch.close();
            touch = null;
        }
        releaseWake();
    }
}

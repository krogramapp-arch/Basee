package com.kroxray;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 41;
    private static final int REQUEST_VPN = 42;
    private static final int INK = Color.rgb(27, 38, 35);
    private static final int MUTED = Color.rgb(105, 119, 113);
    private static final int GREEN = Color.rgb(0, 126, 101);
    private static final int CORAL = Color.rgb(221, 88, 68);
    private static final int CANVAS = Color.rgb(245, 248, 246);

    private ProfileStore store;
    private LinearLayout profileList;
    private TextView selectedLabel;
    private TextView countLabel;
    private TextView statusLabel;
    private Button connectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ProfileStore(this);

        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CANVAS);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(page);
        addHeader(content);
        addConnectionPanel(content);
        addProfileSection(content);
        addFooter(content);
        refreshProfiles();
    }

    private void addHeader(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView mark = text("KX", 15, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(GREEN, 14, 0));
        row.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView brand = text("KroXray", 25, INK, true);
        TextView subtitle = text("اتصالك، بإدارتك", 13, MUTED, false);
        titles.addView(brand);
        titles.addView(subtitle);
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        TextView plan = text("مجاني", 12, GREEN, true);
        plan.setPadding(dp(12), dp(7), dp(12), dp(7));
        plan.setBackground(shape(Color.rgb(224, 242, 235), 18, 0));
        row.addView(plan);
        parent.addView(row, bottom(24));
    }

    private void addConnectionPanel(LinearLayout parent) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(shape(INK, 16, 0));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = text("●", 12, Color.rgb(244, 186, 72), true);
        statusRow.addView(dot, end(8));
        statusLabel = text("غير متصل", 14, Color.WHITE, true);
        statusRow.addView(statusLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView secure = text("VPN", 11, Color.rgb(191, 208, 200), true);
        statusRow.addView(secure);
        panel.addView(statusRow, bottom(18));

        TextView serverTitle = text("الخادم المحدد", 12, Color.rgb(171, 190, 182), false);
        panel.addView(serverTitle, bottom(4));
        selectedLabel = text("أضف ملف اتصال للبدء", 17, Color.WHITE, true);
        panel.addView(selectedLabel, bottom(16));

        connectButton = button("اتصال", GREEN, Color.WHITE);
        connectButton.setOnClickListener(view -> toggleConnection());
        panel.addView(connectButton, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView note = text("لن يبدأ التطبيق نفقاً غير مكتمل أو يغيّر مسار اتصالك.", 12,
                Color.rgb(190, 202, 196), false);
        note.setPadding(0, dp(12), 0, 0);
        panel.addView(note);
        parent.addView(panel, bottom(26));
    }

    private void addProfileSection(LinearLayout parent) {
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("ملفات الاتصال", 19, INK, true);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        countLabel = text("0", 13, MUTED, true);
        titleRow.addView(countLabel);
        parent.addView(titleRow, bottom(12));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = button("+  لصق رابط", Color.WHITE, GREEN);
        paste.setBackground(shape(Color.WHITE, 10, Color.rgb(214, 225, 219)));
        paste.setOnClickListener(view -> showPasteDialog());
        actions.addView(paste, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button importFile = button("استيراد ملف", Color.rgb(255, 232, 224), CORAL);
        importFile.setOnClickListener(view -> openFilePicker());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        importParams.setMarginStart(dp(10));
        actions.addView(importFile, importParams);
        parent.addView(actions, bottom(14));

        profileList = new LinearLayout(this);
        profileList.setOrientation(LinearLayout.VERTICAL);
        parent.addView(profileList);
    }

    private void addFooter(LinearLayout parent) {
        TextView footer = text("تُحفظ الإعدادات على هذا الجهاز فقط.", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, 0);
        parent.addView(footer);
    }

    private void refreshProfiles() {
        if (profileList == null) {
            return;
        }
        profileList.removeAllViews();
        List<ProfileStore.Profile> profiles = store.getProfiles();
        countLabel.setText(String.valueOf(profiles.size()));
        String selectedId = store.getSelectedId();
        String selectedName = "أضف ملف اتصال للبدء";

        if (profiles.isEmpty()) {
            TextView empty = text("لا توجد ملفات بعد", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(26), dp(12), dp(26));
            empty.setBackground(shape(Color.WHITE, 12, Color.rgb(224, 231, 227)));
            profileList.addView(empty);
        }

        for (ProfileStore.Profile profile : profiles) {
            boolean selected = profile.id.equals(selectedId);
            if (selected) {
                selectedName = profile.name;
            }
            profileList.addView(profileRow(profile, selected), bottom(9));
        }
        selectedLabel.setText(selectedName);
        refreshConnectionStatus();
    }

    private View profileRow(ProfileStore.Profile profile, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(shape(Color.WHITE, 12,
                selected ? GREEN : Color.rgb(224, 231, 227)));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(profile.name, 15, INK, true);
        TextView type = text(protocolLabel(profile.content) + "  ·  محفوظ محلياً", 12, MUTED, false);
        details.addView(name, bottom(4));
        details.addView(type);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        TextView marker = text(selected ? "محدد" : "›", 12, selected ? GREEN : MUTED, true);
        row.addView(marker);
        row.setOnClickListener(view -> {
            store.setSelectedId(profile.id);
            refreshProfiles();
        });
        row.setOnLongClickListener(view -> {
            new AlertDialog.Builder(this)
                    .setTitle("حذف ملف الاتصال؟")
                    .setMessage(profile.name)
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("حذف", (dialog, which) -> {
                        store.remove(profile.id);
                        refreshProfiles();
                    })
                    .show();
            return true;
        });
        return row;
    }

    private void showPasteDialog() {
        EditText input = new EditText(this);
        input.setHint("vless:// أو vmess:// أو trojan:// أو ss://");
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int padding = dp(14);
        input.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(this)
                .setTitle("إضافة رابط أو إعداد")
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> importText(input.getText().toString()))
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                startSelectedProfile();
            } else {
                showError("تم رفض صلاحية VPN.");
                refreshConnectionStatus();
            }
            return;
        }
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("تعذر فتح الملف.");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            importText(output.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException exception) {
            showError("تعذر قراءة الملف.");
        }
    }

    private void importText(String text) {
        try {
            List<ProfileStore.Profile> added = store.addFromText(text);
            refreshProfiles();
            Toast.makeText(this, "تم حفظ " + added.size() + " ملف اتصال", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException | JSONException exception) {
            showError(exception.getMessage() == null ? "تعذر قراءة الإعداد." : exception.getMessage());
        }
    }

    private void toggleConnection() {
        if (XrayVpnService.isRunning()) {
            XrayVpnService.stop(this);
            refreshConnectionStatus();
            return;
        }
        startSelectedProfile();
    }

    private void startSelectedProfile() {
        ProfileStore.Profile profile = getSelectedProfile();
        if (profile == null) {
            showError("اختر ملف اتصال أولاً.");
            return;
        }
        Intent vpnPrepare = VpnService.prepare(this);
        if (vpnPrepare != null) {
            startActivityForResult(vpnPrepare, REQUEST_VPN);
            return;
        }
        statusLabel.setText("جارٍ الاتصال...");
        connectButton.setEnabled(false);
        XrayVpnService.start(this, profile.content);
        refreshConnectionStatus();
    }

    private ProfileStore.Profile getSelectedProfile() {
        String selectedId = store.getSelectedId();
        if (selectedId.isEmpty()) {
            return null;
        }
        for (ProfileStore.Profile profile : store.getProfiles()) {
            if (profile.id.equals(selectedId)) {
                return profile;
            }
        }
        return null;
    }

    private void refreshConnectionStatus() {
        if (statusLabel == null || connectButton == null) {
            return;
        }
        boolean running = XrayVpnService.isRunning();
        statusLabel.setText(running ? "متصل" : "غير متصل");
        connectButton.setText(running ? "إيقاف" : "اتصال");
        connectButton.setEnabled(true);
        connectButton.setBackground(shape(running ? CORAL : GREEN, 10, 0));
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("تعذر الاستيراد")
                .setMessage(message)
                .setPositiveButton("حسناً", null)
                .show();
    }

    private String protocolLabel(String content) {
        if (content.startsWith("{")) {
            return "Xray JSON";
        }
        int separator = content.indexOf("://");
        return separator > 0 ? content.substring(0, separator).toUpperCase(Locale.ROOT) : "إعداد";
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(shape(background, 10, 0));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams bottom(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams end(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMarginEnd(dp(margin));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
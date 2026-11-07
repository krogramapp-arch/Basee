package com.kroxray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import org.json.JSONObject;

public final class XrayVpnService extends VpnService {
    public static final String ACTION_START = "com.kroxray.START_VPN";
    public static final String EXTRA_PROFILE = "profile";
    private static final int NOTIFICATION_ID = 7824;
    private static final String CHANNEL_ID = "kroxray_vpn";

    private static volatile boolean running = false;
    private ParcelFileDescriptor tunDescriptor;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context, String profile) {
        Intent intent = new Intent(context, XrayVpnService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_PROFILE, profile);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, XrayVpnService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            String profile = intent.getStringExtra(EXTRA_PROFILE);
            if (profile == null || profile.trim().isEmpty()) {
                stopSelf();
                return START_NOT_STICKY;
            }
            new Thread(() -> {
                try {
                    launch(profile);
                } catch (Exception exception) {
                    running = false;
                    stopSelf();
                    android.util.Log.e("KroXray", "Xray launch failed", exception);
                }
            }, "kroxray-vpn").start();
        }
        return START_STICKY;
    }

    private void launch(String profile) throws Exception {
        ensureForeground();

        Builder builder = new Builder();
        builder.setSession("KroXray")
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .allowFamily(OsConstants.AF_INET);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.allowFamily(OsConstants.AF_INET6);
        }

        tunDescriptor = builder.establish();
        if (tunDescriptor == null) {
            throw new IllegalStateException("Unable to establish VPN tunnel.");
        }

        String xrayJson = prepareXrayJson(profile, tunDescriptor.getFd());
        try {
            libXray.LibXray.setDNS(new libXray.DialerController() {
                @Override
                public boolean protectFd(long fd) {
                    return XrayVpnService.this.protect((int) fd);
                }
            }, "8.8.8.8:53");

            JSONObject request = new JSONObject();
            request.put("apiVersion", 3);
            request.put("method", "runXray");
            JSONObject payload = new JSONObject();
            payload.put("xrayJson", xrayJson);
            request.put("payload", payload);

            String result = libXray.LibXray.invoke(request.toString());
            JSONObject response = new JSONObject(result);
            if (!response.optBoolean("success", false)) {
                String detail = response.optString("error", "Unknown Xray error");
                throw new IllegalStateException(detail);
            }
            running = true;
        } finally {
            if (!running && tunDescriptor != null) {
                try {
                    tunDescriptor.close();
                } catch (Exception ignored) {
                }
                tunDescriptor = null;
            }
        }
    }

    private void ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "KroXray VPN",
                        NotificationManager.IMPORTANCE_LOW
                );
                manager.createNotificationChannel(channel);
            }
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("KroXray")
                .setContentText("VPN is active")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private String prepareXrayJson(String profile, int tunFd) throws Exception {
        String xrayJson = profile.trim();
        if (!xrayJson.startsWith("{")) {
            JSONObject convertRequest = new JSONObject();
            convertRequest.put("apiVersion", 3);
            convertRequest.put("method", "convertShareLinksToXrayJson");
            JSONObject payload = new JSONObject();
            payload.put("text", xrayJson);
            convertRequest.put("payload", payload);

            String rawResponse = libXray.LibXray.invoke(convertRequest.toString());
            JSONObject response = new JSONObject(rawResponse);
            if (!response.optBoolean("success", false)) {
                throw new IllegalStateException(response.optString("error", "Unable to convert profile."));
            }
            Object data = response.opt("data");
            if (data == null) {
                throw new IllegalStateException("No converted Xray JSON was returned.");
            }
            xrayJson = data.toString();
        }

        JSONObject root = new JSONObject(xrayJson);
        JSONObject env = root.optJSONObject("env");
        if (env == null) {
            env = new JSONObject();
            root.put("env", env);
        }
        env.put("xray.tun.fd", tunFd);
        return root.toString();
    }

    private void stopRunningXray() {
        if (!running) {
            return;
        }
        try {
            JSONObject request = new JSONObject();
            request.put("apiVersion", 3);
            request.put("method", "stopXray");
            libXray.LibXray.invoke(request.toString());
        } catch (Exception ignored) {
        } finally {
            running = false;
        }
    }

    @Override
    public void onDestroy() {
        stopRunningXray();
        if (tunDescriptor != null) {
            try {
                tunDescriptor.close();
            } catch (Exception ignored) {
            }
            tunDescriptor = null;
        }
        try {
            libXray.LibXray.resetDNS();
        } catch (Exception ignored) {
        }
        stopForeground(true);
        super.onDestroy();
    }
}

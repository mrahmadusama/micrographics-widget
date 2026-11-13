package com.ahmad.micrographics;

import android.app.Notification;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Watches for running timers (there's no API for them, only the Clock app's notification) and
 * keeps the process alive so flashlight changes redraw the widget straight away. Needs
 * notification access, which the user grants from the setup screen.
 */
public class StatusListener extends NotificationListenerService {
    private final Set<String> torchesOn = new HashSet<>();
    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            boolean wasOn = !torchesOn.isEmpty();
            if (enabled) torchesOn.add(cameraId); else torchesOn.remove(cameraId);
            if (wasOn != !torchesOn.isEmpty()) MicrographicsWidget.refreshAsync(StatusListener.this, null);
        }
    };

    @Override
    public void onListenerConnected() {
        getSystemService(CameraManager.class).registerTorchCallback(torchCallback, new Handler(Looper.getMainLooper()));
        scan();
    }

    @Override
    public void onListenerDisconnected() {
        getSystemService(CameraManager.class).unregisterTorchCallback(torchCallback);
        torchesOn.clear();
        if (Prefs.setTimer(this, 0, 0)) MicrographicsWidget.refreshAsync(this, null);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        scan();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        scan();
    }

    /** Stores the soonest running timer (or a paused one) and redraws when that changes. */
    private void scan() {
        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (SecurityException e) {
            return; // Not connected yet.
        }
        long soonest = 0;
        int count = 0;
        if (active != null) {
            for (StatusBarNotification sbn : active) {
                long end = timerEnd(sbn);
                if (end == 0) continue;
                count++;
                if (soonest == 0 || soonest == Prefs.TIMER_PAUSED || (end != Prefs.TIMER_PAUSED && end < soonest)) {
                    soonest = end;
                }
            }
        }
        if (Prefs.setTimer(this, soonest, count)) MicrographicsWidget.refreshAsync(this, null);
    }

    /**
     * When the timer in this notification goes off, {@link Prefs#TIMER_PAUSED} for a paused
     * timer, or 0 if it isn't a timer.
     */
    static long timerEnd(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        // Android 16's MetricStyle, used by the Clock app: a countdown metric ending at zeroTime.
        Object metrics = extras.get("android.metrics");
        if (metrics instanceof ArrayList) {
            for (Object o : (ArrayList<?>) metrics) {
                if (!(o instanceof Bundle)) continue;
                Bundle value = ((Bundle) o).getBundle("value");
                if (value != null && value.getBoolean("countDown") && value.containsKey("zeroTime")) {
                    return value.getLong("zeroTime");
                }
            }
        }
        // Older clock apps: a countdown chronometer ending at the notification's time.
        if (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
                && extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)) {
            return n.when;
        }
        // A clock app's timer notification with no running countdown: paused.
        String pkg = sbn.getPackageName().toLowerCase(Locale.ROOT);
        String channel = n.getChannelId() == null ? "" : n.getChannelId().toLowerCase(Locale.ROOT);
        if (pkg.contains("clock") && channel.contains("timer")) return Prefs.TIMER_PAUSED;
        return 0;
    }
}

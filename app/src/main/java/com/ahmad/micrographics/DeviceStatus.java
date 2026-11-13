package com.ahmad.micrographics;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** The next alarm and the charging state, read fresh on every redraw. */
final class DeviceStatus {
    /** Alarms further away than this aren't shown. */
    static final long ALARM_LOOKAHEAD_MS = 7 * 24 * 60 * 60 * 1000L;

    private DeviceStatus() {}

    /** The next alarm clock set in any app within the lookahead, or null. */
    static AlarmManager.AlarmClockInfo nextAlarm(Context c, long now) {
        AlarmManager.AlarmClockInfo info = c.getSystemService(AlarmManager.class).getNextAlarmClock();
        if (info == null || info.getTriggerTime() - now > ALARM_LOOKAHEAD_MS) return null;
        return info;
    }

    /**
     * Whether any flashlight is on. Android only reports this through a callback, which fires
     * once for every flash right after registering, so register, collect and unregister.
     */
    static boolean torchOn(Context c) {
        CameraManager cm = c.getSystemService(CameraManager.class);
        int flashes = flashIds(cm).length;
        if (flashes == 0) return false;
        HandlerThread thread = new HandlerThread("micrographics-torch");
        thread.start();
        AtomicBoolean on = new AtomicBoolean();
        CountDownLatch reported = new CountDownLatch(flashes);
        CameraManager.TorchCallback cb = new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String cameraId, boolean enabled) {
                if (enabled) on.set(true);
                reported.countDown();
            }
        };
        try {
            cm.registerTorchCallback(cb, new Handler(thread.getLooper()));
            reported.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cm.unregisterTorchCallback(cb);
            thread.quitSafely();
        }
        return on.get();
    }

    /** Turns every flashlight off. Needs no camera permission. */
    static void torchOff(Context c) {
        CameraManager cm = c.getSystemService(CameraManager.class);
        for (String id : flashIds(cm)) {
            try {
                cm.setTorchMode(id, false);
            } catch (CameraAccessException | IllegalArgumentException e) {
                // Camera in use by an app; nothing to turn off from here.
            }
        }
    }

    private static String[] flashIds(CameraManager cm) {
        try {
            return java.util.Arrays.stream(cm.getCameraIdList()).filter(id -> {
                try {
                    Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    return flash != null && flash;
                } catch (CameraAccessException | IllegalArgumentException e) {
                    return false;
                }
            }).toArray(String[]::new);
        } catch (CameraAccessException e) {
            return new String[0];
        }
    }

    static final class Battery {
        final int percent;
        final boolean full;
        /** Estimated time until full, or -1 when the system doesn't know. */
        final long msToFull;

        Battery(int percent, boolean full, long msToFull) {
            this.percent = percent;
            this.full = full;
            this.msToFull = msToFull;
        }
    }

    /** The battery state while plugged in, or null when running on battery. */
    static Battery charging(Context c) {
        // A null receiver just reads the sticky battery broadcast; nothing stays registered.
        Intent status = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null || status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) return null;
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = level < 0 || scale <= 0 ? 0 : Math.round(100f * level / scale);
        boolean full = status.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_FULL
                || percent >= 100;
        long toFull = full ? -1 : c.getSystemService(BatteryManager.class).computeChargeTimeRemaining();
        return new Battery(percent, full, toFull);
    }
}

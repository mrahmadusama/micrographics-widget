package com.ahmad.micrographics;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

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

package com.ahmad.micrographics;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/**
 * Micrographics widget provider. Every broadcast it gets (widget updates and removals, the
 * minute alarm, boot, clock and timezone changes) ends in the same redraw of both widgets;
 * once no widget is left, that redraw cancels the alarm.
 */
public class MicrographicsWidget extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (WidgetUpdater.ACTION_TORCH_OFF.equals(intent.getAction())) {
            DeviceStatus.torchOff(context);
        }
        refreshAsync(context, goAsync());
    }

    static void refreshAsync(Context context, PendingResult result) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                WidgetUpdater.refresh(app);
            } finally {
                if (result != null) result.finish();
            }
        }, "micrographics-refresh").start();
    }
}

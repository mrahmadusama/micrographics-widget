package com.ahmad.micrographics;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/**
 * Widget provider. Every broadcast it gets (widget updates, our own refresh alarm, boot,
 * clock and timezone changes) ends in the same full redraw.
 */
public class MicrographicsWidget extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(intent.getAction())) {
            WidgetUpdater.cancelAlarm(context);
            CalendarChangeJob.cancel(context);
            return;
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

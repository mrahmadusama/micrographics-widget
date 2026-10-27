package com.ahmad.micrographics;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/** Gooey clock widget provider. Drawing and the minute alarm are shared with Micrographics. */
public class GooeyClockWidget extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        MicrographicsWidget.refreshAsync(context, goAsync());
    }
}

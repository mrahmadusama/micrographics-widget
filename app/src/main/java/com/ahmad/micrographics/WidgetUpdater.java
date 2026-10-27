package com.ahmad.micrographics;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Locale;

/**
 * Builds the RemoteViews for both widgets and wakes them once a minute. All widget text is
 * rendered to bitmaps (see TextBitmap), so the clocks can't tick by themselves.
 */
final class WidgetUpdater {
    static final String ACTION_REFRESH = "com.ahmad.micrographics.REFRESH";

    private static final long MINUTE = 60_000L;
    /** Within this window before a meeting the status row counts down in minutes. */
    private static final long COUNTDOWN_WINDOW = 60 * MINUTE;
    /** Gooey digits are drawn this tall and scaled down by the ImageView to fit the widget. */
    private static final float GOOEY_TEXT_PX = 320;
    /** Geist Mono glyphs are 0.6 em wide, so a 22sp character is 13.2dp. */
    private static final float GEIST_ADVANCE_EM = 0.6f;
    /** Widget width taken by padding, avatar, arrow and the gaps around the meeting title. */
    private static final int MEETING_ROW_CHROME_DP = 16 + 34 + 36 + 16;

    private static final int[] MONTHS = {
            R.drawable.micrographics_month_jan, R.drawable.micrographics_month_feb,
            R.drawable.micrographics_month_mar, R.drawable.micrographics_month_apr,
            R.drawable.micrographics_month_may, R.drawable.micrographics_month_jun,
            R.drawable.micrographics_month_jul, R.drawable.micrographics_month_aug,
            R.drawable.micrographics_month_sep, R.drawable.micrographics_month_oct,
            R.drawable.micrographics_month_nov, R.drawable.micrographics_month_dec,
    };
    private static final int[] HANDS = {
            R.drawable.micrographics_hands_1, R.drawable.micrographics_hands_2,
            R.drawable.micrographics_hands_3, R.drawable.micrographics_hands_4,
            R.drawable.micrographics_hands_5, R.drawable.micrographics_hands_6,
            R.drawable.micrographics_hands_7, R.drawable.micrographics_hands_8,
            R.drawable.micrographics_hands_9, R.drawable.micrographics_hands_10,
    };

    private WidgetUpdater() {}

    /** Redraws every placed widget, fetching weather first if it's stale. Blocks; call off the main thread. */
    static void refresh(Context c) {
        int[] micro = widgetIds(c, MicrographicsWidget.class);
        int[] gooey = widgetIds(c, GooeyClockWidget.class);
        if (micro.length == 0 && gooey.length == 0) {
            cancelAlarm(c);
            CalendarChangeJob.cancel(c);
            return;
        }
        // Draw straight away with cached data, then again once fresh weather arrives.
        renderGooey(c, gooey);
        renderMicrographics(c, micro);
        if (micro.length > 0) {
            Weather before = Prefs.weather(c);
            Weather.refreshIfStale(c);
            Weather after = Prefs.weather(c);
            if (after != null && (before == null || after.fetchedAt != before.fetchedAt)) {
                renderMicrographics(c, micro);
            }
            CalendarChangeJob.ensureScheduled(c);
        }
        scheduleNextMinute(c);
    }

    private static int[] widgetIds(Context c, Class<?> provider) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, provider));
    }

    private static boolean is24Hour(Context c) {
        return DateFormat.is24HourFormat(c);
    }

    // ---- Gooey ------------------------------------------------------------------------------

    private static void renderGooey(Context c, int[] ids) {
        if (ids.length == 0) return;
        String time = DateFormat.format(is24Hour(c) ? "HHmm" : "hhmm", System.currentTimeMillis()).toString();
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_gooey);
        v.setImageViewBitmap(R.id.gooey_time, TextBitmap.drawPx(c, time, TextBitmap.gooey(c), GOOEY_TEXT_PX));
        v.setContentDescription(R.id.gooey_time, DateFormat.getTimeFormat(c).format(System.currentTimeMillis()));
        v.setOnClickPendingIntent(R.id.gooey_root, activity(c, 10, new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        AppWidgetManager.getInstance(c).updateAppWidget(ids, v);
    }

    // ---- Micrographics ----------------------------------------------------------------------

    private static void renderMicrographics(Context c, int[] ids) {
        if (ids.length == 0) return;
        long now = System.currentTimeMillis();
        Meeting meeting = Meeting.next(c, now);
        AppWidgetManager awm = AppWidgetManager.getInstance(c);
        for (int id : ids) {
            awm.updateAppWidget(id, buildMicrographics(c, now, meeting, awm.getAppWidgetOptions(id)));
        }
    }

    private static RemoteViews buildMicrographics(Context c, long now, Meeting meeting, Bundle options) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_micrographics);
        Typeface font = TextBitmap.geistMono(c);
        float textPx = c.getResources().getDimension(R.dimen.mg_text);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);

        // Row 1: FRI (25) (JUL) 10:27
        String dayName = DateFormat.format("EEE", now).toString().toUpperCase(Locale.getDefault());
        v.setImageViewBitmap(R.id.day_name, TextBitmap.drawPx(c, dayName, font, textPx));
        v.setImageViewBitmap(R.id.day_pill, DayPill.draw(c, cal.get(Calendar.DAY_OF_MONTH)));
        v.setImageViewResource(R.id.month, MONTHS[cal.get(Calendar.MONTH)]);
        v.setImageViewBitmap(R.id.time, TextBitmap.drawPx(c, clockTime(c, now), font, textPx));
        v.setContentDescription(R.id.date_row, DateFormat.format("EEEE d MMMM", now) + ", "
                + DateFormat.getTimeFormat(c).format(now));
        v.setOnClickPendingIntent(R.id.date_row, activity(c, 1, new Intent(AlarmClock.ACTION_SHOW_ALARMS)));

        bindWeather(c, v, font, textPx);
        v.setOnClickPendingIntent(R.id.weather_row, activity(c, 2, new Intent(c, SetupActivity.class)));

        bindMeeting(c, v, now, meeting, font, textPx, titleChars(c, options, textPx));
        return v;
    }

    private static void bindWeather(Context c, RemoteViews v, Typeface font, float textPx) {
        boolean fahrenheit = Prefs.fahrenheit(c);
        Weather w = Prefs.weather(c);
        v.setImageViewResource(R.id.temp_unit,
                fahrenheit ? R.drawable.micrographics_farenheight : R.drawable.micrographics_celcius);
        String condition = w == null ? "unavailable" : w.condition();
        v.setImageViewResource(R.id.weather_icon, Weather.iconRes(condition));
        v.setImageViewResource(R.id.weather_desc, Weather.descRes(condition));
        String temp = w == null ? "--°" : Math.round(w.temperature) + "°";
        v.setImageViewBitmap(R.id.temp, TextBitmap.drawPx(c, temp, font, textPx));
        v.setContentDescription(R.id.weather_row, temp + (fahrenheit ? "F " : "C ") + condition.replace('_', ' '));
        float top = 8;
        if (w != null) {
            // The marker slides down the thermometer as it gets colder: -10..40 °C (14..104 °F).
            double low = fahrenheit ? 14 : -10, high = fahrenheit ? 104 : 40;
            double warmth = Math.max(0, Math.min(1, (w.temperature - low) / (high - low)));
            top = (float) (1 + (1 - warmth) * 14);
        }
        v.setViewLayoutMargin(R.id.therm_indicator, RemoteViews.MARGIN_TOP, top, TypedValue.COMPLEX_UNIT_DIP);
    }

    private static void bindMeeting(Context c, RemoteViews v, long now, Meeting m, Typeface font, float textPx,
            int maxTitleChars) {
        int visibility = m == null ? View.GONE : View.VISIBLE;
        v.setViewVisibility(R.id.meeting_row, visibility);
        v.setViewVisibility(R.id.status_row, visibility);
        if (m == null) return;

        String title = m.title.toUpperCase(Locale.getDefault());
        if (title.length() > maxTitleChars) title = title.substring(0, Math.max(1, maxTitleChars - 1)).trim() + "…";
        v.setImageViewBitmap(R.id.meeting_title, TextBitmap.drawPx(c, title, font, textPx));
        v.setContentDescription(R.id.meeting_title, m.title);

        String label;
        String count;
        int hands = 0;
        if (m.begin > now) {
            long minutes = (m.begin - now + MINUTE - 1) / MINUTE;
            if (minutes <= COUNTDOWN_WINDOW / MINUTE) {
                label = c.getString(R.string.label_starting);
                count = c.getString(R.string.minutes_format, minutes);
                if (minutes <= HANDS.length) hands = (int) minutes;
            } else {
                label = c.getString(R.string.label_at);
                count = clockTime(c, m.begin);
            }
        } else {
            label = c.getString(R.string.label_until);
            count = clockTime(c, m.end);
        }
        v.setImageViewBitmap(R.id.status_label, TextBitmap.drawPx(c, label, font, textPx));
        v.setImageViewBitmap(R.id.count, TextBitmap.drawPx(c, count, font,
                c.getResources().getDimension(R.dimen.mg_text_small)));
        v.setContentDescription(R.id.status_row, label + " " + count);
        v.setViewVisibility(R.id.hands, hands > 0 ? View.VISIBLE : View.GONE);
        if (hands > 0) v.setImageViewResource(R.id.hands, HANDS[hands - 1]);

        Intent open = new Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, m.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, m.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, m.end);
        PendingIntent pi = activity(c, 3, open);
        v.setOnClickPendingIntent(R.id.meeting_row, pi);
        v.setOnClickPendingIntent(R.id.status_row, pi);
    }

    /** How many title characters fit beside the avatar and arrow at this widget's width. */
    private static int titleChars(Context c, Bundle options, float textPx) {
        // In portrait the launcher reports the widget's width as OPTION_APPWIDGET_MIN_WIDTH.
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (widthDp <= 0) return 18;
        float density = c.getResources().getDisplayMetrics().density;
        float charDp = textPx * GEIST_ADVANCE_EM / density;
        return Math.max(6, (int) ((widthDp - MEETING_ROW_CHROME_DP) / charDp));
    }

    private static String clockTime(Context c, long time) {
        return DateFormat.format(is24Hour(c) ? "HH:mm" : "h:mm", time).toString();
    }

    private static PendingIntent activity(Context c, int requestCode, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---- Scheduling ------------------------------------------------------------------------

    /**
     * Redraws at the start of the next minute. RTC (not RTC_WAKEUP): while the screen is off
     * nobody sees the widgets, so the alarm waits for the phone to wake and fires straight away.
     */
    private static void scheduleNextMinute(Context c) {
        long next = (System.currentTimeMillis() / MINUTE + 1) * MINUTE + 200;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = refreshIntent(c);
        if (am.canScheduleExactAlarms()) {
            am.setExact(AlarmManager.RTC, next, pi);
        } else {
            am.set(AlarmManager.RTC, next, pi);
        }
    }

    static void cancelAlarm(Context c) {
        c.getSystemService(AlarmManager.class).cancel(refreshIntent(c));
    }

    private static PendingIntent refreshIntent(Context c) {
        Intent i = new Intent(c, MicrographicsWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}

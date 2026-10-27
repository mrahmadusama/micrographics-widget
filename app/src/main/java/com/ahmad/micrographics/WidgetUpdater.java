package com.ahmad.micrographics;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;

/** Builds the widget's RemoteViews and schedules the next time they need to change. */
final class WidgetUpdater {
    static final String ACTION_REFRESH = "com.ahmad.micrographics.REFRESH";

    private static final long MINUTE = 60_000L;
    /** Within this window before a meeting the countdown updates every minute. */
    private static final long COUNTDOWN_WINDOW = 60 * MINUTE;

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
        int[] ids = widgetIds(c);
        if (ids.length == 0) {
            cancelAlarm(c);
            return;
        }
        // Draw straight away with cached data, then again once fresh weather arrives.
        Meeting meeting = render(c, ids);
        Weather before = Prefs.weather(c);
        Weather.refreshIfStale(c);
        Weather after = Prefs.weather(c);
        if (after != null && (before == null || after.fetchedAt != before.fetchedAt)) {
            meeting = render(c, ids);
        }
        scheduleNext(c, meeting);
        CalendarChangeJob.ensureScheduled(c);
    }

    static int[] widgetIds(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, MicrographicsWidget.class));
    }

    private static Meeting render(Context c, int[] ids) {
        long now = System.currentTimeMillis();
        Meeting meeting = Meeting.next(c, now);
        RemoteViews views = build(c, now, meeting);
        AppWidgetManager.getInstance(c).updateAppWidget(ids, views);
        return meeting;
    }

    private static RemoteViews build(Context c, long now, Meeting meeting) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_micrographics);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);

        // Row 1: FRI (25) (JUL) 10:27. Day name and time are TextClocks and tick on their own.
        v.setImageViewBitmap(R.id.day_pill, DayPill.draw(c, cal.get(Calendar.DAY_OF_MONTH)));
        v.setImageViewResource(R.id.month, MONTHS[cal.get(Calendar.MONTH)]);
        v.setOnClickPendingIntent(R.id.date_row,
                activity(c, 1, new Intent(AlarmClock.ACTION_SHOW_ALARMS)));

        bindWeather(c, v);
        v.setOnClickPendingIntent(R.id.weather_row, activity(c, 2, new Intent(c, SetupActivity.class)));

        bindMeeting(c, v, now, meeting);
        return v;
    }

    private static void bindWeather(Context c, RemoteViews v) {
        boolean fahrenheit = Prefs.fahrenheit(c);
        Weather w = Prefs.weather(c);
        v.setImageViewResource(R.id.temp_unit,
                fahrenheit ? R.drawable.micrographics_farenheight : R.drawable.micrographics_celcius);
        String condition = w == null ? "unavailable" : w.condition();
        v.setImageViewResource(R.id.weather_icon, Weather.iconRes(condition));
        v.setImageViewResource(R.id.weather_desc, Weather.descRes(condition));
        if (w == null) {
            v.setTextViewText(R.id.temp, "--°");
            v.setViewLayoutMargin(R.id.therm_indicator, RemoteViews.MARGIN_TOP, 8, TypedValue.COMPLEX_UNIT_DIP);
            return;
        }
        v.setTextViewText(R.id.temp, Math.round(w.temperature) + "°");
        // The marker slides down the thermometer as it gets colder: -10..40 °C (14..104 °F).
        double low = fahrenheit ? 14 : -10, high = fahrenheit ? 104 : 40;
        double warmth = Math.max(0, Math.min(1, (w.temperature - low) / (high - low)));
        float top = (float) (1 + (1 - warmth) * 14);
        v.setViewLayoutMargin(R.id.therm_indicator, RemoteViews.MARGIN_TOP, top, TypedValue.COMPLEX_UNIT_DIP);
    }

    private static void bindMeeting(Context c, RemoteViews v, long now, Meeting m) {
        int visibility = m == null ? View.GONE : View.VISIBLE;
        v.setViewVisibility(R.id.meeting_row, visibility);
        v.setViewVisibility(R.id.status_row, visibility);
        if (m == null) return;

        v.setTextViewText(R.id.meeting_title, m.title);
        int hands = 0;
        if (m.begin > now) {
            long minutes = (m.begin - now + MINUTE - 1) / MINUTE;
            if (minutes <= COUNTDOWN_WINDOW / MINUTE) {
                v.setTextViewText(R.id.status_label, c.getString(R.string.label_starting));
                v.setTextViewText(R.id.count, c.getString(R.string.minutes_format, minutes));
                if (minutes <= HANDS.length) hands = (int) minutes;
            } else {
                v.setTextViewText(R.id.status_label, c.getString(R.string.label_at));
                v.setTextViewText(R.id.count, clockTime(c, m.begin));
            }
        } else {
            v.setTextViewText(R.id.status_label, c.getString(R.string.label_until));
            v.setTextViewText(R.id.count, clockTime(c, m.end));
        }
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

    private static String clockTime(Context c, long time) {
        return DateFormat.format(DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm", time).toString();
    }

    private static PendingIntent activity(Context c, int requestCode, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Wakes the widget for the next change it can't make by itself: date, countdown, meeting state, weather. */
    private static void scheduleNext(Context c, Meeting m) {
        long now = System.currentTimeMillis();
        Calendar midnight = Calendar.getInstance();
        midnight.setTimeInMillis(now);
        midnight.add(Calendar.DAY_OF_YEAR, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 1);
        midnight.set(Calendar.MILLISECOND, 0);
        long next = midnight.getTimeInMillis();

        Weather w = Prefs.weather(c);
        if (Prefs.hasLocation(c)) {
            next = Math.min(next, w == null ? now + 5 * MINUTE : w.fetchedAt + Weather.MAX_AGE_MS);
        }
        if (m != null) {
            if (m.begin > now) {
                long nextMinute = (now / MINUTE + 1) * MINUTE + 500;
                next = Math.min(next, m.begin - now <= COUNTDOWN_WINDOW ? nextMinute : m.begin - COUNTDOWN_WINDOW);
            } else {
                next = Math.min(next, m.end + 500);
            }
        }
        next = Math.max(next, now + 1000);

        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = refreshIntent(c);
        // RTC (not RTC_WAKEUP): while the screen is off nobody sees the widget, so let the
        // alarm wait for the device to wake rather than waking it.
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

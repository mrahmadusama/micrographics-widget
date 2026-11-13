package com.ahmad.micrographics;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Builds the RemoteViews for both widgets and wakes them once a minute. All widget text is
 * rendered to bitmaps (see TextBitmap), so the clocks can't tick by themselves.
 */
final class WidgetUpdater {
    static final String ACTION_REFRESH = "com.ahmad.micrographics.REFRESH";
    static final String ACTION_TORCH_OFF = "com.ahmad.micrographics.TORCH_OFF";

    private static final long MINUTE = 60_000L;
    /** Within this window before a meeting the status row counts down in minutes. */
    private static final long COUNTDOWN_WINDOW = 60 * MINUTE;
    /** Gooey digits are drawn this tall and scaled down by the ImageView to fit the widget. */
    private static final float GOOEY_TEXT_PX = 320;
    /** Geist Mono glyphs are 0.6 em wide, so a 22sp character is 13.2dp. */
    private static final float GEIST_ADVANCE_EM = 0.6f;
    /** Widget width taken by padding, avatar, arrow and the gaps around the meeting title. */
    private static final int MEETING_ROW_CHROME_DP = 16 + 34 + 36 + 16;
    /** Widget width taken by padding, the cake and its gap beside a birthday. */
    private static final int BIRTHDAY_ROW_CHROME_DP = 16 + 24 + 8;

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

    /**
     * The vector icons of the Micrographics layout, by which palette colour they take. They're
     * all drawn in one colour, so a colour filter recolours them.
     */
    private static final int[] INK_TINTED = {
            R.id.month, R.id.thermometer, R.id.therm_indicator, R.id.temp_unit, R.id.meeting_avatar,
    };
    private static final int[] PRIMARY_TINTED = {
            R.id.weather_desc, R.id.meeting_arrow,
    };
    private static final int[] TERTIARY_TINTED = {
            R.id.weather_icon, R.id.hands, R.id.count_corner, R.id.count_line,
            R.id.birthday_icon, R.id.alarm_icon, R.id.alarm_corner, R.id.alarm_line,
            R.id.charge_icon, R.id.charge_corner, R.id.charge_line,
            R.id.timer_icon, R.id.timer_corner, R.id.timer_line,
            R.id.torch_icon,
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
        v.setImageViewBitmap(R.id.gooey_time, TextBitmap.drawPx(c, time, TextBitmap.gooey(c), GOOEY_TEXT_PX, Palette.resolve(c).primary));
        v.setContentDescription(R.id.gooey_time, DateFormat.getTimeFormat(c).format(System.currentTimeMillis()));
        if (Prefs.show(c, Prefs.ROW_FLASHLIGHT) && DeviceStatus.torchOn(c)) {
            Intent off = new Intent(c, MicrographicsWidget.class).setAction(ACTION_TORCH_OFF);
            v.setOnClickPendingIntent(R.id.gooey_root, PendingIntent.getBroadcast(c, 11, off,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            v.setOnClickPendingIntent(R.id.gooey_root, activity(c, 10, new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        }
        AppWidgetManager.getInstance(c).updateAppWidget(ids, v);
    }

    // ---- Micrographics ----------------------------------------------------------------------

    private static void renderMicrographics(Context c, int[] ids) {
        if (ids.length == 0) return;
        long now = System.currentTimeMillis();
        Extras x = Extras.read(c, now);
        AppWidgetManager awm = AppWidgetManager.getInstance(c);
        for (int id : ids) {
            awm.updateAppWidget(id, buildMicrographics(c, now, x, awm.getAppWidgetOptions(id)));
        }
    }

    /** What the optional rows show this minute, read once and shared by every widget. */
    private static final class Extras {
        Meeting meeting;
        List<String> birthdays;
        AlarmManager.AlarmClockInfo alarm;
        DeviceStatus.Battery battery;
        /** Soonest timer end, Prefs.TIMER_PAUSED, or 0 for none. */
        long timerEnd;
        int timerCount;
        boolean torch;

        static Extras read(Context c, long now) {
            Extras x = new Extras();
            if (Prefs.show(c, Prefs.ROW_TIMER)) {
                x.timerEnd = Prefs.timerEnd(c);
                x.timerCount = Prefs.timerCount(c);
            }
            if (Prefs.show(c, Prefs.ROW_FLASHLIGHT)) x.torch = DeviceStatus.torchOn(c);
            if (Prefs.show(c, Prefs.ROW_MEETING)) x.meeting = Meeting.next(c, now);
            if (Prefs.show(c, Prefs.ROW_BIRTHDAY)) x.birthdays = Birthdays.today(c, now);
            if (Prefs.show(c, Prefs.ROW_ALARM)) x.alarm = DeviceStatus.nextAlarm(c, now);
            if (Prefs.show(c, Prefs.ROW_CHARGING)) x.battery = DeviceStatus.charging(c);
            return x;
        }
    }

    private static RemoteViews buildMicrographics(Context c, long now, Extras x, Bundle options) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_micrographics);
        Typeface font = TextBitmap.geistMono(c);
        float textPx = c.getResources().getDimension(R.dimen.mg_text);
        Palette p = Palette.resolve(c);
        for (int id : INK_TINTED) v.setInt(id, "setColorFilter", p.ink);
        for (int id : PRIMARY_TINTED) v.setInt(id, "setColorFilter", p.primary);
        for (int id : TERTIARY_TINTED) v.setInt(id, "setColorFilter", p.tertiary);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);

        // Row 1: FRI (25) (JUL) 10:27
        String dayName = DateFormat.format("EEE", now).toString().toUpperCase(Locale.getDefault());
        v.setImageViewBitmap(R.id.day_name, TextBitmap.drawPx(c, dayName, font, textPx, p.ink));
        v.setImageViewBitmap(R.id.day_pill, DayPill.draw(c, cal.get(Calendar.DAY_OF_MONTH), p.primary));
        v.setImageViewResource(R.id.month, MONTHS[cal.get(Calendar.MONTH)]);
        v.setImageViewBitmap(R.id.time, TextBitmap.drawPx(c, clockTime(c, now), font, textPx, p.ink));
        v.setContentDescription(R.id.date_row, DateFormat.format("EEEE d MMMM", now) + ", "
                + DateFormat.getTimeFormat(c).format(now));

        // Taps: while the flashlight is on, anywhere on the widget turns it off. Otherwise the
        // widget opens the clock app, unless the user chose to have rows open their own apps.
        if (x.torch) {
            Intent off = new Intent(c, MicrographicsWidget.class).setAction(ACTION_TORCH_OFF);
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(c, 1, off,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            v.setOnClickPendingIntent(R.id.root, activity(c, 1, new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        }
        boolean taps = !x.torch && Prefs.rowsOpenApps(c);

        // The rest is optional and fills the widget's height in priority order: timer,
        // flashlight, weather, the meeting (two rows), charging, birthdays, then the next alarm.
        // Rows that don't fit drop.
        int rows = rowsBelowDate(c, options, font, textPx);
        boolean timer = x.timerEnd != 0 && rows >= 1;
        if (timer) rows--;
        boolean torch = x.torch && rows >= 1;
        if (torch) rows--;
        boolean weather = Prefs.show(c, Prefs.ROW_WEATHER) && rows >= 1;
        if (weather) rows--;
        Meeting meeting = x.meeting != null && rows >= 2 ? x.meeting : null;
        if (meeting != null) rows -= 2;
        DeviceStatus.Battery battery = x.battery != null && rows >= 1 ? x.battery : null;
        if (battery != null) rows--;
        boolean birthday = x.birthdays != null && !x.birthdays.isEmpty() && rows >= 1;
        if (birthday) rows--;
        AlarmManager.AlarmClockInfo alarm = x.alarm != null && rows >= 1 ? x.alarm : null;

        v.setViewVisibility(R.id.weather_row, weather ? View.VISIBLE : View.GONE);
        if (weather) {
            bindWeather(c, v, font, textPx, p);
            if (taps) v.setOnClickPendingIntent(R.id.weather_row, activity(c, 2, new Intent(c, SetupActivity.class)));
        }
        int titleChars = titleChars(c, options, textPx, MEETING_ROW_CHROME_DP);
        bindTimer(c, v, now, timer ? x.timerEnd : 0, x.timerCount, font, textPx, p, taps);
        bindTorch(c, v, torch, font, textPx, p, options);
        bindMeeting(c, v, now, meeting, font, textPx, p, titleChars, taps);
        bindBirthdays(c, v, now, birthday ? x.birthdays : null, font, textPx, p,
                titleChars(c, options, textPx, BIRTHDAY_ROW_CHROME_DP), taps);
        bindAlarm(c, v, now, alarm, font, textPx, p, taps);
        bindCharging(c, v, battery, font, textPx, p, taps);
        return v;
    }

    /**
     * How many rows fit under the date row at this widget's height. Every row is one line of
     * text or a 26dp countdown bracket, whichever is taller, plus the gap above it.
     */
    private static int rowsBelowDate(Context c, Bundle options, Typeface font, float textPx) {
        // In portrait the launcher reports the widget's height as OPTION_APPWIDGET_MAX_HEIGHT.
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (heightDp <= 0) return Integer.MAX_VALUE;
        float density = c.getResources().getDisplayMetrics().density;
        Paint paint = new Paint();
        paint.setTypeface(font);
        paint.setTextSize(textPx);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textDp = (fm.descent - fm.ascent) / density;
        float rowDp = Math.max(textDp, 26) + c.getResources().getDimension(R.dimen.mg_row_gap) / density;
        float available = heightDp - 16 - Math.max(textDp, 24);
        return Math.max(0, (int) (available / rowDp));
    }

    private static void bindWeather(Context c, RemoteViews v, Typeface font, float textPx, Palette p) {
        boolean fahrenheit = Prefs.fahrenheit(c);
        Weather w = Prefs.weather(c);
        v.setImageViewResource(R.id.temp_unit,
                fahrenheit ? R.drawable.micrographics_farenheight : R.drawable.micrographics_celcius);
        String condition = w == null ? "unavailable" : w.condition();
        v.setImageViewResource(R.id.weather_icon, Weather.iconRes(condition));
        v.setImageViewResource(R.id.weather_desc, Weather.descRes(condition));
        String temp = w == null ? "--°" : Math.round(w.temperature) + "°";
        v.setImageViewBitmap(R.id.temp, TextBitmap.drawPx(c, temp, font, textPx, p.ink));
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
            Palette p, int maxTitleChars, boolean taps) {
        int visibility = m == null ? View.GONE : View.VISIBLE;
        v.setViewVisibility(R.id.meeting_row, visibility);
        v.setViewVisibility(R.id.status_row, visibility);
        if (m == null) return;

        v.setImageViewBitmap(R.id.meeting_title,
                TextBitmap.drawPx(c, fit(m.title, maxTitleChars), font, textPx, p.ink));
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
        bindBracket(c, v, R.id.status_label, label, R.id.count, count, font, textPx, p);
        v.setContentDescription(R.id.status_row, label + " " + count);
        v.setViewVisibility(R.id.hands, hands > 0 ? View.VISIBLE : View.GONE);
        if (hands > 0) v.setImageViewResource(R.id.hands, HANDS[hands - 1]);

        Intent open = new Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, m.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, m.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, m.end);
        if (!taps) return;
        PendingIntent pi = activity(c, 3, open);
        v.setOnClickPendingIntent(R.id.meeting_row, pi);
        v.setOnClickPendingIntent(R.id.status_row, pi);
    }

    /** (cake) ALEX'S BIRTHDAY, or ALEX'S BIRTHDAY +2 when there are several. */
    private static void bindBirthdays(Context c, RemoteViews v, long now, List<String> titles, Typeface font,
            float textPx, Palette p, int maxChars, boolean taps) {
        v.setViewVisibility(R.id.birthday_row, titles == null ? View.GONE : View.VISIBLE);
        if (titles == null) return;
        String more = titles.size() > 1 ? " +" + (titles.size() - 1) : "";
        String first = titles.get(0);
        String text = fit(first, maxChars - more.length()) + more;
        v.setImageViewBitmap(R.id.birthday_title, TextBitmap.drawPx(c, text, font, textPx, p.ink));
        v.setContentDescription(R.id.birthday_row, String.join(", ", titles));
        Uri day = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(String.valueOf(now)).build();
        if (taps) v.setOnClickPendingIntent(R.id.birthday_row, activity(c, 4, new Intent(Intent.ACTION_VIEW, day)));
    }

    /** ALARM (clock) ╰7:00, with the weekday when it isn't within the next day. */
    private static void bindAlarm(Context c, RemoteViews v, long now, AlarmManager.AlarmClockInfo alarm,
            Typeface font, float textPx, Palette p, boolean taps) {
        v.setViewVisibility(R.id.alarm_row, alarm == null ? View.GONE : View.VISIBLE);
        if (alarm == null) return;
        long at = alarm.getTriggerTime();
        String count = clockTime(c, at);
        if (at - now >= 24 * 60 * MINUTE) {
            count = DateFormat.format("EEE", at).toString().toUpperCase(Locale.getDefault()) + " " + count;
        }
        String label = c.getString(R.string.label_alarm);
        bindBracket(c, v, R.id.alarm_label, label, R.id.alarm_count, count, font, textPx, p);
        v.setContentDescription(R.id.alarm_row, label + " " + count);
        if (!taps) return;
        PendingIntent show = alarm.getShowIntent();
        v.setOnClickPendingIntent(R.id.alarm_row,
                show != null ? show : activity(c, 5, new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
    }

    /** CHARGING (bolt) ╰82% · 40MIN while plugged in; CHARGED (bolt) ╰100% once full. */
    private static void bindCharging(Context c, RemoteViews v, DeviceStatus.Battery b, Typeface font,
            float textPx, Palette p, boolean taps) {
        v.setViewVisibility(R.id.charge_row, b == null ? View.GONE : View.VISIBLE);
        if (b == null) return;
        String label = c.getString(b.full ? R.string.label_charged : R.string.label_charging);
        String count = b.percent + "%";
        if (b.msToFull > 0) count += " · " + duration(c, b.msToFull);
        bindBracket(c, v, R.id.charge_label, label, R.id.charge_count, count, font, textPx, p);
        v.setContentDescription(R.id.charge_row, label + " " + count);
        if (taps) v.setOnClickPendingIntent(R.id.charge_row, activity(c, 6, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));
    }

    /** TIMER (hourglass) ╰4MIN; ╰PAUSED; ╰DONE once it has gone off. "+1" for more timers. */
    private static void bindTimer(Context c, RemoteViews v, long now, long end, int count, Typeface font,
            float textPx, Palette p, boolean taps) {
        v.setViewVisibility(R.id.timer_row, end == 0 ? View.GONE : View.VISIBLE);
        if (end == 0) return;
        String text;
        if (end == Prefs.TIMER_PAUSED) {
            text = c.getString(R.string.timer_paused);
        } else if (end <= now) {
            text = c.getString(R.string.timer_done);
        } else {
            text = duration(c, end - now);
        }
        if (count > 1) text += " +" + (count - 1);
        String label = c.getString(R.string.label_timer);
        bindBracket(c, v, R.id.timer_label, label, R.id.timer_count, text, font, textPx, p);
        v.setContentDescription(R.id.timer_row, label + " " + text);
        if (taps) v.setOnClickPendingIntent(R.id.timer_row, activity(c, 7, new Intent(AlarmClock.ACTION_SHOW_TIMERS)));
    }

    /**
     * FLASHLIGHT (torch) (TAP TO TURN OFF), with the hint in a filled bubble like the weather
     * labels, shortened to TURN OFF when the widget is too narrow. The tap is on the whole
     * widget (see buildMicrographics).
     */
    private static void bindTorch(Context c, RemoteViews v, boolean on, Typeface font, float textPx, Palette p,
            Bundle options) {
        v.setViewVisibility(R.id.torch_row, on ? View.VISIBLE : View.GONE);
        if (!on) return;
        String label = c.getString(R.string.label_flashlight);
        v.setImageViewBitmap(R.id.torch_label, TextBitmap.drawPx(c, label, font, textPx, p.ink));
        String hint = c.getString(R.string.torch_tap_off);
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (widthDp > 0) {
            float density = c.getResources().getDisplayMetrics().density;
            Paint paint = new Paint();
            paint.setTypeface(font);
            paint.setTextSize(textPx);
            int iconPx = Math.round(c.getResources().getDimension(R.dimen.mg_icon));
            float gapPx = c.getResources().getDimension(R.dimen.mg_gap);
            float room = widthDp * density - 16 * density - paint.measureText(label) - iconPx - 2 * gapPx;
            if (BubblePill.width(c, hint, iconPx) > room) hint = c.getString(R.string.torch_off_short);
        }
        v.setImageViewBitmap(R.id.torch_pill, BubblePill.draw(c, hint, p.primary));
        v.setContentDescription(R.id.torch_row, c.getString(R.string.torch_description));
    }

    /** A "LABEL (icon) ╰COUNT" row: label in ink, the bracketed count in the tertiary accent. */
    private static void bindBracket(Context c, RemoteViews v, int labelId, String label, int countId, String count,
            Typeface font, float textPx, Palette p) {
        v.setImageViewBitmap(labelId, TextBitmap.drawPx(c, label, font, textPx, p.ink));
        v.setImageViewBitmap(countId, TextBitmap.drawPx(c, count, font,
                c.getResources().getDimension(R.dimen.mg_text_small), p.tertiary));
    }

    /** 40MIN, 1H 20MIN. */
    private static String duration(Context c, long ms) {
        long minutes = Math.max(1, (ms + MINUTE - 1) / MINUTE);
        if (minutes < 60) return c.getString(R.string.minutes_format, minutes);
        return c.getString(R.string.hours_minutes_format, minutes / 60, minutes % 60);
    }

    /** Upper-cases {@code text} and cuts it to {@code maxChars} with an ellipsis. */
    private static String fit(String text, int maxChars) {
        String s = text.toUpperCase(Locale.getDefault());
        if (s.length() > maxChars) s = s.substring(0, Math.max(1, maxChars - 1)).trim() + "…";
        return s;
    }

    /** How many title characters fit beside a row's icons at this widget's width. */
    private static int titleChars(Context c, Bundle options, float textPx, int chromeDp) {
        // In portrait the launcher reports the widget's width as OPTION_APPWIDGET_MIN_WIDTH.
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (widthDp <= 0) return 18;
        float density = c.getResources().getDisplayMetrics().density;
        float charDp = textPx * GEIST_ADVANCE_EM / density;
        return Math.max(6, (int) ((widthDp - chromeDp) / charDp));
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

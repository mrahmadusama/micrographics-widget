package com.ahmad.micrographics;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Instances;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Today's birthdays: all-day events whose title or calendar mentions "birthday", which covers
 * Google's contact birthdays calendar and birthdays people add by hand.
 */
final class Birthdays {
    private static final long DAY = 24 * 60 * 60 * 1000L;
    private static final String[] COLUMNS = {
            Instances.TITLE, Instances.BEGIN, Instances.END, Instances.CALENDAR_DISPLAY_NAME,
    };

    private Birthdays() {}

    /** Titles of today's birthday events, in calendar order. Empty without calendar access. */
    static List<String> today(Context c, long now) {
        List<String> titles = new ArrayList<>();
        if (!Meeting.hasPermission(c)) return titles;

        // All-day events are stored as UTC midnights, so match them against today's date in UTC.
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(now);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        long todayUtc = utc.getTimeInMillis();

        Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, todayUtc - DAY);
        ContentUris.appendId(uri, todayUtc + 2 * DAY);
        String where = Instances.ALL_DAY + " = 1 AND " + Instances.VISIBLE + " = 1";
        try (Cursor cur = c.getContentResolver().query(uri.build(), COLUMNS, where, null,
                Instances.BEGIN + " ASC")) {
            if (cur == null) return titles;
            while (cur.moveToNext()) {
                String title = cur.getString(0);
                if (title == null || title.isEmpty()) continue;
                if (cur.getLong(1) > todayUtc || cur.getLong(2) <= todayUtc) continue;
                String calendar = cur.getString(3);
                if (mentionsBirthday(title) || (calendar != null && mentionsBirthday(calendar))) {
                    titles.add(title);
                }
            }
        } catch (SecurityException e) {
            // Permission revoked between the check and the query.
        }
        return titles;
    }

    private static boolean mentionsBirthday(String s) {
        return s.toLowerCase(Locale.ROOT).contains("birthday");
    }
}

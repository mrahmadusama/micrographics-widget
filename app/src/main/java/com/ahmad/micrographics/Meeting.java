package com.ahmad.micrographics;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Instances;

/** The next (or current) timed event from the user's visible calendars. */
final class Meeting {
    /** How far ahead to look for a meeting to show. */
    static final long LOOKAHEAD_MS = 12 * 60 * 60 * 1000L;

    private static final String[] COLUMNS = {
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
    };

    final long eventId;
    final String title;
    final long begin;
    final long end;

    private Meeting(long eventId, String title, long begin, long end) {
        this.eventId = eventId;
        this.title = title;
        this.begin = begin;
        this.end = end;
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns the earliest event that hasn't ended, skipping all-day and declined events, or null. */
    static Meeting next(Context c, long now) {
        if (!hasPermission(c)) return null;
        Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, now);
        ContentUris.appendId(uri, now + LOOKAHEAD_MS);
        String where = Instances.ALL_DAY + " = 0 AND " + Instances.VISIBLE + " = 1 AND "
                + Instances.END + " > ? AND " + Instances.SELF_ATTENDEE_STATUS + " != ?";
        String[] args = {String.valueOf(now), String.valueOf(Attendees.ATTENDEE_STATUS_DECLINED)};
        try (Cursor cur = c.getContentResolver().query(uri.build(), COLUMNS, where, args,
                Instances.BEGIN + " ASC")) {
            if (cur == null || !cur.moveToFirst()) return null;
            String title = cur.getString(1);
            return new Meeting(cur.getLong(0), title == null || title.isEmpty() ? "Event" : title,
                    cur.getLong(2), cur.getLong(3));
        } catch (SecurityException e) {
            return null;
        }
    }
}

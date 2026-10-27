package com.ahmad.micrographics;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.CalendarContract;

/** Redraws the widget soon after any calendar change, so new or moved meetings show up. */
public class CalendarChangeJob extends JobService {
    private static final int JOB_ID = 1;

    static void ensureScheduled(Context c) {
        if (!Meeting.hasPermission(c)) return;
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js.getPendingJob(JOB_ID) != null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, CalendarChangeJob.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2_000)
                .setTriggerContentMaxDelay(15_000)
                .build());
    }

    static void cancel(Context c) {
        c.getSystemService(JobScheduler.class).cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context app = getApplicationContext();
        new Thread(() -> {
            // Content-trigger jobs fire once; refresh() schedules the next one.
            jobFinished(params, false);
            WidgetUpdater.refresh(app);
        }, "micrographics-calendar").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}

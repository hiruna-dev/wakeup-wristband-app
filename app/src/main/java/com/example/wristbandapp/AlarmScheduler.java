package com.example.wristbandapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/**
 * Helper that schedules (or cancels) a wristband alarm using AlarmManager.
 * Uses the alarm's database id as the PendingIntent requestCode so each alarm
 * can be cancelled independently.
 */
public class AlarmScheduler {

    static final String ACTION_ALARM_FIRE = "com.example.wristbandapp.ACTION_ALARM_FIRE";
    static final String EXTRA_ALARM_ID    = "alarm_id";
    static final String EXTRA_ALARM_LABEL = "alarm_label";

    /** Schedule the next occurrence of this alarm (today if still in the future, else tomorrow). */
    public static void schedule(Context ctx, int alarmId, int hour, int minute, String label) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        PendingIntent pi = buildPendingIntent(ctx, alarmId, label);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Fallback to inexact if exact alarm permission not granted
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    /** Cancel a previously scheduled alarm. */
    public static void cancel(Context ctx, int alarmId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(ctx, alarmId, ""));
    }

    private static PendingIntent buildPendingIntent(Context ctx, int alarmId, String label) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.setAction(ACTION_ALARM_FIRE);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);
        intent.putExtra(EXTRA_ALARM_LABEL, label);
        return PendingIntent.getBroadcast(
                ctx, alarmId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

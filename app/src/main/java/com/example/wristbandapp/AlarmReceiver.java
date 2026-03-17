package com.example.wristbandapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Receives the AlarmManager broadcast and tells LocationService to fire the
 * wristband buzzer via BLE.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AlarmScheduler.ACTION_ALARM_FIRE.equals(intent.getAction())) return;

        int    alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1);
        String label   = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL);
        Log.i(TAG, "Alarm fired: id=" + alarmId + " label=" + label);

        DatabaseHelper db = new DatabaseHelper(context);
        db.logAlarmTrigger(alarmId, label, System.currentTimeMillis());

        // Tell LocationService to trigger the wristband buzzer
        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.setAction("ACTION_TRIGGER_ALARM");
        serviceIntent.putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}

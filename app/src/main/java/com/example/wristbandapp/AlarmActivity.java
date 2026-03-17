package com.example.wristbandapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * AlarmActivity – Set Wristband Alarm screen.
 * Layout: activity_alarm.xml
 *
 * Returns alarm data to MainActivity via setResult so it can be persisted and
 * shown
 * in the dashboard list.
 */
public class AlarmActivity extends AppCompatActivity {

    // Which vibration segment is currently selected
    private String selectedVibration = "Medium";

    // Tracks which day TextViews are selected
    private final boolean[] daySelected = new boolean[7];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String savedTheme = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("app_theme", "orange");
        if ("teal".equals(savedTheme)) setTheme(R.style.Theme_WristbandApp_Teal);
        else if ("purple".equals(savedTheme)) setTheme(R.style.Theme_WristbandApp_Purple);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Back button
        android.view.View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // TimePicker (12-hour)
        TimePicker timePicker = findViewById(R.id.timePicker);
        if (timePicker != null) {
            timePicker.setIs24HourView(false);
        }

        // Vibration segmented control
        setupVibrationPicker();

        // Day-of-week toggles
        int[] dayIds = {
                R.id.daySun, R.id.dayMon, R.id.dayTue, R.id.dayWed,
                R.id.dayThu, R.id.dayFri, R.id.daySat
        };
        for (int i = 0; i < dayIds.length; i++) {
            final int idx = i;
            TextView tv = findViewById(dayIds[i]);
            if (tv != null) {
                tv.setOnClickListener(v -> toggleDay((TextView) v, idx));
            }
        }

        // Set Alarm – save and return result to MainActivity
        Button btnSetAlarm = findViewById(R.id.btnSetAlarm);
        if (btnSetAlarm != null) {
            btnSetAlarm.setOnClickListener(v -> {
                int hour = timePicker != null ? timePicker.getHour() : 7;
                int minute = timePicker != null ? timePicker.getMinute() : 0;

                String label = "";
                com.google.android.material.textfield.TextInputEditText et = findViewById(R.id.etAlarmLabel);
                if (et != null && et.getText() != null) {
                    label = et.getText().toString().trim();
                }
                if (label.isEmpty())
                    label = "Alarm";

                String repeatDays = buildRepeatDaysString(dayIds);

                Intent result = new Intent();
                result.putExtra("label", label);
                result.putExtra("hour", hour);
                result.putExtra("minute", minute);
                result.putExtra("repeatDays", repeatDays);
                result.putExtra("vibration", selectedVibration);
                setResult(RESULT_OK, result);
                finish();
            });
        }
    }

    // ── Vibration picker ───────────────────────────────────────

    private void setupVibrationPicker() {
        TextView vibLow = findViewById(R.id.vibLow);
        TextView vibMedium = findViewById(R.id.vibMedium);
        TextView vibHigh = findViewById(R.id.vibHigh);

        if (vibLow == null || vibMedium == null || vibHigh == null)
            return;

        vibLow.setOnClickListener(v -> selectVibration("Low", vibLow, vibMedium, vibHigh));
        vibMedium.setOnClickListener(v -> selectVibration("Medium", vibLow, vibMedium, vibHigh));
        vibHigh.setOnClickListener(v -> selectVibration("High", vibLow, vibMedium, vibHigh));
    }

    private void selectVibration(String level, TextView low, TextView medium, TextView high) {
        selectedVibration = level;
        resetVibView(low, "Low");
        resetVibView(medium, "Medium");
        resetVibView(high, "High");
        TextView selected = level.equals("Low") ? low : level.equals("High") ? high : medium;
        selected.setBackgroundColor(getColor(R.color.purple_primary));
        selected.setTextColor(getColor(R.color.white));
    }

    private void resetVibView(TextView tv, String label) {
        tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tv.setTextColor(getColor(R.color.purple_primary));
    }

    // ── Day toggles ────────────────────────────────────────────

    private void toggleDay(TextView dayView, int idx) {
        daySelected[idx] = !daySelected[idx];
        if (daySelected[idx]) {
            dayView.setBackgroundResource(R.drawable.shape_circle_filled);
            dayView.setTextColor(getColor(R.color.white));
        } else {
            dayView.setBackgroundResource(R.drawable.shape_circle_outline);
            dayView.setTextColor(getColor(R.color.text_tertiary));
        }
    }

    private String buildRepeatDaysString(int[] dayIds) {
        String[] labels = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if (daySelected[i])
                selected.add(labels[i]);
        }
        return selected.isEmpty() ? "Once" : String.join(", ", selected);
    }
}

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
        else if ("blue".equals(savedTheme)) setTheme(R.style.Theme_WristbandApp_Blue);

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
                // Initialize background correctly for current theme
                updateDayBackground(tv, false);
                tv.setOnClickListener(v -> toggleDay((TextView) v, idx));
            }
        }

        // Restore alarm properties if editing
        Intent intent = getIntent();
        final int alarmId = intent.getIntExtra("id", -1);
        if (alarmId != -1) {
            if (timePicker != null) {
                timePicker.setHour(intent.getIntExtra("hour", 7));
                timePicker.setMinute(intent.getIntExtra("minute", 0));
            }
            com.google.android.material.textfield.TextInputEditText et = findViewById(R.id.etAlarmLabel);
            if (et != null) et.setText(intent.getStringExtra("label"));

            String vib = intent.getStringExtra("vibration");
            if (vib != null) {
                selectVibration(vib, findViewById(R.id.vibLow), findViewById(R.id.vibMedium), findViewById(R.id.vibHigh));
            }

            String repeatDays = intent.getStringExtra("repeatDays");
            if (repeatDays != null && !repeatDays.equals("Once")) {
                String[] days = repeatDays.split(",");
                for (String d : days) {
                    d = d.trim();
                    if (d.equals("Sun")) toggleDay(findViewById(R.id.daySun), 0);
                    else if (d.equals("Mon")) toggleDay(findViewById(R.id.dayMon), 1);
                    else if (d.equals("Tue")) toggleDay(findViewById(R.id.dayTue), 2);
                    else if (d.equals("Wed")) toggleDay(findViewById(R.id.dayWed), 3);
                    else if (d.equals("Thu")) toggleDay(findViewById(R.id.dayThu), 4);
                    else if (d.equals("Fri")) toggleDay(findViewById(R.id.dayFri), 5);
                    else if (d.equals("Sat")) toggleDay(findViewById(R.id.daySat), 6);
                }
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
                result.putExtra("id", alarmId);
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

    private int getThemeColor() {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(R.attr.colorAppPrimary, typedValue, true);
        return typedValue.data;
    }

    private void selectVibration(String level, TextView low, TextView medium, TextView high) {
        selectedVibration = level;
        resetVibView(low, "Low");
        resetVibView(medium, "Medium");
        resetVibView(high, "High");
        TextView selected = level.equals("Low") ? low : level.equals("High") ? high : medium;
        selected.setBackgroundColor(getThemeColor());
        selected.setTextColor(getColor(R.color.white));
    }

    private void resetVibView(TextView tv, String label) {
        tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tv.setTextColor(getThemeColor());
    }

    // ── Day toggles ────────────────────────────────────────────

    private void toggleDay(TextView dayView, int idx) {
        daySelected[idx] = !daySelected[idx];
        updateDayBackground(dayView, daySelected[idx]);
    }

    private void updateDayBackground(TextView dayView, boolean isSelected) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        int themeColor = getThemeColor();

        if (isSelected) {
            drawable.setColor(themeColor);
            dayView.setBackground(drawable);
            dayView.setTextColor(getColor(R.color.white));
        } else {
            drawable.setColor(android.graphics.Color.TRANSPARENT);
            drawable.setStroke((int)(1.5f * getResources().getDisplayMetrics().density), themeColor);
            dayView.setBackground(drawable);
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

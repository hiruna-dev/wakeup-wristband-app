package com.example.wristbandapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enforce Theme BEFORE super.onCreate
        boolean isDarkTheme = getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("theme_dark", false);
        if (isDarkTheme) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_DarkActionBar);
        } else {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_DarkActionBar);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this);

        setupRadiusSlider();
        setupVibrationSpinner();
        setupThemeToggle(isDarkTheme);
        setupClearHistory();
        setupBottomNav();
    }

    private void setupRadiusSlider() {
        Slider slider = findViewById(R.id.sliderDefaultRadius);
        TextView tvLabel = findViewById(R.id.tvDefaultRadiusLabel);

        float savedRadius = prefs.getFloat("default_radius", 50f);
        slider.setValue(savedRadius);
        tvLabel.setText("Default Radius: " + (int) savedRadius + "m");

        slider.addOnChangeListener((sl, value, fromUser) -> {
            tvLabel.setText("Default Radius: " + (int) value + "m");
            prefs.edit().putFloat("default_radius", value).apply();
        });
    }

    private void setupVibrationSpinner() {
        Spinner spinner = findViewById(R.id.spinnerDefaultVibration);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Low", "Medium", "High"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String savedVib = prefs.getString("default_vibration", "Medium");
        int spinnerPosition = adapter.getPosition(savedVib);
        spinner.setSelection(spinnerPosition);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                prefs.edit().putString("default_vibration", selected).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupThemeToggle(boolean isCurrentlyDark) {
        SwitchMaterial themeSwitch = findViewById(R.id.switchTheme);
        themeSwitch.setChecked(isCurrentlyDark);

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("theme_dark", isChecked).apply();
            // Optional: Recreate activity instantly upon toggle
            recreate();
        });
    }

    private void setupClearHistory() {
        Button btnClear = findViewById(R.id.btnClearHistory);
        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to permanently delete all Analytics and Heatmap data? Saved locations and alarms will NOT be deleted.")
                .setPositiveButton("Clear Everything", (dialog, which) -> {
                    dbHelper.clearAllHistory();
                    Toast.makeText(this, "Analytics history cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void setupBottomNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.navMap).setOnClickListener(v -> {
            startActivity(new Intent(this, MapPickerActivity.class));
            finish();
        });
        findViewById(R.id.navAnalytics).setOnClickListener(v -> {
            startActivity(new Intent(this, AnalyticsActivity.class));
            finish();
        });
        // navSettings does nothing (already here)
    }
}

package com.example.wristbandapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;

    // Vibration segment views
    private TextView defaultVibLow, defaultVibMedium, defaultVibHigh;
    private String selectedVibration = "Medium";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme before inflate
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedTheme = prefs.getString("app_theme", "orange");
        if ("teal".equals(savedTheme)) {
            setTheme(R.style.Theme_WristbandApp_Teal);
        } else if ("purple".equals(savedTheme)) {
            setTheme(R.style.Theme_WristbandApp_Purple);
        } else if ("blue".equals(savedTheme)) {
            setTheme(R.style.Theme_WristbandApp_Blue);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        dbHelper = new DatabaseHelper(this);

        setupRadiusSlider();
        setupVibrationSegment();
        setupThemeButtons();
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

    private void setupVibrationSegment() {
        defaultVibLow    = findViewById(R.id.defaultVibLow);
        defaultVibMedium = findViewById(R.id.defaultVibMedium);
        defaultVibHigh   = findViewById(R.id.defaultVibHigh);

        selectedVibration = prefs.getString("default_vibration", "Medium");
        refreshVibUI();

        defaultVibLow.setOnClickListener(v    -> selectVib("Low"));
        defaultVibMedium.setOnClickListener(v -> selectVib("Medium"));
        defaultVibHigh.setOnClickListener(v   -> selectVib("High"));
    }

    private void selectVib(String level) {
        selectedVibration = level;
        prefs.edit().putString("default_vibration", level).apply();
        refreshVibUI();
    }

    private void refreshVibUI() {
        TextView[] views = {defaultVibLow, defaultVibMedium, defaultVibHigh};
        String[] labels  = {"Low", "Medium", "High"};
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(R.attr.colorAppPrimary, typedValue, true);
        int colorActive  = typedValue.data;
        int colorInactive = typedValue.data;

        for (int i = 0; i < views.length; i++) {
            if (views[i] == null) continue;
            if (labels[i].equals(selectedVibration)) {
                views[i].setBackgroundColor(colorActive);
                views[i].setTextColor(getColor(R.color.white));
            } else {
                views[i].setBackgroundColor(android.graphics.Color.TRANSPARENT);
                views[i].setTextColor(colorInactive);
            }
        }
    }

    private void setupThemeButtons() {
        String currentTheme = prefs.getString("app_theme", "orange");

        View btnOrange = findViewById(R.id.btnThemeOrange);
        View btnTeal   = findViewById(R.id.btnThemeTeal);
        View btnPurple = findViewById(R.id.btnThemePurple);
        View btnBlue   = findViewById(R.id.btnThemeBlue);

        // Highlight active theme with a border/elevation boost
        View activeView;
        if ("teal".equals(currentTheme)) activeView = btnTeal;
        else if ("purple".equals(currentTheme)) activeView = btnPurple;
        else if ("blue".equals(currentTheme)) activeView = btnBlue;
        else activeView = btnOrange;

        activeView.setElevation(8f);
        // Add a check mark to currently active tile
        if (activeView instanceof LinearLayout) {
            ((LinearLayout) activeView).setAlpha(1f);
        }

        btnOrange.setOnClickListener(v -> applyTheme("orange"));
        btnTeal.setOnClickListener(v   -> applyTheme("teal"));
        btnPurple.setOnClickListener(v -> applyTheme("purple"));
        btnBlue.setOnClickListener(v   -> applyTheme("blue"));
    }

    private void applyTheme(String theme) {
        if (theme.equals(prefs.getString("app_theme", "orange"))) {
            Toast.makeText(this, "Already using this theme", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("app_theme", theme).apply();
        // Restart app to apply theme across all activities
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(0, 0); // Remove animation for a smoother swap
    }

    private void setupClearHistory() {
        Button btnClear = findViewById(R.id.btnClearHistory);
        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Permanently delete all Analytics and Heatmap data? Saved Locations and Alarms are NOT deleted.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    dbHelper.clearAllHistory();
                    Toast.makeText(this, "Analytics history cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void setupBottomNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            overridePendingTransition(0, 0);
            finish();
        });
        findViewById(R.id.navMap).setOnClickListener(v -> {
            startActivity(new Intent(this, MapPickerActivity.class));
            overridePendingTransition(0, 0);
            finish();
        });
        findViewById(R.id.navAnalytics).setOnClickListener(v -> {
            startActivity(new Intent(this, AnalyticsActivity.class));
            overridePendingTransition(0, 0);
            finish();
        });
        // navSettings is the current screen — no action
    }
}

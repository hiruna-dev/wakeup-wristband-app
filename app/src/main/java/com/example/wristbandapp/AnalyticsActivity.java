package com.example.wristbandapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class AnalyticsActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        databaseHelper = new DatabaseHelper(this);

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAnalyticsData();
    }

    private void loadAnalyticsData() {
        // 1. Total Alerts & Heatmap
        TextView tvTotalAlerts = findViewById(R.id.tvTotalAlerts);
        int totalAlerts = databaseHelper.getRecentAlertCount();
        tvTotalAlerts.setText(totalAlerts + " Total");

        LinearLayout chartContainer = findViewById(R.id.chartContainer);
        if (chartContainer != null) {
            chartContainer.removeAllViews();
            int[][] weeklyData = databaseHelper.getWeeklyActivity();

            int max = 0;
            for (int[] day : weeklyData) {
                for (int v : day) {
                    if (v > max)
                        max = v;
                }
            }

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_YEAR, -6);
            String[] dayLabels = { "S", "M", "T", "W", "T", "F", "S" };
            float density = getResources().getDisplayMetrics().density;

            // 1. Y-axis Hour Labels Column
            LinearLayout yCol = new LinearLayout(this);
            yCol.setOrientation(LinearLayout.VERTICAL);
            yCol.setGravity(android.view.Gravity.TOP); // Top-down
            LinearLayout.LayoutParams yParams = new LinearLayout.LayoutParams(
                    (int) (26 * density), LinearLayout.LayoutParams.MATCH_PARENT);
            yCol.setLayoutParams(yParams);

            for (int h = 0; h < 24; h++) {
                TextView tv = new TextView(this);
                if (h == 0)
                    tv.setText("12a");
                else if (h == 6)
                    tv.setText("6a");
                else if (h == 12)
                    tv.setText("12p");
                else if (h == 18)
                    tv.setText("6p");

                tv.setTextSize(7f); // Slightly smaller to prevent clipping
                tv.setTextColor(0xFF9CA3AF);
                tv.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
                tv.setPadding(0, 0, (int) (4 * density), 0);
                LinearLayout.LayoutParams tvP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                tv.setLayoutParams(tvP);
                yCol.addView(tv);
            }

            // Legend "Hr" at the bottom of the Y-axis
            TextView hrLegend = new TextView(this);
            hrLegend.setText("Hr");
            hrLegend.setTextSize(9f);
            hrLegend.setTextColor(0xFF6B7280); // text_secondary
            hrLegend.setGravity(android.view.Gravity.END);
            hrLegend.setPadding(0, (int) (4 * density), (int) (4 * density), 0);
            yCol.addView(hrLegend);

            chartContainer.addView(yCol);

            // 2. The 7 Columns
            for (int i = 0; i < 7; i++) {
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                col.setLayoutParams(colParams);

                for (int h = 0; h < 24; h++) {
                    int val = weeklyData[i][h];
                    View box = new View(this);
                    LinearLayout.LayoutParams boxP = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                    boxP.setMargins((int) (1 * density), (int) (1 * density), (int) (1 * density), (int) (1 * density));
                    box.setLayoutParams(boxP);

                    if (val == 0) {
                        box.setBackgroundColor(0xFFF3F4F6); // Light gray / off-white for empty blocks
                    } else {
                        box.setBackgroundColor(0xFF16A34A);
                        box.setAlpha(Math.max(0.3f, (float) val / max));
                    }
                    col.addView(box);
                }

                TextView tv = new TextView(this);
                int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;
                tv.setText(dayLabels[dayOfWeek]);
                tv.setTextSize(9f);
                tv.setTextColor(0xFF9CA3AF);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setPadding(0, (int) (4 * density), 0, 0);
                col.addView(tv);

                chartContainer.addView(col);
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }

            // Legend "Days" at the bottom right of the X-axis
            LinearLayout legendCol = new LinearLayout(this);
            legendCol.setOrientation(LinearLayout.VERTICAL);
            legendCol.setGravity(android.view.Gravity.BOTTOM);
            LinearLayout.LayoutParams legParams = new LinearLayout.LayoutParams(
                    (int) (26 * density), LinearLayout.LayoutParams.MATCH_PARENT);
            legendCol.setLayoutParams(legParams);

            TextView daysLegend = new TextView(this);
            daysLegend.setText("Days");
            daysLegend.setTextSize(9f);
            daysLegend.setTextColor(0xFF6B7280);
            daysLegend.setGravity(android.view.Gravity.START);
            daysLegend.setPadding((int) (4 * density), 0, 0, 0);
            legendCol.addView(daysLegend);

            chartContainer.addView(legendCol);
        }

        // 2. Top Locations
        LinearLayout containerTopLocations = findViewById(R.id.containerTopLocations);
        containerTopLocations.removeAllViews();
        List<String[]> topLocs = databaseHelper.getTopLocations();
        if (topLocs.isEmpty()) {
            addEmptyState(containerTopLocations, "No location data yet");
        } else {
            for (String[] row : topLocs) {
                addRow(containerTopLocations, row[0], row[1]);
            }
        }

        // 3. Average Durations
        LinearLayout containerAvgTime = findViewById(R.id.containerAvgTime);
        containerAvgTime.removeAllViews();
        List<String[]> avgDurs = databaseHelper.getAverageDurations();
        if (avgDurs.isEmpty()) {
            addEmptyState(containerAvgTime, "No duration data yet");
        } else {
            for (String[] row : avgDurs) {
                addRow(containerAvgTime, row[0], row[1]);
            }
        }
    }

    private void addRow(LinearLayout container, String name, String value) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_analytics_row, container, false);
        TextView tvName = view.findViewById(R.id.tvRowName);
        TextView tvValue = view.findViewById(R.id.tvRowValue);
        tvName.setText(name);
        tvValue.setText(value);
        container.addView(view);
    }

    private void addEmptyState(LinearLayout container, String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(getResources().getColor(R.color.text_tertiary, null));
        tv.setTextSize(14f);
        tv.setPadding(0, 8, 0, 8);
        container.addView(tv);
    }

    private void setupBottomNavigation() {
        View navHome = findViewById(R.id.navHome);
        View navMap = findViewById(R.id.navMap);

        navHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            // clear top so we don't stack activities endlessly
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        navMap.setOnClickListener(v -> {
            Intent intent = new Intent(this, MapPickerActivity.class);
            startActivity(intent);
            finish();
        });
    }
}

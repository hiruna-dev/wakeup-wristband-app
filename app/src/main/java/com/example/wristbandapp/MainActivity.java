package com.example.wristbandapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private DashboardAdapter adapter;
    private List<DashboardItem> dashboardItems = new ArrayList<>();

    /** Launcher for MapPickerActivity – adds a saved location on RESULT_OK */
    private ActivityResultLauncher<Intent> mapPickerLauncher;

    /** Launcher for AlarmActivity – saves a new alarm on RESULT_OK */
    private ActivityResultLauncher<Intent> alarmLauncher;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private final BroadcastReceiver bleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean connected = intent.getBooleanExtra("connected", false);
            TextView tvBleStatus = findViewById(R.id.tvBleStatus);
            if (tvBleStatus != null) {
                tvBleStatus.setText(connected ? "Connected" : "Disconnected");
                tvBleStatus.setTextColor(connected ? 0xFF16A34A : 0xFFE65C4F);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme before inflate
        String savedTheme = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("app_theme", "orange");
        if ("teal".equals(savedTheme)) {
            setTheme(R.style.Theme_WristbandApp_Teal);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        databaseHelper = new DatabaseHelper(this);

        // ── Result launchers ──────────────────────────────────

        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String name = data.getStringExtra("name");
                        double lat = data.getDoubleExtra("lat", 0);
                        double lng = data.getDoubleExtra("lng", 0);
                        float radius = data.getFloatExtra("radius", 50);
                        databaseHelper.insertLocation(name, lat, lng, radius);
                        loadDashboard();
                    }
                });

        alarmLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String label = data.getStringExtra("label");
                        int hour = data.getIntExtra("hour", 7);
                        int minute = data.getIntExtra("minute", 0);
                        String repeatDays = data.getStringExtra("repeatDays");
                        String vibration = data.getStringExtra("vibration");
                        int alarmId = (int) databaseHelper.insertAlarm(label, hour, minute, repeatDays, vibration);
                        AlarmScheduler.schedule(this, alarmId, hour, minute, label);
                        loadDashboard();
                    }
                });

        // ── RecyclerView ───────────────────────────────────────

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DashboardAdapter(dashboardItems, item -> {
            if (item.type == DashboardItem.TYPE_ALARM) {
                AlarmScheduler.cancel(this, item.id);
                databaseHelper.deleteAlarm(item.id);
            } else {
                databaseHelper.deleteLocation(item.id);
            }
            loadDashboard();
        });
        recyclerView.setAdapter(adapter);

        // ── Button wiring ──────────────────────────────────────

        findViewById(R.id.btnConnect).setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.setAction("ACTION_SCAN_BLE");
            startService(serviceIntent);
            Toast.makeText(this, "Scanning for Wristband...", Toast.LENGTH_SHORT).show();
        });

        SwitchMaterial alertSwitch = findViewById(R.id.btnTest);
        if (alertSwitch != null) {
            // Fire alert immediately on startup (switch is ON by default)
            Intent enableIntent = new Intent(this, LocationService.class);
            enableIntent.setAction("ACTION_ENABLE_ALERT");
            startService(enableIntent);

            View alertCard = findViewById(R.id.cardEmergencyAlert);

            // Set initial state
            if (alertCard != null) {
                alertCard.setAlpha(alertSwitch.isChecked() ? 1.0f : 0.5f);
            }

            alertSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (alertCard != null) {
                    alertCard.setAlpha(isChecked ? 1.0f : 0.5f);
                }
                Intent serviceIntent = new Intent(this, LocationService.class);
                serviceIntent.setAction(isChecked ? "ACTION_ENABLE_ALERT" : "ACTION_TURN_OFF_LED");
                startService(serviceIntent);
                Toast.makeText(this,
                        isChecked ? "Alert System ON" : "Alert System OFF",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // FAB opens AlarmActivity (uses result launcher to catch saved alarm)
        findViewById(R.id.fabAdd)
                .setOnClickListener(v -> alarmLauncher.launch(new Intent(MainActivity.this, AlarmActivity.class)));

        // Map nav opens MapPickerActivity (via launcher so RESULT_OK is received)
        findViewById(R.id.navMap).setOnClickListener(
                v -> mapPickerLauncher.launch(new Intent(MainActivity.this, MapPickerActivity.class)));

        checkPermissions();
        loadDashboard();
        startLocationService();

        View navAnalytics = findViewById(R.id.navAnalytics);
        if (navAnalytics != null) {
            navAnalytics.setOnClickListener(v -> {
                startActivity(new Intent(this, AnalyticsActivity.class));
            });
        }

        View navSettings = findViewById(R.id.navSettings);
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }
    }

    // ── Load combined dashboard list ───────────────────────────

    private void loadDashboard() {
        dashboardItems.clear();

        // Saved locations first
        for (LocationItem loc : databaseHelper.getAllLocations()) {
            dashboardItems.add(new DashboardItem(
                    loc.id, loc.name, loc.latitude, loc.longitude, loc.radiusMeters));
        }

        // Then alarms
        dashboardItems.addAll(databaseHelper.getAllAlarms());

        adapter.setItems(dashboardItems);
    }

    // ── Lifecycle ──────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.registerReceiver(this, bleReceiver, new IntentFilter("BLE_STATUS"),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bleReceiver, new IntentFilter("BLE_STATUS"));
        }
        Intent intent = new Intent(this, LocationService.class);
        intent.setAction("ACTION_GET_BLE_STATUS");
        startService(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(bleReceiver);
    }

    // ── Service & Permissions ──────────────────────────────────

    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        List<String> needed = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            checkBackgroundLocationPermission();
        }
    }

    private void checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.ACCESS_BACKGROUND_LOCATION },
                        PERMISSION_REQUEST_CODE + 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions required for app to function.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
            checkBackgroundLocationPermission();
        }
    }
}

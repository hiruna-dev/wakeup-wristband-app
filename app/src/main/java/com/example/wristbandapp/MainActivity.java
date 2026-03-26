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
        } else if ("purple".equals(savedTheme)) {
            setTheme(R.style.Theme_WristbandApp_Purple);
        } else if ("blue".equals(savedTheme)) {
            setTheme(R.style.Theme_WristbandApp_Blue);
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
                        int id = data.getIntExtra("id", -1);
                        String label = data.getStringExtra("label");
                        int hour = data.getIntExtra("hour", 7);
                        int minute = data.getIntExtra("minute", 0);
                        String repeatDays = data.getStringExtra("repeatDays");
                        String vibration = data.getStringExtra("vibration");
                        
                        if (id == -1) {
                            int alarmId = (int) databaseHelper.insertAlarm(label, hour, minute, repeatDays, vibration);
                            AlarmScheduler.schedule(MainActivity.this, alarmId, hour, minute, label);
                        } else {
                            databaseHelper.updateAlarm(id, label, hour, minute, repeatDays, vibration);
                            AlarmScheduler.schedule(MainActivity.this, id, hour, minute, label);
                        }
                        loadDashboard();
                    }
                });

        // ── RecyclerView ───────────────────────────────────────

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DashboardAdapter(dashboardItems, new DashboardAdapter.OnItemClickListener() {
            @Override
            public void onDeleteClick(DashboardItem item) {
                if (item.type == DashboardItem.TYPE_ALARM) {
                    AlarmScheduler.cancel(MainActivity.this, item.id);
                    databaseHelper.deleteAlarm(item.id);
                } else {
                    databaseHelper.deleteLocation(item.id);
                }
                loadDashboard();
            }

            @Override
            public void onEditClick(DashboardItem item) {
                if (item.type == DashboardItem.TYPE_ALARM) {
                    Intent intent = new Intent(MainActivity.this, AlarmActivity.class);
                    intent.putExtra("id", item.id);
                    intent.putExtra("label", item.name);
                    intent.putExtra("hour", item.hour);
                    intent.putExtra("minute", item.minute);
                    intent.putExtra("repeatDays", item.repeatDays);
                    intent.putExtra("vibration", item.vibration);
                    alarmLauncher.launch(intent);
                }
            }
        });
        recyclerView.setAdapter(adapter);

        // ── Button wiring ──────────────────────────────────────

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setOnClickListener(new View.OnClickListener() {
                int tapCount = 0;
                long lastTapTime = 0;
                @Override
                public void onClick(View v) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastTapTime > 1000) tapCount = 0;
                    lastTapTime = currentTime;
                    tapCount++;
                    if (tapCount == 7) {
                        databaseHelper.seedDummyData();
                        Toast.makeText(MainActivity.this, "Analytics DB populated!", Toast.LENGTH_SHORT).show();
                        loadDashboard();
                        tapCount = 0;
                    }
                }
            });
        }

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
                v -> {
                    mapPickerLauncher.launch(new Intent(MainActivity.this, MapPickerActivity.class));
                    overridePendingTransition(0, 0);
                });

        checkPermissions();
        loadDashboard();
        startLocationService();
        checkWeatherAndNotify();

        View navAnalytics = findViewById(R.id.navAnalytics);
        if (navAnalytics != null) {
            navAnalytics.setOnClickListener(v -> {
                startActivity(new Intent(this, AnalyticsActivity.class));
                overridePendingTransition(0, 0);
            });
        }

        View navSettings = findViewById(R.id.navSettings);
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(0, 0);
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
        fetchDashboardWeather();
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

    // ── Weather Suggestion Feature ──────────────────────────────

    private void fetchDashboardWeather() {
        new Thread(() -> {
            boolean updated = false;
            for (DashboardItem item : dashboardItems) {
                if (item.type == DashboardItem.TYPE_LOCATION) {
                    try {
                        String key = "c037a0a6a87f46faa74162912262503";
                        String urlStr = "https://api.weatherapi.com/v1/current.json?key=" + key + "&q=" + item.latitude + "," + item.longitude;
                        java.net.URL url = new java.net.URL(urlStr);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(3000);
                        
                        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) response.append(line);
                        in.close();
                        
                        org.json.JSONObject json = new org.json.JSONObject(response.toString());
                        org.json.JSONObject current = json.getJSONObject("current");
                        double tempC = current.getDouble("temp_c");
                        String condition = current.getJSONObject("condition").getString("text");
                        
                        item.weatherStatus = String.format(java.util.Locale.getDefault(), "%.1f\u00B0C, %s", tempC, condition);
                        updated = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        item.weatherStatus = "Weather: N/A";
                        updated = true;
                    }
                }
            }
            if (updated) {
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }
        }).start();
    }

    private void checkWeatherAndNotify() {
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        long lastCheck = prefs.getLong("last_weather_check", 0);
        long today = System.currentTimeMillis() / (1000L * 60 * 60 * 24);
        if (lastCheck == today) return; 

        new Thread(() -> {
            try {
                List<LocationItem> locs = databaseHelper.getAllLocations();
                if (locs.isEmpty()) return;
                LocationItem topLoc = locs.get(0); 
                
                String key = "c037a0a6a87f46faa74162912262503";
                String urlStr = "https://api.weatherapi.com/v1/forecast.json?key=" + key + "&q=" 
                                + topLoc.latitude + "," + topLoc.longitude + "&days=1";
                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();
                
                org.json.JSONObject json = new org.json.JSONObject(response.toString());
                org.json.JSONArray forecastDays = json.getJSONObject("forecast").getJSONArray("forecastday");
                org.json.JSONObject todayObj = forecastDays.getJSONObject(0);
                org.json.JSONArray hours = todayObj.getJSONArray("hour");
                
                String rainTime = null;
                for (int i = 0; i < hours.length(); i++) {
                    org.json.JSONObject hourObj = hours.getJSONObject(i);
                    int willRain = hourObj.optInt("will_it_rain", 0);
                    int willSnow = hourObj.optInt("will_it_snow", 0);
                    if (willRain == 1 || willSnow == 1) {
                        String timeStr = hourObj.getString("time"); 
                        if (timeStr.length() > 11) {
                            rainTime = timeStr.substring(11); // Extract HH:MM
                            break;
                        }
                    }
                }
                
                if (rainTime != null) {
                    sendRainNotification(topLoc.name, rainTime);
                }

                prefs.edit().putLong("last_weather_check", today).apply();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendRainNotification(String locName, String time) {
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "weather_alerts";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                channelId, "Weather Alerts", android.app.NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), 
            android.app.PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Weather Alert \u26C8")
            .setContentText("Precipitation expected today around " + time + " near " + locName + ".")
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent);

        notificationManager.notify(2001, builder.build());
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

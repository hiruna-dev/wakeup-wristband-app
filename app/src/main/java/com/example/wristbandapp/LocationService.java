package com.example.wristbandapp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "LocationServiceChannel";
    private LocationManager locationManager;
    private DatabaseHelper databaseHelper;
    private BleManager bleManager;
    private boolean isLedOn = false;
    private boolean isAlertSystemEnabled = true; // ON by default
    private boolean isAlarmActive = false;

    // Analytics tracking
    private long currentEnteredLocationId = -1;
    private long activeLocationLogId = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        databaseHelper = new DatabaseHelper(this);

        SharedPreferences prefs = getSharedPreferences("WristbandPrefs", MODE_PRIVATE);
        String savedMac = prefs.getString("ESP32_MAC", null);

        bleManager = new BleManager(this, connected -> {
            Log.d(TAG, "BLE Connected: " + connected);
            broadcastBleStatus(connected);
        });

        if (savedMac != null) {
            bleManager.connectToDevice(savedMac);
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void broadcastBleStatus(boolean connected) {
        Intent bcIntent = new Intent("BLE_STATUS");
        bcIntent.setPackage(getPackageName());
        bcIntent.putExtra("connected", connected);
        sendBroadcast(bcIntent);
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Travel Wake Assist Running")
                .setContentText("Monitoring location for alarms...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        if (intent != null) {
            String action = intent.getAction();
            if ("ACTION_SCAN_BLE".equals(action)) {
                if (bleManager != null) {
                    if (bleManager.isConnected()) {
                        broadcastBleStatus(true);
                    } else {
                        bleManager.scanAndConnect();
                    }
                }
            } else if ("ACTION_GET_BLE_STATUS".equals(action)) {
                if (bleManager != null) {
                    broadcastBleStatus(bleManager.isConnected());
                }
            } else if ("ACTION_ENABLE_ALERT".equals(action)) {
                Log.i(TAG, "Alert System ON.");
                isAlertSystemEnabled = true;
                // DO NOT call sendAlert() here! This is just a master toggle.
            } else if ("ACTION_TURN_OFF_LED".equals(action)) {
                Log.i(TAG, "Master Alert System disabled by user.");
                isAlertSystemEnabled = false;

                if (!isAlarmActive) {
                    if (bleManager != null) bleManager.sendOffAlert();
                    isLedOn = false;
                }
                isAlarmActive = false;
            } else if ("ACTION_TRIGGER_ALARM".equals(action)) {
                String label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL);
                Log.i(TAG, "Alarm triggered: " + label + ". Activating wristband buzzer.");
                // When an alarm rings, it guarantees the buzzer stays on
                isAlarmActive = true;
                if (!isLedOn) {
                    if (bleManager != null) bleManager.sendAlert();
                    isLedOn = true;
                }

                // Auto-stop alarm after 20 seconds
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Log.i(TAG, "Alarm auto-stop (20s).");
                    isAlarmActive = false;
                    if (bleManager != null) bleManager.sendOffAlert();
                    isLedOn = false;
                }, 20000); // 20 seconds

            } else if ("ACTION_STOP_ALARM".equals(action)) {
                Log.i(TAG, "Alarm stopped manually.");
                isAlarmActive = false;
                if (bleManager != null) bleManager.sendOffAlert();
                isLedOn = false;
            }
        }

        try {
            // minTimeMs = 5000 (5 seconds)
            // minDistanceM = 0 (Force updates even if perfectly stationary)
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000,
                    0,
                    locationListener);
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000,
                    0,
                    locationListener);
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }

        return START_STICKY;
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.d(TAG, "New Location: " + location.getLatitude() + ", " + location.getLongitude());
            checkDistanceToLocations(location);
        }
    };

    private void checkDistanceToLocations(Location currentLocation) {
        // If an alarm is actively ringing, ignore GPS entirely
        if (isAlarmActive) return;

        List<LocationItem> savedLocations = databaseHelper.getAllLocations();
        long newLocationId = -1;
        String newLocationName = null;

        for (LocationItem item : savedLocations) {
            float[] results = new float[1];
            Location.distanceBetween(
                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                    item.latitude, item.longitude,
                    results);

            if (results[0] <= item.radiusMeters) {
                newLocationId = item.id;
                newLocationName = item.name;
                break;
            }
        }

        // Log Analytics (Independent of Buzzer status and Toggle status)
        if (newLocationId != currentEnteredLocationId) {
            if (currentEnteredLocationId != -1 && activeLocationLogId != -1) {
                databaseHelper.logLocationExit(activeLocationLogId, System.currentTimeMillis());
                activeLocationLogId = -1;
            }
            if (newLocationId != -1) {
                activeLocationLogId = databaseHelper.logLocationEntry(newLocationId, newLocationName, System.currentTimeMillis());
            }
            currentEnteredLocationId = newLocationId;
        }

        boolean nearAny = (newLocationId != -1);

        // If the Master Toggle is OFF, do not trigger the buzzer (but we still logged the analytics above!)
        if (!isAlertSystemEnabled) {
            // Ensure the buzzer turns off if they toggled it off while inside a zone
            if (isLedOn) {
                Log.i(TAG, "System Disabled. Turning OFF ESP32.");
                if (bleManager != null) bleManager.sendOffAlert();
                isLedOn = false;
            }
            return;
        }

        if (nearAny) {
            // We are inside a saved location's radius AND system is enabled
            if (!isLedOn) {
                Log.i(TAG, "Entered radius! Triggering ESP32.");
                if (bleManager != null) bleManager.sendAlert();
                isLedOn = true;
            }
        } else {
            // We are NOT near any saved location.
            if (isLedOn) {
                Log.i(TAG, "Exited all radii. Turning OFF ESP32.");
                if (bleManager != null) bleManager.sendOffAlert();
                isLedOn = false;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException ignored) {
            }
        }
        bleManager.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}

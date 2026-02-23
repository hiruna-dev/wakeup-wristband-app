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
    private boolean isManualOverride = false;

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
                .setContentTitle("Wristband App Running")
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
            } else if ("ACTION_TURN_OFF_LED".equals(action)) {
                if (bleManager != null) {
                    Log.i(TAG, "Manual Stop Triggered. Turning off LED.");
                    bleManager.sendOffAlert();
                    isLedOn = false;
                    isManualOverride = true;
                }
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
        List<LocationItem> savedLocations = databaseHelper.getAllLocations();
        boolean nearAny = false;

        for (LocationItem item : savedLocations) {
            float[] results = new float[1];
            Location.distanceBetween(
                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                    item.latitude, item.longitude,
                    results);

            float distanceInMeters = results[0];
            Log.d(TAG, "Distance to " + item.name + ": " + distanceInMeters + "m");

            if (distanceInMeters <= item.radiusMeters) {
                nearAny = true;
                if (!isLedOn && !isManualOverride) {
                    Log.i(TAG, "Within radius of " + item.name + "! Triggering ESP32.");
                    bleManager.sendAlert();
                    isLedOn = true;
                }
                break;
            }
        }

        if (!nearAny) {
            if (isLedOn) {
                Log.i(TAG, "Exited all radii or deleted target. Turning OFF ESP32.");
                bleManager.sendOffAlert();
                isLedOn = false;
            }
            isManualOverride = false;
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

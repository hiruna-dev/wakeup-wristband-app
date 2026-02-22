package com.example.wristbandapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
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
    private LocationAdapter adapter;
    private List<LocationItem> locationItems = new ArrayList<>();
    private ActivityResultLauncher<Intent> mapPickerLauncher;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private final BroadcastReceiver bleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean connected = intent.getBooleanExtra("connected", false);
            TextView tvBleStatus = findViewById(R.id.tvBleStatus);
            if (tvBleStatus != null) {
                tvBleStatus.setText("BLE: " + (connected ? "Connected" : "Disconnected"));
                tvBleStatus.setTextColor(connected ? 0xFF00AA00 : 0xFFAA0000); // Green if connected, Red if
                                                                               // disconnected
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        databaseHelper = new DatabaseHelper(this);

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
                        loadLocations();
                    }
                });

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LocationAdapter(locationItems, item -> {
            databaseHelper.deleteLocation(item.id);
            loadLocations();
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnConnect).setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.setAction("ACTION_SCAN_BLE");
            startService(serviceIntent);
            Toast.makeText(this, "Scanning for Wristband...", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnTest).setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.setAction("ACTION_TURN_OFF_LED");
            startService(serviceIntent);
            Toast.makeText(this, "Turning LED OFF manually...", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        checkPermissions();
        loadLocations();
        startLocationService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register receiver for all apps internal traffic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.registerReceiver(this, bleReceiver, new IntentFilter("BLE_STATUS"),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bleReceiver, new IntentFilter("BLE_STATUS"));
        }

        // Request current status from service
        Intent intent = new Intent(this, LocationService.class);
        intent.setAction("ACTION_GET_BLE_STATUS");
        startService(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(bleReceiver);
    }

    private void loadLocations() {
        locationItems.clear();
        locationItems.addAll(databaseHelper.getAllLocations());
        adapter.notifyDataSetChanged();
    }

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

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
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
                    Toast.makeText(this, "Permissions required for app to function.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            checkBackgroundLocationPermission();
        }
    }
}

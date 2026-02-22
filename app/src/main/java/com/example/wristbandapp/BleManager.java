package com.example.wristbandapp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.UUID;

public class BleManager {
    private static final String TAG = "BleManager";
    // Standard Nordic UART Service
    private static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private boolean isConnected = false;

    public interface BleCallback {
        void onConnectionStateChange(boolean connected);
    }

    private BleCallback callback;

    public BleManager(Context context, BleCallback callback) {
        this.context = context;
        this.callback = callback;
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            bluetoothAdapter = bm.getAdapter();
        }
    }

    @SuppressLint("MissingPermission")
    public void scanAndConnect() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> android.widget.Toast
                    .makeText(context, "Bluetooth is disabled!", android.widget.Toast.LENGTH_LONG).show());
            return;
        }

        android.location.LocationManager locationManager = (android.location.LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null
                && !locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> android.widget.Toast.makeText(context,
                    "Please turn on device Location (GPS) for BLE scanning!", android.widget.Toast.LENGTH_LONG).show());
            return;
        }

        android.bluetooth.le.BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null. Are permissions granted?");
            return;
        }

        android.bluetooth.le.ScanCallback scanCallback = new android.bluetooth.le.ScanCallback() {
            @Override
            public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                BluetoothDevice device = result.getDevice();

                // Sometimes the name comes back null on Android despite Advertising correctly.
                // We check by Name OR by scanning for the UUID. The filter handles the UUID
                // part.
                if (device != null) {
                    Log.i(TAG, "Found device: " + device.getAddress() + " Name: " + device.getName());
                    scanner.stopScan(this);

                    android.content.SharedPreferences prefs = context.getSharedPreferences("WristbandPrefs",
                            Context.MODE_PRIVATE);
                    prefs.edit().putString("ESP32_MAC", device.getAddress()).apply();

                    connectToDevice(device.getAddress());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "BLE Scan Failed with code: " + errorCode);
            }
        };

        // Aggressive Scan Settings
        android.bluetooth.le.ScanSettings settings = new android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // Filter exclusively for our Service UUID so we don't pick up random
        // TVs/laptops
        java.util.List<android.bluetooth.le.ScanFilter> filters = new java.util.ArrayList<>();
        filters.add(new android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(new android.os.ParcelUuid(SERVICE_UUID))
                .build());

        scanner.startScan(filters, settings, scanCallback);
        Log.i(TAG, "Started aggressive BLE scan for Service UUID...");

        // Stop scan after 30 seconds (longer discovery window)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                scanner.stopScan(scanCallback);
                Log.i(TAG, "Stopped BLE scan after timeout.");
            } catch (Exception ignored) {
            }
        }, 30000);
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(String deviceAddress) {
        if (bluetoothAdapter == null || deviceAddress == null)
            return;
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null)
            return;

        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isConnected = false;
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onConnectionStateChange(false));
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    @SuppressLint("MissingPermission")
    public void sendAlert() {
        if (bluetoothGatt == null || !isConnected) {
            Log.e(TAG, "Cannot send alert: Not connected (gatt=" + bluetoothGatt + ")");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service != null) {
            BluetoothGattCharacteristic charac = service.getCharacteristic(RX_CHAR_UUID);
            if (charac != null) {
                charac.setValue("ON".getBytes());
                bluetoothGatt.writeCharacteristic(charac);
                Log.d(TAG, "SUCCESS! Alert payload 'ON' sent to ESP32");
            } else {
                Log.e(TAG, "ERROR: Found Service, but Characteristic " + RX_CHAR_UUID + " is missing!");
            }
        } else {
            Log.e(TAG, "ERROR: Service " + SERVICE_UUID + " not found on device!");

            // Debug: print what services DO exist
            for (BluetoothGattService s : bluetoothGatt.getServices()) {
                Log.e(TAG, "Available Service UUID: " + s.getUuid().toString());
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void sendOffAlert() {
        if (bluetoothGatt == null || !isConnected) {
            Log.e(TAG, "Cannot send OFF alert: Not connected");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service != null) {
            BluetoothGattCharacteristic charac = service.getCharacteristic(RX_CHAR_UUID);
            if (charac != null) {
                charac.setValue("OFF".getBytes());
                bluetoothGatt.writeCharacteristic(charac);
                Log.d(TAG, "SUCCESS! Alert payload 'OFF' sent to ESP32");
            }
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                Log.i(TAG, "Connected to GATT server.");
                gatt.discoverServices();
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onConnectionStateChange(true));
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                Log.i(TAG, "Disconnected from GATT server.");
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onConnectionStateChange(false));
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered.");
            }
        }
    };
}

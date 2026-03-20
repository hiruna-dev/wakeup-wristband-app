package com.example.wristbandapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.slider.Slider;

import java.util.Arrays;

public class MapPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private LatLng selectedLocation = null;
    private String selectedPlaceName = "";
    private ExtendedFloatingActionButton fabSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String savedTheme = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("app_theme", "orange");
        if ("teal".equals(savedTheme)) setTheme(R.style.Theme_WristbandApp_Teal);
        else if ("purple".equals(savedTheme)) setTheme(R.style.Theme_WristbandApp_Purple);
        else if ("blue".equals(savedTheme)) setTheme(R.style.Theme_WristbandApp_Blue);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        fabSave = findViewById(R.id.fabSave);
        fabSave.setOnClickListener(v -> showSaveDialog());

        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
            if (apiKey != null && !apiKey.isEmpty() && !Places.isInitialized()) {
                Places.initialize(getApplicationContext(), apiKey);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupAutocomplete();

        // Bottom nav: Home goes back to MainActivity
        View navHome = findViewById(R.id.navHome);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            });
        }

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

    private void setupAutocomplete() {
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment) getSupportFragmentManager()
                .findFragmentById(R.id.autocomplete_fragment);

        if (autocompleteFragment != null) {
            autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    if (place.getLatLng() != null) {
                        selectedLocation = place.getLatLng();
                        selectedPlaceName = place.getName();
                        updateMapMarker(selectedLocation, selectedPlaceName);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15f));
                    }
                }

                @Override
                public void onError(@NonNull Status status) {
                    Toast.makeText(MapPickerActivity.this, "Error: " + status.getStatusMessage(), Toast.LENGTH_SHORT)
                            .show();
                }
            });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        // If opened from "View on Map", zoom straight to that location
        Intent incoming = getIntent();
        if (incoming.hasExtra("focusLat") && incoming.hasExtra("focusLng")) {
            double lat = incoming.getDoubleExtra("focusLat", 0);
            double lng = incoming.getDoubleExtra("focusLng", 0);
            String name = incoming.getStringExtra("focusName");
            LatLng focus = new LatLng(lat, lng);
            selectedLocation = focus;
            selectedPlaceName = name != null ? name : "Saved Location";
            updateMapMarker(focus, selectedPlaceName);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 15f));
        } else {
            // Default: start centred on Sri Lanka
            LatLng sriLanka = new LatLng(7.8731, 80.7718);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sriLanka, 7f));
        }

        mMap.setOnMapClickListener(latLng -> {
            selectedLocation = latLng;
            selectedPlaceName = "Custom Location";
            updateMapMarker(latLng, selectedPlaceName);
        });
    }

    private void updateMapMarker(LatLng latLng, String title) {
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(latLng).title(title));
        fabSave.setVisibility(View.VISIBLE);
    }

    private void showSaveDialog() {
        if (selectedLocation == null)
            return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_save_location, null);
        EditText etName = view.findViewById(R.id.etName);
        Slider sliderRadius = view.findViewById(R.id.sliderRadius);
        TextView tvRadiusLabel = view.findViewById(R.id.tvRadiusLabel);

        etName.setText(selectedPlaceName);

        float defaultRadius = getSharedPreferences("AppPrefs", MODE_PRIVATE).getFloat("default_radius", 50f);
        sliderRadius.setValue(defaultRadius);
        tvRadiusLabel.setText("Radius: " + (int) defaultRadius + "m");

        // Update label when slider moves
        sliderRadius.addOnChangeListener((slider, value, fromUser) -> {
            tvRadiusLabel.setText("Radius: " + (int) value + "m");
        });

        builder.setView(view);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString();
            float radius = sliderRadius.getValue();

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent returnIntent = new Intent();
            returnIntent.putExtra("name", name);
            returnIntent.putExtra("lat", selectedLocation.latitude);
            returnIntent.putExtra("lng", selectedLocation.longitude);
            returnIntent.putExtra("radius", radius);
            setResult(RESULT_OK, returnIntent);
            finish();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}

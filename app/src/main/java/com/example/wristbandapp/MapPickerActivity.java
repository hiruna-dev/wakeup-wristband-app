package com.example.wristbandapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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

import java.util.Arrays;

public class MapPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private LatLng selectedLocation = null;
    private String selectedPlaceName = "";
    private ExtendedFloatingActionButton fabSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        EditText etRadius = view.findViewById(R.id.etRadius);

        etName.setText(selectedPlaceName);

        builder.setView(view);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString();
            String radiusStr = etRadius.getText().toString();

            if (name.isEmpty() || radiusStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float radius = Float.parseFloat(radiusStr);
                Intent returnIntent = new Intent();
                returnIntent.putExtra("name", name);
                returnIntent.putExtra("lat", selectedLocation.latitude);
                returnIntent.putExtra("lng", selectedLocation.longitude);
                returnIntent.putExtra("radius", radius);
                setResult(RESULT_OK, returnIntent);
                finish();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid radius", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}

package com.example.wristbandapp;

public class LocationItem {
    public int id;
    public String name;
    public double latitude;
    public double longitude;
    public float radiusMeters;

    public LocationItem(int id, String name, double latitude, double longitude, float radiusMeters) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
    }
}

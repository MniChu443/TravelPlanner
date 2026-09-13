package com.example.travelplanner.data.model;

import java.util.List;
import java.util.UUID;

public class Trip {
    private String id;
    private String cityName;
    private long dateInMillis;
    private List<PackingItem> items;
    private String imageUrl;
    private double lat;
    private double lon;

    public Trip(String cityName, long dateInMillis, List<PackingItem> items, String imageUrl, double lat, double lon) {
        this.id = UUID.randomUUID().toString();
        this.cityName = cityName;
        this.dateInMillis = dateInMillis;
        this.items = items;
        this.imageUrl = imageUrl;
        this.lat = lat;
        this.lon = lon;
    }

    public String getId() { return id; }
    public String getCityName() { return cityName; }
    public long getDateInMillis() { return dateInMillis; }
    public List<PackingItem> getItems() { return items; }
    public void setItems(List<PackingItem> items) { this.items = items; }
    public String getImageUrl() { return imageUrl; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
}

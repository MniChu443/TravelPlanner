package com.example.travelplanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history")
public class SearchHistory {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private final String cityName;
    private long timestamp;

    public SearchHistory(String cityName) {
        this.cityName = cityName;
        this.timestamp = System.currentTimeMillis();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCityName() { return cityName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

package com.example.travelplanner.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A single item on the packing list.
 * `packed` is updated locally by the user via the CheckBox.
 */
@Entity(tableName = "packing_items")
public class PackingItem {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private final String name;
    private boolean packed;
    private String cityName; // To associate item with a specific search

    public PackingItem(String name) {
        this.name = name;
        this.packed = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getName() { return name; }
    
    public boolean isPacked() { return packed; }
    public void setPacked(boolean packed) { this.packed = packed; }
    
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
}

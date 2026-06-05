package com.example.travelplanner.data.model;

/**
 * A single item on the packing list.
 * `packed` is updated locally by the user via the CheckBox.
 */
public class PackingItem {
    private final String name;
    private boolean packed;

    public PackingItem(String name) {
        this.name = name;
        this.packed = false;
    }

    public String getName() { return name; }
    public boolean isPacked() { return packed; }
    public void setPacked(boolean packed) { this.packed = packed; }
}

package com.example.travelplanner.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.SearchHistory;

import java.util.List;

@Dao
public interface PackingDao {
    @Query("SELECT * FROM packing_items WHERE cityName = :cityName")
    List<PackingItem> getItemsForCity(String cityName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PackingItem> items);

    @Update
    void updateItem(PackingItem item);

    @Query("DELETE FROM packing_items WHERE cityName = :cityName")
    void deleteAllForCity(String cityName);

    @Query("SELECT cityName FROM search_history ORDER BY timestamp DESC")
    List<String> getSearchHistory();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSearch(SearchHistory search);

    @Query("DELETE FROM search_history WHERE cityName = :cityName")
    void deleteSearch(String cityName);
}

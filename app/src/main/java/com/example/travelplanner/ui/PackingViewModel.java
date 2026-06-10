package com.example.travelplanner.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PackingState;
import com.example.travelplanner.data.repository.PackingRepository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds UI state for the home screen.
 */
public class PackingViewModel extends AndroidViewModel {

    private static final String PREFS_NAME = "travel_planner_prefs";
    private static final String KEY_LAST_CITY = "last_city";

    private final PackingRepository repository;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<PackingState> state = new MutableLiveData<>();
    private final MutableLiveData<List<String>> searchHistory = new MutableLiveData<>(new java.util.ArrayList<>());
    
    // New: Flag to control when HomeFragment should auto-navigate
    private final MutableLiveData<Boolean> shouldNavigateToPacking = new MutableLiveData<>(false);

    public PackingViewModel(@NonNull Application application) {
        super(application);
        this.repository = new PackingRepository(application);
        this.prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        state.setValue(PackingState.idle());
        loadPersistentData();
    }

    private void loadPersistentData() {
        // Load history from Room
        repository.loadSearchHistory(searchHistory::postValue);

        // Load last city from SharedPreferences and trigger auto-load WITHOUT navigation
        String lastCity = prefs.getString(KEY_LAST_CITY, null);
        if (lastCity != null) {
            loadCityData(lastCity, null, false);
        }
    }

    public LiveData<PackingState> getState() { return state; }
    public LiveData<List<String>> getSearchHistory() { return searchHistory; }
    public LiveData<Boolean> getShouldNavigateToPacking() { return shouldNavigateToPacking; }

    public void setShouldNavigateToPacking(boolean should) {
        shouldNavigateToPacking.setValue(should);
    }

    public boolean consumeNavigationRequest() {
        Boolean should = shouldNavigateToPacking.getValue();
        if (Objects.equals(should, Boolean.TRUE)) {
            shouldNavigateToPacking.setValue(false);
            return true;
        }
        return false;
    }

    public void generatePackingList(@NonNull String cityName) {
        loadCityData(cityName, null, true);
    }

    public void generatePackingList(@NonNull String cityName, @Nullable String travelDate) {
        loadCityData(cityName, travelDate, true);
    }

    private void loadCityData(@NonNull String cityName, @Nullable String travelDate, boolean navigate) {
        if (cityName.trim().isEmpty()) {
            state.setValue(PackingState.error("Please enter a city name."));
            return;
        }

        // Save as last city
        prefs.edit().putString(KEY_LAST_CITY, cityName.trim()).apply();

        shouldNavigateToPacking.setValue(navigate); 
        state.setValue(PackingState.loading());
        repository.buildPackingList(cityName.trim(), travelDate, new PackingRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<PackingItem> items, String imageUrl, @NonNull String city, double lat, double lon) {
                updateHistory(city); // This now saves to DB and reloads LiveData immediately
                state.postValue(PackingState.success(items, imageUrl, city, lat, lon));
            }

            @Override
            public void onError(@NonNull String message) {
                state.postValue(PackingState.error(message));
                shouldNavigateToPacking.postValue(false);
            }
        });
    }

    private void updateHistory(String city) {
        repository.saveSearch(city);
        repository.loadSearchHistory(searchHistory::postValue);
    }

    public void togglePacked(int position) {
        PackingState current = state.getValue();
        if (current == null || current.items == null) return;
        
        PackingItem item = current.items.get(position);
        item.setPacked(!item.isPacked());
        repository.updateItem(item);
        
        state.setValue(PackingState.success(current.items, current.imageUrl, current.cityName, current.lat, current.lon));
    }

    public void resetState() {
        prefs.edit().remove(KEY_LAST_CITY).apply();
        state.setValue(PackingState.idle());
    }

    public void deleteHistoryItem(String city) {
        repository.deleteSearch(city);
        repository.loadSearchHistory(history -> {
            searchHistory.postValue(history);
        });
    }

    public void goBackToHome() {
        state.setValue(PackingState.idle());
    }

    public void clearError() {
        PackingState current = state.getValue();
        if (current != null && current.errorMessage != null) {
            state.setValue(PackingState.success(current.items, current.imageUrl, current.cityName, current.lat, current.lon));
        }
    }

    @NonNull
    public String formatPackingListForShare() {
        PackingState current = state.getValue();
        if (current == null || current.items == null) return "";
        
        StringBuilder sb = new StringBuilder("Packing List for " + current.cityName + ":\n");
        for (PackingItem item : current.items) {
            sb.append(item.isPacked() ? "[x] " : "[ ] ").append(item.getName()).append("\n");
        }
        return sb.toString();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdown();
    }
}

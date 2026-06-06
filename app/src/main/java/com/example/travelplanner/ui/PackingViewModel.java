package com.example.travelplanner.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PackingState;
import com.example.travelplanner.data.repository.PackingRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds UI state for the home screen.
 * Owns a single-thread Executor so the Repository can run its chained
 * network calls off the main thread.
 */
public class PackingViewModel extends ViewModel {

    private final PackingRepository repository = new PackingRepository();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<PackingState> state = new MutableLiveData<>();
    private final MutableLiveData<List<String>> searchHistory = new MutableLiveData<>(new java.util.ArrayList<>());

    public LiveData<PackingState> getState() {
        return state;
    }

    public LiveData<List<String>> getSearchHistory() {
        return searchHistory;
    }

    /** Triggered when the user taps the "Search & Generate" button. */
    public void generatePackingList(@NonNull String cityName) {
        if (cityName.trim().isEmpty()) {
            state.setValue(PackingState.error("Please enter a city name."));
            return;
        }

        // 1. Force a clean 'loading' state on the main thread
        // We set a special flag or just clear thecityName to indicate this is a NEW search
        state.setValue(PackingState.loading());

        io.execute(() -> {
            repository.buildPackingList(cityName.trim(), new PackingRepository.Callback() {
                @Override
                public void onSuccess(@NonNull List<PackingItem> items,
                                      @androidx.annotation.Nullable String imageUrl,
                                      @NonNull String city,
                                      double lat,
                                      double lon) {
                    
                    // 3. Post history update separately
                    List<String> currentHistory = searchHistory.getValue();
                    List<String> newHistory = (currentHistory == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(currentHistory);
                    newHistory.remove(city);
                    newHistory.add(0, city);
                    if (newHistory.size() > 5) newHistory.remove(5);
                    searchHistory.postValue(newHistory);

                    // 4. Success - using postValue for thread safety
                    state.postValue(PackingState.success(items, imageUrl, city, lat, lon));
                }

                @Override
                public void onError(@NonNull String message) {
                    state.postValue(PackingState.error(message));
                }
            });
        });
    }

    /** Toggle one item's `packed` flag (used by the RecyclerView checkbox). */
    public void togglePacked(int position) {
        PackingState current = state.getValue();
        if (current == null || current.items == null) return;
        if (position < 0 || position >= current.items.size()) return;

        PackingItem item = current.items.get(position);
        item.setPacked(!item.isPacked());

        // Rebuild a new state so observers receive a new immutable object.
        state.setValue(PackingState.success(current.items, current.imageUrl, current.cityName, current.lat, current.lon));
    }

    /** Add a custom item to the list. */
    public void addCustomItem(@NonNull String itemName) {
        PackingState current = state.getValue();
        if (current == null) return;

        List<PackingItem> newItems = new java.util.ArrayList<>(current.items);
        newItems.add(0, new PackingItem(itemName));

        state.setValue(PackingState.success(newItems, current.imageUrl, current.cityName, current.lat, current.lon));
    }

    /** Clear error message from state. */
    public void clearError() {
        PackingState current = state.getValue();
        if (current != null && current.errorMessage != null) {
            state.setValue(PackingState.success(current.items, current.imageUrl, current.cityName, current.lat, current.lon));
        }
    }

    /** Reset state to idle. */
    public void resetToIdle() {
        state.setValue(PackingState.idle());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdown();
    }
}

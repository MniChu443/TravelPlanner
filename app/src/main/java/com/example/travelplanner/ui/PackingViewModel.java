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

    public LiveData<PackingState> getState() {
        return state;
    }

    /** Triggered when the user taps the "Search & Generate" button. */
    public void generatePackingList(@NonNull String cityName) {
        if (cityName.trim().isEmpty()) {
            state.setValue(PackingState.error("Please enter a city name."));
            return;
        }

        state.setValue(PackingState.loading());

        io.execute(() -> repository.buildPackingList(cityName.trim(), new PackingRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<PackingItem> items,
                                  @androidx.annotation.Nullable String imageUrl,
                                  @NonNull String city,
                                  double lat,
                                  double lon) {
                // The callback may run on a worker thread; post to main.
                state.postValue(PackingState.success(items, imageUrl, city, lat, lon));
            }

            @Override
            public void onError(@NonNull String message) {
                state.postValue(PackingState.error(message));
            }
        }));
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

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdown();
    }
}

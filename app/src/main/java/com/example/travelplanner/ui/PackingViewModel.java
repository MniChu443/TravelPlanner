package com.example.travelplanner.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PackingState;
import com.example.travelplanner.data.model.Trip;
import com.example.travelplanner.data.repository.PackingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PackingViewModel extends ViewModel {

    private final PackingRepository repository = new PackingRepository();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<PackingState> state = new MutableLiveData<>();
    private final MutableLiveData<List<Trip>> myTrips = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> navigateToPackingEvent = new MutableLiveData<>(false);

    public LiveData<PackingState> getState() {
        return state;
    }

    public LiveData<List<Trip>> getMyTrips() {
        return myTrips;
    }

    public LiveData<Boolean> getNavigateToPackingEvent() {
        return navigateToPackingEvent;
    }

    public void onNavigatedToPacking() {
        navigateToPackingEvent.setValue(false);
    }

    public void generatePackingList(@NonNull String cityName, long startDateInMillis, long endDateInMillis) {
        if (cityName.trim().isEmpty()) {
            state.setValue(PackingState.error("Please enter a city name."));
            return;
        }

        state.setValue(PackingState.loading());

        io.execute(() -> {
            repository.buildPackingList(cityName.trim(), new PackingRepository.Callback() {
                @Override
                public void onSuccess(@NonNull List<PackingItem> items,
                                      @androidx.annotation.Nullable String imageUrl,
                                      @NonNull String city,
                                      double lat,
                                      double lon) {
                    
                    Trip trip = new Trip(city, startDateInMillis, endDateInMillis, items, imageUrl, lat, lon);
                    
                    List<Trip> currentTrips = myTrips.getValue();
                    if (currentTrips == null) currentTrips = new ArrayList<>();
                    List<Trip> updatedTrips = new ArrayList<>(currentTrips);
                    updatedTrips.add(0, trip);
                    myTrips.postValue(updatedTrips);

                    state.postValue(PackingState.success(items, imageUrl, city, lat, lon, trip.getId(), startDateInMillis, endDateInMillis));
                    navigateToPackingEvent.postValue(true);
                }

                @Override
                public void onError(@NonNull String message) {
                    state.postValue(PackingState.error(message));
                }
            });
        });
    }

    public void selectTrip(String tripId) {
        List<Trip> trips = myTrips.getValue();
        if (trips == null) return;
        for (Trip trip : trips) {
            if (trip.getId().equals(tripId)) {
                state.setValue(PackingState.success(
                        trip.getItems(),
                        trip.getImageUrl(),
                        trip.getCityName(),
                        trip.getLat(),
                        trip.getLon(),
                        trip.getId(),
                        trip.getStartDateInMillis(),
                        trip.getEndDateInMillis()
                ));
                navigateToPackingEvent.setValue(true);
                break;
            }
        }
    }

    public void togglePacked(int position) {
        PackingState current = state.getValue();
        if (current == null || current.items == null) return;
        if (position < 0 || position >= current.items.size()) return;

        PackingItem item = current.items.get(position);
        item.setPacked(!item.isPacked());

        updateCurrentTripItems(current.items);
        state.setValue(PackingState.success(current.items, current.imageUrl, current.cityName, current.lat, current.lon, current.tripId, current.startDateInMillis, current.endDateInMillis));
    }

    public void addCustomItem(@NonNull String itemName) {
        PackingState current = state.getValue();
        if (current == null || current.items == null) return;

        List<PackingItem> newItems = new ArrayList<>(current.items);
        newItems.add(0, new PackingItem(itemName));

        updateCurrentTripItems(newItems);
        state.setValue(PackingState.success(newItems, current.imageUrl, current.cityName, current.lat, current.lon, current.tripId, current.startDateInMillis, current.endDateInMillis));
    }
    
    private void updateCurrentTripItems(List<PackingItem> updatedItems) {
        PackingState current = state.getValue();
        if (current == null || current.tripId == null) return;
        
        List<Trip> trips = myTrips.getValue();
        if (trips != null) {
            for (Trip trip : trips) {
                if (trip.getId().equals(current.tripId)) {
                    trip.setItems(updatedItems);
                    break;
                }
            }
        }
    }

    public void deleteTrip(String tripId) {
        List<Trip> currentTrips = myTrips.getValue();
        if (currentTrips == null) return;
        List<Trip> updatedTrips = new ArrayList<>();
        for (Trip trip : currentTrips) {
            if (!trip.getId().equals(tripId)) {
                updatedTrips.add(trip);
            }
        }
        myTrips.setValue(updatedTrips);
    }

    public void clearAllTrips() {
        myTrips.setValue(new ArrayList<>());
    }

    public List<String> getSuggestedOptionalItems() {
        return java.util.Arrays.asList(
                "Aparat fotograficzny", "Laptop do pracy", "Karta pamięci / Pendrive",
                "Przewodnik drukowany", "Suszarka do włosów", "Żelazko turystyczne",
                "Gry planszowe / Karty", "Namiot", "Karimata", "Kijki trekkingowe",
                "Naczynia turystyczne", "Mata plażowa", "Krem po opalaniu",
                "Środek na komary", "Poduszka podróżna (rogal)"
        );
    }

    public void clearError() {
        PackingState current = state.getValue();
        if (current != null && current.errorMessage != null) {
            state.setValue(PackingState.success(current.items, current.imageUrl, current.cityName, current.lat, current.lon, current.tripId, current.startDateInMillis, current.endDateInMillis));
        }
    }

    public void resetToIdle() {
        state.setValue(PackingState.idle());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdown();
    }
}

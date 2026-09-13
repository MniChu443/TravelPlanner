package com.example.travelplanner.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.travelplanner.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private PackingViewModel viewModel;
    private GoogleMap googleMap;
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLocation;
    
    private final ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (fineLocationGranted != null && fineLocationGranted) {
                    fetchCurrentLocation();
                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                    fetchCurrentLocation();
                } else {
                    Log.d(TAG, "Location permission denied");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        requestLocationPermissions();
        return root;
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation();
        } else {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchCurrentLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if (location != null) {
                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing location permission", e);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        try {
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style)
            );
            if (!success) {
                Log.e(TAG, "Parsowanie stylu mapy się nie powiodło.");
            }
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.e(TAG, "Nie znaleziono pliku stylu mapy. Upewnij się, że plik map_style.json istnieje w res/raw/", e);
        }

        // Ustaw domyslną lokalizację
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(51.505, 10.0), 4));

        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.setMyLocationEnabled(true);
            }
        } catch (SecurityException ignored) { }

        googleMap.setOnMapClickListener(latLng -> {
            googleMap.clear();
            googleMap.addMarker(new MarkerOptions().position(latLng));
            if (currentLocation != null) {
                drawLineTo(latLng);
            }
            reverseGeocode(latLng);
        });

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.lat != 0 && state.lon != 0) {
                LatLng destination = new LatLng(state.lat, state.lon);
                googleMap.clear();
                googleMap.addMarker(new MarkerOptions().position(destination));
                
                // Narysowanie lini od bieżącej lokalizacji do celu
                if (currentLocation != null) {
                    drawLineTo(destination);
                }
                
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destination, 6));
            }
        });
    }

    private void drawLineTo(LatLng destination) {
        if (googleMap != null && currentLocation != null) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .add(currentLocation, destination)
                    .width(10)
                    .color(Color.parseColor("#1E88E5")) // Primary color
                    .geodesic(true);
            googleMap.addPolyline(polylineOptions);
        }
    }

    private void reverseGeocode(LatLng latLng) {
        geocodeExecutor.execute(() -> {
            Geocoder geocoder = new Geocoder(requireContext(), new Locale("pl", "PL"));
            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String city = address.getLocality();
                    if (city == null) city = address.getSubAdminArea();
                    if (city == null) city = address.getAdminArea();
                    if (city == null) city = address.getCountryName();

                    if (city != null) {
                        final String selectedCity = city;
                        requireActivity().runOnUiThread(() -> {
                            String msg = getString(R.string.selected_city, selectedCity);
                            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                            // Tymczasowo generujemy na dzisiaj (albo wywolujemy nawigacje do HOME)
                            // Najlepiej wrocic do Home, bo tam trzeba wybrac date. Zrobmy to tak:
                            viewModel.generatePackingList(selectedCity, System.currentTimeMillis());
                            
                            // Switch to Packing List tab
                            com.google.android.material.bottomnavigation.BottomNavigationView navView = 
                                requireActivity().findViewById(R.id.nav_view);
                            if (navView != null) {
                                navView.setSelectedItemId(R.id.navigation_packing);
                            }
                        });
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Geocoding error", e);
            }
        });
    }
}
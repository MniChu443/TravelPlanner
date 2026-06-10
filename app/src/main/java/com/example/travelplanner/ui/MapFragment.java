package com.example.travelplanner.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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

public class MapFragment extends Fragment {

    private PackingViewModel viewModel;
    private WebView webViewMap;
    private View fabZoomUser, fabZoomDest;
    private FusedLocationProviderClient fusedLocationClient;
    private double userLat = 0, userLon = 0;

    private final ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (fineLocationGranted != null && fineLocationGranted) {
                    fetchUserLocation();
                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                    fetchUserLocation();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map, container, false);

        webViewMap = root.findViewById(R.id.webViewMap);
        fabZoomUser = root.findViewById(R.id.fabZoomUser);
        fabZoomDest = root.findViewById(R.id.fabZoomDest);
        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupButtons();
        checkLocationPermissions();
        observeViewModel();

        return root;
    }

    private void setupButtons() {
        fabZoomUser.setOnClickListener(v -> {
            if (userLat != 0 && userLon != 0) {
                webViewMap.loadUrl("javascript:map.setView([" + userLat + ", " + userLon + "], 13);");
            } else {
                Toast.makeText(getContext(), "Lokalizacja niedostępna", Toast.LENGTH_SHORT).show();
            }
        });

        fabZoomDest.setOnClickListener(v -> {
            if (viewModel.getState().getValue() != null && viewModel.getState().getValue().lat != 0) {
                double dLat = viewModel.getState().getValue().lat;
                double dLon = viewModel.getState().getValue().lon;
                webViewMap.loadUrl("javascript:map.setView([" + dLat + ", " + dLon + "], 13);");
            } else {
                Toast.makeText(getContext(), "Cel nie został wybrany", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchUserLocation();
        } else {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchUserLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if (location != null) {
                    userLat = location.getLatitude();
                    userLon = location.getLongitude();
                    // Refresh map if state is already loaded
                    if (viewModel.getState().getValue() != null) {
                        setupWebView(viewModel.getState().getValue().lat, viewModel.getState().getValue().lon);
                    }
                }
            });
        } catch (SecurityException ignored) { }
    }

    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            setupWebView(state.lat, state.lon);
        });
    }

    private void setupWebView(double destLat, double destLon) {
        // Fallback to London if no city selected and no user location
        double startLat = destLat != 0 ? destLat : (userLat != 0 ? userLat : 51.505);
        double startLon = destLon != 0 ? destLon : (userLon != 0 ? userLon : -0.09);

        webViewMap.getSettings().setJavaScriptEnabled(true);
        webViewMap.setWebViewClient(new WebViewClient());
        webViewMap.addJavascriptInterface(new WebAppInterface(), "Android");

        StringBuilder scriptBuilder = new StringBuilder();
        scriptBuilder.append("var map = L.map('map').setView([").append(startLat).append(", ").append(startLon).append("], 13);");
        scriptBuilder.append("L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {")
                     .append("    attribution: '&copy; OpenStreetMap contributors',")
                     .append("    lang: 'pl'")
                     .append("}).addTo(map);");

        scriptBuilder.append("var markers = [];");

        // Destination marker
        if (destLat != 0 && destLon != 0) {
            scriptBuilder.append("var destMarker = L.marker([").append(destLat).append(", ").append(destLon).append("], {title: 'Cel'}).addTo(map);")
                         .append("destMarker.bindPopup('Twoje miejsce docelowe').openPopup();")
                         .append("markers.push([ ").append(destLat).append(", ").append(destLon).append("]);");
        }

        // User location marker
        if (userLat != 0 && userLon != 0) {
            scriptBuilder.append("var userMarker = L.marker([").append(userLat).append(", ").append(userLon).append("], ")
                         .append("{ icon: L.icon({ iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png', shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png', iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41] }) })")
                         .append(".addTo(map);")
                         .append("userMarker.bindPopup('Twoja lokalizacja');")
                         .append("markers.push([ ").append(userLat).append(", ").append(userLon).append("]);");
        }

        // Arc (Polyline) between user and destination
        if (destLat != 0 && userLat != 0) {
            scriptBuilder.append("var pointList = [ [").append(userLat).append(", ").append(userLon).append("], [").append(destLat).append(", ").append(destLon).append("] ];")
                         .append("var polyline = L.polyline(pointList, {")
                         .append("  color: '#2196F3',")
                         .append("  weight: 4,")
                         .append("  opacity: 0.6,")
                         .append("  dashArray: '10, 10',")
                         .append("  lineJoin: 'round'")
                         .append("}).addTo(map);");

            scriptBuilder.append("if (markers.length > 1) { map.fitBounds(markers, {padding: [50, 50]}); }");
        }

        scriptBuilder.append("var selectionMarker = L.marker([").append(startLat).append(", ").append(startLon).append("]);")
                     .append("map.on('click', function(e) {")
                     .append("  var coord = e.latlng;")
                     .append("  selectionMarker.setLatLng(coord).addTo(map);")
                     .append("  fetch('https://nominatim.openstreetmap.org/reverse?format=json&lat=' + coord.lat + '&lon=' + coord.lng + '&zoom=10&accept-language=pl')")
                     .append("    .then(response => response.json())")
                     .append("    .then(data => {")
                     .append("      var city = data.address.city || data.address.town || data.address.village || data.address.state || 'Nieznane';")
                     .append("      Android.onCitySelected(city);")
                     .append("    });")
                     .append("});");

        String html = "<html><head>" +
                "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />" +
                "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>" +
                "<style>#map { height: 100%; width: 100%; margin: 0; padding: 0; }</style>" +
                "</head><body style=\"margin: 0; padding: 0;\">" +
                "<div id=\"map\"></div>" +
                "<script>" + scriptBuilder.toString() + "</script></body></html>";

        webViewMap.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void onCitySelected(String cityName) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                String msg = getString(R.string.selected_city, cityName);
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.generatePackingList(cityName);
                
                // Get bottom nav and switch selection manually
                com.google.android.material.bottomnavigation.BottomNavigationView navView = 
                    getActivity().findViewById(R.id.nav_view);
                if (navView != null) {
                    navView.setSelectedItemId(R.id.navigation_packing);
                }
            });
        }
    }
}

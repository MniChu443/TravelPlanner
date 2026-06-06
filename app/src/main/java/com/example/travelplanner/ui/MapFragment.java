package com.example.travelplanner.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.travelplanner.R;

public class MapFragment extends Fragment {

    private PackingViewModel viewModel;
    private WebView webViewMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map, container, false);

        webViewMap = root.findViewById(R.id.webViewMap);
        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);

        observeViewModel();

        return root;
    }

    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            setupWebView(state.lat, state.lon);
        });
    }

    private void setupWebView(double lat, double lon) {
        // Fallback to London if no city selected
        if (lat == 0 && lon == 0) {
            lat = 51.505;
            lon = -0.09;
        }

        webViewMap.getSettings().setJavaScriptEnabled(true);
        webViewMap.setWebViewClient(new WebViewClient());
        webViewMap.addJavascriptInterface(new WebAppInterface(), "Android");

        String html = "<html><head>" +
                "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />" +
                "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>" +
                "<style>#map { height: 100%; width: 100%; margin: 0; padding: 0; }</style>" +
                "</head><body style=\"margin: 0; padding: 0;\">" +
                "<div id=\"map\"></div>" +
                "<script>" +
                "var map = L.map('map').setView([" + lat + ", " + lon + "], 13);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {" +
                "    attribution: '&copy; OpenStreetMap contributors'," +
                "    lang: 'pl'" +
                "}).addTo(map);" +
                "var marker = L.marker([" + lat + ", " + lon + "]).addTo(map);" +
                "map.on('click', function(e) {" +
                "  var coord = e.latlng;" +
                "  var lat = coord.lat;" +
                "  var lng = coord.lng;" +
                "  marker.setLatLng(coord);" +
                "  fetch('https://nominatim.openstreetmap.org/reverse?format=json&lat=' + lat + '&lon=' + lng + '&zoom=10&accept-language=pl')" +
                "    .then(response => response.json())" +
                "    .then(data => {" +
                "      var city = data.address.city || data.address.town || data.address.village || data.address.state || 'Nieznane';" +
                "      Android.onCitySelected(city);" +
                "    });" +
                "});" +
                "</script></body></html>";
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

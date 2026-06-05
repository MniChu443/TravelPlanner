package com.example.travelplanner;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.bumptech.glide.Glide;
import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PackingState;
import com.example.travelplanner.ui.PackingAdapter;
import com.example.travelplanner.ui.PackingViewModel;
import com.google.android.material.button.MaterialButton;

/**
 * Smart Packing Assistant – single screen.
 *
 *   ┌─────────────────────────────────────┐
 *   │  [EditText city]   [Go]             │
 *   │  ┌───────────────────────────────┐  │
 *   │  │      City header photo        │  │
 *   │  └───────────────────────────────┘  │
 *   │  Progress: 3 / 7 packed             │
 *   │  ☐ Passport                         │
 *   │  ☐ Jacket                           │
 *   │  ☑ Phone Charger                    │
 *   │  …                                  │
 *   └─────────────────────────────────────┘
 */
public class MainActivity extends AppCompatActivity {

    private PackingViewModel viewModel;
    private PackingAdapter adapter;

    private EditText etCity;
    private MaterialButton btnSearch;
    private ImageView ivHeader;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private RecyclerView rvPacking;
    private WebView webViewMap;
    private MaterialButton btnMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        viewModel = new ViewModelProvider(this).get(PackingViewModel.class);
        setupRecycler();
        setupClickListeners();
        observeViewModel();

        // Default map view (e.g., London)
        setupWebView(51.505, -0.09);
    }

    private void bindViews() {
        etCity     = findViewById(R.id.etCity);
        btnSearch  = findViewById(R.id.btnSearch);
        ivHeader   = findViewById(R.id.ivCityHeader);
        tvProgress = findViewById(R.id.tvProgress);
        progressBar = findViewById(R.id.progressBar);
        rvPacking  = findViewById(R.id.rvPacking);
        webViewMap = findViewById(R.id.webViewMap);
        btnMap     = findViewById(R.id.btnMap);
    }

    private void setupRecycler() {
        rvPacking.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PackingAdapter(java.util.Collections.emptyList(),
                (position, isChecked) -> {
                    // Ask the ViewModel to flip the flag and re-emit state.
                    viewModel.togglePacked(position);
                });
        rvPacking.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnSearch.setOnClickListener(v -> {
            String city = etCity.getText().toString().trim();
            if (TextUtils.isEmpty(city)) {
                Toast.makeText(this, "Enter a city name first", Toast.LENGTH_SHORT).show();
                return;
            }
            hideKeyboard();
            viewModel.generatePackingList(city);
        });

        btnMap.setOnClickListener(v -> {
            if (webViewMap.getVisibility() == View.VISIBLE) {
                webViewMap.setVisibility(View.GONE);
                btnMap.setText("Show Map");
            } else {
                webViewMap.setVisibility(View.VISIBLE);
                btnMap.setText("Hide Map");
            }
        });
    }

    private void observeViewModel() {
        viewModel.getState().observe(this, this::render);
    }

    /**
     * Single place where state → view happens.
     * Keeps the Activity dumb; ViewModel is the source of truth.
     */
    private void render(PackingState state) {
        if (state == null) return;

        progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);

        if (state.errorMessage != null) {
            Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show();
            return;
        }

        // Update the city photo via Glide (handles cache + placeholder).
        if (!TextUtils.isEmpty(state.imageUrl)) {
            Glide.with(this)
                    .load(state.imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(ivHeader);
        } else {
            ivHeader.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Update the list and progress text
        List<PackingItem> items = state.items;
        if (items != null) {
            adapter.updateItems(items);

            int packed = 0;
            for (PackingItem item : items) {
                if (item.isPacked()) packed++;
            }
            tvProgress.setText(packed + " / " + items.size() + " packed");
        }

        if (state.lat != 0 || state.lon != 0) {
            btnMap.setVisibility(View.VISIBLE);
            webViewMap.setVisibility(View.VISIBLE);
            setupWebView(state.lat, state.lon);
        } else {
            btnMap.setVisibility(View.GONE);
        }
    }

    private void setupWebView(double lat, double lon) {
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
                "    attribution: '&copy; OpenStreetMap contributors'" +
                "}).addTo(map);" +
                "var marker = L.marker([" + lat + ", " + lon + "]).addTo(map);" +
                "map.on('click', function(e) {" +
                "  var coord = e.latlng;" +
                "  var lat = coord.lat;" +
                "  var lng = coord.lng;" +
                "  marker.setLatLng(coord);" +
                "  fetch('https://nominatim.openstreetmap.org/reverse?format=json&lat=' + lat + '&lon=' + lng + '&zoom=10')" +
                "    .then(response => response.json())" +
                "    .then(data => {" +
                "      var city = data.address.city || data.address.town || data.address.village || data.address.state || 'Unknown';" +
                "      Android.onCitySelected(city);" +
                "    });" +
                "});" +
                "</script></body></html>";
        webViewMap.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void onCitySelected(String cityName) {
            runOnUiThread(() -> {
                etCity.setText(cityName);
                Toast.makeText(MainActivity.this, "Selected: " + cityName, Toast.LENGTH_SHORT).show();
                // Add a small delay to let the UI settle before starting a new search
                etCity.postDelayed(() -> viewModel.generatePackingList(cityName), 300);
            });
        }
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}

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
    }

    private void bindViews() {
        etCity     = findViewById(R.id.etCity);
        btnSearch  = findViewById(R.id.btnSearch);
        ivHeader   = findViewById(R.id.ivCityHeader);
        tvProgress = findViewById(R.id.tvProgress);
        progressBar = findViewById(R.id.progressBar);
        rvPacking  = findViewById(R.id.rvPacking);
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
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}

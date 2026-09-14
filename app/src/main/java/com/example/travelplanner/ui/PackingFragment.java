package com.example.travelplanner.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelplanner.R;
import com.example.travelplanner.data.model.PackingItem;

import java.util.Collections;
import java.util.List;

public class PackingFragment extends Fragment {

    private PackingViewModel viewModel;
    private PackingAdapter adapter;
    private ImageView ivHeader;
    private TextView tvCityName;
    private TextView tvTripCountdown;
    private TextView tvProgress;
    private RecyclerView rvPacking;
    private EditText etNewItem;
    private ImageButton btnAddItem;
    private View btnSearchAgain;
    private Button btnSuggestedItems;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_packing, container, false);

        ivHeader = root.findViewById(R.id.ivCityHeader);
        tvCityName = root.findViewById(R.id.tvCityName);
        tvTripCountdown = root.findViewById(R.id.tvTripCountdown);
        tvProgress = root.findViewById(R.id.tvProgress);
        rvPacking = root.findViewById(R.id.rvPacking);
        etNewItem = root.findViewById(R.id.etNewItem);
        btnAddItem = root.findViewById(R.id.btnAddItem);
        btnSearchAgain = root.findViewById(R.id.btnSearchAgain);
        btnSuggestedItems = root.findViewById(R.id.btnSuggestedItems);

        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);
        
        setupRecycler();
        setupClickListeners();
        observeViewModel();

        return root;
    }

    private void setupRecycler() {
        rvPacking.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PackingAdapter(Collections.emptyList(), (position, isChecked) -> {
            viewModel.togglePacked(position);
        });
        rvPacking.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnAddItem.setOnClickListener(v -> {
            String itemName = etNewItem.getText().toString().trim();
            if (!TextUtils.isEmpty(itemName)) {
                viewModel.addCustomItem(itemName);
                etNewItem.setText("");
            }
        });
        
        if (btnSuggestedItems != null) {
            btnSuggestedItems.setOnClickListener(v -> showSuggestedItemsDialog());
        }

        btnSearchAgain.setOnClickListener(v -> {
            viewModel.resetToIdle();
            if (getActivity() != null) {
                com.google.android.material.bottomnavigation.BottomNavigationView navView = 
                    getActivity().findViewById(R.id.nav_view);
                if (navView != null) {
                    navView.setSelectedItemId(R.id.navigation_home);
                    return; 
                }
            }
            androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
            navController.navigate(R.id.navigation_home);
        });
    }

    private void showSuggestedItemsDialog() {
        List<String> suggested = viewModel.getSuggestedOptionalItems();
        CharSequence[] items = suggested.toArray(new CharSequence[0]);
        boolean[] checkedItems = new boolean[items.length];
        
        new AlertDialog.Builder(getContext())
            .setTitle("Wybierz dodatkowe przedmioty")
            .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                checkedItems[which] = isChecked;
            })
            .setPositiveButton("Dodaj wybrane", (dialog, which) -> {
                for (int i = 0; i < items.length; i++) {
                    if (checkedItems[i]) {
                        viewModel.addCustomItem(items[i].toString());
                    }
                }
            })
            .setNegativeButton("Anuluj", null)
            .show();
    }

    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || state.loading) return;

            // Skracamy długa nazwę geokodowania (np. "Paris, Île-de-France, France" -> "Paris")
            String shortCity = state.cityName;
            if (shortCity != null && shortCity.contains(",")) {
                shortCity = shortCity.split(",")[0].trim();
            }
            tvCityName.setText(shortCity);
            
            if (state.startDateInMillis > 0 && state.endDateInMillis > 0) {
                long today = System.currentTimeMillis();
                if (today >= state.startDateInMillis && today <= state.endDateInMillis) {
                    long remainingDiff = state.endDateInMillis - today;
                    int daysRemaining = (int) (remainingDiff / (1000 * 60 * 60 * 24)) + 1;
                    tvTripCountdown.setText("Wyjazd trwa! Do końca: " + daysRemaining + " dni");
                } else if (today < state.startDateInMillis) {
                    long untilStartDiff = state.startDateInMillis - today;
                    int daysUntil = (int) (untilStartDiff / (1000 * 60 * 60 * 24)) + 1;
                    tvTripCountdown.setText("Dni do wyjazdu: " + daysUntil);
                } else {
                    tvTripCountdown.setText("Wyjazd zakończony");
                }
                tvTripCountdown.setVisibility(View.VISIBLE);
            } else {
                tvTripCountdown.setVisibility(View.GONE);
            }
            
            if (state.items != null) {
                adapter.updateItems(state.items);
                int packed = 0;
                for (PackingItem item : state.items) {
                    if (item.isPacked()) packed++;
                }
                tvProgress.setText(getString(R.string.packed_status, packed, state.items.size()));
            }

            if (state.imageUrl != null && !state.imageUrl.isEmpty()) {
                com.bumptech.glide.load.model.GlideUrl glideUrl = new com.bumptech.glide.load.model.GlideUrl(
                        state.imageUrl,
                        new com.bumptech.glide.load.model.LazyHeaders.Builder()
                                .addHeader("User-Agent", "TravelPlannerApp/1.0 (android-app-support@travelplanner.com)")
                                .build()
                );

                Glide.with(this)
                        .load(glideUrl)
                        .placeholder(R.drawable.ic_city_placeholder)
                        .error(R.drawable.ic_city_placeholder)
                        .centerCrop()
                        .into(ivHeader);
            } else {
                ivHeader.setImageResource(R.drawable.ic_city_placeholder);
            }
        });
    }
}

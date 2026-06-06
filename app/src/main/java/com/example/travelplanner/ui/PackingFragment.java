package com.example.travelplanner.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

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

public class PackingFragment extends Fragment {

    private PackingViewModel viewModel;
    private PackingAdapter adapter;
    private ImageView ivHeader;
    private TextView tvCityName;
    private TextView tvProgress;
    private RecyclerView rvPacking;
    private EditText etNewItem;
    private ImageButton btnAddItem;
    private View btnSearchAgain;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_packing, container, false);

        ivHeader = root.findViewById(R.id.ivCityHeader);
        tvCityName = root.findViewById(R.id.tvCityName);
        tvProgress = root.findViewById(R.id.tvProgress);
        rvPacking = root.findViewById(R.id.rvPacking);
        etNewItem = root.findViewById(R.id.etNewItem);
        btnAddItem = root.findViewById(R.id.btnAddItem);
        btnSearchAgain = root.findViewById(R.id.btnSearchAgain);

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

        btnSearchAgain.setOnClickListener(v -> {
            // Navigate back to Home screen to search for a new city
            androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
            navController.navigate(R.id.navigation_home);
        });
    }

    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || state.loading) return;

            tvCityName.setText(state.cityName);
            
            if (state.items != null) {
                adapter.updateItems(state.items);
                int packed = 0;
                for (PackingItem item : state.items) {
                    if (item.isPacked()) packed++;
                }
                tvProgress.setText(getString(R.string.packed_status, packed, state.items.size()));
            }

            if (state.imageUrl != null) {
                Glide.with(this).load(state.imageUrl).into(ivHeader);
            }
        });
    }
}

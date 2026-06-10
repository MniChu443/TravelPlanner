package com.example.travelplanner.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelplanner.R;
import com.example.travelplanner.data.model.PackingItem;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;

public class PackingFragment extends Fragment {

    private PackingViewModel viewModel;
    private PackingAdapter adapter;
    private ImageView ivHeader;
    private TextView tvCityName;
    private TextView tvProgress;
    private RecyclerView rvPacking;
    private Toolbar toolbar;
    private FloatingActionButton fabShare;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_packing, container, false);

        ivHeader = root.findViewById(R.id.ivCityHeader);
        tvCityName = root.findViewById(R.id.tvCityName);
        tvProgress = root.findViewById(R.id.tvProgress);
        rvPacking = root.findViewById(R.id.rvPacking);
        toolbar = root.findViewById(R.id.toolbar);
        fabShare = root.findViewById(R.id.fabShare);

        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);

        setupRecycler();
        setupNavigation();
        observeViewModel();

        fabShare.setOnClickListener(v -> sharePackingList());

        return root;
    }

    private void setupRecycler() {
        rvPacking.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PackingAdapter(Collections.emptyList(), (position, isChecked) -> viewModel.togglePacked(position));
        rvPacking.setAdapter(adapter);
    }

    private void setupNavigation() {
        // Toolbar navigation icon (search/back)
        toolbar.setNavigationOnClickListener(v -> goBackToHome());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBackToHome();
            }
        });
    }

    private void goBackToHome() {
        // Just navigate back, do NOT reset the state.
        // This is now consistent with the "Start" tab in the bottom menu.
        NavController navController = Navigation.findNavController(requireView());
        if (!navController.popBackStack()) {
            navController.navigate(R.id.navigation_home);
        }
    }

    private void sharePackingList() {
        String text = viewModel.formatPackingListForShare();
        if (text.isEmpty()) {
            Toast.makeText(getContext(), "List is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share Packing List");
        startActivity(shareIntent);
    }

    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || state.items == null) return;

            tvCityName.setText(state.cityName);
            adapter.updateItems(state.items);

            int packedCount = 0;
            for (PackingItem item : state.items) {
                if (item.isPacked()) packedCount++;
            }
            tvProgress.setText(getString(R.string.packed_status, packedCount, state.items.size()));

            if (state.imageUrl != null && !state.imageUrl.isEmpty()) {
                Glide.with(this)
                        .load(state.imageUrl)
                        .placeholder(R.color.card_bg)
                        .error(R.color.card_bg)
                        .into(ivHeader);
                ivHeader.setVisibility(View.VISIBLE);
            } else {
                ivHeader.setVisibility(View.GONE);
            }
        });
    }
}

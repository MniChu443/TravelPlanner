package com.example.travelplanner.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.travelplanner.R;

import java.util.List;

import static android.content.Context.INPUT_METHOD_SERVICE;

public class HomeFragment extends Fragment {

    private PackingViewModel viewModel;
    private EditText etCity;
    private ProgressBar progressBar;
    private LinearLayout historyContainer;
    private TextView tvHistoryLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        etCity = root.findViewById(R.id.etCity);
        progressBar = root.findViewById(R.id.progressBar);
        historyContainer = root.findViewById(R.id.historyContainer);
        tvHistoryLabel = root.findViewById(R.id.tvHistoryLabel);
        View btnSearch = root.findViewById(R.id.btnSearch);

        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);

        btnSearch.setOnClickListener(v -> {
            String city = etCity.getText().toString().trim();
            if (TextUtils.isEmpty(city)) {
                Toast.makeText(getContext(), R.string.error_city_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            hideKeyboard();
            viewModel.generatePackingList(city);
        });

        viewModel.getSearchHistory().observe(getViewLifecycleOwner(), history -> {
            updateHistoryUI(history);
        });

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            
            // If we just finished loading and have items, navigate
            if (!state.loading && state.items != null && !state.items.isEmpty() && state.errorMessage == null) {
                // Clear input for next search
                etCity.setText("");
                
                // Only navigate if we are currently in HomeFragment to avoid loops or unexpected jumps
                androidx.navigation.NavController navController = Navigation.findNavController(root);
                if (navController.getCurrentDestination() != null && 
                    navController.getCurrentDestination().getId() == R.id.navigation_home) {
                    navController.navigate(R.id.navigation_packing);
                }
            }

            if (state.errorMessage != null) {
                Toast.makeText(getContext(), state.errorMessage, Toast.LENGTH_SHORT).show();
            }
        });

        return root;
    }

    private void updateHistoryUI(List<String> history) {
        historyContainer.removeAllViews();
        if (history == null || history.isEmpty()) {
            tvHistoryLabel.setVisibility(View.GONE);
            return;
        }

        tvHistoryLabel.setVisibility(View.VISIBLE);
        for (String city : history) {
            TextView tv = new TextView(getContext());
            tv.setText(city);
            tv.setPadding(16, 16, 16, 16);
            tv.setTextSize(16);
            tv.setTextColor(getResources().getColor(R.color.black, null));
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setOnClickListener(v -> {
                etCity.setText(city);
                viewModel.generatePackingList(city);
            });
            historyContainer.addView(tv);
        }
    }

    private void hideKeyboard() {
        View v = getActivity().getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}

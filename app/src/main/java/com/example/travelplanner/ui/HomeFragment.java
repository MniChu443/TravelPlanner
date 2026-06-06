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
            
            // Only handle loading spinner here
            progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            
            // Navigate ONLY if we have items AND it's a fresh success
            if (!state.loading && state.items != null && !state.items.isEmpty() && state.errorMessage == null && !TextUtils.isEmpty(state.cityName)) {
                
                androidx.navigation.NavController navController = Navigation.findNavController(root);
                if (navController.getCurrentDestination() != null && 
                    navController.getCurrentDestination().getId() == R.id.navigation_home) {
                    
                    etCity.setText("");
                    navController.navigate(R.id.navigation_packing);
                    
                    // REMOVED: root.postDelayed(() -> viewModel.resetToIdle(), 500);
                    // Instead of resetting to idle here, we will let PackingFragment display the data.
                    // The 'idle' state should only be set when the user EXPLICITLY wants to search again.
                }
            }

            if (state.errorMessage != null) {
                Toast.makeText(getContext(), state.errorMessage, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
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
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(getContext());
            card.setCardElevation(2f);
            card.setRadius(24f);
            card.setCardBackgroundColor(getResources().getColor(R.color.card_bg, null));
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 8, 0, 8);
            card.setLayoutParams(params);

            TextView tv = new TextView(getContext());
            tv.setText(city);
            tv.setPadding(32, 24, 32, 24);
            tv.setTextSize(16);
            tv.setTextColor(getResources().getColor(R.color.text_primary, null));
            tv.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_recent_history, 0, 0, 0);
            tv.setCompoundDrawablePadding(24);
            
            card.addView(tv);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                etCity.setText(city);
                viewModel.generatePackingList(city);
            });
            historyContainer.addView(card);
        }
    }

    private void hideKeyboard() {
        View v = getActivity().getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}

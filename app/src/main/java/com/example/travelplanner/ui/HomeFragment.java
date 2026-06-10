package com.example.travelplanner.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import static android.content.Context.INPUT_METHOD_SERVICE;

public class HomeFragment extends Fragment {

    private PackingViewModel viewModel;
    private EditText etCity, etDate;
    private ProgressBar progressBar;
    private LinearLayout historyContainer;
    private TextView tvHistoryLabel;
    private String selectedDate = null;
    private final Calendar calendar = Calendar.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        etCity = root.findViewById(R.id.etCity);
        etDate = root.findViewById(R.id.etDate);
        progressBar = root.findViewById(R.id.progressBar);
        historyContainer = root.findViewById(R.id.historyContainer);
        tvHistoryLabel = root.findViewById(R.id.tvHistoryLabel);
        View btnSearch = root.findViewById(R.id.btnSearch);

        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);

        etDate.setOnClickListener(v -> showDatePicker());

        btnSearch.setOnClickListener(v -> {
            String city = etCity.getText().toString().trim();
            if (TextUtils.isEmpty(city)) {
                Toast.makeText(getContext(), R.string.error_city_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            hideKeyboard();
            viewModel.generatePackingList(city, selectedDate);
        });

        viewModel.getSearchHistory().observe(getViewLifecycleOwner(), this::updateHistoryUI);

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            
            if (!state.loading && !state.items.isEmpty() && !TextUtils.isEmpty(state.cityName)) {
                // Check if we should navigate (flag set by generatePackingList)
                // We consume the request so it doesn't trigger again when returning to this fragment
                if (viewModel.consumeNavigationRequest()) {
                    androidx.navigation.NavController navController = Navigation.findNavController(root);
                    if (navController.getCurrentDestination() != null &&
                        navController.getCurrentDestination().getId() == R.id.navigation_home) {

                        etCity.setText("");
                        navController.navigate(R.id.navigation_packing);
                    }
                }
            }

            if (state.errorMessage != null) {
                Toast.makeText(getContext(), state.errorMessage, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        return root;
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            
            SimpleDateFormat displayFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            
            selectedDate = apiFormat.format(calendar.getTime());
            etDate.setText(displayFormat.format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        // Limit range: today to 14 days in future
        Calendar minDate = Calendar.getInstance();
        dialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.DAY_OF_YEAR, 14);
        dialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

        dialog.show();
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

            // Create a horizontal layout to hold the 'X' and the City Name
            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // Delete button (X) on the left
            ImageView ivDelete = new ImageView(requireContext());
            ivDelete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            ivDelete.setPadding(32, 24, 16, 24);
            ivDelete.setClickable(true);
            ivDelete.setFocusable(true);
            ivDelete.setBackground(androidx.core.content.res.ResourcesCompat.getDrawable(getResources(), android.R.drawable.list_selector_background, null));
            ivDelete.setColorFilter(getResources().getColor(R.color.text_primary, null));
            
            ivDelete.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                        .setTitle("Usuń z historii")
                        .setMessage("Czy na pewno chcesz usunąć " + city + " z historii?")
                        .setPositiveButton("Tak", (dialog, which) -> {
                            viewModel.deleteHistoryItem(city);
                        })
                        .setNegativeButton("Nie", null)
                        .show();
            });

            // City name text
            TextView tv = new TextView(getContext());
            tv.setText(city);
            tv.setPadding(16, 24, 32, 24);
            tv.setTextSize(16);
            tv.setTextColor(getResources().getColor(R.color.text_primary, null));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            
            itemLayout.addView(ivDelete);
            itemLayout.addView(tv);
            card.addView(itemLayout);
            
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
        if (getActivity() == null) return;
        View v = getActivity().getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}

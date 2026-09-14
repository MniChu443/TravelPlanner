package com.example.travelplanner.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.example.travelplanner.R;
import com.example.travelplanner.data.model.Trip;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.content.Context.INPUT_METHOD_SERVICE;

public class HomeFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "HomeFragment";
    
    private PackingViewModel viewModel;
    private EditText etCity;
    private ProgressBar progressBar;
    private LinearLayout tripsContainer;
    private TextView tvSelectedDate;
    
    private long selectedStartDateInMillis = 0;
    private long selectedEndDateInMillis = 0;

    private TextView tvCountdownText;
    private TextView tvNextTripTitle;
    private TextView tvFireCounter;
    private ImageView imgFirewoodStatic;
    private ImageView imgFireActive;
    private LottieAnimationView lottieCampfireActive;
    private GoogleMap googleMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        etCity = root.findViewById(R.id.etCity);
        progressBar = root.findViewById(R.id.progressBar);
        tripsContainer = root.findViewById(R.id.tripsContainer);
        tvSelectedDate = root.findViewById(R.id.tvSelectedDate);
        View btnSearch = root.findViewById(R.id.btnSearch);

        tvCountdownText = root.findViewById(R.id.tv_countdown_text);
        tvNextTripTitle = root.findViewById(R.id.tvNextTripTitle);
        tvFireCounter = root.findViewById(R.id.tv_fire_counter);
        imgFirewoodStatic = root.findViewById(R.id.img_firewood_static);
        imgFireActive = root.findViewById(R.id.img_fire_active);
        lottieCampfireActive = root.findViewById(R.id.lottie_campfire_active);

        viewModel = new ViewModelProvider(requireActivity()).get(PackingViewModel.class);

        tvSelectedDate.setOnClickListener(v -> {
            CalendarConstraints constraints = new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build();

            MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Wybierz termin wyjazdu")
                    .setCalendarConstraints(constraints)
                    .build();

            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection.first != null && selection.second != null) {
                    selectedStartDateInMillis = selection.first;
                    selectedEndDateInMillis = selection.second;
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    tvSelectedDate.setText("Termin: " + sdf.format(new Date(selectedStartDateInMillis)) + " - " + sdf.format(new Date(selectedEndDateInMillis)));
                }
            });
            picker.show(getParentFragmentManager(), "DATE_RANGE_PICKER");
        });

        btnSearch.setOnClickListener(v -> {
            String city = etCity.getText().toString().trim();
            if (TextUtils.isEmpty(city)) {
                Toast.makeText(getContext(), R.string.error_city_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            // Domyślny termin od dzisiaj na 7 dni, jeśli użytkownik sam nie wybrał w kalendarzu
            if (selectedStartDateInMillis == 0 || selectedEndDateInMillis == 0) {
                selectedStartDateInMillis = System.currentTimeMillis();
                selectedEndDateInMillis = selectedStartDateInMillis + (7L * 24 * 60 * 60 * 1000);
            }

            hideKeyboard();
            viewModel.generatePackingList(city, selectedStartDateInMillis, selectedEndDateInMillis);
        });

        viewModel.getMyTrips().observe(getViewLifecycleOwner(), trips -> {
            updateTripsUI(trips);
            updateNextTripCountdown(trips);
        });

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            if (state.errorMessage != null) {
                Toast.makeText(getContext(), state.errorMessage, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        viewModel.getNavigateToPackingEvent().observe(getViewLifecycleOwner(), navigate -> {
            if (navigate != null && navigate) {
                viewModel.onNavigatedToPacking();
                if (getActivity() != null) {
                    com.google.android.material.bottomnavigation.BottomNavigationView navView = 
                            getActivity().findViewById(R.id.nav_view);
                    if (navView != null) {
                        navView.setSelectedItemId(R.id.navigation_packing);
                    }
                }
            }
        });

        setupMap();
        return root;
    }

    private void updateNextTripCountdown(List<Trip> trips) {
        if (trips == null || trips.isEmpty()) {
            tvNextTripTitle.setVisibility(View.GONE);
            tvCountdownText.setText("Brak zaplanowanych podróży");
            imgFirewoodStatic.setVisibility(View.VISIBLE);
            if (imgFireActive != null) imgFireActive.setVisibility(View.GONE);
            lottieCampfireActive.setVisibility(View.GONE);
            lottieCampfireActive.pauseAnimation();
            if (tvFireCounter != null) tvFireCounter.setVisibility(View.GONE);
            return;
        }

        long today = System.currentTimeMillis();
        long closestDiff = Long.MAX_VALUE;
        Trip closestTrip = null;

        for (Trip t : trips) {
            if (t.getEndDateInMillis() >= today) {
                long diff = t.getStartDateInMillis() - today;
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closestTrip = t;
                }
            }
        }

        if (closestTrip == null) {
            tvNextTripTitle.setVisibility(View.GONE);
            tvCountdownText.setText("Wszystkie wyjazdy zakończone");
            imgFirewoodStatic.setVisibility(View.VISIBLE);
            if (imgFireActive != null) imgFireActive.setVisibility(View.GONE);
            lottieCampfireActive.setVisibility(View.GONE);
            lottieCampfireActive.pauseAnimation();
            if (tvFireCounter != null) tvFireCounter.setVisibility(View.GONE);
            return;
        }

        String cleanCity = closestTrip.getCityName();
        if (cleanCity != null && cleanCity.contains(",")) {
            cleanCity = cleanCity.split(",")[0].trim();
        }

        tvNextTripTitle.setVisibility(View.VISIBLE);
        tvNextTripTitle.setText("Wyjazd: " + cleanCity);
        
        // Czy jesteśmy w trakcie wyjazdu?
        if (today >= closestTrip.getStartDateInMillis() && today <= closestTrip.getEndDateInMillis()) {
            long remainingDiff = closestTrip.getEndDateInMillis() - today;
            int daysRemaining = (int) (remainingDiff / (1000 * 60 * 60 * 24)) + 1;
            tvCountdownText.setText("Wyjazd trwa! Do końca: " + daysRemaining + " dni");
            
            // W trakcie wyjazdu: SAMA IKONKA OGNIA + Cyfrowy licznik w środku płomienia
            imgFirewoodStatic.setVisibility(View.GONE);
            if (imgFireActive != null) imgFireActive.setVisibility(View.VISIBLE);
            lottieCampfireActive.setVisibility(View.VISIBLE);
            lottieCampfireActive.playAnimation();
            
            if (tvFireCounter != null) {
                tvFireCounter.setVisibility(View.VISIBLE);
                tvFireCounter.setText(String.valueOf(daysRemaining));
            }
        } else if (today < closestTrip.getStartDateInMillis()) {
            long untilStartDiff = closestTrip.getStartDateInMillis() - today;
            int daysUntil = (int) (untilStartDiff / (1000 * 60 * 60 * 24)) + 1;
            tvCountdownText.setText("Dni do wyjazdu: " + daysUntil);
            
            // Przed wyjazdem: SAMO NIEPODPALONE DREWNO
            imgFirewoodStatic.setVisibility(View.VISIBLE);
            if (imgFireActive != null) imgFireActive.setVisibility(View.GONE);
            lottieCampfireActive.setVisibility(View.GONE);
            lottieCampfireActive.pauseAnimation();

            if (tvFireCounter != null) {
                tvFireCounter.setVisibility(View.GONE);
            }
        } else {
            tvCountdownText.setText("Wyjazd zakończony");
            imgFirewoodStatic.setVisibility(View.VISIBLE);
            if (imgFireActive != null) imgFireActive.setVisibility(View.GONE);
            lottieCampfireActive.setVisibility(View.GONE);
            lottieCampfireActive.pauseAnimation();
            if (tvFireCounter != null) tvFireCounter.setVisibility(View.GONE);
        }
    }

    private void updateTripsUI(List<Trip> trips) {
        tripsContainer.removeAllViews();
        if (trips == null || trips.isEmpty()) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        for (Trip trip : trips) {
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(getContext());
            card.setCardElevation(2f);
            card.setRadius(24f);
            card.setCardBackgroundColor(getResources().getColor(R.color.card_bg, null));
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 8, 0, 8);
            card.setLayoutParams(params);

            TextView tv = new TextView(getContext());
            String cleanName = trip.getCityName();
            if (cleanName != null && cleanName.contains(",")) {
                cleanName = cleanName.split(",")[0].trim();
            }
            String dateText = sdf.format(new Date(trip.getStartDateInMillis())) + " - " + sdf.format(new Date(trip.getEndDateInMillis()));
            tv.setText(cleanName + "\n" + dateText);
            tv.setPadding(32, 24, 32, 24);
            tv.setTextSize(15);
            tv.setTextColor(getResources().getColor(R.color.text_primary, null));
            tv.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_myplaces, 0, 0, 0);
            tv.setCompoundDrawablePadding(24);
            
            card.addView(tv);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> viewModel.selectTrip(trip.getId()));
            tripsContainer.addView(card);
        }
    }

    private void hideKeyboard() {
        if (getActivity() == null) return;
        View v = getActivity().getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        try {
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style)
            );
            if (!success) {
                Log.e(TAG, "Parsowanie stylu mapy się nie powiodło.");
            }
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.e(TAG, "Nie znaleziono pliku stylu mapy. Upewnij się, że plik map_style.json istnieje w res/raw/", e);
        }
    }
}
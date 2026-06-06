package com.example.travelplanner;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            BottomNavigationView navView = findViewById(R.id.nav_view);
            NavigationUI.setupWithNavController(navView, navController);

            // Ensure back button works for fragments
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.navigation_home) {
                    com.example.travelplanner.ui.PackingViewModel viewModel = 
                        new androidx.lifecycle.ViewModelProvider(this).get(com.example.travelplanner.ui.PackingViewModel.class);
                    viewModel.resetToIdle();
                }
            });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            return navHostFragment.getNavController().navigateUp() || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}

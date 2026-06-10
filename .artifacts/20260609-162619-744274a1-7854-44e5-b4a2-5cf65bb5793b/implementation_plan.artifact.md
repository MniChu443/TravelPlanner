# Implementation Plan - TravelPlanner Fixes & Improvements

This plan outlines the steps to fix critical bugs, implement local persistence (Room), upgrade the UI to Material Design 3, and add a "Share" feature.

## Proposed Changes

### 1. Dependencies & Theme Configuration
Update `libs.versions.toml`, `build.gradle.kts`, and `themes.xml` to support Room and Material 3.

#### [libs.versions.toml](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/gradle/libs.versions.toml)
- Add Room versions and library definitions.
- Ensure Material version is at least 1.10.0.

#### [build.gradle.kts](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/build.gradle.kts)
- Add Room implementation and annotation processor.

#### [themes.xml](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/res/values/themes.xml)
- Change base theme to `Theme.Material3.DayNight.NoActionBar`.

---

### 2. Local Persistence (Room Database)
Convert `PackingItem` to a Room Entity and create the necessary DAO and Database classes.

#### [PackingItem.java](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/java/com/example/travelplanner/data/model/PackingItem.java)
- Add `@Entity`, `@PrimaryKey`, and `@ColumnInfo` annotations.
- Add a `cityId` field to associate items with a specific search.

#### [NEW] [PackingDao.java](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/java/com/example/travelplanner/data/local/PackingDao.java)
- Define methods: `insertAll`, `updateItem`, `getItemsForCity`, `deleteAllForCity`.

#### [NEW] [AppDatabase.java](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/java/com/example/travelplanner/data/local/AppDatabase.java)
- Abstract class extending `RoomDatabase`.
- Singleton pattern implementation.

---

### 3. MVVM & Repository Updates
Integrate Room into the Repository and add state-clearing logic to the ViewModel.

#### [PackingRepository.java](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/java/com/example/travelplanner/data/repository/PackingRepository.java)
- Update to check local DB before fetching from API.
- Add methods to update `isPacked` status in the DB.

#### [PackingViewModel.java](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/java/com/example/travelplanner/ui/PackingViewModel.java)
- **Bug A Fix**: Add `resetState()` method to clear `PackingState` LiveData.
- Add async methods to interact with the repository using `ExecutorService`.

---

### 4. UI/UX Improvements (Material 3)
Upgrade layouts with `CollapsingToolbarLayout`, `MaterialCardView`, and styled Checkboxes.

#### [fragment_packing.xml](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/res/layout/fragment_packing.xml)
- Replace root with `CoordinatorLayout`.
- Add `AppBarLayout` and `CollapsingToolbarLayout` for the header image.
- Add a `FloatingActionButton` for the Share feature.

#### [item_packing.xml](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/res/layout/item_packing.xml)
- Wrap content in a `MaterialCardView`.
- Use `com.google.android.material.checkbox.MaterialCheckBox`.

#### [NEW] [checkbox_selector.xml](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/res/color/checkbox_selector.xml)
- Define a color state list for the checkbox `buttonTint` (checked = Primary, unchecked = Gray).

---

### 5. Navigation & New Features
Fix back navigation and implement the Share intent.

#### [PackingFragment.java](file:///C:/Users/MICHAL/AndroidStudioProjects/TravelPlanner/app/src/main/java/com/example/travelplanner/ui/PackingFragment.java)
- **Bug B Fix**: Handle the back arrow in the toolbar to navigate back to Home.
- **Bug C Fix**: Implement the search icon click listener to trigger `resetState()` and navigate back.
- **Share Feature**: Implement `sharePackingList()` using `ACTION_SEND`.

---

## Verification Plan

### Automated Tests
- Run `./gradlew test` to ensure existing logic holds.

### Manual Verification
1. **State Reset**: Perform a search, go to list, click "Search Again" (magnifying glass) -> verify Home screen is empty and ready for new search.
2. **Back Navigation**: Verify system back button and toolbar back arrow both return to Home without getting stuck.
3. **Persistence**: Check a few items, close app, reopen -> verify checkboxes remain checked.
4. **UI Styling**: Verify Checkbox has primary background when checked and a white tick.
5. **Share**: Click FAB -> Verify share sheet opens with formatted text.

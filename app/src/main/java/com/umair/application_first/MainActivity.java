package com.umair.application_first;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    private MapView map;
    private MyLocationNewOverlay locationOverlay;
    private Polyline roadOverlay;
    private GeoPoint searchedPoint;
    private Road currentRoad;
    private ImageButton btnMyLocation, btnShowPath;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private List<Marker> markers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        // Buttons
        ImageButton btnWalk = findViewById(R.id.btnWalk);
        ImageButton btnBike = findViewById(R.id.btnBike);
        ImageButton btnCar = findViewById(R.id.btnCar);

        btnMyLocation = findViewById(R.id.btnMyLocation);
        btnShowPath = findViewById(R.id.btnShowPath);

        LinearLayout bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetBehavior.setSkipCollapsed(true);
        bottomSheetBehavior.setHideable(true);

        btnMyLocation.setOnClickListener(v -> showCurrentLocation());

        btnShowPath.setOnClickListener(v -> {
            GeoPoint current = locationOverlay.getMyLocation();
            if (current != null && searchedPoint != null) {
                clearMap();
                addMarker(current, "You are here");
                addMarker(searchedPoint, "Destination");
                // Draw minimum distance path initially
                drawInitialRoute(current, searchedPoint);

                bottomSheet.setVisibility(LinearLayout.VISIBLE);
            } else {
                Toast.makeText(this, "Current or searched location missing", Toast.LENGTH_SHORT).show();
            }
        });

        btnWalk.setOnClickListener(v -> updateDurationForMode("walk", R.drawable.ic_walk, "You (Walking)"));
        btnBike.setOnClickListener(v -> updateDurationForMode("bike", R.drawable.ic_bike, "You (Biking)"));
        btnCar.setOnClickListener(v -> updateDurationForMode("car", R.drawable.ic_car, "You (Driving)"));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        } else {
            initializeMap();
        }

        EditText searchLocation = findViewById(R.id.searchLocation);
        searchLocation.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String locationName = searchLocation.getText().toString();
                if (!locationName.isEmpty()) {
                    findLocationAndMark(locationName);
                    hideKeyboard();
                }
                return true;
            }
            return false;
        });
    }

    private void initializeMap() {
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        map.setMinZoomLevel(3.0);
        map.setMaxZoomLevel(20.0);

        BoundingBox boundingBox = new BoundingBox(
                85.0,
                180.0,
                -85.0,
                -180.0
        );
        map.setScrollableAreaLimitDouble(boundingBox);

        map.getController().setZoom(6.0);

        LinearLayout bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheet.setVisibility(LinearLayout.GONE);

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        locationOverlay.enableMyLocation();
    }

    private void showCurrentLocation() {
        GeoPoint currentLocation = locationOverlay.getMyLocation();
        if (currentLocation != null) {
            removeCurrentMarkers();
            addMarker(currentLocation, "You are here");
            map.getController().setZoom(15.0);
            map.getController().animateTo(currentLocation);
        } else {
            Toast.makeText(this, "Current location not available yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void findLocationAndMark(String locationName) {
        try {
            List<Address> addresses = new Geocoder(this, Locale.getDefault()).getFromLocationName(locationName, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                searchedPoint = new GeoPoint(address.getLatitude(), address.getLongitude());
                clearMap();
                addMarker(searchedPoint, locationName);
                map.getController().animateTo(searchedPoint);
                map.getController().setZoom(15.0);
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error finding location", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawInitialRoute(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            try {
                String userAgent = Configuration.getInstance().getUserAgentValue();
                OSRMRoadManager roadManager = new OSRMRoadManager(this, userAgent);
                roadManager.setMean(OSRMRoadManager.MEAN_BY_CAR); // minimum distance path

                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);

                Road road = roadManager.getRoad(waypoints);
                currentRoad = road; // store for later use

                runOnUiThread(() -> {
                    if (road != null && road.mStatus == Road.STATUS_OK) {
                        if (roadOverlay != null) map.getOverlays().remove(roadOverlay);
                        roadOverlay = RoadManager.buildRoadOverlay(road);
                        map.getOverlays().add(roadOverlay);

                        // Only distance, no duration yet
                        String distanceText = String.format(Locale.getDefault(),
                                "%.2f km", road.mLength);

                        showRouteInfo(distanceText);
                        map.invalidate();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateDurationForMode(String mode, int iconResId, String title) {
        GeoPoint current = locationOverlay.getMyLocation();
        if (current == null || searchedPoint == null || currentRoad == null) return;

        removeCurrentMarkers();

        // Current location marker
        Marker currentMarker = new Marker(map);
        currentMarker.setPosition(current);
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentMarker.setTitle(title);
        currentMarker.setIcon(getResources().getDrawable(iconResId));
        map.getOverlays().add(currentMarker);
        markers.add(currentMarker);

        // Recalculate duration based on mode
        double distance = currentRoad.mLength; // use Road object
        double durationMin = 0;

        switch (mode) {
            case "walk":
                durationMin = distance / 5 * 60; // 5 km/h
                break;
            case "bike":
                durationMin = distance / 15 * 60; // 15 km/h
                break;
            case "car":
                durationMin = distance / 40 * 60; // 40 km/h approx
                break;
        }

        String distanceText = String.format(Locale.getDefault(),
                "%.2f km | %.0f min", distance, durationMin);
        showRouteInfo(distanceText);
    }


    private void removeCurrentMarkers() {
        List<Marker> toRemove = new ArrayList<>();
        for (Marker m : markers) {
            if (m.getTitle() != null && m.getTitle().startsWith("You")) {
                map.getOverlays().remove(m);
                toRemove.add(m);
            }
        }
        markers.removeAll(toRemove);
        map.invalidate();
    }

    private void showRouteInfo(String distanceText) {
        TextView tvDistance = findViewById(R.id.tvDistance);
        tvDistance.setText("Distance: " + distanceText);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void addMarker(GeoPoint point, String title) {
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(title);
        map.getOverlays().add(marker);
        markers.add(marker);
        map.invalidate();
    }

    private void clearMap() {
        if (roadOverlay != null) map.getOverlays().remove(roadOverlay);
        roadOverlay = null;
        for (Marker m : markers) map.getOverlays().remove(m);
        markers.clear();
        map.invalidate();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null)
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                initializeMap();
            else
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}

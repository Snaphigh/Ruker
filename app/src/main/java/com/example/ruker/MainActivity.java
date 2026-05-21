package com.example.ruker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LatLng lastLatLng;
    private boolean isFirstLocationUpdate = true;

    private TerrainClassifier terrainClassifier;
    private int lastClassificationColor = Color.GRAY;
    private String lastClassificationLabel = "Idle";

    private TextView statusText;
    private TextView timerText;
    private MaterialButton recordButton;
    private MaterialButton communityButton;
    private MaterialButton profileButton;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    private boolean isRecording = false;
    private boolean isShowingCommunity = false;
    private String currentRunId;
    private String lastLoadedDocId;
    private Timestamp startTimestamp;
    private final List<Map<String, Object>> currentRunPath = new ArrayList<>();
    private final List<Polyline> communityPolylines = new ArrayList<>();
    private final List<Polyline> myPathPolylines = new ArrayList<>();

    private long startTime = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = SystemClock.uptimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            timerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
            timerHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        userId = currentUser.getUid();
        db = FirebaseFirestore.getInstance();

        statusText = findViewById(R.id.statusText);
        timerText = findViewById(R.id.timerText);
        recordButton = findViewById(R.id.recordButton);
        communityButton = findViewById(R.id.communityButton);
        profileButton = findViewById(R.id.profileButton);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationUpdates();

        terrainClassifier = new TerrainClassifier(this, (label, color, confidence) -> {
            lastClassificationLabel = label;
            lastClassificationColor = SettingsActivity.getTerrainColor(this, label);
            runOnUiThread(() -> statusText.setText(label));
        });

        recordButton.setOnClickListener(v -> {
            if (!isRecording) showSecurePhoneDialog();
            else stopRecording();
        });

        communityButton.setOnClickListener(v -> toggleCommunityMap());

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivityForResult(intent, MyPathsActivity.REQUEST_SHOW_PATH);
        });

        SettingsActivity.applyFontSize(this, recordButton, statusText, timerText);
        
        getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsActivity.KEY_COLORBLIND.equals(key)) {
            runOnUiThread(() -> {
                if (isShowingCommunity) {
                    // Re-draw community paths to update styles instantly
                    fetchCommunityPaths();
                }
                if (lastLoadedDocId != null) {
                    // Re-draw last loaded path to update styles instantly
                    loadAndShowPathOnMap(lastLoadedDocId);
                }
            });
        } else if (SettingsActivity.KEY_FONT_SIZE.equals(key)) {
            SettingsActivity.applyFontSize(this, recordButton, statusText, timerText);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MyPathsActivity.REQUEST_SHOW_PATH && resultCode == RESULT_OK && data != null) {
            String docId = data.getStringExtra(MyPathsActivity.EXTRA_PATH_DOC_ID);
            if (docId != null) loadAndShowPathOnMap(docId);
        }
    }

    private void loadAndShowPathOnMap(String docId) {
        lastLoadedDocId = docId;
        db.collection("recorded_paths").document(docId).get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                Toast.makeText(this, R.string.path_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            clearMyPathPolylines();
            List<Map<String, Object>> path = (List<Map<String, Object>>) documentSnapshot.get("path");
            if (path == null || path.size() < 2) {
                Toast.makeText(this, R.string.insufficient_points, Toast.LENGTH_SHORT).show();
                return;
            }

            for (int i = 0; i < path.size() - 1; i++) {
                Map<String, Object> p1 = path.get(i);
                Map<String, Object> p2 = path.get(i + 1);
                try {
                    LatLng l1 = new LatLng(((Number) p1.get("latitude")).doubleValue(), ((Number) p1.get("longitude")).doubleValue());
                    LatLng l2 = new LatLng(((Number) p2.get("latitude")).doubleValue(), ((Number) p2.get("longitude")).doubleValue());
                    String terrain = (String) p1.get("terrain_type");
                    
                    myPathPolylines.add(mMap.addPolyline(new PolylineOptions().add(l1, l2).width(15)
                            .color(SettingsActivity.getTerrainColor(this, terrain))
                            .pattern(SettingsActivity.getTerrainPattern(this, terrain))));
                } catch (Exception e) { Log.e("Map", "Error drawing path", e); }
            }
        });
    }

    private void clearMyPathPolylines() {
        for (Polyline p : myPathPolylines) p.remove();
        myPathPolylines.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SettingsActivity.applyFontSize(this, recordButton, statusText, timerText);
        if (isRecording) terrainClassifier.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        terrainClassifier.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(this);
        terrainClassifier.shutdown();
    }

    private void toggleCommunityMap() {
        if (!isShowingCommunity) fetchCommunityPaths();
        else {
            clearCommunityPaths();
            isShowingCommunity = false;
            Toast.makeText(this, R.string.community_hidden, Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchCommunityPaths() {
        communityButton.setEnabled(false);
        Toast.makeText(this, R.string.loading_community, Toast.LENGTH_SHORT).show();
        
        db.collection("recorded_paths").whereEqualTo("is_public", true).get().addOnSuccessListener(snapshots -> {
            clearCommunityPaths();
            for (QueryDocumentSnapshot doc : snapshots) {
                drawPathFromData(doc.getData());
            }
            communityButton.setEnabled(true);
            isShowingCommunity = true;
        }).addOnFailureListener(e -> communityButton.setEnabled(true));
    }

    private void drawPathFromData(Map<String, Object> data) {
        List<Map<String, Object>> path = (List<Map<String, Object>>) data.get("path");
        if (path == null) return;
        for (int i = 0; i < path.size() - 1; i++) {
            Map<String, Object> p1 = path.get(i);
            Map<String, Object> p2 = path.get(i + 1);
            try {
                LatLng l1 = new LatLng(((Number) p1.get("latitude")).doubleValue(), ((Number) p1.get("longitude")).doubleValue());
                LatLng l2 = new LatLng(((Number) p2.get("latitude")).doubleValue(), ((Number) p2.get("longitude")).doubleValue());
                String type = (String) p1.get("terrain_type");
                
                communityPolylines.add(mMap.addPolyline(new PolylineOptions().add(l1, l2).width(10)
                        .color(SettingsActivity.getTerrainColor(this, type))
                        .pattern(SettingsActivity.getTerrainPattern(this, type))));
            } catch (Exception ignored) {}
        }
    }

    private void clearCommunityPaths() {
        for (Polyline p : communityPolylines) p.remove();
        communityPolylines.clear();
    }

    private void showSecurePhoneDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.secure_phone)
            .setMessage(R.string.secure_phone_msg)
            .setPositiveButton("OK", (dialog, which) -> startCountdown())
            .setCancelable(false).show();
    }

    private void startCountdown() {
        recordButton.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        recordButton.setEnabled(false);
        final int[] secondsLeft = {5};
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (secondsLeft[0] > 0) {
                    recordButton.setText(String.format(Locale.US, "Recording in %d...", secondsLeft[0]));
                    secondsLeft[0]--;
                    handler.postDelayed(this, 1000);
                } else startRecording();
            }
        });
    }

    private void startRecording() {
        isRecording = true;
        recordButton.setEnabled(true); recordButton.setText(R.string.stop_recording);
        startTime = SystemClock.uptimeMillis(); startTimestamp = Timestamp.now(); currentRunId = UUID.randomUUID().toString();
        timerHandler.postDelayed(timerRunnable, 0);
        currentRunPath.clear(); if (mMap != null) mMap.clear();
        clearCommunityPaths(); clearMyPathPolylines();
        isShowingCommunity = false;
        terrainClassifier.start();
    }

    private void stopRecording() {
        terrainClassifier.stop();
        isRecording = false;
        timerHandler.removeCallbacks(timerRunnable);
        recordButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#388E3C")));
        recordButton.setText(R.string.start_recording); timerText.setText("00:00");
        showUploadOptionsDialog();
    }

    private void showUploadOptionsDialog() {
        if (currentRunPath.isEmpty()) { 
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show(); 
            return; 
        }
        String[] options = {getString(R.string.upload_community), getString(R.string.save_privately), getString(R.string.discard)};
        new AlertDialog.Builder(this)
            .setTitle(R.string.finished)
            .setItems(options, (dialog, which) -> {
                if (which == 0) uploadRunToFirebase(true);
                else if (which == 1) uploadRunToFirebase(false);
            })
            .setCancelable(false).show();
    }

    private void uploadRunToFirebase(boolean isPublic) {
        Map<String, Object> data = new HashMap<>();
        data.put("run_id", currentRunId); data.put("user_id", userId);
        data.put("start_time", startTimestamp); data.put("path", new ArrayList<>(currentRunPath)); data.put("is_public", isPublic);
        db.collection("recorded_paths").add(data).addOnSuccessListener(d -> 
            Toast.makeText(this, isPublic ? R.string.uploaded_community : R.string.saved_private, Toast.LENGTH_SHORT).show()
        ).addOnFailureListener(e -> Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show());
    }

    private void setupLocationUpdates() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location loc : result.getLocations()) {
                    LatLng curr = new LatLng(loc.getLatitude(), loc.getLongitude());
                    if (isFirstLocationUpdate && mMap != null) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(curr, 19f));
                        isFirstLocationUpdate = false;
                    }
                    if (isRecording && lastLatLng != null && mMap != null) {
                        float[] dist = new float[1];
                        Location.distanceBetween(lastLatLng.latitude, lastLatLng.longitude, curr.latitude, curr.longitude, dist);
                        if (dist[0] >= 2.0f) {
                            mMap.addPolyline(new PolylineOptions().add(lastLatLng, curr).width(15)
                                    .color(lastClassificationColor)
                                    .pattern(SettingsActivity.getTerrainPattern(MainActivity.this, lastClassificationLabel)));
                            
                            mMap.animateCamera(CameraUpdateFactory.newLatLng(curr));
                            if (!"Idle".equals(lastClassificationLabel)) {
                                Map<String, Object> p = new HashMap<>();
                                p.put("latitude", loc.getLatitude()); p.put("longitude", loc.getLongitude());
                                p.put("terrain_type", lastClassificationLabel); p.put("timestamp", Timestamp.now());
                                currentRunPath.add(p);
                            }
                        }
                    }
                    lastLatLng = curr;
                }
            }
        };
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.requestLocationUpdates(new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500).setMinUpdateIntervalMillis(250).build(), locationCallback, Looper.getMainLooper());
        } else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }
}

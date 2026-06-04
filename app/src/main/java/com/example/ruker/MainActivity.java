package com.example.ruker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    private MapManager mapManager;
    private FirestoreManager firestoreManager;
    private TerrainClassifier terrainClassifier;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    
    private LatLng lastLatLng;
    private boolean isFirstLocationUpdate = true;
    private boolean isRecording = false;
    private boolean isShowingCommunity = false;
    private String currentRunId;
    private String lastLoadedDocId;
    private Timestamp startTimestamp;
    private final List<Map<String, Object>> currentRunPath = new ArrayList<>();

    private TextView statusText, timerText;
    private MaterialButton recordButton;

    private long startTime = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = SystemClock.uptimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            timerText.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
            timerHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initManagers();
        initUI();
        setupLocationUpdates();

        ((SupportMapFragment) Objects.requireNonNull(getSupportFragmentManager().findFragmentById(R.id.map)))
                .getMapAsync(this);

        getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);
    }

    private void initManagers() {
        firestoreManager = new FirestoreManager();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        terrainClassifier = new TerrainClassifier(this, (label, color, confidence) -> 
            runOnUiThread(() -> statusText.setText(label)));
    }

    private void initUI() {
        statusText = findViewById(R.id.statusText);
        timerText = findViewById(R.id.timerText);
        recordButton = findViewById(R.id.recordButton);
        MaterialButton communityButton = findViewById(R.id.communityButton);
        MaterialButton profileButton = findViewById(R.id.profileButton);

        recordButton.setOnClickListener(v -> toggleRecording());
        communityButton.setOnClickListener(v -> toggleCommunityMap());
        profileButton.setOnClickListener(v -> 
            startActivityForResult(new Intent(this, ProfileActivity.class), MyPathsActivity.REQUEST_SHOW_PATH));

        SettingsActivity.applyFontSize(this, recordButton, statusText, timerText);
    }

    private void toggleRecording() {
        if (!isRecording) showSecurePhoneDialog();
        else stopRecording();
    }

    private void toggleCommunityMap() {
        isShowingCommunity = !isShowingCommunity;
        if (isShowingCommunity) {
            Toast.makeText(this, R.string.loading_community, Toast.LENGTH_SHORT).show();
            firestoreManager.fetchCommunityPaths(paths -> {
                mapManager.clearCommunityPaths();
                for (Map<String, Object> path : paths) {
                    mapManager.addPathToMap((List<Map<String, Object>>) path.get("path"), true);
                }
            }, e -> Toast.makeText(this, "Failed to load community map", Toast.LENGTH_SHORT).show());
        } else {
            mapManager.clearCommunityPaths();
            Toast.makeText(this, R.string.community_hidden, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsActivity.KEY_COLORBLIND.equals(key) || SettingsActivity.KEY_FONT_SIZE.equals(key)) {
            runOnUiThread(() -> {
                if (SettingsActivity.KEY_FONT_SIZE.equals(key)) {
                    SettingsActivity.applyFontSize(this, recordButton, statusText, timerText);
                } else {
                    if (isShowingCommunity) toggleCommunityMap(); // Refresh community
                    if (lastLoadedDocId != null) loadAndShowPathOnMap(lastLoadedDocId);
                }
            });
        }
    }

    private void showSecurePhoneDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.secure_phone)
            .setMessage(R.string.secure_phone_msg)
            .setPositiveButton("OK", (dialog, which) -> startCountdown())
            .setCancelable(false).show();
    }

    private void startCountdown() {
        recordButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.RED));
        recordButton.setEnabled(false);
        final int[] secondsLeft = {5};
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (secondsLeft[0] > 0) {
                    recordButton.setText(String.format(Locale.US, "Recording in %d...", secondsLeft[0]--));
                    handler.postDelayed(this, 1000);
                } else startRecording();
            }
        });
    }

    private void startRecording() {
        isRecording = true;
        recordButton.setEnabled(true);
        recordButton.setText(R.string.stop_recording);
        startTime = SystemClock.uptimeMillis();
        startTimestamp = Timestamp.now();
        currentRunId = UUID.randomUUID().toString();
        timerHandler.postDelayed(timerRunnable, 0);
        currentRunPath.clear();
        mapManager.clearAll();
        isShowingCommunity = false;
        terrainClassifier.start();
    }

    private void stopRecording() {
        terrainClassifier.stop();
        isRecording = false;
        timerHandler.removeCallbacks(timerRunnable);
        recordButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#388E3C")));
        recordButton.setText(R.string.start_recording);
        timerText.setText("00:00");
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
                if (which < 2) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("run_id", currentRunId);
                    data.put("start_time", startTimestamp);
                    data.put("path", new ArrayList<>(currentRunPath));
                    data.put("is_public", which == 0);
                    firestoreManager.saveRun(data, 
                        ref -> Toast.makeText(this, which == 0 ? R.string.uploaded_community : R.string.saved_private, Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show());
                }
            }).setCancelable(false).show();
    }

    private void setupLocationUpdates() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location loc : result.getLocations()) {
                    LatLng curr = new LatLng(loc.getLatitude(), loc.getLongitude());
                    if (isFirstLocationUpdate) {
                        mapManager.moveCamera(curr, 19f);
                        isFirstLocationUpdate = false;
                    }
                    if (isRecording && lastLatLng != null) {
                        float[] dist = new float[1];
                        Location.distanceBetween(lastLatLng.latitude, lastLatLng.longitude, curr.latitude, curr.longitude, dist);
                        if (dist[0] >= 2.0f) {
                            String label = statusText.getText().toString();
                            mapManager.drawRecordingSegment(lastLatLng, curr, SettingsActivity.getTerrainColor(MainActivity.this, label), label);
                            mapManager.animateCamera(curr, -1f);
                            Map<String, Object> point = new HashMap<>();
                            point.put("latitude", loc.getLatitude());
                            point.put("longitude", loc.getLongitude());
                            point.put("terrain_type", label);
                            point.put("timestamp", Timestamp.now());
                            currentRunPath.add(point);
                        }
                    }
                    lastLatLng = curr;
                }
            }
        };
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mapManager = new MapManager(this, googleMap);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            fusedLocationClient.requestLocationUpdates(new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
                    .setMinUpdateIntervalMillis(250).build(), locationCallback, Looper.getMainLooper());
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
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
        firestoreManager.fetchPathDetails(docId, doc -> {
            if (!doc.exists()) return;
            mapManager.clearMyPathPolylines();
            mapManager.addPathToMap((List<Map<String, Object>>) doc.get("path"), false);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
}

package com.example.ruker;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapManager {
    private static final String TAG = "MapManager";
    private final GoogleMap mMap;
    private final Context context;
    private final List<Polyline> communityPolylines = new ArrayList<>();
    private final List<Polyline> myPathPolylines = new ArrayList<>();

    public MapManager(Context context, GoogleMap googleMap) {
        this.context = context;
        this.mMap = googleMap;
    }

    public void clearAll() {
        mMap.clear();
        communityPolylines.clear();
        myPathPolylines.clear();
    }

    public void drawRecordingSegment(LatLng start, LatLng end, int color, String terrainLabel) {
        PolylineOptions options = new PolylineOptions()
                .add(start, end)
                .color(color)
                .width(15)
                .pattern(SettingsActivity.getTerrainPattern(context, terrainLabel));
        mMap.addPolyline(options);
    }

    public void addPathToMap(List<Map<String, Object>> pathData, boolean isCommunity) {
        if (pathData == null || pathData.size() < 2) return;

        List<Polyline> targetList = isCommunity ? communityPolylines : myPathPolylines;
        int width = isCommunity ? 10 : 15;

        for (int i = 0; i < pathData.size() - 1; i++) {
            Map<String, Object> p1 = pathData.get(i);
            Map<String, Object> p2 = pathData.get(i + 1);
            try {
                LatLng l1 = new LatLng(((Number) p1.get("latitude")).doubleValue(), ((Number) p1.get("longitude")).doubleValue());
                LatLng l2 = new LatLng(((Number) p2.get("latitude")).doubleValue(), ((Number) p2.get("longitude")).doubleValue());
                String terrain = (String) p1.get("terrain_type");

                PolylineOptions options = new PolylineOptions()
                        .add(l1, l2)
                        .width(width)
                        .color(SettingsActivity.getTerrainColor(context, terrain))
                        .pattern(SettingsActivity.getTerrainPattern(context, terrain));

                targetList.add(mMap.addPolyline(options));
            } catch (Exception e) {
                Log.e(TAG, "Error drawing path segment", e);
            }
        }
    }

    public void clearCommunityPaths() {
        for (Polyline p : communityPolylines) p.remove();
        communityPolylines.clear();
    }

    public void clearMyPathPolylines() {
        for (Polyline p : myPathPolylines) p.remove();
        myPathPolylines.clear();
    }

    public void animateCamera(LatLng location, float zoom) {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom));
    }

    public void moveCamera(LatLng location, float zoom) {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, zoom));
    }
}

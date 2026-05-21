package com.example.ruker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "ruker_prefs";
    public static final String KEY_COLORBLIND = "colorblind_mode";
    public static final String KEY_FONT_SIZE = "font_size";

    public static final String FONT_SMALL = "small";
    public static final String FONT_MEDIUM = "medium";
    public static final String FONT_LARGE = "large";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        SwitchMaterial colorblindSwitch = findViewById(R.id.colorblindSwitch);
        RadioGroup fontSizeGroup = findViewById(R.id.fontSizeGroup);

        colorblindSwitch.setChecked(prefs.getBoolean(KEY_COLORBLIND, false));

        String savedFont = prefs.getString(KEY_FONT_SIZE, FONT_MEDIUM);
        switch (savedFont) {
            case FONT_SMALL:  fontSizeGroup.check(R.id.fontSmall);  break;
            case FONT_LARGE:  fontSizeGroup.check(R.id.fontLarge);  break;
            default:          fontSizeGroup.check(R.id.fontMedium); break;
        }

        colorblindSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_COLORBLIND, isChecked).apply()
        );

        fontSizeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String newSize;
            if (checkedId == R.id.fontSmall) {
                newSize = FONT_SMALL;
            } else if (checkedId == R.id.fontLarge) {
                newSize = FONT_LARGE;
            } else {
                newSize = FONT_MEDIUM;
            }
            prefs.edit().putString(KEY_FONT_SIZE, newSize).apply();
        });
    }

    public static int getTerrainColor(Context context, String terrain) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean pathTypeMode = prefs.getBoolean(KEY_COLORBLIND, false);

        if (pathTypeMode) {
            return Color.BLACK;
        }

        if (terrain == null) return Color.GRAY;

        switch (terrain) {
            case "Smooth": return Color.argb(200, 76, 175, 80); // Green
            case "Tough":  return Color.argb(200, 255, 165, 0); // Orange
            case "Rough":  return Color.argb(200, 244, 67, 54); // Red
            default:       return Color.argb(120, 128, 128, 128);
        }
    }

    public static List<PatternItem> getTerrainPattern(Context context, String terrain) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean pathTypeMode = prefs.getBoolean(KEY_COLORBLIND, false);
        if (!pathTypeMode) return null;

        if (terrain == null) return null;

        switch (terrain) {
            case "Tough":
                return Arrays.asList(new Dot(), new Gap(10));
            case "Rough":
                return Arrays.asList(new Dot(), new Gap(30));
            case "Smooth":
            default:
                return null;
        }
    }

    public static void applyFontSize(Context context, TextView... views) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String fontSize = prefs.getString(KEY_FONT_SIZE, FONT_MEDIUM);
        float sp;
        switch (fontSize) {
            case FONT_SMALL: sp = 11f; break;
            case FONT_LARGE: sp = 16f; break;
            default:         sp = 13f; break;
        }
        for (TextView v : views) {
            if (v != null) v.setTextSize(sp);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

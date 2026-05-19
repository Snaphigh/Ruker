package com.example.ruker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;


public class SettingsActivity extends AppCompatActivity {


    public static final String PREFS_NAME   = "ruker_prefs";
    public static final String KEY_COLORBLIND = "colorblind_mode";
    public static final String KEY_FONT_SIZE  = "font_size";

    public static final String FONT_SMALL  = "small";
    public static final String FONT_MEDIUM = "medium";
    public static final String FONT_LARGE  = "large";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Puščica
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Nastavitve");
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


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
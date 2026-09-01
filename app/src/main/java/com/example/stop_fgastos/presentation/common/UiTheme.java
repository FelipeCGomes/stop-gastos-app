package com.example.stop_fgastos.presentation.common;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class UiTheme {
    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static final String PREFS = "stop_gastos_ui";
    private static final String KEY_THEME = "theme";

    private UiTheme() {}

    public static void apply(Context context) {
        setDelegateMode(mode(context));
    }

    public static String mode(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.getString(KEY_THEME, SYSTEM);
    }

    public static void set(Context context, String mode) {
        String safe = LIGHT.equals(mode) || DARK.equals(mode) ? mode : SYSTEM;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, safe)
                .apply();
        setDelegateMode(safe);
    }

    private static void setDelegateMode(String mode) {
        switch (mode) {
            case LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}

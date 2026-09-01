package com.example.stop_fgastos.presentation.common;

import android.content.Context;
import android.content.SharedPreferences;

public final class UiPrivacy {
    private static final String PREFS = "stop_gastos_ui";
    private static final String KEY_DASHBOARD_PRIVACY = "dashboard_privacy";

    private UiPrivacy() {}

    public static boolean enabled(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.getBoolean(KEY_DASHBOARD_PRIVACY, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DASHBOARD_PRIVACY, enabled)
                .apply();
    }
}

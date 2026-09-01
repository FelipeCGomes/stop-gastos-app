package com.example.stop_fgastos.presentation.common;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

public final class UiMotion {
    private UiMotion() {}

    public static void enter(View view) {
        if (view == null) return;
        float distance = 10f * view.getResources().getDisplayMetrics().density;
        view.setAlpha(0f);
        view.setTranslationY(distance);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public static void pop(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(1.025f)
                .scaleY(1.025f)
                .setDuration(100L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .start())
                .start();
    }
}

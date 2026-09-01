package com.example.stop_fgastos.presentation.reports;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.stop_fgastos.R;

import java.util.ArrayList;
import java.util.List;

public final class FinanceBarChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Double> values = new ArrayList<>();
    private float progress = 1f;

    public FinanceBarChartView(Context context) { this(context, null); }

    public FinanceBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        axisPaint.setColor(ContextCompat.getColor(context, R.color.line));
        axisPaint.setStrokeWidth(dp(1));
    }

    public void setValues(List<Double> newValues) {
        values.clear();
        if (newValues != null) values.addAll(newValues);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(460L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float bottom = height - dp(10);
        for (int i = 0; i < 3; i++) {
            float y = dp(8) + (bottom - dp(8)) * i / 2f;
            canvas.drawLine(dp(4), y, width - dp(4), y, axisPaint);
        }
        if (values.isEmpty()) return;

        double max = 1.0;
        for (double value : values) max = Math.max(max, value);
        float gap = dp(8);
        float usable = width - gap * (values.size() + 1);
        float barWidth = Math.max(dp(8), usable / values.size());

        int start = ContextCompat.getColor(getContext(), R.color.primary_2);
        int end = ContextCompat.getColor(getContext(), R.color.primary);

        for (int i = 0; i < values.size(); i++) {
            float left = gap + i * (barWidth + gap);
            float barHeight = (float) (values.get(i) / max) * (bottom - dp(18)) * progress;
            float top = bottom - barHeight;
            barPaint.setShader(new LinearGradient(left, bottom, left, top, start, end, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(left, top, left + barWidth, bottom), dp(5), dp(5), barPaint);
        }
        barPaint.setShader(null);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

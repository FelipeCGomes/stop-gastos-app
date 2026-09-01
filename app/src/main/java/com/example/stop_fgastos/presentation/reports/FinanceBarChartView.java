package com.example.stop_fgastos.presentation.reports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.stop_fgastos.R;

import java.util.ArrayList;
import java.util.List;

public final class FinanceBarChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Double> values = new ArrayList<>();

    public FinanceBarChartView(Context context) {
        this(context, null);
    }

    public FinanceBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(context.getColor(R.color.primary));
        axisPaint.setColor(context.getColor(R.color.surface_variant));
        axisPaint.setStrokeWidth(dp(1));
    }

    public void setValues(List<Double> newValues) {
        values.clear();
        if (newValues != null) values.addAll(newValues);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float bottom = height - dp(10);

        canvas.drawLine(dp(4), bottom, width - dp(4), bottom, axisPaint);
        if (values.isEmpty()) return;

        double max = 1.0;
        for (double value : values) max = Math.max(max, value);

        float gap = dp(8);
        float usable = width - gap * (values.size() + 1);
        float barWidth = Math.max(dp(8), usable / values.size());

        for (int i = 0; i < values.size(); i++) {
            float left = gap + i * (barWidth + gap);
            float barHeight = (float) (values.get(i) / max) * (bottom - dp(14));
            canvas.drawRoundRect(
                    left,
                    bottom - barHeight,
                    left + barWidth,
                    bottom,
                    dp(4),
                    dp(4),
                    barPaint
            );
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

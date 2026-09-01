package com.example.stop_fgastos.presentation.dashboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.stop_fgastos.R;

import java.util.ArrayList;
import java.util.List;

public final class FinanceTrendChartView extends View {
    private final List<Double> income = new ArrayList<>();
    private final List<Double> expense = new ArrayList<>();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 1f;

    public FinanceTrendChartView(Context context) { this(context, null); }

    public FinanceTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.line));
        gridPaint.setStrokeWidth(dp(1f));
        incomePaint.setColor(ContextCompat.getColor(context, R.color.income));
        expensePaint.setColor(ContextCompat.getColor(context, R.color.expense));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_muted));
        labelPaint.setTextSize(dp(9f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setSeries(List<Double> incomeValues, List<Double> expenseValues) {
        income.clear();
        expense.clear();
        if (incomeValues != null) income.addAll(incomeValues);
        if (expenseValues != null) expense.addAll(expenseValues);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(520L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (income.isEmpty() && expense.isEmpty()) return;
        float left = dp(8), right = getWidth() - dp(8), top = dp(10), bottom = getHeight() - dp(24);
        for (int i = 0; i <= 3; i++) {
            float y = top + ((bottom - top) / 3f) * i;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        double max = 1.0;
        for (double value : income) max = Math.max(max, value);
        for (double value : expense) max = Math.max(max, value);
        int count = Math.max(income.size(), expense.size());
        if (count == 0) return;

        float slot = (right - left) / count;
        float pairWidth = Math.min(dp(28), slot * 0.60f);
        float barWidth = Math.max(dp(5), pairWidth * 0.42f);
        float gap = Math.max(dp(2), pairWidth * 0.16f);
        String[] labels = {"-5", "-4", "-3", "-2", "-1", "Atual"};

        for (int i = 0; i < count; i++) {
            float center = left + slot * i + slot / 2f;
            double in = i < income.size() ? income.get(i) : 0;
            double out = i < expense.size() ? expense.get(i) : 0;
            float inHeight = (float) (in / max) * (bottom - top) * progress;
            float outHeight = (float) (out / max) * (bottom - top) * progress;
            canvas.drawRoundRect(new RectF(center - gap / 2f - barWidth, bottom - inHeight, center - gap / 2f, bottom), dp(4), dp(4), incomePaint);
            canvas.drawRoundRect(new RectF(center + gap / 2f, bottom - outHeight, center + gap / 2f + barWidth, bottom), dp(4), dp(4), expensePaint);
            canvas.drawText(i < labels.length ? labels[i] : "", center, getHeight() - dp(6), labelPaint);
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

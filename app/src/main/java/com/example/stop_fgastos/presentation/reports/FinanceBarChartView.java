package com.example.stop_fgastos.presentation.reports;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.stop_fgastos.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FinanceBarChartView extends View {
    private final List<String> labels = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bottomLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float progress = 1f;
    private int selectedIndex = -1;

    public FinanceBarChartView(Context context) {
        this(context, null);
    }

    public FinanceBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);

        axisPaint.setColor(ContextCompat.getColor(context, R.color.line));
        axisPaint.setStrokeWidth(dp(1));

        axisLabelPaint.setColor(ContextCompat.getColor(context, R.color.text_muted));
        axisLabelPaint.setTextSize(dp(8f));
        axisLabelPaint.setTextAlign(Paint.Align.RIGHT);

        bottomLabelPaint.setColor(ContextCompat.getColor(context, R.color.text_muted));
        bottomLabelPaint.setTextSize(dp(8f));
        bottomLabelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextSize(dp(8f));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);

        tooltipPaint.setColor(ContextCompat.getColor(context, R.color.surface_3));
        tooltipTextPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        tooltipTextPaint.setTextSize(dp(9f));
        tooltipTextPaint.setFakeBoldText(true);
    }

    public void setData(List<String> newLabels, List<Double> newValues) {
        labels.clear();
        values.clear();

        if (newLabels != null) labels.addAll(newLabels);
        if (newValues != null) values.addAll(newValues);

        selectedIndex = -1;
        animateChart();
    }

    public void setValues(List<Double> newValues) {
        setData(null, newValues);
    }

    private void animateChart() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(500L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (values.isEmpty()) return;

        double max = 1.0;
        for (double value : values) max = Math.max(max, value);

        float left = dp(48);
        float right = getWidth() - dp(7);
        float top = selectedIndex >= 0 ? dp(58) : dp(24);
        float bottom = getHeight() - dp(34);

        for (int i = 0; i <= 3; i++) {
            float fraction = i / 3f;
            float y = top + (bottom - top) * fraction;
            canvas.drawLine(left, y, right, y, axisPaint);
            canvas.drawText(
                    compactMoney(max * (1.0 - fraction), true),
                    left - dp(6),
                    y + dp(3),
                    axisLabelPaint
            );
        }

        float slot = (right - left) / values.size();
        float barWidth = Math.min(dp(34), slot * 0.56f);

        int start = ContextCompat.getColor(getContext(), R.color.primary_2);
        int end = ContextCompat.getColor(getContext(), R.color.primary);

        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            float center = left + slot * i + slot / 2f;
            float barHeight = (float) (value / max) * (bottom - top) * progress;
            float barTop = bottom - barHeight;

            barPaint.setShader(new LinearGradient(
                    center,
                    bottom,
                    center,
                    barTop,
                    start,
                    end,
                    Shader.TileMode.CLAMP
            ));

            canvas.drawRoundRect(
                    new RectF(
                            center - barWidth / 2f,
                            barTop,
                            center + barWidth / 2f,
                            bottom
                    ),
                    dp(6),
                    dp(6),
                    barPaint
            );

            if (value > 0) {
                canvas.drawText(
                        compactMoney(value, false),
                        center,
                        Math.max(top + dp(10), barTop - dp(5)),
                        valuePaint
                );
            }

            String label = i < labels.size()
                    ? shorten(labels.get(i), 8)
                    : String.valueOf(i + 1);

            canvas.drawText(
                    label,
                    center,
                    getHeight() - dp(8),
                    bottomLabelPaint
            );
        }

        barPaint.setShader(null);

        if (selectedIndex >= 0 && selectedIndex < values.size()) {
            drawTooltip(canvas, selectedIndex, left, right);
        }
    }

    private void drawTooltip(Canvas canvas, int index, float left, float right) {
        String label = index < labels.size() ? labels.get(index) : "Categoria";
        double value = values.get(index);

        RectF box = new RectF(left, dp(4), right, dp(48));
        canvas.drawRoundRect(box, dp(11), dp(11), tooltipPaint);

        canvas.drawText(
                label + "   " + compactMoney(value, true),
                left + dp(10),
                dp(31),
                tooltipTextPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        if (values.isEmpty()) return super.onTouchEvent(event);

        float left = dp(48);
        float right = getWidth() - dp(7);
        float slot = (right - left) / values.size();

        int index = Math.round((event.getX() - left - slot / 2f) / slot);
        selectedIndex = Math.max(0, Math.min(values.size() - 1, index));

        invalidate();
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private String shorten(String text, int max) {
        if (text == null) return "";
        String clean = text.trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)) + "…";
    }

    private String compactMoney(double value, boolean currency) {
        double abs = Math.abs(value);
        String suffix = "";
        double display = value;

        if (abs >= 1_000_000) {
            display = value / 1_000_000.0;
            suffix = " mi";
        } else if (abs >= 1_000) {
            display = value / 1_000.0;
            suffix = " mil";
        }

        String number;
        if (Math.abs(display) >= 100 || Math.abs(display - Math.rint(display)) < 0.05) {
            number = String.format(Locale.getDefault(), "%.0f", display);
        } else {
            number = String.format(Locale.getDefault(), "%.1f", display);
        }

        return (currency ? "R$ " : "") + number + suffix;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

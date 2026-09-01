package com.example.stop_fgastos.presentation.dashboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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

public final class FinanceTrendChartView extends View {
    private final List<String> labels = new ArrayList<>();
    private final List<Double> income = new ArrayList<>();
    private final List<Double> expense = new ArrayList<>();

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float progress = 1f;
    private int selectedIndex = -1;
    private boolean privacyEnabled;

    public FinanceTrendChartView(Context context) {
        this(context, null);
    }

    public FinanceTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);

        gridPaint.setColor(ContextCompat.getColor(context, R.color.line));
        gridPaint.setStrokeWidth(dp(1f));

        incomePaint.setColor(ContextCompat.getColor(context, R.color.income));
        expensePaint.setColor(ContextCompat.getColor(context, R.color.expense));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_muted));
        labelPaint.setTextSize(dp(9f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        axisLabelPaint.setColor(ContextCompat.getColor(context, R.color.text_muted));
        axisLabelPaint.setTextSize(dp(8f));
        axisLabelPaint.setTextAlign(Paint.Align.RIGHT);

        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextSize(dp(7.5f));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);

        tooltipPaint.setColor(ContextCompat.getColor(context, R.color.surface_3));
        tooltipTextPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        tooltipTextPaint.setTextSize(dp(9f));
        tooltipTextPaint.setFakeBoldText(true);
    }

    public void setPrivacyEnabled(boolean enabled) {
        privacyEnabled = enabled;
        invalidate();
    }

    public void setSeries(
            List<String> monthLabels,
            List<Double> incomeValues,
            List<Double> expenseValues
    ) {
        labels.clear();
        income.clear();
        expense.clear();

        if (monthLabels != null) labels.addAll(monthLabels);
        if (incomeValues != null) income.addAll(incomeValues);
        if (expenseValues != null) expense.addAll(expenseValues);

        selectedIndex = -1;
        animateChart();
    }

    public void setSeries(List<Double> incomeValues, List<Double> expenseValues) {
        setSeries(null, incomeValues, expenseValues);
    }

    private void animateChart() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(560L);
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

        int count = Math.max(income.size(), expense.size());
        if (count == 0) return;

        double max = 1.0;
        for (double value : income) max = Math.max(max, value);
        for (double value : expense) max = Math.max(max, value);

        float left = dp(48);
        float right = getWidth() - dp(7);
        float top = selectedIndex >= 0 ? dp(58) : dp(22);
        float bottom = getHeight() - dp(30);

        for (int i = 0; i <= 3; i++) {
            float fraction = i / 3f;
            float y = top + (bottom - top) * fraction;
            canvas.drawLine(left, y, right, y, gridPaint);

            double axisValue = max * (1.0 - fraction);
            if (!privacyEnabled) {
                canvas.drawText(
                        compactMoney(axisValue, true),
                        left - dp(6),
                        y + dp(3),
                        axisLabelPaint
                );
            }
        }

        float slot = (right - left) / count;
        float pairWidth = Math.min(dp(34), slot * 0.68f);
        float barWidth = Math.max(dp(5), pairWidth * 0.42f);
        float gap = Math.max(dp(2), pairWidth * 0.16f);

        for (int i = 0; i < count; i++) {
            float center = left + slot * i + slot / 2f;
            double in = i < income.size() ? income.get(i) : 0;
            double out = i < expense.size() ? expense.get(i) : 0;

            float inHeight = (float) (in / max) * (bottom - top) * progress;
            float outHeight = (float) (out / max) * (bottom - top) * progress;

            float inLeft = center - gap / 2f - barWidth;
            float outLeft = center + gap / 2f;

            canvas.drawRoundRect(
                    new RectF(inLeft, bottom - inHeight, inLeft + barWidth, bottom),
                    dp(4),
                    dp(4),
                    incomePaint
            );
            canvas.drawRoundRect(
                    new RectF(outLeft, bottom - outHeight, outLeft + barWidth, bottom),
                    dp(4),
                    dp(4),
                    expensePaint
            );

            if (!privacyEnabled && in > 0) {
                canvas.drawText(
                        compactMoney(in, false),
                        inLeft + barWidth / 2f,
                        Math.max(top + dp(9), bottom - inHeight - dp(4)),
                        valuePaint
                );
            }

            if (!privacyEnabled && out > 0) {
                canvas.drawText(
                        compactMoney(out, false),
                        outLeft + barWidth / 2f,
                        Math.max(top + dp(9), bottom - outHeight - dp(4)),
                        valuePaint
                );
            }

            String month = i < labels.size() ? labels.get(i) : String.valueOf(i + 1);
            canvas.drawText(month, center, getHeight() - dp(7), labelPaint);
        }

        if (selectedIndex >= 0 && selectedIndex < count) {
            drawTooltip(canvas, selectedIndex, left, right);
        }
    }

    private void drawTooltip(Canvas canvas, int index, float left, float right) {
        String month = index < labels.size() ? labels.get(index) : "Mês";
        double in = index < income.size() ? income.get(index) : 0;
        double out = index < expense.size() ? expense.get(index) : 0;

        RectF box = new RectF(left, dp(4), right, dp(48));
        canvas.drawRoundRect(box, dp(11), dp(11), tooltipPaint);

        String text = privacyEnabled
                ? month + "   Valores ocultos"
                : month
                + "   Entradas " + compactMoney(in, true)
                + "   •   Saídas " + compactMoney(out, true);

        canvas.drawText(text, left + dp(10), dp(31), tooltipTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }

        int count = Math.max(income.size(), expense.size());
        if (count == 0) return super.onTouchEvent(event);

        float left = dp(48);
        float right = getWidth() - dp(7);
        float slot = (right - left) / count;

        int index = Math.round((event.getX() - left - slot / 2f) / slot);
        selectedIndex = Math.max(0, Math.min(count - 1, index));
        invalidate();
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
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

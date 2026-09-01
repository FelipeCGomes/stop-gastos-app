package com.example.stop_fgastos.presentation.common;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stop_fgastos.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.Holder> {
    public interface Listener {
        void onPrimary(DisplayRow row);
        default void onSecondary(DisplayRow row) {}
        void onDelete(DisplayRow row);
    }

    private final List<DisplayRow> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;

    public RecordAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<DisplayRow> rows) {
        items.clear();
        if (rows != null) items.addAll(rows);
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_record, parent, false)
        );
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DisplayRow row = items.get(position);

        holder.title.setText(row.title);
        holder.subtitle.setText(row.subtitle);
        holder.value.setText(row.value);

        holder.primary.setText(row.primaryLabel);
        holder.primary.setVisibility(row.primaryLabel.isBlank() ? View.GONE : View.VISIBLE);

        String secondary = row.secondaryLabel == null ? "" : row.secondaryLabel;
        boolean paymentAction = isPaymentAction(secondary);

        holder.payment.setVisibility(paymentAction ? View.VISIBLE : View.GONE);
        holder.aux.setVisibility(!paymentAction && !secondary.isBlank() ? View.VISIBLE : View.GONE);

        if (paymentAction) {
            holder.payment.setText(secondary);
            boolean undo = secondary.toLowerCase(Locale.ROOT).contains("desfazer");

            int background = ContextCompat.getColor(
                    holder.itemView.getContext(),
                    undo ? R.color.danger_soft : R.color.primary
            );
            int foreground = ContextCompat.getColor(
                    holder.itemView.getContext(),
                    undo ? R.color.expense : R.color.white
            );

            holder.payment.setBackgroundTintList(ColorStateList.valueOf(background));
            holder.payment.setTextColor(foreground);
            holder.payment.setIconTint(ColorStateList.valueOf(foreground));
        } else {
            holder.aux.setText(secondary);
        }

        holder.delete.setVisibility(row.deletable ? View.VISIBLE : View.GONE);

        String value = row.value == null ? "" : row.value.trim();
        int accent = ContextCompat.getColor(
                holder.itemView.getContext(),
                value.startsWith("-")
                        ? R.color.expense
                        : value.startsWith("+")
                        ? R.color.income
                        : R.color.primary_2
        );
        holder.indicator.setBackgroundTintList(ColorStateList.valueOf(accent));
        holder.value.setTextColor(accent);

        holder.primary.setOnClickListener(v -> {
            UiMotion.pop(v);
            listener.onPrimary(row);
        });
        holder.aux.setOnClickListener(v -> {
            UiMotion.pop(v);
            listener.onSecondary(row);
        });
        holder.payment.setOnClickListener(v -> {
            UiMotion.pop(v);
            listener.onSecondary(row);
        });
        holder.delete.setOnClickListener(v -> listener.onDelete(row));

        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position;
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(
                    12f * holder.itemView.getResources().getDisplayMetrics().density
            );
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(position, 6) * 35L)
                    .setDuration(240L)
                    .start();
        }
    }

    private boolean isPaymentAction(String label) {
        if (label == null || label.isBlank()) return false;
        String normalized = label.toLowerCase(Locale.ROOT);
        return normalized.contains("pagamento") || normalized.contains("registrar pago");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View indicator;
        final TextView title;
        final TextView subtitle;
        final TextView value;
        final MaterialButton primary;
        final MaterialButton aux;
        final MaterialButton payment;
        final MaterialButton delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            indicator = itemView.findViewById(R.id.item_indicator);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            value = itemView.findViewById(R.id.item_value);
            primary = itemView.findViewById(R.id.item_secondary);
            aux = itemView.findViewById(R.id.item_aux);
            payment = itemView.findViewById(R.id.item_payment);
            delete = itemView.findViewById(R.id.item_delete);
        }
    }
}

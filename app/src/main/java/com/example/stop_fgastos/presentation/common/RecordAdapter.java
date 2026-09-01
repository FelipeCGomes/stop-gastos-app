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
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DisplayRow row = items.get(position);
        holder.title.setText(row.title);
        holder.subtitle.setText(row.subtitle);
        holder.value.setText(row.value);
        holder.primary.setText(row.primaryLabel);
        holder.primary.setVisibility(row.primaryLabel.isBlank() ? View.GONE : View.VISIBLE);
        holder.aux.setText(row.secondaryLabel);
        holder.aux.setVisibility(row.secondaryLabel.isBlank() ? View.GONE : View.VISIBLE);
        holder.delete.setVisibility(row.deletable ? View.VISIBLE : View.GONE);

        String value = row.value == null ? "" : row.value.trim();
        int accent = ContextCompat.getColor(holder.itemView.getContext(),
                value.startsWith("-") ? R.color.expense : value.startsWith("+") ? R.color.income : R.color.primary_2);
        holder.indicator.setBackgroundTintList(ColorStateList.valueOf(accent));
        holder.value.setTextColor(accent);

        holder.primary.setOnClickListener(v -> { UiMotion.pop(v); listener.onPrimary(row); });
        holder.aux.setOnClickListener(v -> { UiMotion.pop(v); listener.onSecondary(row); });
        holder.delete.setOnClickListener(v -> listener.onDelete(row));

        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position;
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(12f * holder.itemView.getResources().getDisplayMetrics().density);
            holder.itemView.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(position, 6) * 35L).setDuration(240L).start();
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final View indicator;
        final TextView title, subtitle, value;
        final MaterialButton primary, aux, delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            indicator = itemView.findViewById(R.id.item_indicator);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            value = itemView.findViewById(R.id.item_value);
            primary = itemView.findViewById(R.id.item_secondary);
            aux = itemView.findViewById(R.id.item_aux);
            delete = itemView.findViewById(R.id.item_delete);
        }
    }
}

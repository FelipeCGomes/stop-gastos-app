package com.example.stop_fgastos.presentation.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    public RecordAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<DisplayRow> rows) {
        items.clear();
        if (rows != null) items.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record, parent, false);
        return new Holder(view);
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
        holder.primary.setOnClickListener(v -> listener.onPrimary(row));
        holder.aux.setOnClickListener(v -> listener.onSecondary(row));
        holder.delete.setOnClickListener(v -> listener.onDelete(row));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView value;
        final MaterialButton primary;
        final MaterialButton aux;
        final MaterialButton delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            value = itemView.findViewById(R.id.item_value);
            primary = itemView.findViewById(R.id.item_secondary);
            aux = itemView.findViewById(R.id.item_aux);
            delete = itemView.findViewById(R.id.item_delete);
        }
    }
}

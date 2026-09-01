package com.example.stop_fgastos.presentation.common;

import com.example.stop_fgastos.domain.model.FinanceRecord;

public final class DisplayRow {
    public final FinanceRecord record;
    public final String title;
    public final String subtitle;
    public final String value;
    public final String primaryLabel;
    public final boolean deletable;

    public DisplayRow(
            FinanceRecord record,
            String title,
            String subtitle,
            String value,
            String primaryLabel,
            boolean deletable
    ) {
        this.record = record;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.value = value == null ? "" : value;
        this.primaryLabel = primaryLabel == null ? "Editar" : primaryLabel;
        this.deletable = deletable;
    }
}

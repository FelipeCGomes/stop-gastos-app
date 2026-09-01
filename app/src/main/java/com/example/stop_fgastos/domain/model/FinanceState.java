package com.example.stop_fgastos.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FinanceState {
    private final EnumMap<FinanceSection, List<FinanceRecord>> sections;

    public FinanceState() {
        this.sections = new EnumMap<>(FinanceSection.class);
        for (FinanceSection section : FinanceSection.values()) {
            this.sections.put(section, Collections.emptyList());
        }
    }

    private FinanceState(EnumMap<FinanceSection, List<FinanceRecord>> sections) {
        this.sections = sections;
    }

    public List<FinanceRecord> records(FinanceSection section) {
        return sections.getOrDefault(section, Collections.emptyList());
    }

    public Optional<FinanceRecord> find(FinanceSection section, String id) {
        return records(section).stream().filter(record -> record.id().equals(id)).findFirst();
    }

    public FinanceState withSection(FinanceSection section, List<FinanceRecord> records) {
        EnumMap<FinanceSection, List<FinanceRecord>> copy = new EnumMap<>(sections);
        copy.put(section, Collections.unmodifiableList(new ArrayList<>(records)));
        return new FinanceState(copy);
    }

    public Map<FinanceSection, List<FinanceRecord>> asMap() {
        return Collections.unmodifiableMap(sections);
    }
}

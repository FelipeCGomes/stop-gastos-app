package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.repository.FinanceRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

public final class ManageFinanceUseCase {
    private final FinanceRepository repository;

    public ManageFinanceUseCase(FinanceRepository repository) {
        this.repository = repository;
    }

    public void save(FinanceSection section, FinanceRecord record, ResultCallback<Void> callback) {
        repository.upsert(section, record, callback);
    }

    public void delete(FinanceSection section, FinanceRecord record, ResultCallback<Void> callback) {
        repository.delete(section, record.id(), callback);
    }
}

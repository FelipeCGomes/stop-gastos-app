package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.repository.FinanceRepository;

public final class ObserveFinanceUseCase {
    private final FinanceRepository repository;

    public ObserveFinanceUseCase(FinanceRepository repository) {
        this.repository = repository;
    }

    public void start(String uid, FinanceRepository.Listener listener) {
        repository.start(uid, listener);
    }

    public void stop() {
        repository.stop();
    }
}

package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.repository.NotificationRepository;
import com.example.stop_fgastos.domain.repository.ResultCallback;

public final class NotificationUseCase {
    private final NotificationRepository repository;

    public NotificationUseCase(NotificationRepository repository) {
        this.repository = repository;
    }

    public void enable(ResultCallback<Void> callback) { repository.enable(callback); }
    public void disable(ResultCallback<Void> callback) { repository.disable(callback); }
}

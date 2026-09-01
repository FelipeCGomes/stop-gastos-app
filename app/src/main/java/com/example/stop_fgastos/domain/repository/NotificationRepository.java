package com.example.stop_fgastos.domain.repository;

public interface NotificationRepository {
    void enable(ResultCallback<Void> callback);
    void disable(ResultCallback<Void> callback);
}

package com.example.stop_fgastos.domain.repository;

public interface ResultCallback<T> {
    void onSuccess(T value);
    void onError(Throwable error);
}

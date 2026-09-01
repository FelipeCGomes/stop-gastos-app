package com.example.stop_fgastos;

import android.app.Application;

import com.example.stop_fgastos.di.AppContainer;

public final class StopGastosApplication extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer container() {
        return container;
    }
}

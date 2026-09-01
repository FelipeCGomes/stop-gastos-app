package com.example.stop_fgastos.presentation.common;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.stop_fgastos.StopGastosApplication;
import com.example.stop_fgastos.presentation.main.MainViewModel;

public final class ViewModelAccess {
    private ViewModelAccess() {}

    public static MainViewModel from(Fragment fragment) {
        StopGastosApplication app = (StopGastosApplication) fragment.requireActivity().getApplication();
        return new ViewModelProvider(
                fragment.requireActivity(),
                new MainViewModel.Factory(app.container())
        ).get(MainViewModel.class);
    }
}

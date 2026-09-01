package com.example.stop_fgastos.presentation.more;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.stop_fgastos.R;

public final class MoreFragment extends Fragment {
    public MoreFragment() {
        super(R.layout.fragment_more);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.more_family).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_more_to_family));
        view.findViewById(R.id.more_shopping).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_more_to_shopping));
        view.findViewById(R.id.more_calendar).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_more_to_calendar));
        view.findViewById(R.id.more_reports).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_more_to_reports));
        view.findViewById(R.id.more_settings).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_more_to_settings));
    }
}

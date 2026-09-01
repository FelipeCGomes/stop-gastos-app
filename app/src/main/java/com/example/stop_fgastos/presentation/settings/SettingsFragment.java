package com.example.stop_fgastos.presentation.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.UserSession;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.example.stop_fgastos.presentation.auth.GoogleAuthUi;
import com.example.stop_fgastos.presentation.common.UiTheme;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class SettingsFragment extends Fragment {
    private MainViewModel viewModel;
    private TextView userLabel;
    private TextView status;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) enableNotifications();
                        else setStatus("Permissão de notificações não concedida.");
                    }
            );

    private final ActivityResultLauncher<Intent> reauthLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            setStatus("Exclusão cancelada.");
                            return;
                        }

                        try {
                            GoogleSignInAccount account = GoogleSignIn
                                    .getSignedInAccountFromIntent(result.getData())
                                    .getResult(ApiException.class);
                            String token = account.getIdToken();
                            if (token == null || token.isBlank()) {
                                setStatus("O Google não retornou token para reautenticação.");
                                return;
                            }

                            setStatus("Excluindo conta…");
                            viewModel.deleteAccount(token, new ResultCallback<Void>() {
                                @Override
                                public void onSuccess(Void value) {
                                    GoogleAuthUi.client(requireContext()).signOut();
                                    if (isAdded()) requireActivity().recreate();
                                }

                                @Override
                                public void onError(Throwable error) {
                                    setStatus(error.getMessage());
                                }
                            });
                        } catch (ApiException error) {
                            setStatus("Falha na confirmação Google: " + error.getStatusCode());
                        }
                    }
            );

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);
        userLabel = view.findViewById(R.id.settings_user);
        status = view.findViewById(R.id.settings_status);

        configureTheme(view);

        view.findViewById(R.id.settings_enable_notifications).setOnClickListener(v -> requestNotifications());

        view.findViewById(R.id.settings_disable_notifications).setOnClickListener(v ->
                viewModel.disableNotifications(new ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        setStatus("Notificações desativadas neste aparelho.");
                    }

                    @Override
                    public void onError(Throwable error) {
                        setStatus(error.getMessage());
                    }
                })
        );

        view.findViewById(R.id.settings_sign_out).setOnClickListener(v -> {
            GoogleAuthUi.client(requireContext()).signOut();
            viewModel.signOut();
            requireActivity().recreate();
        });

        view.findViewById(R.id.settings_delete_account).setOnClickListener(v -> confirmDelete());

        viewModel.user().observe(getViewLifecycleOwner(), this::renderUser);
        viewModel.error().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isBlank()) setStatus(message);
        });
    }

    private void configureTheme(View view) {
        MaterialButtonToggleGroup group = view.findViewById(R.id.settings_theme_group);
        String mode = UiTheme.mode(requireContext());

        if (UiTheme.LIGHT.equals(mode)) {
            group.check(R.id.settings_theme_light);
        } else if (UiTheme.DARK.equals(mode)) {
            group.check(R.id.settings_theme_dark);
        } else {
            group.check(R.id.settings_theme_system);
        }

        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.settings_theme_light) {
                UiTheme.set(requireContext(), UiTheme.LIGHT);
            } else if (checkedId == R.id.settings_theme_dark) {
                UiTheme.set(requireContext(), UiTheme.DARK);
            } else {
                UiTheme.set(requireContext(), UiTheme.SYSTEM);
            }
        });
    }

    private void renderUser(UserSession user) {
        userLabel.setText(
                (user.displayName().isBlank() ? "Conta Google" : user.displayName())
                        + "\n" + user.email()
        );
    }

    private void requestNotifications() {
        if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            enableNotifications();
        }
    }

    private void enableNotifications() {
        viewModel.enableNotifications(new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                setStatus("Notificações ativadas neste aparelho.");
            }

            @Override
            public void onError(Throwable error) {
                setStatus(error.getMessage());
            }
        });
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Excluir conta permanentemente?")
                .setMessage(
                        "Seus dados financeiros, perfil, dispositivos e vínculos permitidos serão removidos. "
                                + "A conta Google será solicitada novamente para confirmar sua identidade."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    if (!GoogleAuthUi.configured(requireContext())) {
                        setStatus("Configure o app Android no Firebase antes de excluir a conta.");
                        return;
                    }
                    reauthLauncher.launch(GoogleAuthUi.client(requireContext()).getSignInIntent());
                })
                .show();
    }

    private void setStatus(String message) {
        if (status != null) status.setText(message == null ? "" : message);
    }
}

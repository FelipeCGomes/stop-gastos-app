package com.example.stop_fgastos.presentation.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.UserSession;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;

public final class LoginFragment extends Fragment {
    private MainViewModel viewModel;
    private GoogleSignInClient googleClient;
    private TextView status;

    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    if (status != null) status.setText("Login cancelado.");
                    return;
                }

                try {
                    GoogleSignInAccount account = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    String token = account.getIdToken();
                    if (token == null || token.isBlank()) {
                        status.setText("O Google não retornou o token de autenticação.");
                        return;
                    }

                    status.setText("Entrando…");
                    viewModel.signIn(token, new ResultCallback<UserSession>() {
                        @Override
                        public void onSuccess(UserSession value) {
                            if (!isAdded()) return;
                            NavHostFragment.findNavController(LoginFragment.this)
                                    .navigate(R.id.action_login_to_dashboard);
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (status != null) status.setText(error.getMessage());
                        }
                    });
                } catch (ApiException error) {
                    status.setText("Falha no login Google: " + error.getStatusCode());
                }
            });

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);
        status = view.findViewById(R.id.login_status);
        MaterialButton button = view.findViewById(R.id.button_google);

        UserSession current = viewModel.user().getValue();
        if (current != null && current.signedIn()) {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_login_to_dashboard);
            return;
        }

        String clientId = resolveClientId();
        boolean configured = !clientId.isBlank() && !clientId.startsWith("REPLACE_WITH_");
        button.setEnabled(configured);

        if (!configured) {
            status.setText(
                    "Pendente apenas do Firebase: registre o app Android, adicione SHA e coloque google-services.json em app/."
            );
            return;
        }

        GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
        )
                .requestIdToken(clientId)
                .requestEmail()
                .build();

        googleClient = GoogleSignIn.getClient(requireActivity(), options);
        button.setOnClickListener(v -> signInLauncher.launch(googleClient.getSignInIntent()));
    }

    private String resolveClientId() {
        int generatedId = getResources().getIdentifier(
                "default_web_client_id",
                "string",
                requireContext().getPackageName()
        );
        if (generatedId != 0) {
            String generated = getString(generatedId);
            if (!generated.isBlank() && !generated.startsWith("REPLACE_WITH_")) {
                return generated;
            }
        }
        return getString(R.string.firebase_web_client_id_override);
    }
}

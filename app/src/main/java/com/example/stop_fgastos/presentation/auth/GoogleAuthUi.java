package com.example.stop_fgastos.presentation.auth;

import android.content.Context;

import com.example.stop_fgastos.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

public final class GoogleAuthUi {
    private GoogleAuthUi() {}

    public static String clientId(Context context) {
        int generatedId = context.getResources().getIdentifier(
                "default_web_client_id",
                "string",
                context.getPackageName()
        );
        if (generatedId != 0) {
            String generated = context.getString(generatedId);
            if (!generated.isBlank() && !generated.startsWith("REPLACE_WITH_")) {
                return generated;
            }
        }
        return context.getString(R.string.firebase_web_client_id_override);
    }

    public static boolean configured(Context context) {
        String id = clientId(context);
        return !id.isBlank() && !id.startsWith("REPLACE_WITH_");
    }

    public static GoogleSignInClient client(Context context) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
        )
                .requestIdToken(clientId(context))
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(context, options);
    }
}

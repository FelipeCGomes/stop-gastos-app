package com.example.stop_fgastos.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.stop_fgastos.notifications.NotificationRegistrar
import com.example.stop_fgastos.viewmodel.MainUiState
import com.example.stop_fgastos.viewmodel.MainViewModel

@Composable
fun AccountActionsPanel(
    state: MainUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var notificationStatus by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            notificationStatus = "Permissão de notificação não concedida."
        } else {
            NotificationRegistrar.registerCurrentDevice(context) { result ->
                notificationStatus = result.fold(
                    onSuccess = { "Notificações ativadas neste aparelho." },
                    onFailure = { it.message ?: "Falha ao registrar notificações." }
                )
            }
        }
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Notificações Android",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Alertas financeiros e convites familiares usam Firebase Cloud Messaging.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        NotificationRegistrar.registerCurrentDevice(context) { result ->
                            notificationStatus = result.fold(
                                onSuccess = { "Notificações ativadas neste aparelho." },
                                onFailure = { it.message ?: "Falha ao registrar notificações." }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ativar notificações")
            }

            OutlinedButton(
                onClick = {
                    NotificationRegistrar.disableCurrentDevice(context) { result ->
                        notificationStatus = result.fold(
                            onSuccess = { "Notificações desativadas neste aparelho." },
                            onFailure = { it.message ?: "Falha ao desativar notificações." }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desativar neste aparelho")
            }

            if (notificationStatus.isNotBlank()) {
                Text(
                    notificationStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Excluir conta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                "Remove seus dados financeiros, dispositivos, perfil, diretório, vínculos permitidos e a conta do Firebase Authentication. A identidade Google será confirmada antes da exclusão.",
                style = MaterialTheme.typography.bodySmall
            )
            if (state.family.family?.ownerUid == state.family.profile.uid &&
                state.family.members.any { it.uid != state.family.profile.uid }
            ) {
                Text(
                    "Antes de excluir, remova os demais vínculos ou transfira a administração da família.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(
                enabled = !deleting && activity != null,
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (deleting) "Excluindo…" else "Excluir minha conta")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text("Excluir conta permanentemente?") },
            text = {
                Text(
                    "Essa ação remove os dados do Stop Gastos e a conta autenticada. A confirmação Google será solicitada."
                )
            },
            confirmButton = {
                Button(
                    enabled = !deleting && activity != null,
                    onClick = {
                        val host = activity ?: return@Button
                        deleting = true
                        viewModel.reauthenticateAndDeleteAccount(host) { success ->
                            deleting = false
                            if (success) {
                                showDeleteConfirm = false
                            }
                        }
                    }
                ) {
                    Text("Confirmar e excluir")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { showDeleteConfirm = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

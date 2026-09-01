package com.example.stop_fgastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.stop_fgastos.viewmodel.MainUiState
import com.example.stop_fgastos.viewmodel.MainViewModel

@Composable
fun FamilyScreen(state: MainUiState, viewModel: MainViewModel) {
    val familyState = state.family
    var showCreate by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var showSharedList by remember { mutableStateOf(false) }
    var showSharedItem by remember { mutableStateOf(false) }
    var activeListId by remember(familyState.sharedLists) {
        mutableStateOf(familyState.sharedLists.firstOrNull()?.id.orEmpty())
    }

    LaunchedEffect(familyState.sharedLists) {
        if (familyState.sharedLists.none { it.id == activeListId }) {
            activeListId = familyState.sharedLists.firstOrNull()?.id.orEmpty()
        }
    }

    LaunchedEffect(activeListId) {
        if (activeListId.isNotBlank()) {
            viewModel.loadSharedShoppingItems(activeListId)
        }
    }

    val activeList = familyState.sharedLists.firstOrNull { it.id == activeListId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Família", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Membros, convites e listas compartilhadas")
                }
                TextButton(onClick = viewModel::refreshFamily) { Text("Atualizar") }
            }
        }

        if (familyState.invitations.isNotEmpty()) {
            item {
                Text("Convites recebidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(familyState.invitations, key = { it.id }) { invitation ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(invitation.familyName, fontWeight = FontWeight.Bold)
                        Text(
                            "Convite de " + invitation.createdByName.ifBlank { "administrador" },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row {
                            Button(onClick = { viewModel.respondFamilyInvitation(invitation, true) }) {
                                Text("Aceitar")
                            }
                            TextButton(onClick = { viewModel.respondFamilyInvitation(invitation, false) }) {
                                Text("Recusar")
                            }
                        }
                    }
                }
            }
        }

        if (!familyState.hasFamily) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Você ainda não participa de uma família.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Crie uma família ou aceite um convite enviado para sua conta Google.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { showCreate = true }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Criar família")
                        }
                    }
                }
            }
        } else {
            val family = familyState.family!!
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(family.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (familyState.isAdmin) "Administrador" else "Membro",
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("${familyState.members.count { it.status == "active" }} membro(s) ativo(s)")
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Membros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (familyState.isAdmin) {
                        Button(onClick = { showInvite = true }) { Text("Convidar") }
                    }
                }
            }

            items(familyState.members, key = { it.uid }) { member ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName.ifBlank { member.email }, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${member.role} · ${member.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (familyState.isAdmin && member.uid != family.ownerUid) {
                            TextButton(onClick = { viewModel.removeFamilyMember(member) }) {
                                Text("Remover")
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Listas compartilhadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Todos os membros ativos podem colaborar", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { showSharedList = true }) { Text("Nova") }
                }
            }

            if (familyState.sharedLists.isEmpty()) {
                item {
                    Card {
                        Text(
                            "Nenhuma lista compartilhada.",
                            Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            } else {
                item {
                    ChoiceField(
                        "Lista ativa",
                        activeList?.name ?: "Selecione",
                        familyState.sharedLists.map { it.name }
                    ) { name ->
                        activeListId = familyState.sharedLists.firstOrNull { it.name == name }?.id.orEmpty()
                    }
                }

                if (activeList != null) {
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(activeList.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            activeList.store.ifBlank { "Sem mercado definido" } +
                                                " · ${activeList.items.size} itens · ${extendedMoney(activeList.total)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    TextButton(onClick = { showSharedItem = true }) { Text("Item") }
                                    if (familyState.isAdmin || activeList.createdBy == familyState.profile.uid) {
                                        TextButton(onClick = { viewModel.deleteSharedShoppingList(activeList.id) }) {
                                            Text("Excluir")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (activeList.items.isEmpty()) {
                        item {
                            Card {
                                Text("Nenhum produto nesta lista.", Modifier.fillMaxWidth().padding(16.dp))
                            }
                        }
                    } else {
                        items(activeList.items, key = { it.id }) { item ->
                            Card {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.product, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${formatQty(item.qty)} × ${extendedMoney(item.unitPrice)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(extendedMoney(item.total), fontWeight = FontWeight.Bold)
                                    TextButton(
                                        onClick = {
                                            viewModel.deleteSharedShoppingItem(activeList.id, item.id)
                                        }
                                    ) { Text("×") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        FamilyNameDialog(
            onDismiss = { showCreate = false },
            onSave = {
                viewModel.createFamily(it)
                showCreate = false
            }
        )
    }

    if (showInvite) {
        FamilyInviteDialog(
            onDismiss = { showInvite = false },
            onSave = {
                viewModel.inviteFamilyMember(it)
                showInvite = false
            }
        )
    }

    if (showSharedList) {
        ShoppingListDialog(
            onDismiss = { showSharedList = false },
            onSave = { name, store ->
                viewModel.createSharedShoppingList(name, store)
                showSharedList = false
            }
        )
    }

    if (showSharedItem && activeList != null) {
        ShoppingItemDialog(
            onDismiss = { showSharedItem = false },
            onSave = { product, qty, price ->
                viewModel.addSharedShoppingItem(activeList.id, product, qty, price)
                showSharedItem = false
            }
        )
    }
}

@Composable
private fun FamilyNameDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar família") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Nome da família") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (value.trim().length >= 2) onSave(value.trim()) }) {
                Text("Criar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun FamilyInviteDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convidar membro") },
        text = {
            Column {
                Text(
                    "A pessoa precisa ter entrado no Stop Gastos ao menos uma vez com essa conta Google.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail Google") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (email.contains("@")) onSave(email.trim()) }) {
                Text("Enviar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

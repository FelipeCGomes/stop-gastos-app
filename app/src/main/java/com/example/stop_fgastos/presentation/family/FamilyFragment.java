package com.example.stop_fgastos.presentation.family;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.FamilyState;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;
import com.example.stop_fgastos.domain.model.UserSession;
import com.example.stop_fgastos.domain.repository.ResultCallback;
import com.example.stop_fgastos.presentation.common.DisplayRow;
import com.example.stop_fgastos.presentation.common.RecordAdapter;
import com.example.stop_fgastos.presentation.common.RecordDialogs;
import com.example.stop_fgastos.presentation.common.UiFormat;
import com.example.stop_fgastos.presentation.common.ViewModelAccess;
import com.example.stop_fgastos.presentation.main.MainViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FamilyFragment extends Fragment {
    private static final String[] MODES = {"Membros", "Convites", "Listas compartilhadas"};

    private MainViewModel viewModel;
    private FamilyState family = new FamilyState();
    private FinanceState finance = new FinanceState();
    private UserSession user = new UserSession("", "", "", "");

    private TextView status;
    private Spinner mode;
    private MaterialButton primary;
    private MaterialButton secondary;
    private MaterialButton leave;
    private RecordAdapter adapter;

    private FinanceRecord activeSharedList;
    private List<FinanceRecord> sharedItems = new ArrayList<>();

    public FamilyFragment() {
        super(R.layout.fragment_family);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = ViewModelAccess.from(this);

        status = view.findViewById(R.id.family_status);
        mode = view.findViewById(R.id.family_mode);
        primary = view.findViewById(R.id.family_primary_action);
        secondary = view.findViewById(R.id.family_secondary_action);
        leave = view.findViewById(R.id.family_leave);

        mode.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                MODES
        ));

        RecyclerView recycler = view.findViewById(R.id.family_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecordAdapter(new RecordAdapter.Listener() {
            @Override
            public void onPrimary(DisplayRow row) {
                handlePrimary(row);
            }

            @Override
            public void onSecondary(DisplayRow row) {
                handleSecondary(row);
            }

            @Override
            public void onDelete(DisplayRow row) {
                handleDelete(row);
            }
        });
        recycler.setAdapter(adapter);

        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                activeSharedList = null;
                sharedItems = new ArrayList<>();
                configureActions();
                render();
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        primary.setOnClickListener(v -> handleTopPrimary());
        secondary.setOnClickListener(v -> handleTopSecondary());
        leave.setOnClickListener(v -> confirmLeave());

        viewModel.user().observe(getViewLifecycleOwner(), value -> {
            user = value;
            configureActions();
            render();
        });
        viewModel.finance().observe(getViewLifecycleOwner(), value -> finance = value);
        viewModel.family().observe(getViewLifecycleOwner(), value -> {
            family = value;
            if (activeSharedList != null) {
                activeSharedList = family.sharedLists().stream()
                        .filter(record -> record.id().equals(activeSharedList.id()))
                        .findFirst()
                        .orElse(null);
            }
            configureActions();
            render();
        });
    }

    private void configureActions() {
        if (!family.hasFamily()) {
            status.setText("Você ainda não participa de uma família.");
            primary.setText("Criar família");
            primary.setVisibility(View.VISIBLE);
            secondary.setVisibility(View.GONE);
            leave.setVisibility(View.GONE);
            return;
        }

        status.setText(
                family.familyName()
                        + " · "
                        + (family.isAdmin() ? "Administrador" : "Membro")
                        + " · "
                        + family.members().stream().filter(record -> "active".equals(record.text("status"))).count()
                        + " ativo(s)"
        );

        int selectedMode = mode.getSelectedItemPosition();
        if (selectedMode == 0) {
            primary.setText(family.isAdmin() ? "Convidar membro" : "Atualizar");
            secondary.setText("Atualizar");
            secondary.setVisibility(family.isAdmin() ? View.VISIBLE : View.GONE);
        } else if (selectedMode == 1) {
            primary.setText("Atualizar convites");
            secondary.setVisibility(View.GONE);
        } else {
            if (activeSharedList == null) {
                primary.setText("Nova lista");
                secondary.setText("Atualizar");
            } else {
                primary.setText("Adicionar item");
                secondary.setText("Voltar às listas");
            }
            secondary.setVisibility(View.VISIBLE);
        }

        primary.setVisibility(View.VISIBLE);
        leave.setVisibility(View.VISIBLE);
        leave.setEnabled(!user.uid().equals(family.ownerUid()));
        leave.setText(
                user.uid().equals(family.ownerUid())
                        ? "Transfira a administração antes de sair"
                        : "Sair da família"
        );
    }

    private void handleTopPrimary() {
        if (!family.hasFamily()) {
            RecordDialogs.prompt(
                    requireContext(),
                    "Criar família",
                    "Nome da família",
                    "",
                    viewModel::createFamily
            );
            return;
        }

        int selectedMode = mode.getSelectedItemPosition();
        if (selectedMode == 0) {
            if (family.isAdmin()) {
                RecordDialogs.prompt(
                        requireContext(),
                        "Convidar membro",
                        "E-mail Google",
                        "",
                        viewModel::inviteFamily
                );
            } else {
                viewModel.refreshFamily();
            }
        } else if (selectedMode == 1) {
            viewModel.refreshFamily();
        } else {
            if (activeSharedList == null) {
                RecordDialogs.record(
                        requireContext(),
                        finance,
                        FinanceSection.SHOPPING_LISTS,
                        null,
                        record -> viewModel.createSharedList(
                                record.text("name"),
                                record.text("store")
                        )
                );
            } else {
                RecordDialogs.shoppingItem(
                        requireContext(),
                        null,
                        sharedItems.size() + 1,
                        item -> viewModel.addSharedItem(activeSharedList.id(), item)
                );
            }
        }
    }

    private void handleTopSecondary() {
        if (!family.hasFamily()) return;

        if (mode.getSelectedItemPosition() == 2 && activeSharedList != null) {
            activeSharedList = null;
            sharedItems = new ArrayList<>();
            configureActions();
            render();
        } else {
            viewModel.refreshFamily();
        }
    }

    private void handlePrimary(DisplayRow row) {
        int selectedMode = mode.getSelectedItemPosition();

        if (selectedMode == 0) {
            if ("Transferir".equals(row.primaryLabel)) {
                confirmTransfer(row.record);
            }
            return;
        }

        if (selectedMode == 1) {
            viewModel.respondInvite(row.record.id(), true);
            return;
        }

        if (activeSharedList == null) {
            activeSharedList = row.record;
            sharedItems = new ArrayList<>();
            configureActions();
            viewModel.loadSharedItems(row.record.id(), new ResultCallback<List<FinanceRecord>>() {
                @Override
                public void onSuccess(List<FinanceRecord> value) {
                    sharedItems = value;
                    render();
                }

                @Override
                public void onError(Throwable error) {
                }
            });
        } else {
            RecordDialogs.shoppingItem(
                    requireContext(),
                    row.record,
                    row.record.integer("order"),
                    item -> {
                        viewModel.updateSharedItem(activeSharedList.id(), item);
                        reloadSharedItems();
                    }
            );
        }
    }

    private void handleSecondary(DisplayRow row) {
        int selectedMode = mode.getSelectedItemPosition();
        if (selectedMode == 1) {
            viewModel.respondInvite(row.record.id(), false);
            return;
        }

        if (selectedMode == 2 && activeSharedList == null) {
            RecordDialogs.record(
                    requireContext(),
                    finance,
                    FinanceSection.SHOPPING_LISTS,
                    row.record,
                    viewModel::updateSharedList
            );
        }
    }

    private void handleDelete(DisplayRow row) {
        int selectedMode = mode.getSelectedItemPosition();

        if (selectedMode == 0) {
            viewModel.removeMember(row.record.id());
        } else if (selectedMode == 2) {
            if (activeSharedList == null) {
                viewModel.deleteSharedList(row.record.id());
            } else {
                viewModel.deleteSharedItem(activeSharedList.id(), row.record.id());
                reloadSharedItems();
            }
        }
    }

    private void render() {
        if (adapter == null) return;
        int selectedMode = mode.getSelectedItemPosition();

        if (selectedMode == 0) {
            renderMembers();
        } else if (selectedMode == 1) {
            renderInvites();
        } else {
            renderSharedShopping();
        }
    }

    private void renderMembers() {
        List<DisplayRow> rows = new ArrayList<>();
        for (FinanceRecord member : family.members()) {
            boolean owner = member.id().equals(family.ownerUid());
            boolean self = member.id().equals(user.uid());
            boolean canTransfer = user.uid().equals(family.ownerUid())
                    && !self
                    && "active".equals(member.text("status"));
            boolean canRemove = family.isAdmin() && !owner && !self;

            rows.add(new DisplayRow(
                    member,
                    member.text("displayName", member.text("email", "Membro")),
                    (owner ? "Proprietário" : member.text("role", "member"))
                            + " · " + member.text("status", "active"),
                    member.text("email"),
                    canTransfer ? "Transferir" : "",
                    canRemove
            ));
        }
        adapter.submit(rows);
    }

    private void renderInvites() {
        List<DisplayRow> rows = new ArrayList<>();
        for (FinanceRecord invite : family.invitations()) {
            rows.add(new DisplayRow(
                    invite,
                    invite.text("familyName", "Família"),
                    "Convite de " + invite.text("createdByName", "administrador"),
                    invite.text("targetEmail"),
                    "Aceitar",
                    "Recusar",
                    false
            ));
        }
        adapter.submit(rows);
    }

    private void renderSharedShopping() {
        List<DisplayRow> rows = new ArrayList<>();

        if (activeSharedList == null) {
            for (FinanceRecord list : family.sharedLists()) {
                boolean canManage = family.isAdmin()
                        || user.uid().equals(list.text("createdBy"));
                rows.add(new DisplayRow(
                        list,
                        list.text("name", "Lista"),
                        list.text("store", "Sem mercado definido"),
                        "",
                        "Abrir",
                        canManage ? "Editar" : "",
                        canManage
                ));
            }
        } else {
            sharedItems.sort(Comparator.comparingInt(record -> record.integer("order")));
            for (FinanceRecord item : sharedItems) {
                rows.add(new DisplayRow(
                        item,
                        item.text("product", "Produto"),
                        formatQty(item.number("qty"))
                                + " × " + UiFormat.money(item.number("unitPrice"))
                                + (item.text("createdByName").isBlank()
                                ? ""
                                : " · por " + item.text("createdByName")),
                        UiFormat.money(item.number("qty") * item.number("unitPrice")),
                        "Editar",
                        true
                ));
            }
        }

        adapter.submit(rows);
    }

    private void reloadSharedItems() {
        if (activeSharedList == null) return;
        viewModel.loadSharedItems(activeSharedList.id(), new ResultCallback<List<FinanceRecord>>() {
            @Override
            public void onSuccess(List<FinanceRecord> value) {
                sharedItems = value;
                render();
            }

            @Override
            public void onError(Throwable error) {
            }
        });
    }

    private void confirmTransfer(FinanceRecord member) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Transferir administração?")
                .setMessage(
                        member.text("displayName", member.text("email"))
                                + " passará a ser o proprietário. Você continuará como membro."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Transferir", (dialog, which) ->
                        viewModel.transferOwnership(member.id()))
                .show();
    }

    private void confirmLeave() {
        if (!family.hasFamily() || user.uid().equals(family.ownerUid())) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sair da família?")
                .setMessage("Seus dados financeiros pessoais continuarão na sua conta.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair", (dialog, which) -> viewModel.leaveFamily())
                .show();
    }

    private String formatQty(double value) {
        return value == Math.rint(value)
                ? String.valueOf((int) value)
                : String.format(java.util.Locale.getDefault(), "%.2f", value);
    }
}

package com.example.stop_fgastos.presentation.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class BillPaymentDialog {
    public interface Handler {
        void onConfirm(double paidAmount, LocalDate paidDate);
    }

    private BillPaymentDialog() {}

    public static void show(Context context, FinanceRecord bill, Handler handler) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_bill_payment, null, false);

        TextView description = view.findViewById(R.id.payment_bill_description);
        TextView original = view.findViewById(R.id.payment_bill_original);
        TextView due = view.findViewById(R.id.payment_bill_due);
        TextView adjustment = view.findViewById(R.id.payment_bill_adjustment);
        TextInputEditText paymentDate = view.findViewById(R.id.payment_bill_date);
        TextInputEditText amount = view.findViewById(R.id.payment_bill_amount);
        MaterialButton confirm = view.findViewById(R.id.payment_bill_confirm);

        double originalAmount = bill.number("originalAmount") > 0
                ? bill.number("originalAmount")
                : bill.number("amount");

        LocalDate today = LocalDate.now();
        LocalDate dueDate = parseStoredDate(
                bill.text("dueDate", bill.text("date")),
                today
        );

        description.setText(bill.text("description", "Conta"));
        original.setText("Valor original: " + UiFormat.money(originalAmount));
        due.setText("Vencimento: " + UiFormat.date(dueDate));
        paymentDate.setText(UiFormat.date(today));

        amount.setText(String.format(Locale.US, "%.2f", originalAmount));
        amount.setSelectAllOnFocus(true);

        Runnable update = () -> {
            double paid = UiFormat.parseMoney(
                    amount.getText() == null ? "" : amount.getText().toString()
            );

            LocalDate selectedDate;
            try {
                selectedDate = UiFormat.parseDate(
                        paymentDate.getText() == null
                                ? ""
                                : paymentDate.getText().toString()
                );
                paymentDate.setError(null);
            } catch (Exception error) {
                paymentDate.setError("Use dd/MM/aa.");
                adjustment.setText("Informe uma data de pagamento válida.");
                return;
            }

            long daysLate = Math.max(
                    0,
                    ChronoUnit.DAYS.between(dueDate, selectedDate)
            );
            double difference = paid - originalAmount;

            if (paid <= 0) {
                adjustment.setText("Informe o valor que foi realmente pago.");
                return;
            }

            if (difference > 0.005) {
                double rate = originalAmount <= 0
                        ? 0
                        : (difference / originalAmount) * 100.0;

                adjustment.setText(
                        "Data: " + UiFormat.date(selectedDate)
                                + "\nValor pago: " + UiFormat.money(paid)
                                + "\nAcréscimo: " + UiFormat.money(difference)
                                + " (" + String.format(
                                Locale.getDefault(),
                                "%.2f%%",
                                rate
                        ) + ")"
                                + (daysLate > 0
                                ? "\nAtraso: " + daysLate + " dia(s)"
                                : "")
                );
            } else if (difference < -0.005) {
                adjustment.setText(
                        "Data: " + UiFormat.date(selectedDate)
                                + "\nValor pago: " + UiFormat.money(paid)
                                + "\nDesconto/diferença: "
                                + UiFormat.money(Math.abs(difference))
                                + (daysLate > 0
                                ? "\nPagamento " + daysLate
                                + " dia(s) após o vencimento"
                                : "")
                );
            } else {
                adjustment.setText(
                        "Data: " + UiFormat.date(selectedDate)
                                + "\nValor pago: " + UiFormat.money(paid)
                                + (daysLate > 0
                                ? "\nSem acréscimo informado · "
                                + daysLate + " dia(s) de atraso"
                                : "\nPago pelo valor original, sem diferença.")
                );
            }
        };

        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {}

            @Override public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                update.run();
            }

            @Override public void afterTextChanged(android.text.Editable s) {}
        };

        amount.addTextChangedListener(watcher);
        paymentDate.addTextChangedListener(watcher);
        update.run();

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Registrar pagamento")
                        .setView(view)
                        .setNegativeButton("Cancelar", null)
                        .create();

        confirm.setOnClickListener(button -> {
            double paid = UiFormat.parseMoney(
                    amount.getText() == null ? "" : amount.getText().toString()
            );

            if (paid <= 0) {
                adjustment.setText("O valor pago precisa ser maior que zero.");
                amount.requestFocus();
                return;
            }

            LocalDate selectedDate;
            try {
                selectedDate = UiFormat.parseDate(
                        paymentDate.getText() == null
                                ? ""
                                : paymentDate.getText().toString()
                );
            } catch (Exception error) {
                paymentDate.setError("Use dd/MM/aa.");
                paymentDate.requestFocus();
                return;
            }

            handler.onConfirm(paid, selectedDate);
            dialog.dismiss();
        });

        amount.setOnEditorActionListener((v, actionId, event) -> {
            confirm.performClick();
            return true;
        });

        dialog.show();
    }

    private static LocalDate parseStoredDate(String value, LocalDate fallback) {
        try {
            return UiFormat.parseDate(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

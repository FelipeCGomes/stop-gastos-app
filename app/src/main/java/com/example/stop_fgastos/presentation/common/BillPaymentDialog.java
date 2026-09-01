package com.example.stop_fgastos.presentation.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.example.stop_fgastos.R;
import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class BillPaymentDialog {
    public interface Handler {
        void onConfirm(double paidAmount);
    }

    private BillPaymentDialog() {}

    public static void show(Context context, FinanceRecord bill, Handler handler) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_bill_payment, null, false);

        TextView description = view.findViewById(R.id.payment_bill_description);
        TextView original = view.findViewById(R.id.payment_bill_original);
        TextView due = view.findViewById(R.id.payment_bill_due);
        TextView paymentDate = view.findViewById(R.id.payment_bill_date);
        TextView adjustment = view.findViewById(R.id.payment_bill_adjustment);
        TextInputEditText amount = view.findViewById(R.id.payment_bill_amount);

        double originalAmount = bill.number("amount");
        LocalDate today = LocalDate.now();
        LocalDate dueDate = parseDate(bill.text("dueDate"), today);

        description.setText(bill.text("description", "Conta"));
        original.setText("Valor original: " + UiFormat.money(originalAmount));
        due.setText("Vencimento: " + dueDate);
        paymentDate.setText("Pagamento: " + today + " (automático)");
        amount.setText(String.format(Locale.US, "%.2f", originalAmount));
        amount.setSelectAllOnFocus(true);

        Runnable update = () -> {
            double paid = UiFormat.parseMoney(amount.getText() == null ? "" : amount.getText().toString());
            long daysLate = Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
            double difference = paid - originalAmount;

            if (paid <= 0) {
                adjustment.setText("Informe o valor que foi realmente pago.");
                return;
            }

            if (difference > 0.005) {
                double rate = originalAmount <= 0 ? 0 : (difference / originalAmount) * 100.0;
                if (daysLate > 0) {
                    adjustment.setText(
                            "Acréscimo/juros por atraso: " + UiFormat.money(difference)
                                    + " (" + String.format(Locale.getDefault(), "%.2f%%", rate) + ")"
                                    + " · " + daysLate + " dia(s) de atraso"
                    );
                } else {
                    adjustment.setText(
                            "Acréscimo sobre o valor original: " + UiFormat.money(difference)
                                    + " (" + String.format(Locale.getDefault(), "%.2f%%", rate) + ")"
                    );
                }
            } else if (difference < -0.005) {
                adjustment.setText(
                        "Desconto/diferença: " + UiFormat.money(Math.abs(difference))
                                + (daysLate > 0 ? " · " + daysLate + " dia(s) após o vencimento" : "")
                );
            } else {
                adjustment.setText(
                        daysLate > 0
                                ? "Sem acréscimo informado · " + daysLate + " dia(s) de atraso"
                                : "Pago pelo valor original, sem diferença."
                );
            }
        };

        amount.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        update.run();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Registrar pagamento")
                .setView(view)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar pagamento", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(button -> {
                            double paid = UiFormat.parseMoney(
                                    amount.getText() == null ? "" : amount.getText().toString()
                            );
                            if (paid <= 0) {
                                adjustment.setText("O valor pago precisa ser maior que zero.");
                                return;
                            }
                            handler.onConfirm(paid);
                            dialog.dismiss();
                        })
        );

        dialog.show();
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

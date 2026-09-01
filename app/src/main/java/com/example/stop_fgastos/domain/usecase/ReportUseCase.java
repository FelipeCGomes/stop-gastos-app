package com.example.stop_fgastos.domain.usecase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.example.stop_fgastos.domain.model.FinanceSection;
import com.example.stop_fgastos.domain.model.FinanceState;

import java.time.YearMonth;
import java.util.Locale;

public final class ReportUseCase {
    public String csv(FinanceState state, YearMonth month) {
        String key = month.toString();
        StringBuilder out = new StringBuilder();
        out.append("Data;Tipo;Descrição;Categoria;Pagamento;Conta;Cartão;Parcela;Valor;Tags;Observações\n");

        for (FinanceRecord tx : state.records(FinanceSection.TRANSACTIONS)) {
            if (!tx.text("date").startsWith(key)) continue;
            String installment = Math.max(1, tx.integer("installmentNo")) + "/" + Math.max(1, tx.integer("installmentCount"));
            out.append(cell(tx.text("date"))).append(';')
                    .append(cell("expense".equals(tx.text("type")) ? "Despesa" : "Receita")).append(';')
                    .append(cell(tx.text("description"))).append(';')
                    .append(cell(tx.text("category"))).append(';')
                    .append(cell(tx.text("payment"))).append(';')
                    .append(cell(tx.text("accountId"))).append(';')
                    .append(cell(tx.text("cardId"))).append(';')
                    .append(cell(installment)).append(';')
                    .append(String.format(Locale.US, "%.2f", tx.number("amount"))).append(';')
                    .append(cell(tx.text("tags"))).append(';')
                    .append(cell(tx.text("notes"))).append('\n');
        }
        return out.toString();
    }

    private String cell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

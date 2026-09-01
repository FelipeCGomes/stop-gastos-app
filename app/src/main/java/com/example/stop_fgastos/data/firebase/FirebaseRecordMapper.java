package com.example.stop_fgastos.data.firebase;

import com.example.stop_fgastos.domain.model.FinanceRecord;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FirebaseRecordMapper {
    private FirebaseRecordMapper() {}

    @SuppressWarnings("unchecked")
    static List<FinanceRecord> readRecords(DocumentSnapshot snapshot) {
        List<FinanceRecord> records = new ArrayList<>();
        if (snapshot == null || !snapshot.exists()) return records;

        Object value = snapshot.get("value");
        if (value instanceof List<?>) {
            for (Object raw : (List<?>) value) {
                if (!(raw instanceof Map<?, ?>)) continue;
                Map<String, Object> map = stringKeyMap((Map<?, ?>) raw);
                String id = String.valueOf(map.getOrDefault("id", ""));
                if (!id.isBlank()) records.add(new FinanceRecord(id, map));
            }
            return records;
        }

        if (value instanceof Map<?, ?>) {
            Map<String, Object> map = stringKeyMap((Map<?, ?>) value);
            String id = String.valueOf(map.getOrDefault("id", snapshot.getId()));
            records.add(new FinanceRecord(id, map));
        }
        return records;
    }

    static List<Map<String, Object>> toMaps(List<FinanceRecord> records) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FinanceRecord record : records) result.add(record.toMap());
        return result;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}

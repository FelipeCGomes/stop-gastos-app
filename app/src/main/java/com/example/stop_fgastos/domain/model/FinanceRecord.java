package com.example.stop_fgastos.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class FinanceRecord {
    private final String id;
    private final Map<String, Object> fields;

    public FinanceRecord(String id, Map<String, Object> fields) {
        this.id = id == null ? "" : id;
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        if (fields != null) copy.putAll(fields);
        copy.put("id", this.id);
        this.fields = Collections.unmodifiableMap(copy);
    }

    public String id() {
        return id;
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public String text(String key) {
        Object value = fields.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public String text(String key, String fallback) {
        String value = text(key);
        return value.isBlank() ? fallback : value;
    }

    public double number(String key) {
        Object value = fields.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value).replace(",", "."));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    public int integer(String key) {
        return (int) Math.round(number(key));
    }

    public boolean bool(String key) {
        Object value = fields.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    public FinanceRecord with(String key, Object value) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(fields);
        copy.put(key, value);
        return new FinanceRecord(id, copy);
    }

    public FinanceRecord withAll(Map<String, Object> values) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(fields);
        if (values != null) copy.putAll(values);
        return new FinanceRecord(id, copy);
    }

    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(fields);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FinanceRecord)) return false;
        FinanceRecord that = (FinanceRecord) other;
        return id.equals(that.id) && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fields);
    }
}

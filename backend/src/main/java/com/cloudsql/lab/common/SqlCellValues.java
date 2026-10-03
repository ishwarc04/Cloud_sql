package com.cloudsql.lab.common;
/** Preserve SQL calendar values instead of serializing them as timezone-shifted instants. */
public final class SqlCellValues {
    private SqlCellValues() { }
    public static Object jsonValue(Object value) {
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof java.time.temporal.TemporalAccessor) return value.toString();
        return value;
    }
}

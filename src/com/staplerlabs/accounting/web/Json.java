package com.staplerlabs.accounting.web;

import java.math.BigDecimal;
import java.util.*;

/** Minimal JSON reader and writer, so the project needs no third-party JSON library. Records are written as objects. */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    /* ---------------- Reading ---------------- */

    public static Object parse(String text) {
        if (text == null || text.isBlank()) return new LinkedHashMap<String, Object>();
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != text.length()) throw j.error();
        return v;
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private boolean at(char c) { return i < s.length() && s.charAt(i) == c; }

    private IllegalArgumentException error() { return new IllegalArgumentException("Invalid JSON near position " + i); }

    private Object value() {
        if (i >= s.length()) throw error();
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error();
        return new BigDecimal(s.substring(start, i));
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (at('}')) { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            if (!at(':')) throw error();
            i++; ws();
            m.put(key, value());
            ws();
            if (at(',')) { i++; continue; }
            if (at('}')) { i++; return m; }
            throw error();
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (at(']')) { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (at(',')) { i++; continue; }
            if (at(']')) { i++; return l; }
            throw error();
        }
    }

    private String string() {
        if (!at('"')) throw error();
        i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                default -> b.append(e);
            }
        }
        throw error();
    }

    /* ---------------- Writing ---------------- */

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o);
        return b.toString();
    }

    private static void write(StringBuilder b, Object o) {
        if (o == null) b.append("null");
        else if (o instanceof String str) quote(b, str);
        else if (o instanceof BigDecimal d) b.append(d.toPlainString());
        else if (o instanceof Number || o instanceof Boolean) b.append(o);
        else if (o instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (o instanceof Collection<?> c) {
            b.append('[');
            boolean first = true;
            for (Object x : c) {
                if (!first) b.append(',');
                first = false;
                write(b, x);
            }
            b.append(']');
        } else if (o instanceof Record r) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (var rc : r.getClass().getRecordComponents()) {
                try {
                    rc.getAccessor().setAccessible(true);
                    m.put(rc.getName(), rc.getAccessor().invoke(r));
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            write(b, m);
        } else quote(b, o.toString());
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '<' -> b.append("\\u003c");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        b.append('"');
    }
}

package me.abdoabk.reportx.web;

import java.util.*;

/**
 * Minimal flat JSON array-of-objects parser.
 * Handles only the structure returned by /api/internal/poll-notifications —
 * no nested objects, arrays, or escape sequences beyond \".
 * Avoids adding any JSON library dependency to the plugin JAR.
 */
public class SimpleJsonParser {

    public static List<Map<String, Object>> parseArray(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) return result;
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return result;

        int depth = 0, start = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if      (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0) result.add(parseObject(json.substring(start, i + 1))); }
        }
        return result;
    }

    private static Map<String, Object> parseObject(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        // Strip outer braces
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}"))   json = json.substring(0, json.length() - 1);

        int i = 0;
        while (i < json.length()) {
            // skip whitespace / commas
            while (i < json.length() && (json.charAt(i) == ',' || json.charAt(i) <= ' ')) i++;
            if (i >= json.length() || json.charAt(i) != '"') break;

            // key
            int ks = i + 1, ke = nextQuote(json, ks);
            String key = json.substring(ks, ke);
            i = ke + 1;

            // colon
            while (i < json.length() && (json.charAt(i) == ':' || json.charAt(i) <= ' ')) i++;
            if (i >= json.length()) break;

            // value
            char v = json.charAt(i);
            if (v == '"') {
                int vs = i + 1, ve = nextQuote(json, vs);
                map.put(key, json.substring(vs, ve));
                i = ve + 1;
            } else if (v == 'n') { map.put(key, null);         i += 4; }
            else if   (v == 't') { map.put(key, Boolean.TRUE);  i += 4; }
            else if   (v == 'f') { map.put(key, Boolean.FALSE); i += 5; }
            else {
                int ne = i;
                while (ne < json.length() && ",} \n\r\t".indexOf(json.charAt(ne)) < 0) ne++;
                String num = json.substring(i, ne).trim();
                try { map.put(key, num.contains(".") ? Double.parseDouble(num) : Long.parseLong(num)); }
                catch (NumberFormatException ex) { map.put(key, num); }
                i = ne;
            }
        }
        return map;
    }

    /** Find the next unescaped quote starting at pos. */
    private static int nextQuote(String s, int pos) {
        for (int i = pos; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) return i;
        }
        return s.length();
    }
}

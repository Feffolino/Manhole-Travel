// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Map;

/** Converts script values (Rhino objects are Maps / Lists) to Gson. */
final class JsonConvert {
    private JsonConvert() {}

    static JsonObject toJsonObject(Object o) {
        JsonElement e = toJson(o);
        if (!e.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object, got " + o);
        }
        return e.getAsJsonObject();
    }

    static JsonElement toJson(Object o) {
        if (o == null) {
            return JsonNull.INSTANCE;
        }
        if (o instanceof JsonElement e) {
            return e;
        }
        if (o instanceof CharSequence s) {
            String str = s.toString().trim();
            if (str.startsWith("{") || str.startsWith("[")) {
                return JsonParser.parseString(str);
            }
            return new JsonPrimitive(s.toString());
        }
        if (o instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return new JsonPrimitive((long) d);
            }
            return new JsonPrimitive(d);
        }
        if (o instanceof Map<?, ?> m) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                obj.add(String.valueOf(e.getKey()), toJson(e.getValue()));
            }
            return obj;
        }
        if (o instanceof Iterable<?> it) {
            JsonArray a = new JsonArray();
            for (Object x : it) {
                a.add(toJson(x));
            }
            return a;
        }
        if (o instanceof Object[] arr) {
            JsonArray a = new JsonArray();
            for (Object x : arr) {
                a.add(toJson(x));
            }
            return a;
        }
        return new JsonPrimitive(o.toString());
    }
}

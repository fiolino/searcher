package org.fiolino.searcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

/**
 * Static utility class to retrieve Json data.
 * <p>
 * Created by kuli on 10.03.16.
 */
final class Json {
    private static final Gson GSON = new GsonBuilder().create();

    private Json() {
        throw new AssertionError();
    }

    /**
     * Extracts some data that is part of a json structure.
     * Reads only that part and ignores the surrounding content.
     *
     * @param content The JSON content
     * @param key     Which to look up in the structure
     * @param type    The returned data type
     * @param <T>     The type
     * @return The read value
     */
    static <T> T extractFrom(String content, String key, Type type) {
        JsonElement element = fromString(content, key);
        if (element == null || element.isJsonPrimitive()) {
            return null;
        }
        return GSON.fromJson(element, type);
    }

    private static JsonElement fromString(String json, String path) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                return null;
            }
            int lastIndex = 0;
            int nextIndex;
            do {
                nextIndex = path.indexOf('.', lastIndex);
                String element = nextIndex < 0 ? path.substring(lastIndex) : path.substring(lastIndex, nextIndex);
                JsonElement ele = obj.get(element);
                if (ele == null) {
                    return null;
                }
                if (!ele.isJsonObject()) {
                    return ele;
                } else {
                    obj = ele.getAsJsonObject();
                }
            } while ((lastIndex = nextIndex + 1) > 0);
            return obj;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

}

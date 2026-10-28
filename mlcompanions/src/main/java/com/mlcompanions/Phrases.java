package com.mlcompanions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Loads phrases from config/mlcompanions_phrases.json. */
public class Phrases {

    public static class Dialog {
        public final String trigger;
        /** each line: [who, text], who = "mystic" or "lagger" */
        public final List<String[]> lines = new ArrayList<String[]>();

        Dialog(String trigger) {
            this.trigger = trigger;
        }
    }

    private static final String DEFAULT_RES = "/assets/mlcompanions/phrases_default.json";
    private static final Map<String, Map<String, List<String>>> PHRASES = new HashMap<String, Map<String, List<String>>>();
    private static final List<Dialog> DIALOGS = new ArrayList<Dialog>();

    public static void load(File configDir) {
        File file = new File(configDir, "mlcompanions_phrases.json");
        try {
            if (!file.exists()) copyDefault(file);
        } catch (Exception e) {
            System.err.println("[mlcompanions] Can't write default phrases: " + e);
        }
        boolean ok = false;
        try {
            InputStream in = new FileInputStream(file);
            try {
                parse(new InputStreamReader(in, "UTF-8"));
                ok = true;
            } finally {
                in.close();
            }
        } catch (Exception e) {
            System.err.println("[mlcompanions] Error in " + file + ", using default phrases: " + e);
        }
        if (!ok) {
            try {
                InputStream in = Phrases.class.getResourceAsStream(DEFAULT_RES);
                try {
                    parse(new InputStreamReader(in, "UTF-8"));
                } finally {
                    in.close();
                }
            } catch (Exception e) {
                System.err.println("[mlcompanions] Can't load default phrases: " + e);
            }
        }
    }

    private static void copyDefault(File file) throws Exception {
        InputStream in = Phrases.class.getResourceAsStream(DEFAULT_RES);
        if (in == null) return;
        file.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(file);
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            out.close();
            in.close();
        }
    }

    private static void parse(Reader reader) {
        PHRASES.clear();
        DIALOGS.clear();
        JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
        for (String who : new String[] { "mystic", "lagger" }) {
            Map<String, List<String>> map = new HashMap<String, List<String>>();
            if (root.has(who) && root.get(who).isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(who).entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    List<String> list = new ArrayList<String>();
                    for (JsonElement s : e.getValue().getAsJsonArray()) list.add(s.getAsString());
                    if (!list.isEmpty()) map.put(e.getKey(), list);
                }
            }
            PHRASES.put(who, map);
        }
        if (root.has("dialogs") && root.get("dialogs").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("dialogs")) {
                JsonObject o = el.getAsJsonObject();
                Dialog d = new Dialog(o.get("trigger").getAsString());
                JsonArray lines = o.getAsJsonArray("lines");
                for (JsonElement l : lines) {
                    JsonArray pair = l.getAsJsonArray();
                    d.lines.add(new String[] { pair.get(0).getAsString(), pair.get(1).getAsString() });
                }
                if (!d.lines.isEmpty()) DIALOGS.add(d);
            }
        }
    }

    /** Random phrase, avoiding repeating "last". May return null. */
    public static String get(String who, String category, String last, Random rand) {
        Map<String, List<String>> map = PHRASES.get(who);
        if (map == null) return null;
        List<String> list = map.get(category);
        if (list == null || list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);
        for (int i = 0; i < 5; i++) {
            String s = list.get(rand.nextInt(list.size()));
            if (!s.equals(last)) return s;
        }
        return list.get(rand.nextInt(list.size()));
    }

    public static Dialog getDialog(String trigger, Random rand) {
        List<Dialog> found = new ArrayList<Dialog>();
        for (Dialog d : DIALOGS) if (d.trigger.equals(trigger)) found.add(d);
        if (found.isEmpty()) return null;
        return found.get(rand.nextInt(found.size()));
    }
}

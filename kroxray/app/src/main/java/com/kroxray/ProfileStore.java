package com.kroxray;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ProfileStore {
    private static final String PREFS = "kroxray_profiles";
    private static final String PROFILES = "profiles";
    private static final String SELECTED_ID = "selected_id";

    private final SharedPreferences preferences;

    ProfileStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Profile> getProfiles() {
        List<Profile> profiles = new ArrayList<>();
        JSONArray saved;
        try {
            saved = new JSONArray(preferences.getString(PROFILES, "[]"));
        } catch (JSONException exception) {
            return profiles;
        }

        for (int index = 0; index < saved.length(); index++) {
            JSONObject item = saved.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String id = item.optString("id");
            String name = item.optString("name");
            String content = item.optString("content");
            if (!id.isEmpty() && !content.isEmpty()) {
                profiles.add(new Profile(id, name, content));
            }
        }
        return profiles;
    }

    List<Profile> addFromText(String input) throws JSONException {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("الملف أو الرابط فارغ.");
        }

        List<Profile> added = new ArrayList<>();
        if (text.startsWith("{")) {
            JSONObject configuration = new JSONObject(text);
            if (!configuration.has("outbounds") && !configuration.has("server")) {
                throw new IllegalArgumentException("لم يتم التعرف على إعداد Xray صالح.");
            }
            String name = firstNonEmpty(
                    configuration.optString("remarks"),
                    configuration.optString("name"),
                    "إعداد Xray"
            );
            added.add(new Profile(UUID.randomUUID().toString(), name, text));
        } else {
            String[] lines = text.split("\\R");
            for (String line : lines) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                String protocol = protocolOf(value);
                if (protocol == null) {
                    throw new IllegalArgumentException("رابط غير مدعوم: " + shortValue(value));
                }
                added.add(new Profile(UUID.randomUUID().toString(), protocol + " • " + (added.size() + 1), value));
            }
        }

        if (added.isEmpty()) {
            throw new IllegalArgumentException("لم يتم العثور على روابط اتصال.");
        }

        List<Profile> all = getProfiles();
        all.addAll(added);
        save(all);
        if (preferences.getString(SELECTED_ID, "").isEmpty()) {
            setSelectedId(added.get(0).id);
        }
        return added;
    }

    void setSelectedId(String id) {
        preferences.edit().putString(SELECTED_ID, id).apply();
    }

    String getSelectedId() {
        return preferences.getString(SELECTED_ID, "");
    }

    void remove(String id) {
        List<Profile> remaining = new ArrayList<>();
        for (Profile profile : getProfiles()) {
            if (!profile.id.equals(id)) {
                remaining.add(profile);
            }
        }
        save(remaining);
        if (id.equals(getSelectedId())) {
            setSelectedId(remaining.isEmpty() ? "" : remaining.get(0).id);
        }
    }

    private void save(List<Profile> profiles) {
        JSONArray encoded = new JSONArray();
        for (Profile profile : profiles) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", profile.id);
                item.put("name", profile.name);
                item.put("content", profile.content);
                encoded.put(item);
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(PROFILES, encoded.toString()).apply();
    }

    private static String protocolOf(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String protocol : new String[]{"vless", "vmess", "trojan", "ss", "socks"}) {
            if (lower.startsWith(protocol + "://")) {
                return protocol.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static String firstNonEmpty(String first, String second, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return fallback;
    }

    private static String shortValue(String value) {
        return value.length() > 28 ? value.substring(0, 28) + "…" : value;
    }

    static final class Profile {
        final String id;
        final String name;
        final String content;

        Profile(String id, String name, String content) {
            this.id = id;
            this.name = name;
            this.content = content;
        }
    }
}
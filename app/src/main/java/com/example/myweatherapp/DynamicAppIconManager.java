package com.example.myweatherapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.Locale;

/** Switches launcher aliases so the installed icon follows local weather. */
final class DynamicAppIconManager {

    private static final String PREFS = "dynamic_icon_state";
    private static final String KEY_ALIAS = "active_alias";
    private static final String[] ALIASES = {
            "LauncherDefault", "LauncherSunny", "LauncherCloudy",
            "LauncherRain", "LauncherSnow", "LauncherNight",
            "LauncherRainNight", "LauncherSnowNight", "LauncherCloudyNight",
            "LauncherThunder", "LauncherFog"
    };

    private DynamicAppIconManager() { }

    static void update(Context context, String condition, boolean isDay) {
        String desired = chooseAlias(condition, isDay);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (desired.equals(prefs.getString(KEY_ALIAS, "LauncherDefault"))) return;

        PackageManager manager = context.getPackageManager();
        String packageName = context.getPackageName();
        manager.setComponentEnabledSetting(
                new ComponentName(packageName, packageName + "." + desired),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        for (String alias : ALIASES) {
            if (alias.equals(desired)) continue;
            manager.setComponentEnabledSetting(
                    new ComponentName(packageName, packageName + "." + alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        prefs.edit().putString(KEY_ALIAS, desired).apply();
    }

    private static String chooseAlias(String condition, boolean isDay) {
        String value = condition == null ? "" : condition.toLowerCase(new Locale("ru", "RU"));
        if (containsAny(value, "гроз", "гром")) return "LauncherThunder";
        if (containsAny(value, "дожд", "морос", "ливень")) {
            return isDay ? "LauncherRain" : "LauncherRainNight";
        }
        if (containsAny(value, "снег", "метель", "лед", "круп")) {
            return isDay ? "LauncherSnow" : "LauncherSnowNight";
        }
        if (containsAny(value, "туман", "дымка", "мгла")) return "LauncherFog";
        if (!isDay && containsAny(value, "ясно", "солнечно")) return "LauncherNight";
        if (containsAny(value, "облач", "пасмур")) {
            return isDay ? "LauncherCloudy" : "LauncherCloudyNight";
        }
        if (containsAny(value, "ясно", "солнечно")) return "LauncherSunny";
        if (!isDay) return "LauncherNight";
        return "LauncherDefault";
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}

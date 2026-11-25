package com.example.myweatherapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_REFRESH = "com.example.myweatherapp.WIDGET_REFRESH";
    private static final String PREFS = "weather_widget_state";
    private static final String KEY_CITY = "city";
    private static final String KEY_CONDITION = "condition";
    private static final String KEY_UPDATED = "updated";
    private static final String KEY_TEMP_C = "temp_c";
    private static final String KEY_TEMP_F = "temp_f";
    private static final String KEY_MAX_C = "max_c";
    private static final String KEY_MAX_F = "max_f";
    private static final String KEY_MIN_C = "min_c";
    private static final String KEY_MIN_F = "min_f";
    private static final String KEY_IS_DAY = "is_day";
    private static final String KEY_FAHRENHEIT = "fahrenheit";
    private static final String KEY_USE_LOCATION = "use_location";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_HAS_LOCATION = "has_location";
    private static final String KEY_WIDGET_CITY = "widget_city";
    private static final String KEY_HUMIDITY = "humidity";
    private static final String KEY_WIND_KPH = "wind_kph";
    private static final String KEY_WIND_MPH = "wind_mph";
    private static final String KEY_FEELS_C = "feels_c";
    private static final String KEY_FEELS_F = "feels_f";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Locale RU_LOCALE = new Locale("ru", "RU");
    private static final int MODE_VERTICAL = 1;
    private static final int MODE_HORIZONTAL = 2;
    private static final int MODE_COMPACT = 3;
    private static final int MODE_WIDE = 4;
    private static final int MODE_EXPANDED = 5;
    private static final int MODE_SQUARE = 6;
    private static final int MODE_TALL = 7;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) updateWidget(context, manager, widgetId);
        refreshFromNetwork(context.getApplicationContext());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            showRefreshing(context);
            refreshFromNetwork(context.getApplicationContext());
        }
    }

    static void publish(Context context, String city, String condition,
                        double tempC, double tempF, double maxC, double maxF,
                        double minC, double minF, boolean isDay, boolean fahrenheit,
                        boolean locationBased, int humidity, double windKph, double windMph,
                        double feelsC, double feelsF) {
        String configuredCity = getWidgetCity(context);
        boolean isSelectedCity = !configuredCity.isEmpty()
                && (configuredCity.equalsIgnoreCase(city)
                || configuredCity.toLowerCase(RU_LOCALE).startsWith(city.toLowerCase(RU_LOCALE)));
        if (!locationBased && !isSelectedCity) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_FAHRENHEIT, fahrenheit).apply();
            updateAllWidgets(context);
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_CITY, city)
                .putString(KEY_CONDITION, condition)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .putFloat(KEY_TEMP_C, (float) tempC)
                .putFloat(KEY_TEMP_F, (float) tempF)
                .putFloat(KEY_MAX_C, (float) maxC)
                .putFloat(KEY_MAX_F, (float) maxF)
                .putFloat(KEY_MIN_C, (float) minC)
                .putFloat(KEY_MIN_F, (float) minF)
                .putBoolean(KEY_IS_DAY, isDay)
                .putBoolean(KEY_FAHRENHEIT, fahrenheit)
                .putBoolean(KEY_USE_LOCATION, locationBased)
                .putInt(KEY_HUMIDITY, humidity)
                .putFloat(KEY_WIND_KPH, (float) windKph)
                .putFloat(KEY_WIND_MPH, (float) windMph)
                .putFloat(KEY_FEELS_C, (float) feelsC)
                .putFloat(KEY_FEELS_F, (float) feelsF)
                .apply();
        if (locationBased) DynamicAppIconManager.update(context, condition, isDay);
        updateAllWidgets(context);
    }

    static String getWidgetCity(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_WIDGET_CITY, "");
    }

    static void setWidgetCity(Context context, String city) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_WIDGET_CITY, city == null ? "" : city.trim()).apply();
        showRefreshing(context);
        refreshFromNetwork(context.getApplicationContext());
    }

    static void setFahrenheit(Context context, boolean fahrenheit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_FAHRENHEIT, fahrenheit).apply();
        updateAllWidgets(context);
    }

    static void saveLocation(Context context, double latitude, double longitude) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LATITUDE, Double.doubleToRawLongBits(latitude))
                .putLong(KEY_LONGITUDE, Double.doubleToRawLongBits(longitude))
                .putBoolean(KEY_HAS_LOCATION, true)
                .putBoolean(KEY_USE_LOCATION, true)
                .apply();
    }

    private static void refreshFromNetwork(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean hasLocation = prefs.getBoolean(KEY_HAS_LOCATION, false);
        String configuredCity = prefs.getString(KEY_WIDGET_CITY, "");
        if ((configuredCity == null || configuredCity.isEmpty()) && !hasLocation) {
            updateAllWidgets(context);
            return;
        }
        final String requestQuery;
        if (configuredCity != null && !configuredCity.isEmpty()) {
            requestQuery = configuredCity;
        } else {
            double latitude = Double.longBitsToDouble(prefs.getLong(KEY_LATITUDE, 0L));
            double longitude = Double.longBitsToDouble(prefs.getLong(KEY_LONGITUDE, 0L));
            requestQuery = String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
        }

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.weatherapi.com")
                .addPathSegments("v1/forecast.json")
                .addQueryParameter("key", MainActivity.API_KEY)
                .addQueryParameter("q", requestQuery)
                .addQueryParameter("days", "1")
                .addQueryParameter("aqi", "no")
                .addQueryParameter("alerts", "no")
                .addQueryParameter("lang", "ru")
                .build();

        CLIENT.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                markUpdateFailed(context);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    markUpdateFailed(context);
                    return;
                }
                try {
                    JSONObject root = new JSONObject(response.body().string());
                    JSONObject current = root.getJSONObject("current");
                    JSONArray forecastDays = root.getJSONObject("forecast").getJSONArray("forecastday");
                    JSONObject day = forecastDays.getJSONObject(0).getJSONObject("day");
                    String resolvedCity = root.getJSONObject("location").getString("name");
                    publish(context,
                            resolvedCity,
                            current.getJSONObject("condition").getString("text"),
                            current.getDouble("temp_c"), current.getDouble("temp_f"),
                            day.getDouble("maxtemp_c"), day.getDouble("maxtemp_f"),
                            day.getDouble("mintemp_c"), day.getDouble("mintemp_f"),
                            current.optInt("is_day", 1) == 1,
                            prefs.getBoolean(KEY_FAHRENHEIT, false),
                            configuredCity == null || configuredCity.isEmpty(),
                            current.optInt("humidity", 0),
                            current.optDouble("wind_kph", 0), current.optDouble("wind_mph", 0),
                            current.optDouble("feelslike_c", 0), current.optDouble("feelslike_f", 0));
                } catch (Exception e) {
                    markUpdateFailed(context);
                }
            }
        });
    }

    private static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, WeatherWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int mode = modeForOptions(options);
        int layout = layoutForMode(mode);
        boolean compact = mode == MODE_VERTICAL || mode == MODE_HORIZONTAL
                || mode == MODE_COMPACT || mode == MODE_SQUARE || mode == MODE_TALL;
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);

        String city = prefs.getString(KEY_CITY, "");
        String condition = prefs.getString(KEY_CONDITION, "Нет данных");
        boolean fahrenheit = prefs.getBoolean(KEY_FAHRENHEIT, false);
        boolean hasData = city != null && !city.isEmpty();
        double temp = prefs.getFloat(fahrenheit ? KEY_TEMP_F : KEY_TEMP_C, 0f);
        double max = prefs.getFloat(fahrenheit ? KEY_MAX_F : KEY_MAX_C, 0f);
        double min = prefs.getFloat(fahrenheit ? KEY_MIN_F : KEY_MIN_C, 0f);
        int humidity = prefs.getInt(KEY_HUMIDITY, 0);
        double wind = prefs.getFloat(fahrenheit ? KEY_WIND_MPH : KEY_WIND_KPH, 0f);
        double feels = prefs.getFloat(fahrenheit ? KEY_FEELS_F : KEY_FEELS_C, 0f);

        views.setTextViewText(R.id.widgetCity, hasData ? city : "Откройте приложение");
        views.setTextViewText(R.id.widgetTemperature, hasData ? formatTemp(temp) : "--°");
        views.setTextViewText(R.id.widgetCondition, hasData ? capitalize(condition) : "Нужна геолокация");
        views.setTextViewText(R.id.widgetHighLow, hasData
                ? (compact
                    ? formatTemp(max) + " / " + formatTemp(min)
                    : "Макс. " + formatTemp(max) + "  Мин. " + formatTemp(min))
                : (compact ? "--° / --°" : "Макс. --°  Мин. --°"));
        if (mode == MODE_EXPANDED) {
            views.setTextViewText(R.id.widgetHumidity, hasData ? humidity + "%" : "--%");
            views.setTextViewText(R.id.widgetWind, hasData
                    ? (fahrenheit
                        ? String.format(RU_LOCALE, "%.0f mph", wind)
                        : String.format(RU_LOCALE, "%.1f м/с", wind / 3.6))
                    : "--");
            views.setTextViewText(R.id.widgetFeels, hasData ? formatTemp(feels) : "--°");
        }
        boolean isDay = prefs.getBoolean(KEY_IS_DAY, true);
        int weatherIcon = hasData ? iconForCondition(condition, isDay) : R.drawable.ic_widget_cloud;
        // Always reset both flippers after a resize/update. Without this, the
        // launcher can retain a child index from the previous size and leave
        // the icon area empty on 2×2/2×3 widgets.
        views.setInt(R.id.widgetBackgroundMotion, "setDisplayedChild", 0);
        views.setInt(R.id.widgetIconMotion, "setDisplayedChild", 0);
        views.setImageViewResource(R.id.widgetIcon, weatherIcon);
        views.setImageViewResource(R.id.widgetIcon2, weatherIcon);
        views.setImageViewResource(R.id.widgetIcon3, weatherIcon);
        views.setImageViewResource(R.id.widgetMotion1, motionForCondition(condition, isDay, 0));
        views.setImageViewResource(R.id.widgetMotion2, motionForCondition(condition, isDay, 1));
        views.setImageViewResource(R.id.widgetMotion3, motionForCondition(condition, isDay, 2));
        views.setInt(R.id.widgetRoot, "setBackgroundResource",
                hasData ? backgroundForCondition(condition, isDay)
                        : (compact ? R.drawable.widget_background_compact : R.drawable.widget_background_wide));

        long updated = prefs.getLong(KEY_UPDATED, 0L);
        String updatedText = updated == 0
                ? "Нажмите, чтобы настроить"
                : "Обновлено " + new SimpleDateFormat("HH:mm", RU_LOCALE).format(new Date(updated));
        views.setTextViewText(R.id.widgetUpdated, updatedText);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(context, widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent);

        Intent refreshIntent = new Intent(context, WeatherWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 10_000 + widgetId,
                refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPendingIntent);
        manager.updateAppWidget(widgetId, views);
    }

    private static void showRefreshing(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, WeatherWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(component)) {
            Bundle options = manager.getAppWidgetOptions(id);
            RemoteViews views = new RemoteViews(context.getPackageName(),
                    layoutForMode(modeForOptions(options)));
            views.setTextViewText(R.id.widgetUpdated, "Обновление…");
            manager.partiallyUpdateAppWidget(id, views);
        }
    }

    private static void markUpdateFailed(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, WeatherWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(component)) {
            Bundle options = manager.getAppWidgetOptions(id);
            RemoteViews views = new RemoteViews(context.getPackageName(),
                    layoutForMode(modeForOptions(options)));
            views.setTextViewText(R.id.widgetUpdated, "Нет сети — данные сохранены");
            manager.partiallyUpdateAppWidget(id, views);
        }
    }

    private static int iconForCondition(String condition, boolean isDay) {
        String value = condition == null ? "" : condition.toLowerCase(RU_LOCALE);
        if (containsAny(value, "гроз", "гром")) return R.drawable.ic_widget_thunder;
        if (containsAny(value, "дожд", "морос", "ливень")) return R.drawable.ic_widget_rain;
        if (containsAny(value, "снег", "метель", "лед", "круп")) return R.drawable.ic_widget_snow;
        if (containsAny(value, "туман", "дымка", "мгла")) return R.drawable.ic_widget_fog;
        if (containsAny(value, "облач", "пасмур")) return R.drawable.ic_widget_cloud;
        return isDay ? R.drawable.ic_widget_sun : R.drawable.ic_widget_cloud;
    }

    private static int backgroundForCondition(String condition, boolean isDay) {
        if (!isDay) return R.drawable.widget_bg_night;
        String value = condition == null ? "" : condition.toLowerCase(RU_LOCALE);
        if (containsAny(value, "дожд", "морос", "ливень", "гроз")) return R.drawable.widget_bg_rain;
        if (containsAny(value, "снег", "метель", "лед", "круп")) return R.drawable.widget_bg_snow;
        if (containsAny(value, "облач", "пасмур", "туман", "дымка")) return R.drawable.widget_bg_cloudy;
        return R.drawable.widget_bg_sunny;
    }

    private static int motionForCondition(String condition, boolean isDay, int frame) {
        if (!isDay) return frame == 1
                ? R.drawable.widget_scene_night_2 : R.drawable.widget_scene_night_1;
        String value = condition == null ? "" : condition.toLowerCase(RU_LOCALE);
        if (containsAny(value, "дожд", "морос", "ливень", "гроз")) {
            if (frame == 1) return R.drawable.widget_scene_rain_2;
            return R.drawable.widget_scene_rain_1;
        }
        if (containsAny(value, "снег", "метель", "лед", "круп")) {
            if (frame == 1) return R.drawable.widget_scene_snow_2;
            return R.drawable.widget_scene_snow_1;
        }
        if (containsAny(value, "облач", "пасмур", "туман", "дымка")) return R.drawable.widget_motion_cloudy;
        return frame == 1 ? R.drawable.widget_scene_sunny_2 : R.drawable.widget_scene_sunny_1;
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private static int modeForOptions(Bundle options) {
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90);
        return modeForDimensions(width, height);
    }

    static int modeForDimensions(int width, int height) {
        if (width < 105 && height >= 105) return MODE_VERTICAL;
        if (height < 105) return MODE_HORIZONTAL;
        if (width < 220 && height >= 205) return MODE_TALL;
        if (width < 220) return MODE_SQUARE;
        // Pixel Launcher reports a three-column widget as roughly 220-235 dp wide.
        // Switch to the information-rich layout as soon as there is enough real
        // space for its artwork and three metrics, instead of requiring four columns.
        if (width >= 220 && height >= 180) return MODE_EXPANDED;
        return MODE_WIDE;
    }

    private static int layoutForMode(int mode) {
        switch (mode) {
            case MODE_VERTICAL: return R.layout.weather_widget_vertical;
            case MODE_HORIZONTAL: return R.layout.weather_widget_horizontal;
            case MODE_EXPANDED: return R.layout.weather_widget_expanded;
            case MODE_SQUARE: return R.layout.weather_widget_square;
            case MODE_TALL: return R.layout.weather_widget_tall;
            case MODE_WIDE: return R.layout.weather_widget;
            case MODE_COMPACT:
            default: return R.layout.weather_widget_compact;
        }
    }

    private static String formatTemp(double value) {
        return Math.round(value) + "°";
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase(RU_LOCALE) + value.substring(1);
    }
}

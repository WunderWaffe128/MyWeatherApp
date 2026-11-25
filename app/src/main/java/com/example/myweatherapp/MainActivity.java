package com.example.myweatherapp;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final OkHttpClient CLIENT = new OkHttpClient();
    static final String API_KEY = BuildConfig.WEATHER_API_KEY;
    private static final String PREFS_NAME = "weather_prefs";
    private static final String KEY_HISTORY = "search_history"; // legacy, never auto-filled anymore
    private static final String KEY_FAVORITES = "favorite_cities_v2";
    private static final String KEY_FAHRENHEIT = "fahrenheit";
    private static final String KEY_CACHED_WEATHER = "cached_weather_json";
    private static final String KEY_CACHED_AT = "cached_weather_at";
    private static final String KEY_LOCATION_ASKED = "location_asked";
    private static final String KEY_SMOOTH_ANIMATIONS = "smooth_animations";
    private static final String KEY_STRONG_GLASS = "strong_glass";
    private static final String KEY_GLASS_THEME = "glass_theme";
    private static final String HISTORY_SEPARATOR = "\u001F";
    private static final Locale RU_LOCALE = new Locale("ru", "RU");
    private final PathInterpolator smoothOut = new PathInterpolator(0.16f, 1f, 0.30f, 1f);
    private final PathInterpolator smoothInOut = new PathInterpolator(0.45f, 0f, 0.55f, 1f);

    private AutoCompleteTextView cityAutoComplete;
    private ImageButton searchButton;
    private ImageButton menuButton;
    private ProgressBar loadingProgressBar;
    private ImageView backgroundImage;
    private ImageView seasonOverlay;
    private AnimatedWeatherIconView weatherIcon;
    private View ambientOrb;
    private View weatherContent;
    private View emptyState;
    private TextView statusText;
    private TextView locationText;
    private TextView localTimeText;
    private TextView temperatureText;
    private TextView weatherConditionText;
    private TextView highLowText;
    private TextView updatedText;
    private LinearLayout historyContainer;
    private LinearLayout hourlyContainer;
    private LinearLayout dailyContainer;
    private View favoritesPanel;
    private TextView manageFavoritesButton;
    private TextView favoriteToggleButton;
    private TextView astronomyText;
    private TextView adviceText;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private TextView detailWindValue;
    private TextView detailPressureValue;
    private TextView detailVisibilityValue;
    private TextView detailGustValue;
    private ImageView detailWindIcon;
    private CelestialView celestialView;
    private View humidityMetric;
    private View windMetric;
    private View feelsMetric;
    private ScrollView weatherScrollView;
    private View radarCard;
    private WeatherChartView weatherChart;
    private TextView chartSummary;

    private ArrayAdapter<String> suggestAdapter;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Call suggestionCall;
    private String lastFetchedJson = "";
    private boolean lastFetchUsedLocation;
    private boolean initialLocationResolved;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private LocationManager locationManager;
    private LocationListener activeLocationListener;
    private ObjectAnimator orbAnimator;
    private boolean animationsEnabled = true;
    private float glassScale = 1f;
    private int glassTheme;
    private String currentDisplayedCity = "";
    private boolean scrollPerformanceMode;
    private double currentLatitude = Double.NaN;
    private double currentLongitude = Double.NaN;
    private String currentRadarCity = "";
    private final Runnable scrollIdleRunnable = () -> setScrollPerformanceMode(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) requestCurrentLocation();
                    else loadFallbackWeather(true);
                });
        configureWindow();
        setContentView(R.layout.activity_main);

        initViews();
        SharedPreferences appearance = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        animationsEnabled = appearance.getBoolean(KEY_SMOOTH_ANIMATIONS, true);
        glassScale = appearance.getBoolean(KEY_STRONG_GLASS, true) ? 1.18f : 0.72f;
        glassTheme = appearance.getInt(KEY_GLASS_THEME, 0);
        applyAppearanceToTree(findViewById(R.id.mainLayout));
        setupAutoSuggest();
        setupListeners();
        setupScrollPerformance();
        updateHistoryUI();
        if (animationsEnabled) startAmbientAnimations();
        startInitialWeatherLoad();
    }

    private void startInitialWeatherLoad() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            requestCurrentLocation();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_LOCATION_ASKED, false)) {
            prefs.edit().putBoolean(KEY_LOCATION_ASKED, true).apply();
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            loadFallbackWeather(false);
        }
    }

    private void requestCurrentLocation() {
        if (initialLocationResolved) return;
        setLoading(true);
        showStatus("Определяем ваше местоположение…");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        try {
            Location best = null;
            for (String provider : locationManager.getProviders(true)) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 6 * 60 * 60 * 1000L) {
                useDeviceLocation(best);
                return;
            }

            activeLocationListener = new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location location) { useDeviceLocation(location); }
                @Override public void onProviderEnabled(@NonNull String provider) { }
                @Override public void onProviderDisabled(@NonNull String provider) { }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            };
            boolean requested = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(
                        LocationManager.GPS_PROVIDER, activeLocationListener, Looper.getMainLooper());
                requested = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER, activeLocationListener, Looper.getMainLooper());
                requested = true;
            }
            if (!requested) {
                loadFallbackWeather(false);
                return;
            }
            searchHandler.postDelayed(() -> {
                if (!initialLocationResolved) {
                    stopLocationUpdates();
                    loadFallbackWeather(false);
                }
            }, 10_000);
        } catch (SecurityException | IllegalArgumentException e) {
            loadFallbackWeather(false);
        }
    }

    private void useDeviceLocation(Location location) {
        if (initialLocationResolved) return;
        initialLocationResolved = true;
        stopLocationUpdates();
        WeatherWidgetProvider.saveLocation(this, location.getLatitude(), location.getLongitude());
        String query = String.format(Locale.US, "%.6f,%.6f", location.getLatitude(), location.getLongitude());
        fetchWeather(query, true);
    }

    private void stopLocationUpdates() {
        if (locationManager != null && activeLocationListener != null) {
            try { locationManager.removeUpdates(activeLocationListener); } catch (SecurityException ignored) { }
        }
        activeLocationListener = null;
    }

    private void loadFallbackWeather(boolean permissionDenied) {
        if (initialLocationResolved) return;
        initialLocationResolved = true;
        setLoading(false);
        List<String> favorites = getFavoritesFromPrefs();
        if (!favorites.isEmpty()) {
            cityAutoComplete.setText(favorites.get(0));
            cityAutoComplete.setSelection(cityAutoComplete.length());
            fetchWeather(favorites.get(0), false);
        } else {
            String cached = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_CACHED_WEATHER, "");
            if (cached != null && !cached.isEmpty()) {
                showCachedWeather("Показываем последний сохранённый прогноз");
            } else {
                emptyState.setVisibility(View.VISIBLE);
                if (permissionDenied) showStatus("Разрешите доступ к геопозиции, чтобы сразу видеть погоду рядом с вами.");
                else hideStatus();
            }
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(7, 17, 31));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void initViews() {
        cityAutoComplete = findViewById(R.id.cityEditText);
        searchButton = findViewById(R.id.searchButton);
        menuButton = findViewById(R.id.menuButton);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        backgroundImage = findViewById(R.id.backgroundImage);
        seasonOverlay = findViewById(R.id.seasonOverlay);
        weatherIcon = findViewById(R.id.weatherIcon);
        ambientOrb = findViewById(R.id.ambientOrb);
        weatherContent = findViewById(R.id.weatherContent);
        emptyState = findViewById(R.id.emptyState);
        statusText = findViewById(R.id.statusText);
        locationText = findViewById(R.id.locationText);
        localTimeText = findViewById(R.id.localTimeText);
        temperatureText = findViewById(R.id.temperatureText);
        weatherConditionText = findViewById(R.id.weatherConditionText);
        highLowText = findViewById(R.id.highLowText);
        updatedText = findViewById(R.id.updatedText);
        historyContainer = findViewById(R.id.historyContainer);
        hourlyContainer = findViewById(R.id.hourlyContainer);
        dailyContainer = findViewById(R.id.dailyContainer);
        favoritesPanel = findViewById(R.id.favoritesPanel);
        manageFavoritesButton = findViewById(R.id.manageFavoritesButton);
        favoriteToggleButton = findViewById(R.id.favoriteToggleButton);
        astronomyText = findViewById(R.id.astronomyText);
        adviceText = findViewById(R.id.adviceText);
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        detailWindValue = findViewById(R.id.detailWindValue);
        detailPressureValue = findViewById(R.id.detailPressureValue);
        detailVisibilityValue = findViewById(R.id.detailVisibilityValue);
        detailGustValue = findViewById(R.id.detailGustValue);
        detailWindIcon = findViewById(R.id.detailWindIcon);
        celestialView = findViewById(R.id.celestialView);
        humidityMetric = findViewById(R.id.humidityMetric);
        windMetric = findViewById(R.id.windMetric);
        feelsMetric = findViewById(R.id.feelsMetric);
        weatherScrollView = findViewById(R.id.weatherScrollView);
        radarCard = findViewById(R.id.radarCard);
        weatherChart = findViewById(R.id.weatherChart);
        chartSummary = findViewById(R.id.chartSummary);
        backgroundImage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ambientOrb.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private void setupAutoSuggest() {
        suggestAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>()) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.rgb(19, 33, 52));
                view.setTextSize(15);
                view.setPadding(dp(16), dp(12), dp(16), dp(12));
                return view;
            }
        };
        cityAutoComplete.setAdapter(suggestAdapter);
    }

    private void setupListeners() {
        searchButton.setOnClickListener(v -> submitSearch());
        menuButton.setOnClickListener(v -> showCustomizationMenu());
        manageFavoritesButton.setOnClickListener(v -> showCustomizationMenu());
        favoriteToggleButton.setOnClickListener(v -> toggleCurrentFavorite());
        radarCard.setOnClickListener(v -> openRadar());
        cityAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch();
                return true;
            }
            return false;
        });
        cityAutoComplete.setOnItemClickListener((parent, view, position, id) -> submitSearch());

        cityAutoComplete.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    searchRunnable = () -> fetchCitySuggestions(query);
                    searchHandler.postDelayed(searchRunnable, 350);
                }
            }

            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void openRadar() {
        if (Double.isNaN(currentLatitude) || Double.isNaN(currentLongitude)) {
            showStatus("Сначала загрузите погоду для города — затем откроется радар.");
            return;
        }
        Intent intent = new Intent(this, RadarActivity.class);
        intent.putExtra("latitude", currentLatitude);
        intent.putExtra("longitude", currentLongitude);
        intent.putExtra("city", currentRadarCity);
        startActivity(intent);
    }

    private void setupScrollPerformance() {
        weatherScrollView.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY == oldScrollY) return;
            setScrollPerformanceMode(true);
            searchHandler.removeCallbacks(scrollIdleRunnable);
            searchHandler.postDelayed(scrollIdleRunnable, 180L);
        });
        weatherScrollView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                setScrollPerformanceMode(true);
                searchHandler.removeCallbacks(scrollIdleRunnable);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                searchHandler.removeCallbacks(scrollIdleRunnable);
                searchHandler.postDelayed(scrollIdleRunnable, 220L);
            }
            return false;
        });
    }

    private void setScrollPerformanceMode(boolean active) {
        if (scrollPerformanceMode == active) return;
        scrollPerformanceMode = active;
        if (active) {
            backgroundImage.animate().cancel();
            if (orbAnimator != null) orbAnimator.cancel();
        } else if (animationsEnabled && !isFinishing()) {
            startAmbientAnimations();
        }
        applyPerformanceToTree(findViewById(R.id.mainLayout));
    }

    private void applyPerformanceToTree(View view) {
        if (view instanceof LiquidGlassLayout) {
            ((LiquidGlassLayout) view).setInteractionPaused(scrollPerformanceMode);
        }
        if (view instanceof AnimatedWeatherIconView) {
            ((AnimatedWeatherIconView) view)
                    .setAnimationsEnabled(animationsEnabled && !scrollPerformanceMode);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyPerformanceToTree(group.getChildAt(i));
            }
        }
    }

    private void submitSearch() {
        String city = cityAutoComplete.getText().toString().trim();
        if (city.isEmpty()) {
            showStatus("Введите название города");
            cityAutoComplete.requestFocus();
            return;
        }
        hideKeyboard();
        cityAutoComplete.dismissDropDown();
        fetchWeather(city);
    }

    private void fetchCitySuggestions(String query) {
        if (suggestionCall != null) suggestionCall.cancel();
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.weatherapi.com")
                .addPathSegments("v1/search.json")
                .addQueryParameter("key", API_KEY)
                .addQueryParameter("q", query)
                .build();
        Request request = new Request.Builder().url(url).build();

        suggestionCall = CLIENT.newCall(request);
        suggestionCall.enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONArray jsonArray = new JSONArray(response.body().string());
                    List<String> cities = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject object = jsonArray.getJSONObject(i);
                        String region = object.optString("region");
                        String label = object.getString("name");
                        if (!region.isEmpty() && !region.equalsIgnoreCase(label)) label += ", " + region;
                        label += ", " + object.getString("country");
                        cities.add(label);
                    }
                    runOnUiThread(() -> {
                        if (!cityAutoComplete.getText().toString().toLowerCase(RU_LOCALE)
                                .startsWith(query.toLowerCase(RU_LOCALE))) return;
                        suggestAdapter.clear();
                        suggestAdapter.addAll(cities);
                        suggestAdapter.notifyDataSetChanged();
                        if (cityAutoComplete.hasFocus()) cityAutoComplete.showDropDown();
                    });
                } catch (JSONException ignored) { }
            }
        });
    }

    private void fetchWeather(String city) {
        fetchWeather(city, false);
    }

    private void fetchWeather(String city, boolean locationBased) {
        lastFetchUsedLocation = locationBased;
        setLoading(true);
        hideStatus();

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.weatherapi.com")
                .addPathSegments("v1/forecast.json")
                .addQueryParameter("key", API_KEY)
                .addQueryParameter("q", city)
                .addQueryParameter("days", "3")
                .addQueryParameter("aqi", "yes")
                .addQueryParameter("alerts", "yes")
                .addQueryParameter("lang", "ru")
                .build();
        Request request = new Request.Builder().url(url).build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showCachedWeather("Нет сети — показываем последний сохранённый прогноз");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    if (response.code() == 400) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showStatus("Город не найден. Проверьте написание.");
                        });
                    } else {
                        showCachedWeather("Сервис недоступен — показываем сохранённый прогноз");
                    }
                    return;
                }
                lastFetchedJson = response.body().string();
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(KEY_CACHED_WEATHER, lastFetchedJson)
                        .putLong(KEY_CACHED_AT, System.currentTimeMillis())
                        .apply();
                parseAndDisplayWeather(lastFetchedJson, true);
            }
        });
    }

    private void showCachedWeather(String message) {
        String cached = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_CACHED_WEATHER, "");
        if (cached == null || cached.isEmpty()) {
            runOnUiThread(() -> {
                setLoading(false);
                showStatus(message.startsWith("Нет сети")
                        ? "Нет соединения. Проверьте интернет и попробуйте снова."
                        : "Сервис погоды временно недоступен.");
            });
            return;
        }
        try {
            WeatherPayload payload = parseWeather(cached);
            runOnUiThread(() -> {
                displayWeather(payload, false);
                showStatus(message);
            });
        } catch (JSONException e) {
            runOnUiThread(() -> {
                setLoading(false);
                showStatus("Не удалось открыть сохранённый прогноз.");
            });
        }
    }

    private void parseAndDisplayWeather(String jsonData, boolean animate) {
        try {
            WeatherPayload payload = parseWeather(jsonData);
            runOnUiThread(() -> displayWeather(payload, animate));
        } catch (JSONException e) {
            runOnUiThread(() -> {
                setLoading(false);
                showStatus("Не удалось обработать данные прогноза.");
            });
        }
    }

    private WeatherPayload parseWeather(String jsonData) throws JSONException {
        JSONObject root = new JSONObject(jsonData);
        JSONObject location = root.getJSONObject("location");
        JSONObject current = root.getJSONObject("current");
        JSONArray forecastDays = root.getJSONObject("forecast").getJSONArray("forecastday");
        JSONObject today = forecastDays.getJSONObject(0).getJSONObject("day");

        WeatherPayload payload = new WeatherPayload();
        payload.city = location.getString("name");
        payload.country = location.optString("country");
        payload.latitude = location.optDouble("lat", Double.NaN);
        payload.longitude = location.optDouble("lon", Double.NaN);
        payload.timeZone = location.optString("tz_id", "UTC");
        payload.localEpoch = location.getLong("localtime_epoch");
        payload.currentEpoch = current.optLong("last_updated_epoch", payload.localEpoch);
        payload.tempC = current.getDouble("temp_c");
        payload.tempF = current.getDouble("temp_f");
        payload.feelsC = current.getDouble("feelslike_c");
        payload.feelsF = current.getDouble("feelslike_f");
        payload.humidity = current.getInt("humidity");
        payload.windKph = current.getDouble("wind_kph");
        payload.windMph = current.getDouble("wind_mph");
        payload.windDegree = current.optInt("wind_degree", 0);
        payload.windDirection = current.optString("wind_dir", "—");
        payload.gustKph = current.optDouble("gust_kph", payload.windKph);
        payload.gustMph = current.optDouble("gust_mph", payload.windMph);
        payload.pressureMb = current.optDouble("pressure_mb", 0);
        payload.visibilityKm = current.optDouble("vis_km", 0);
        payload.uv = current.optDouble("uv", 0);
        payload.cloud = current.optInt("cloud", 0);
        payload.isDay = current.optInt("is_day", 1);
        payload.condition = current.getJSONObject("condition").getString("text");
        payload.maxC = today.getDouble("maxtemp_c");
        payload.minC = today.getDouble("mintemp_c");
        payload.maxF = today.getDouble("maxtemp_f");
        payload.minF = today.getDouble("mintemp_f");

        JSONObject astro = forecastDays.getJSONObject(0).optJSONObject("astro");
        if (astro != null) {
            payload.sunrise = astro.optString("sunrise", "—");
            payload.sunset = astro.optString("sunset", "—");
            payload.moonPhase = astro.optString("moon_phase", "—");
        }
        JSONObject air = current.optJSONObject("air_quality");
        if (air != null) {
            payload.airQualityIndex = air.optInt("us-epa-index", 0);
            payload.pm25 = air.optDouble("pm2_5", -1);
        }
        JSONObject alerts = root.optJSONObject("alerts");
        if (alerts != null) {
            JSONArray alertItems = alerts.optJSONArray("alert");
            if (alertItems != null && alertItems.length() > 0) {
                payload.alertHeadline = alertItems.optJSONObject(0) == null ? ""
                        : alertItems.optJSONObject(0).optString("headline", "");
            }
        }

        payload.hourly = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < forecastDays.length() && payload.hourly.size() < 12; dayIndex++) {
            JSONArray hours = forecastDays.getJSONObject(dayIndex).getJSONArray("hour");
            for (int hourIndex = 0; hourIndex < hours.length() && payload.hourly.size() < 12; hourIndex++) {
                JSONObject hour = hours.getJSONObject(hourIndex);
                long epoch = hour.getLong("time_epoch");
                if (epoch < payload.currentEpoch - 1800) continue;
                HourlyWeather item = new HourlyWeather();
                item.epoch = epoch;
                item.tempC = hour.getDouble("temp_c");
                item.tempF = hour.getDouble("temp_f");
                item.precipitation = Math.max(hour.optInt("chance_of_rain"), hour.optInt("chance_of_snow"));
                item.condition = hour.getJSONObject("condition").getString("text");
                item.isDay = hour.optInt("is_day", 1) == 1;
                payload.hourly.add(item);
            }
        }

        payload.daily = new ArrayList<>();
        for (int i = 0; i < forecastDays.length(); i++) {
            JSONObject forecastDay = forecastDays.getJSONObject(i);
            JSONObject day = forecastDay.getJSONObject("day");
            DailyWeather item = new DailyWeather();
            item.date = forecastDay.getString("date");
            item.condition = day.getJSONObject("condition").getString("text");
            item.minC = day.getDouble("mintemp_c");
            item.maxC = day.getDouble("maxtemp_c");
            item.minF = day.getDouble("mintemp_f");
            item.maxF = day.getDouble("maxtemp_f");
            item.precipitation = Math.max(day.optInt("daily_chance_of_rain"), day.optInt("daily_chance_of_snow"));
            payload.daily.add(item);
        }
        return payload;
    }

    private void displayWeather(WeatherPayload payload, boolean animate) {
        boolean fahrenheit = isFahrenheit();
        setLoading(false);
        hideStatus();
        emptyState.setVisibility(View.GONE);
        weatherContent.setVisibility(View.VISIBLE);
        weatherContent.setAlpha(1f);

        currentDisplayedCity = payload.country.isEmpty()
                ? payload.city : payload.city + ", " + payload.country;
        currentRadarCity = currentDisplayedCity;
        currentLatitude = payload.latitude;
        currentLongitude = payload.longitude;
        updateFavoriteToggle();

        headerTitle.setText(buildAtmosphereTitle(payload));
        headerSubtitle.setText(String.format(RU_LOCALE, "%s  •  %s  •  ощущается %s",
                payload.city, capitalize(payload.condition),
                formatTemp(fahrenheit ? payload.feelsF : payload.feelsC)));

        locationText.setText(payload.country.isEmpty() ? payload.city : payload.city + ", " + payload.country);
        localTimeText.setText(formatEpoch(payload.localEpoch, payload.timeZone, "HH:mm"));
        temperatureText.setText(formatTemp(fahrenheit ? payload.tempF : payload.tempC));
        weatherConditionText.setText(capitalize(payload.condition));
        highLowText.setText(String.format(RU_LOCALE, "Макс. %s  •  Мин. %s",
                formatTemp(fahrenheit ? payload.maxF : payload.maxC),
                formatTemp(fahrenheit ? payload.minF : payload.minC)));
        updatedText.setText("Обновлено в " + formatEpoch(payload.currentEpoch, payload.timeZone, "HH:mm"));

        setMetric(humidityMetric, payload.humidity + "%", "Влажность");
        setMetric(windMetric,
                fahrenheit
                        ? String.format(RU_LOCALE, "%.0f mph", payload.windMph)
                        : String.format(RU_LOCALE, "%.1f м/с", payload.windKph / 3.6),
                "Ветер");
        setMetric(feelsMetric,
                formatTemp(fahrenheit ? payload.feelsF : payload.feelsC),
                "Ощущается");

        detailWindValue.setText(translateWindDirection(payload.windDirection)
                + " · " + payload.windDegree + "°");
        detailWindIcon.setRotation(payload.windDegree);
        detailPressureValue.setText(String.format(RU_LOCALE, "%.0f гПа", payload.pressureMb));
        detailVisibilityValue.setText(String.format(RU_LOCALE, "%.1f км", payload.visibilityKm));
        detailGustValue.setText(fahrenheit
                ? String.format(RU_LOCALE, "%.0f mph", payload.gustMph)
                : String.format(RU_LOCALE, "%.1f м/с", payload.gustKph / 3.6));
        String zodiac = zodiacFor(payload.localEpoch, payload.timeZone);
        astronomyText.setText(String.format(RU_LOCALE,
                "Воздух: %s%s  •  UV %.1f  •  Облачность %d%%",
                airQualityLabel(payload.airQualityIndex),
                payload.pm25 >= 0 ? String.format(RU_LOCALE, " · PM2.5 %.0f", payload.pm25) : "",
                payload.uv, payload.cloud));
        celestialView.setData(payload.sunrise, payload.sunset, payload.localEpoch,
                payload.moonPhase, zodiac);
        adviceText.setText(buildWeatherAdvice(payload, fahrenheit));

        weatherIcon.setCondition(payload.condition, payload.isDay == 1);
        populateHourly(payload.hourly, payload.timeZone, fahrenheit, animate);
        populateChart(payload.hourly, payload.timeZone, fahrenheit);
        populateDaily(payload.daily, fahrenheit, animate);
        updateWeatherBackground(payload.condition, payload.isDay);
        applyAppearanceToTree(weatherContent);
        WeatherWidgetProvider.publish(this, payload.city, payload.condition,
                payload.tempC, payload.tempF, payload.maxC, payload.maxF,
                payload.minC, payload.minF, payload.isDay == 1, fahrenheit,
                lastFetchUsedLocation, payload.humidity, payload.windKph, payload.windMph,
                payload.feelsC, payload.feelsF);

        if (animate) animateWeatherEntrance();
    }

    private void populateHourly(List<HourlyWeather> hours, String timeZone, boolean fahrenheit, boolean animate) {
        hourlyContainer.removeAllViews();
        for (int i = 0; i < hours.size(); i++) {
            HourlyWeather hour = hours.get(i);
            LiquidGlassLayout card = new LiquidGlassLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(10), dp(13), dp(10), dp(13));
            card.setRefractionEnabled(true);
            card.setLightweight(true);
            card.setRefractionScale(glassScale);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(82), dp(154));
            cardParams.setMarginEnd(dp(10));
            card.setLayoutParams(cardParams);

            TextView time = createText(13, Color.argb(205, 255, 255, 255));
            time.setText(i == 0 ? "Сейчас" : formatEpoch(hour.epoch, timeZone, "HH:mm"));
            card.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)));

            AnimatedWeatherIconView icon = new AnimatedWeatherIconView(this);
            icon.setCondition(hour.condition, hour.isDay);
            icon.setAnimationsEnabled(animationsEnabled);
            card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView temp = createText(18, Color.WHITE);
            temp.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            temp.setText(formatTemp(fahrenheit ? hour.tempF : hour.tempC));
            card.addView(temp, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(27)));

            TextView precipitation = createText(12, Color.argb(190, 220, 247, 255));
            precipitation.setText(hour.precipitation > 0 ? hour.precipitation + "%" : "—");
            card.addView(precipitation);
            hourlyContainer.addView(card);

            if (animate && i < 5) animateChild(card, i * 35L);
        }
    }

    private void populateChart(List<HourlyWeather> hours, String timeZone, boolean fahrenheit) {
        int count = Math.min(12, hours.size());
        float[] temperatures = new float[count];
        int[] precipitation = new int[count];
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            HourlyWeather hour = hours.get(i);
            temperatures[i] = (float) (fahrenheit ? hour.tempF : hour.tempC);
            precipitation[i] = hour.precipitation;
            labels[i] = i == 0 ? "Сейчас" : formatEpoch(hour.epoch, timeZone, "HH:mm");
        }
        weatherChart.setData(temperatures, precipitation, labels, fahrenheit ? "°F" : "°C");
        if (count > 0) {
            float min = temperatures[0];
            float max = temperatures[0];
            int maxRain = 0;
            for (int i = 0; i < count; i++) {
                min = Math.min(min, temperatures[i]);
                max = Math.max(max, temperatures[i]);
                maxRain = Math.max(maxRain, precipitation[i]);
            }
            float displayMin = Math.abs(min) < 0.5f ? 0f : min;
            float displayMax = Math.abs(max) < 0.5f ? 0f : max;
            chartSummary.setText(String.format(RU_LOCALE,
                    "От %.0f° до %.0f°  •  осадки максимум %d%%",
                    displayMin, displayMax, maxRain));
        }
    }

    private void populateDaily(List<DailyWeather> days, boolean fahrenheit, boolean animate) {
        dailyContainer.removeAllViews();
        for (int i = 0; i < days.size(); i++) {
            DailyWeather day = days.get(i);
            LiquidGlassLayout row = new LiquidGlassLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(17), dp(12), dp(16), dp(12));
            row.setRefractionEnabled(true);
            row.setLightweight(true);
            row.setRefractionScale(glassScale);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
            rowParams.setMargins(0, 0, 0, dp(10));
            row.setLayoutParams(rowParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView dayName = createText(16, Color.WHITE);
            dayName.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            dayName.setText(getDayLabel(day.date, i));
            labels.addView(dayName);
            TextView condition = createText(12, Color.argb(195, 255, 255, 255));
            condition.setMaxLines(2);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(condition, 10, 12, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            condition.setText(capitalize(day.condition));
            labels.addView(condition);
            row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView precipitation = createText(12, Color.argb(210, 215, 246, 255));
            precipitation.setText(day.precipitation > 0 ? day.precipitation + "%" : "");
            LinearLayout.LayoutParams precipParams = new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT);
            row.addView(precipitation, precipParams);

            AnimatedWeatherIconView icon = new AnimatedWeatherIconView(this);
            icon.setCondition(day.condition, true);
            icon.setAnimationsEnabled(animationsEnabled);
            row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView temperatures = createText(15, Color.WHITE);
            temperatures.setGravity(Gravity.END);
            temperatures.setMaxLines(1);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(temperatures, 11, 15, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            temperatures.setText(String.format(RU_LOCALE, "%s  %s",
                    formatTemp(fahrenheit ? day.maxF : day.maxC),
                    formatTemp(fahrenheit ? day.minF : day.minC)));
            row.addView(temperatures, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
            dailyContainer.addView(row);

            if (animate && i < 4) animateChild(row, 180L + i * 55L);
        }
    }

    private void animateWeatherEntrance() {
        if (!animationsEnabled) {
            weatherContent.setAlpha(1f);
            weatherContent.setTranslationY(0f);
            return;
        }
        weatherContent.setAlpha(0f);
        weatherContent.setTranslationY(dp(22));
        weatherContent.animate().alpha(1f).translationY(0f)
                .setInterpolator(smoothOut).setDuration(720).start();
    }

    private void animateChild(View view, long delay) {
        if (!animationsEnabled) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(13));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay)
                .setInterpolator(smoothOut).setDuration(620).start();
    }

    private void startAmbientAnimations() {
        backgroundImage.animate().cancel();
        if (orbAnimator != null) orbAnimator.cancel();
        backgroundImage.setScaleX(1.08f);
        backgroundImage.setScaleY(1.08f);
        backgroundImage.animate().translationX(dp(8)).translationY(dp(-6)).setDuration(26000)
                .setInterpolator(smoothInOut)
                .withEndAction(this::reverseBackgroundMotion).start();

        orbAnimator = ObjectAnimator.ofFloat(ambientOrb, View.TRANSLATION_Y, 0f, dp(34), 0f);
        orbAnimator.setDuration(14000);
        orbAnimator.setRepeatCount(ValueAnimator.INFINITE);
        orbAnimator.setInterpolator(smoothInOut);
        orbAnimator.start();
    }

    private void reverseBackgroundMotion() {
        if (isFinishing() || !animationsEnabled) return;
        float targetX = backgroundImage.getTranslationX() > 0 ? dp(-8) : dp(8);
        float targetY = backgroundImage.getTranslationY() < 0 ? dp(6) : dp(-6);
        backgroundImage.animate().translationX(targetX).translationY(targetY).setDuration(26000)
                .setInterpolator(smoothInOut)
                .withEndAction(this::reverseBackgroundMotion).start();
    }

    private void updateWeatherBackground(String condition, int isDay) {
        int resource = getWeatherBackground(condition, isDay);
        seasonOverlay.setImageResource(getSeasonOverlay());
        backgroundImage.animate().alpha(0.18f).setInterpolator(smoothInOut).setDuration(380).withEndAction(() -> {
            backgroundImage.setImageResource(resource);
            backgroundImage.animate().alpha(1f).setInterpolator(smoothOut).setDuration(1050).start();
        }).start();
    }

    private int getSeasonOverlay() {
        int month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;
        if (month == 12 || month <= 2) return R.drawable.season_winter_overlay;
        if (month <= 5) return R.drawable.season_spring_overlay;
        if (month <= 8) return R.drawable.season_summer_overlay;
        return R.drawable.season_autumn_overlay;
    }

    private int getWeatherBackground(String condition, int isDay) {
        String value = condition.toLowerCase(RU_LOCALE);
        boolean night = isDay == 0;
        if (containsAny(value, "дожд", "морос", "ливень", "гроза")) {
            return night ? R.drawable.rainy_night : R.drawable.rainy_day;
        }
        if (containsAny(value, "снег", "метель", "лед", "круп")) {
            return night ? R.drawable.snow_night : R.drawable.snow_day;
        }
        if (containsAny(value, "туман", "дымка", "мгла")) {
            return night ? R.drawable.fogy_night : R.drawable.fogy_day;
        }
        if (containsAny(value, "облач", "пасмур")) {
            return night ? R.drawable.cloudy_night : R.drawable.cloudy_day;
        }
        if (containsAny(value, "ясно", "солнечно")) {
            return night ? R.drawable.sunny_night : R.drawable.sunny_day;
        }
        return R.drawable.atmospheric_glass_sky;
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private void setLoading(boolean loading) {
        loadingProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!loading);
        searchButton.setAlpha(loading ? 0.55f : 1f);
        if (loading) {
            emptyState.setVisibility(View.GONE);
            if (weatherContent.getVisibility() == View.VISIBLE) weatherContent.setAlpha(0.42f);
        } else if (weatherContent.getVisibility() == View.VISIBLE) {
            weatherContent.setAlpha(1f);
        }
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
        statusText.setAlpha(0f);
        statusText.animate().alpha(1f).setInterpolator(smoothOut).setDuration(420).start();
        if (weatherContent.getVisibility() != View.VISIBLE) emptyState.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        statusText.setVisibility(View.GONE);
    }

    private void setMetric(View metric, String value, String label) {
        ((TextView) metric.findViewById(R.id.metricValue)).setText(value);
        ((TextView) metric.findViewById(R.id.metricLabel)).setText(label);
    }

    private boolean isFahrenheit() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_FAHRENHEIT, false);
    }

    private String airQualityLabel(int index) {
        switch (index) {
            case 1: return "отличный";
            case 2: return "хороший";
            case 3: return "умеренный";
            case 4: return "плохой";
            case 5: return "очень плохой";
            case 6: return "опасный";
            default: return "нет данных";
        }
    }

    private String translateMoonPhase(String phase) {
        if (phase == null) return "—";
        String value = phase.toLowerCase(Locale.US);
        if (value.contains("new")) return "новолуние";
        if (value.contains("full")) return "полнолуние";
        if (value.contains("waxing")) return "растущая";
        if (value.contains("waning")) return "убывающая";
        if (value.contains("quarter")) return "четверть";
        return phase;
    }

    private String formatApiClock(String value) {
        if (value == null || value.equals("—")) return "—";
        try {
            Date parsed = new SimpleDateFormat("hh:mm a", Locale.US).parse(value);
            return parsed == null ? value : new SimpleDateFormat("HH:mm", RU_LOCALE).format(parsed);
        } catch (ParseException ignored) {
            return value;
        }
    }

    private String buildWeatherAdvice(WeatherPayload payload, boolean fahrenheit) {
        if (payload.alertHeadline != null && !payload.alertHeadline.isEmpty()) {
            return "⚠ Действует погодное предупреждение. Будьте осторожны и следите за обновлениями.";
        }
        String condition = payload.condition.toLowerCase(RU_LOCALE);
        if (containsAny(condition, "гроз", "ливень")) {
            return "Возьмите зонт и по возможности переждите сильные осадки в помещении.";
        }
        if (containsAny(condition, "дожд", "морос")) {
            return "Непромокаемая куртка и зонт сегодня точно пригодятся.";
        }
        if (containsAny(condition, "снег", "метель", "лед")) {
            return "На улице скользко и холодно — выбирайте тёплую обувь с хорошим протектором.";
        }
        if (payload.uv >= 6) return "Высокий UV: используйте SPF и избегайте долгого солнца в полдень.";
        if (payload.airQualityIndex >= 4) return "Качество воздуха снижено — сократите интенсивные нагрузки на улице.";
        double tempC = fahrenheit ? (payload.tempF - 32) * 5 / 9 : payload.tempC;
        if (tempC < 0) return "Морозно: перчатки, шарф и несколько слоёв одежды сделают прогулку комфортнее.";
        if (tempC > 27) return "Жарко: возьмите воду и выбирайте тень в самые солнечные часы.";
        return "Погода спокойная — хороший момент для прогулки и свежего воздуха.";
    }

    private TextView createText(float size, int color) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private String formatTemp(double value) {
        return Math.round(value) + "°";
    }

    private String formatEpoch(long epoch, String timeZone, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, RU_LOCALE);
        format.setTimeZone(TimeZone.getTimeZone(timeZone));
        return format.format(new Date(epoch * 1000L));
    }

    private String getDayLabel(String date, int position) {
        if (position == 0) return "Сегодня";
        if (position == 1) return "Завтра";
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            if (parsed == null) return date;
            String label = new SimpleDateFormat("EEEE, d MMM", RU_LOCALE).format(parsed);
            return capitalize(label);
        } catch (ParseException e) {
            return date;
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase(RU_LOCALE) + value.substring(1);
    }

    private String buildAtmosphereTitle(WeatherPayload payload) {
        String condition = payload.condition == null ? "" : payload.condition.toLowerCase(RU_LOCALE);
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone(payload.timeZone));
        local.setTimeInMillis(payload.localEpoch * 1000L);
        int hour = local.get(Calendar.HOUR_OF_DAY);
        String moment = hour < 6 ? "Ночное небо" : hour < 12 ? "Утро в небе"
                : hour < 18 ? "Небо сегодня" : "Вечер в небе";
        if (condition.contains("снег")) return hour >= 18 || hour < 6 ? "Снежный вечер" : "Снег рисует небо";
        if (condition.contains("дожд") || condition.contains("морос")) return "Дождь рядом";
        if (condition.contains("гроз")) return "Гроза на горизонте";
        if (condition.contains("ясн") || condition.contains("солнеч")) return hour >= 18 ? "Ясный вечер" : "Светлое небо";
        if (condition.contains("туман")) return "Город в тумане";
        if (condition.contains("облач") || condition.contains("пасмур")) return "Небо в облаках";
        return moment;
    }

    private String translateWindDirection(String direction) {
        if (direction == null) return "—";
        switch (direction.toUpperCase(Locale.US)) {
            case "N": return "С";
            case "NNE": return "ССВ";
            case "NE": return "СВ";
            case "ENE": return "ВСВ";
            case "E": return "В";
            case "ESE": return "ВЮВ";
            case "SE": return "ЮВ";
            case "SSE": return "ЮЮВ";
            case "S": return "Ю";
            case "SSW": return "ЮЮЗ";
            case "SW": return "ЮЗ";
            case "WSW": return "ЗЮЗ";
            case "W": return "З";
            case "WNW": return "ЗСЗ";
            case "NW": return "СЗ";
            case "NNW": return "ССЗ";
            default: return direction;
        }
    }

    private String zodiacFor(long localEpoch, String timeZone) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(localEpoch * 1000L);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int key = month * 100 + day;
        if (key >= 321 && key <= 419) return "Овен";
        if (key >= 420 && key <= 520) return "Телец";
        if (key >= 521 && key <= 620) return "Близнецы";
        if (key >= 621 && key <= 722) return "Рак";
        if (key >= 723 && key <= 822) return "Лев";
        if (key >= 823 && key <= 922) return "Дева";
        if (key >= 923 && key <= 1022) return "Весы";
        if (key >= 1023 && key <= 1121) return "Скорпион";
        if (key >= 1122 && key <= 1221) return "Стрелец";
        if (key >= 1222 || key <= 119) return "Козерог";
        if (key <= 218) return "Водолей";
        return "Рыбы";
    }

    private List<String> getFavoritesFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String favorites = prefs.getString(KEY_FAVORITES, "");
        if (favorites == null || favorites.isEmpty()) return new ArrayList<>();
        if (favorites.contains(HISTORY_SEPARATOR)) {
            return new ArrayList<>(Arrays.asList(favorites.split(HISTORY_SEPARATOR)));
        }
        return new ArrayList<>(Collections.singletonList(favorites));
    }

    private boolean isFavorite(String city) {
        for (String item : getFavoritesFromPrefs()) {
            if (item.equalsIgnoreCase(city)) return true;
        }
        return false;
    }

    private void toggleCurrentFavorite() {
        if (currentDisplayedCity.isEmpty()) return;
        List<String> cities = getFavoritesFromPrefs();
        boolean removed = cities.removeIf(value -> value.equalsIgnoreCase(currentDisplayedCity));
        if (!removed) {
            if (cities.size() >= 8) {
                Toast.makeText(this, "Можно закрепить до 8 городов", Toast.LENGTH_SHORT).show();
                return;
            }
            cities.add(currentDisplayedCity);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_FAVORITES, TextUtils.join(HISTORY_SEPARATOR, cities)).apply();
        updateFavoriteToggle();
        updateHistoryUI();
        Toast.makeText(this, removed ? "Город откреплён" : "Город закреплён", Toast.LENGTH_SHORT).show();
    }

    private void updateFavoriteToggle() {
        boolean pinned = isFavorite(currentDisplayedCity);
        favoriteToggleButton.setText(pinned ? "★" : "☆");
        favoriteToggleButton.setContentDescription(pinned ? "Открепить город" : "Закрепить город");
    }

    private void updateHistoryUI() {
        historyContainer.removeAllViews();
        List<String> favorites = getFavoritesFromPrefs();
        if (favorites.isEmpty()) {
            TextView hint = createText(12, Color.argb(165, 255, 255, 255));
            hint.setText("Нажмите ☆ на карточке города, чтобы закрепить его");
            hint.setPadding(dp(3), dp(7), dp(8), dp(7));
            historyContainer.addView(hint);
        }
        for (String city : favorites) {
            String favorite = city.trim();
            LiquidGlassLayout card = new LiquidGlassLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(6), dp(12), dp(6));
            card.setRefractionEnabled(true);
            card.setLightweight(true);

            TextView star = createText(15, Color.rgb(255, 205, 67));
            star.setText("●");
            card.addView(star);
            TextView name = createText(13, Color.argb(235, 255, 255, 255));
            name.setText(favorite);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            // The horizontal list already scrolls, so keep a useful city width
            // instead of cutting names such as "Жлобин, Беларусь" in half.
            name.setMaxWidth(dp(230));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.setMarginStart(dp(6));
            card.addView(name, nameParams);
            card.setOnClickListener(v -> {
                cityAutoComplete.setText(favorite);
                cityAutoComplete.setSelection(cityAutoComplete.length());
                fetchWeather(favorite);
            });
            card.setOnLongClickListener(v -> {
                removeFavorite(favorite);
                Toast.makeText(this, favorite + " удалён из избранного", Toast.LENGTH_SHORT).show();
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            historyContainer.addView(card, params);
        }
        favoritesPanel.setVisibility(View.VISIBLE);
    }

    private void removeFavorite(String city) {
        List<String> cities = getFavoritesFromPrefs();
        cities.removeIf(value -> value.equalsIgnoreCase(city));
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_FAVORITES, TextUtils.join(HISTORY_SEPARATOR, cities)).apply();
        if (city.equalsIgnoreCase(WeatherWidgetProvider.getWidgetCity(this))) {
            WeatherWidgetProvider.setWidgetCity(this, "");
        }
        updateFavoriteToggle();
        updateHistoryUI();
    }

    private void showCustomizationMenu() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.settings_sheet_background);

        TextView title = createText(23, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setText("Настройте под себя");
        panel.addView(title);

        TextView subtitle = createText(13, Color.argb(185, 225, 237, 255));
        subtitle.setText("Оформление, анимации, единицы и закреплённые города");
        subtitle.setPadding(0, dp(4), 0, dp(14));
        panel.addView(subtitle);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SwitchCompat animationToggle = createSettingsSwitch("Плавные анимации", animationsEnabled);
        animationToggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(KEY_SMOOTH_ANIMATIONS, checked).apply();
            setAnimationsEnabled(checked);
        });
        panel.addView(animationToggle);

        SwitchCompat glassToggle = createSettingsSwitch("Сильное преломление стекла",
                prefs.getBoolean(KEY_STRONG_GLASS, true));
        glassToggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(KEY_STRONG_GLASS, checked).apply();
            glassScale = checked ? 1.18f : 0.72f;
            applyAppearanceToTree(findViewById(R.id.mainLayout));
        });
        panel.addView(glassToggle);

        TextView glassThemeButton = createText(14, Color.WHITE);
        glassThemeButton.setText("Тема стекла: " + glassThemeName(glassTheme));
        glassThemeButton.setGravity(Gravity.CENTER_VERTICAL);
        glassThemeButton.setPadding(dp(14), dp(12), dp(14), dp(12));
        glassThemeButton.setBackgroundResource(R.drawable.glass_chip);
        LinearLayout.LayoutParams glassThemeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glassThemeParams.setMargins(0, dp(6), 0, dp(6));
        glassThemeButton.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Тема жидкого стекла")
                .setSingleChoiceItems(new String[]{"Кристалл", "Аврора", "Закат"}, glassTheme,
                        (themeDialog, which) -> {
                            glassTheme = which;
                            prefs.edit().putInt(KEY_GLASS_THEME, which).apply();
                            glassThemeButton.setText("Тема стекла: " + glassThemeName(which));
                            applyAppearanceToTree(findViewById(R.id.mainLayout));
                            themeDialog.dismiss();
                        })
                .show());
        panel.addView(glassThemeButton, glassThemeParams);

        SwitchCompat unitToggle = createSettingsSwitch("Температура в °F",
                prefs.getBoolean(KEY_FAHRENHEIT, false));
        unitToggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(KEY_FAHRENHEIT, checked).apply();
            WeatherWidgetProvider.setFahrenheit(this, checked);
            if (!lastFetchedJson.isEmpty()) parseAndDisplayWeather(lastFetchedJson, false);
        });
        panel.addView(unitToggle);

        TextView widgetCity = createText(14, Color.WHITE);
        String selectedWidgetCity = WeatherWidgetProvider.getWidgetCity(this);
        widgetCity.setText("Город виджета: " + (selectedWidgetCity.isEmpty()
                ? "текущее местоположение" : selectedWidgetCity));
        widgetCity.setGravity(Gravity.CENTER_VERTICAL);
        widgetCity.setPadding(dp(14), dp(12), dp(14), dp(12));
        widgetCity.setBackgroundResource(R.drawable.glass_chip);
        LinearLayout.LayoutParams widgetCityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        widgetCityParams.setMargins(0, dp(8), 0, 0);
        widgetCity.setOnClickListener(v -> {
            dialog.dismiss();
            showWidgetCityPicker();
        });
        panel.addView(widgetCity, widgetCityParams);

        TextView widgetHint = createText(13, Color.argb(205, 225, 237, 255));
        widgetHint.setText("Виджет адаптируется от 1×2 и 2×1 до большого: при растягивании добавляются состояние, диапазон, ветер, влажность и ощущаемая температура.");
        widgetHint.setPadding(0, dp(14), 0, dp(12));
        panel.addView(widgetHint);

        TextView clear = createText(14, Color.rgb(255, 164, 174));
        clear.setText("Очистить закреплённые города");
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(12), dp(13), dp(12), dp(13));
        clear.setBackgroundResource(R.drawable.glass_chip);
        clear.setOnClickListener(v -> {
            prefs.edit().remove(KEY_FAVORITES).remove(KEY_HISTORY).apply();
            WeatherWidgetProvider.setWidgetCity(this, "");
            updateHistoryUI();
            Toast.makeText(this, "Избранные очищены", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        panel.addView(clear);

        dialog.setContentView(panel);
        dialog.show();
    }

    private void showWidgetCityPicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.settings_sheet_background);

        TextView title = createText(21, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setText("Погода на виджете");
        panel.addView(title);

        TextView subtitle = createText(13, Color.argb(190, 225, 237, 255));
        subtitle.setText("Выберите геолокацию или один из закреплённых городов");
        subtitle.setPadding(0, dp(5), 0, dp(12));
        panel.addView(subtitle);

        addWidgetCityOption(panel, dialog, "Текущее местоположение", "");
        for (String city : getFavoritesFromPrefs()) {
            addWidgetCityOption(panel, dialog, city, city);
        }
        if (getFavoritesFromPrefs().isEmpty()) {
            TextView hint = createText(13, Color.argb(165, 255, 255, 255));
            hint.setText("Сначала найдите город и нажмите ☆ на его карточке.");
            hint.setPadding(dp(4), dp(12), dp(4), 0);
            panel.addView(hint);
        }
        dialog.setContentView(panel);
        dialog.show();
    }

    private void addWidgetCityOption(LinearLayout panel, BottomSheetDialog dialog,
                                     String label, String value) {
        TextView option = createText(15, Color.WHITE);
        boolean selected = WeatherWidgetProvider.getWidgetCity(this).equalsIgnoreCase(value);
        option.setText((selected ? "●  " : "○  ") + label);
        option.setPadding(dp(14), dp(12), dp(14), dp(12));
        option.setBackgroundResource(R.drawable.glass_chip);
        option.setOnClickListener(v -> {
            WeatherWidgetProvider.setWidgetCity(this, value);
            Toast.makeText(this, value.isEmpty()
                    ? "Виджет использует геолокацию" : "На виджете: " + value,
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        panel.addView(option, params);
    }

    private SwitchCompat createSettingsSwitch(String label, boolean checked) {
        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setText(label);
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(15);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setChecked(checked);
        toggle.setPadding(0, dp(4), 0, dp(4));
        toggle.setMinHeight(dp(48));
        return toggle;
    }

    private void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        if (enabled && !scrollPerformanceMode) {
            startAmbientAnimations();
        } else {
            backgroundImage.animate().cancel();
            if (orbAnimator != null) orbAnimator.cancel();
            backgroundImage.setTranslationX(0f);
            backgroundImage.setTranslationY(0f);
            ambientOrb.setTranslationY(0f);
        }
        applyAppearanceToTree(findViewById(R.id.mainLayout));
    }

    private void applyAppearanceToTree(View view) {
        if (view instanceof LiquidGlassLayout) {
            LiquidGlassLayout glass = (LiquidGlassLayout) view;
            glass.setRefractionScale(glassScale);
            glass.setGlassTheme(glassTheme);
            glass.setAnimatedShimmer(animationsEnabled);
            glass.setInteractionPaused(scrollPerformanceMode);
        }
        if (view instanceof AnimatedWeatherIconView) {
            ((AnimatedWeatherIconView) view)
                    .setAnimationsEnabled(animationsEnabled && !scrollPerformanceMode);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyAppearanceToTree(group.getChildAt(i));
            }
        }
    }

    private String glassThemeName(int theme) {
        if (theme == 1) return "Аврора";
        if (theme == 2) return "Закат";
        return "Кристалл";
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        super.onDestroy();
    }

    private static class WeatherPayload {
        String city;
        String country;
        String timeZone;
        String condition;
        double latitude = Double.NaN;
        double longitude = Double.NaN;
        long localEpoch;
        long currentEpoch;
        double tempC;
        double tempF;
        double feelsC;
        double feelsF;
        double windKph;
        double windMph;
        int windDegree;
        String windDirection = "—";
        double gustKph;
        double gustMph;
        double pressureMb;
        double visibilityKm;
        double uv;
        int cloud;
        double maxC;
        double minC;
        double maxF;
        double minF;
        int humidity;
        int isDay;
        String sunrise = "—";
        String sunset = "—";
        String moonPhase = "—";
        int airQualityIndex;
        double pm25 = -1;
        String alertHeadline = "";
        List<HourlyWeather> hourly = Collections.emptyList();
        List<DailyWeather> daily = Collections.emptyList();
    }

    private static class HourlyWeather {
        long epoch;
        double tempC;
        double tempF;
        int precipitation;
        String condition;
        boolean isDay;
    }

    private static class DailyWeather {
        String date;
        String condition;
        double minC;
        double maxC;
        double minF;
        double maxF;
        int precipitation;
    }
}

# MyWeatherApp

Android weather application with a liquid-glass interface, animated weather artwork, forecasts, precipitation radar, astronomy data, charts, favorites, and adaptive home-screen widgets.

## Setup

1. Open the project in Android Studio.
2. Copy `local.properties.example` to `local.properties`.
3. Set `sdk.dir` to your Android SDK directory.
4. Add a free [WeatherAPI](https://www.weatherapi.com/) key as `WEATHER_API_KEY`.
5. Build and run the `app` configuration.

`local.properties`, build outputs, IDE caches, and signing files are excluded from Git.

## Main features

- current conditions and multi-day forecast;
- animated custom weather icons and seasonal scenes;
- temperature and precipitation charts;
- wind direction, pressure, visibility, gusts, air quality, and UV data;
- graphical sunrise, sunset, Moon phase, and zodiac information;
- RainViewer precipitation radar;
- favorites and customizable weather location;
- adaptive widgets for 1×2, 2×1, 2×2, and 2×3 layouts;
- offline cache of the latest forecast.

## Build

```powershell
.\gradlew.bat assembleDebug
```

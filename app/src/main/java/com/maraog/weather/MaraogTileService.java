package com.maraog.weather;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;
import androidx.wear.tiles.TimelineBuilders;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaraogTileService extends TileService {

    private static final String RESOURCES_VERSION = "1";
    private static final long FRESHNESS_MS = 15L * 60L * 1000L;
    private static final double LATITUDE = 30.88166;
    private static final double LONGITUDE = 77.55215;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(
            @NonNull RequestBuilders.TileRequest requestParams) {

        return Futures.submit(new Callable<TileBuilders.Tile>() {
            @Override
            public TileBuilders.Tile call() {
                Forecast forecast = loadForecast();

                return new TileBuilders.Tile.Builder()
                        .setResourcesVersion(RESOURCES_VERSION)
                        .setFreshnessIntervalMillis(FRESHNESS_MS)
                        .setTileTimeline(
                                new TimelineBuilders.Timeline.Builder()
                                        .addTimelineEntry(
                                                new TimelineBuilders.TimelineEntry.Builder()
                                                        .setLayout(
                                                                new LayoutElementBuilders.Layout.Builder()
                                                                        .setRoot(buildLayout(forecast))
                                                                        .build())
                                                        .build())
                                        .build())
                        .build();
            }
        }, executor);
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            @NonNull RequestBuilders.ResourcesRequest requestParams) {

        return Futures.immediateFuture(
                new ResourceBuilders.Resources.Builder()
                        .setVersion(RESOURCES_VERSION)
                        .build());
    }

    private Forecast loadForecast() {
        HttpURLConnection connection = null;
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + LATITUDE
                    + "&longitude=" + LONGITUDE
                    + "&current=temperature_2m,weather_code"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&forecast_days=7"
                    + "&timezone=auto";

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);

            if (connection.getResponseCode() != 200) {
                return Forecast.error();
            }

            StringBuilder body = new StringBuilder();
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONObject root = new JSONObject(body.toString());
            JSONObject current = root.getJSONObject("current");
            JSONObject daily = root.getJSONObject("daily");

            double currentTemp = current.optDouble("temperature_2m", Double.NaN);
            int currentCode = current.optInt("weather_code", -1);

            JSONArray dates = daily.getJSONArray("time");
            JSONArray max = daily.getJSONArray("temperature_2m_max");
            JSONArray min = daily.getJSONArray("temperature_2m_min");
            JSONArray codes = daily.getJSONArray("weather_code");

            int count = Math.min(7, dates.length());
            Day[] days = new Day[count];

            for (int i = 0; i < count; i++) {
                days[i] = new Day(
                        dates.getString(i),
                        max.optDouble(i, Double.NaN),
                        min.optDouble(i, Double.NaN),
                        codes.optInt(i, -1)
                );
            }

            return new Forecast(currentTemp, currentCode, days, false);

        } catch (Exception e) {
            return Forecast.error();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private LayoutElementBuilders.LayoutElement buildLayout(Forecast forecast) {
        LayoutElementBuilders.Column.Builder column = new LayoutElementBuilders.Column.Builder()
                .setWidth(new DimensionBuilders.ExpandDimensionProp.Builder().build())
                .setHeight(new DimensionBuilders.ExpandDimensionProp.Builder().build())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER);

        if (forecast.error) {
            column.addContent(text("MARAOG", 12));
            column.addContent(text("Offline", 16));
        } else {
            String temp = Double.isNaN(forecast.currentTemp)
                    ? "--°"
                    : String.format(Locale.US, "%.0f°", forecast.currentTemp);

            column.addContent(text("MARAOG", 11));
            column.addContent(text(icon(forecast.currentCode) + " " + temp, 20));

            for (int i = 0; i < forecast.days.length; i++) {
                Day d = forecast.days[i];
                String dayLabel = (i == 0) ? "Today" : d.date.substring(5);
                String hi = Double.isNaN(d.max) ? "--" : String.format(Locale.US, "%.0f", d.max);
                String lo = Double.isNaN(d.min) ? "--" : String.format(Locale.US, "%.0f", d.min);

                column.addContent(text(dayLabel + " " + icon(d.code) + " " + hi + "°/" + lo + "°", 9));
            }
        }

        return new LayoutElementBuilders.Box.Builder()
                .setWidth(new DimensionBuilders.ExpandDimensionProp.Builder().build())
                .setHeight(new DimensionBuilders.ExpandDimensionProp.Builder().build())
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setBackground(new ModifiersBuilders.Background.Builder()
                                .setColor(new ColorBuilders.ColorProp.Builder(0xFF10141C).build())
                                .build())
                        .build())
                .addContent(column.build())
                .build();
    }

    private LayoutElementBuilders.Text text(String value, int sizeSp) {
        return new LayoutElementBuilders.Text.Builder()
                .setText(value)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(new DimensionBuilders.SpProp.Builder(sizeSp).build())
                        .setColor(new ColorBuilders.ColorProp.Builder(0xFFFFFFFF).build())
                        .build())
                .build();
    }

    private String icon(int code) {
        if (code == 0) return "☀";
        if (code <= 2) return "⛅";
        if (code == 3 || code == 45 || code == 48) return "☁";
        if (code >= 51 && code <= 67) return "🌧";
        if (code >= 71 && code <= 77) return "❄";
        if (code >= 80 && code <= 82) return "🌦";
        if (code >= 95) return "⚡";
        return "☁";
    }

    private static class Day {
        final String date;
        final double max;
        final double min;
        final int code;

        Day(String date, double max, double min, int code) {
            this.date = date;
            this.max = max;
            this.min = min;
            this.code = code;
        }
    }

    private static class Forecast {
        final double currentTemp;
        final int currentCode;
        final Day[] days;
        final boolean error;

        Forecast(double currentTemp, int currentCode, Day[] days, boolean error) {
            this.currentTemp = currentTemp;
            this.currentCode = currentCode;
            this.days = days;
            this.error = error;
        }

        static Forecast error() {
            return new Forecast(Double.NaN, -1, new Day[0], true);
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
            }
            

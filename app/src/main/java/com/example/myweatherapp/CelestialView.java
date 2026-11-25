package com.example.myweatherapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Draws a compact sun path, the actual-looking moon phase and a constellation. */
public class CelestialView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String sunrise = "—";
    private String sunset = "—";
    private String moonPhase = "";
    private String zodiac = "";
    private long localEpoch;

    public CelestialView(Context context) { super(context); }
    public CelestialView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
    public CelestialView(Context context, @Nullable AttributeSet attrs, int style) { super(context, attrs, style); }

    public void setData(String sunrise, String sunset, long localEpoch, String moonPhase, String zodiac) {
        this.sunrise = sunrise == null ? "—" : sunrise;
        this.sunset = sunset == null ? "—" : sunset;
        this.localEpoch = localEpoch;
        this.moonPhase = moonPhase == null ? "" : moonPhase;
        this.zodiac = zodiac == null ? "" : zodiac;
        setContentDescription("Восход " + this.sunrise + ", закат " + this.sunset
                + ", фаза Луны " + this.moonPhase + ", знак " + this.zodiac);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float sunLeft = dp(24);
        float sunRight = w * 0.62f;
        float horizon = dp(122);
        float peak = dp(39);

        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        paint.setTextSize(dp(10));
        paint.setColor(Color.argb(170, 225, 241, 255));
        canvas.drawText("СОЛНЕЧНЫЙ ПУТЬ", sunLeft, dp(15), paint);

        path.reset();
        path.moveTo(sunLeft, horizon);
        path.quadTo((sunLeft + sunRight) / 2f, peak, sunRight, horizon);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(95, 255, 235, 178));
        canvas.drawPath(path, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(50, 255, 255, 255));
        canvas.drawLine(sunLeft, horizon, sunRight, horizon, paint);

        float progress = daylightProgress();
        float x = sunLeft + (sunRight - sunLeft) * progress;
        float normalized = progress * 2f - 1f;
        float y = horizon - (1f - normalized * normalized) * (horizon - peak);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(38, 255, 210, 91));
        canvas.drawCircle(x, y, dp(15), paint);
        paint.setColor(Color.rgb(255, 213, 91));
        canvas.drawCircle(x, y, dp(7), paint);

        paint.setTextSize(dp(10));
        paint.setColor(Color.argb(205, 255, 255, 255));
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(shortClock(sunrise), sunLeft, horizon + dp(18), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(shortClock(sunset), sunRight, horizon + dp(18), paint);

        float moonX = w - dp(52);
        float moonY = dp(65);
        drawMoon(canvas, moonX, moonY, dp(24));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(10));
        paint.setColor(Color.argb(205, 255, 255, 255));
        canvas.drawText(phaseLabel(), moonX, moonY + dp(39), paint);

        drawConstellation(canvas, sunLeft + dp(52), dp(184));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(11));
        paint.setColor(Color.WHITE);
        canvas.drawText(zodiac, sunLeft + dp(102), dp(181), paint);
        paint.setTextSize(dp(9));
        paint.setColor(Color.argb(150, 220, 239, 255));
        canvas.drawText("знак зодиака сейчас", sunLeft + dp(102), dp(198), paint);
    }

    private void drawMoon(Canvas canvas, float x, float y, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(35, 180, 210, 255));
        canvas.drawCircle(x, y, radius + dp(7), paint);
        paint.setColor(Color.rgb(226, 237, 255));
        canvas.drawCircle(x, y, radius, paint);

        String value = moonPhase.toLowerCase(Locale.US);
        if (value.contains("full")) return;
        if (value.contains("new")) {
            paint.setColor(Color.rgb(32, 48, 73));
            canvas.drawCircle(x, y, radius, paint);
            return;
        }
        boolean waxing = value.contains("wax") || value.contains("first");
        float shift = (value.contains("crescent") ? radius * 0.55f : radius * 1.25f);
        paint.setColor(Color.rgb(32, 48, 73));
        canvas.save();
        canvas.clipRect(new RectF(x - radius, y - radius, x + radius, y + radius));
        canvas.drawCircle(x + (waxing ? -shift : shift), y, radius, paint);
        canvas.restore();
    }

    private void drawConstellation(Canvas canvas, float cx, float cy) {
        float[][] pts = {{-42,-12},{-20,10},{2,-16},{18,9},{42,-6},{29,24}};
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.argb(115, 145, 211, 255));
        path.reset();
        path.moveTo(cx + dp(pts[0][0]), cy + dp(pts[0][1]));
        for (int i = 1; i < pts.length; i++) path.lineTo(cx + dp(pts[i][0]), cy + dp(pts[i][1]));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < pts.length; i++) {
            paint.setColor(i == 2 ? Color.WHITE : Color.rgb(135, 210, 255));
            canvas.drawCircle(cx + dp(pts[i][0]), cy + dp(pts[i][1]), i == 2 ? dp(3) : dp(2), paint);
        }
    }

    private float daylightProgress() {
        int rise = clockMinutes(sunrise);
        int set = clockMinutes(sunset);
        if (rise < 0 || set <= rise) return 0.5f;
        Date local = new Date(localEpoch * 1000L);
        int now = local.getHours() * 60 + local.getMinutes();
        return Math.max(0f, Math.min(1f, (now - rise) / (float) (set - rise)));
    }

    private int clockMinutes(String value) {
        for (String format : new String[]{"hh:mm a", "HH:mm"}) {
            try {
                Date date = new SimpleDateFormat(format, Locale.US).parse(value);
                if (date != null) return date.getHours() * 60 + date.getMinutes();
            } catch (ParseException ignored) { }
        }
        return -1;
    }

    private String shortClock(String value) {
        int minutes = clockMinutes(value);
        if (minutes < 0) return value;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60);
    }

    private String phaseLabel() {
        String value = moonPhase.toLowerCase(Locale.US);
        if (value.contains("new")) return "Новолуние";
        if (value.contains("full")) return "Полнолуние";
        if (value.contains("first")) return "Первая четверть";
        if (value.contains("last")) return "Последняя четверть";
        if (value.contains("wax")) return "Растущая Луна";
        if (value.contains("wan")) return "Убывающая Луна";
        return "Фаза Луны";
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

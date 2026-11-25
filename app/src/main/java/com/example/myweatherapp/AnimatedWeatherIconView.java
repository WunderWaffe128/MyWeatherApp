package com.example.myweatherapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Lightweight, resolution-independent weather artwork with no external image assets. */
public class AnimatedWeatherIconView extends View {

    private enum WeatherKind { CLEAR, CLOUDY, RAIN, SNOW, FOG, THUNDER }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect visibleRect = new Rect();
    private WeatherKind kind = WeatherKind.CLOUDY;
    private boolean daytime = true;
    private boolean running;
    private boolean animationsEnabled = true;
    private long animationStartedAt;

    public AnimatedWeatherIconView(Context context) {
        super(context);
        init();
    }

    public AnimatedWeatherIconView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimatedWeatherIconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription("Погодные условия");
    }

    public void setCondition(String condition, boolean isDay) {
        daytime = isDay;
        String value = condition == null ? "" : condition.toLowerCase(new Locale("ru", "RU"));
        if (containsAny(value, "гроз", "гром")) kind = WeatherKind.THUNDER;
        else if (containsAny(value, "дожд", "морос", "ливень")) kind = WeatherKind.RAIN;
        else if (containsAny(value, "снег", "метель", "лед", "круп")) kind = WeatherKind.SNOW;
        else if (containsAny(value, "туман", "дымка", "мгла")) kind = WeatherKind.FOG;
        else if (containsAny(value, "облач", "пасмур")) kind = WeatherKind.CLOUDY;
        else kind = WeatherKind.CLEAR;
        setContentDescription(condition);
        animationStartedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        animationStartedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        animationStartedAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        if (side <= 0) return;
        float scale = side / 100f;
        float left = (getWidth() - side) / 2f;
        float top = (getHeight() - side) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);

        float seconds = animationsEnabled
                ? (SystemClock.uptimeMillis() - animationStartedAt) / 1000f : 0f;
        switch (kind) {
            case CLEAR: drawClear(canvas, seconds); break;
            case RAIN: drawRain(canvas, seconds); break;
            case SNOW: drawSnow(canvas, seconds); break;
            case FOG: drawFog(canvas, seconds); break;
            case THUNDER: drawThunder(canvas, seconds); break;
            case CLOUDY:
            default: drawCloudy(canvas, seconds); break;
        }
        canvas.restore();

        // 30 fps is visually smooth for these small illustrations and avoids
        // animating off-screen forecast rows while the page is being scrolled.
        if (running && animationsEnabled && isShown() && getGlobalVisibleRect(visibleRect)) {
            postInvalidateDelayed(33L);
        }
    }

    private void drawClear(Canvas canvas, float seconds) {
        if (!daytime) {
            drawMoon(canvas, seconds);
            return;
        }
        float pulse = 1f + 0.035f * (float) Math.sin(seconds * 2.2f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(80, 255, 181, 28));
        canvas.drawCircle(50, 50, 31 * pulse, paint);
        canvas.save();
        canvas.rotate(seconds * 14f, 50, 50);
        paint.setColor(Color.rgb(255, 170, 20));
        paint.setStrokeWidth(6f);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            canvas.drawLine(
                    50 + (float) Math.cos(angle) * 30,
                    50 + (float) Math.sin(angle) * 30,
                    50 + (float) Math.cos(angle) * 40,
                    50 + (float) Math.sin(angle) * 40,
                    paint);
        }
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(43, 41, 29,
                new int[]{Color.rgb(255, 249, 133), Color.rgb(255, 208, 40), Color.rgb(255, 133, 10)},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(50, 50, 22 * pulse, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(165, 255, 255, 219));
        canvas.drawCircle(43, 42, 7, paint);
    }

    private void drawMoon(Canvas canvas, float seconds) {
        float floatY = (float) Math.sin(seconds * 1.1f) * 1.4f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 124, 181, 255));
        canvas.drawCircle(50, 49 + floatY, 31, paint);
        paint.setShader(new RadialGradient(42, 39 + floatY, 31,
                new int[]{Color.WHITE, Color.rgb(220, 234, 255), Color.rgb(119, 158, 221)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(50, 49 + floatY, 23, paint);
        paint.setShader(null);
        paint.setColor(Color.rgb(31, 54, 91));
        canvas.drawCircle(61, 39 + floatY, 23, paint);
        paint.setColor(Color.argb(230, 225, 240, 255));
        for (int i = 0; i < 4; i++) {
            double angle = seconds * 0.35f + i * Math.PI / 2;
            float x = 50 + (float) Math.cos(angle) * (34 + i * 2);
            float y = 49 + floatY + (float) Math.sin(angle) * (34 + i * 2);
            canvas.save();
            canvas.rotate(seconds * 20f + i * 22f, x, y);
            path.reset();
            path.moveTo(x, y - 4);
            path.lineTo(x + 1.5f, y - 1.5f);
            path.lineTo(x + 4, y);
            path.lineTo(x + 1.5f, y + 1.5f);
            path.lineTo(x, y + 4);
            path.lineTo(x - 1.5f, y + 1.5f);
            path.lineTo(x - 4, y);
            path.lineTo(x - 1.5f, y - 1.5f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }

    private void drawCloudy(Canvas canvas, float seconds) {
        float drift = (float) Math.sin(seconds * 1.1f) * 2.2f;
        if (daytime) drawSmallSun(canvas, 67, 34, seconds);
        drawCloud(canvas, 50 + drift, 58, 1f, Color.rgb(241, 248, 255));
    }

    private void drawRain(Canvas canvas, float seconds) {
        float drift = (float) Math.sin(seconds * 0.9f) * 1.4f;
        drawCloud(canvas, 50 + drift, 43, 0.96f, Color.rgb(224, 235, 248));
        paint.setColor(Color.rgb(36, 194, 255));
        paint.setStrokeWidth(5f);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            float phase = (seconds * 0.58f + i * 0.31f) % 1f;
            float fade = Math.min(1f, phase * 7f) * Math.min(1f, (1f - phase) * 7f);
            float x = 33 + i * 17 + (float) Math.sin(seconds * 1.4f + i) * 1.2f;
            float y = 58 + phase * 33f;
            paint.setAlpha((int) (255 * fade));
            canvas.drawLine(x, y, x - 4, y + 9, paint);
        }
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSnow(Canvas canvas, float seconds) {
        float drift = (float) Math.sin(seconds * 0.85f) * 1.5f;
        drawCloud(canvas, 50 + drift, 42, 0.96f, Color.rgb(235, 244, 253));
        for (int i = 0; i < 3; i++) {
            float phase = (seconds * 0.34f + i * 0.32f) % 1f;
            float fade = Math.min(1f, phase * 6f) * Math.min(1f, (1f - phase) * 6f);
            float sway = (float) Math.sin(seconds * 1.55f + i * 1.7f) * 3f;
            drawSnowflake(canvas, 32 + i * 18 + sway, 60 + phase * 34f, 5.2f, fade);
        }
    }

    private void drawFog(Canvas canvas, float seconds) {
        drawCloud(canvas, 50, 39, 0.86f, Color.rgb(230, 239, 248));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.argb(220, 226, 241, 250));
        for (int i = 0; i < 3; i++) {
            float shift = (float) Math.sin(seconds * 1.1f + i) * 4f;
            canvas.drawLine(22 + shift, 62 + i * 11, 78 + shift, 62 + i * 11, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawThunder(Canvas canvas, float seconds) {
        drawCloud(canvas, 50, 42, 0.98f, Color.rgb(218, 229, 244));
        float glow = 0.7f + 0.3f * Math.abs((float) Math.sin(seconds * 3.1f));
        paint.setColor(Color.argb((int) (255 * glow), 255, 220, 86));
        path.reset();
        path.moveTo(50, 58);
        path.lineTo(39, 76);
        path.lineTo(49, 75);
        path.lineTo(43, 92);
        path.lineTo(66, 68);
        path.lineTo(55, 69);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawSmallSun(Canvas canvas, float x, float y, float seconds) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(72, 255, 172, 18));
        canvas.drawCircle(x, y, 17, paint);
        canvas.save();
        canvas.rotate(seconds * 17f, x, y);
        paint.setColor(Color.rgb(255, 164, 12));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4.2f);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            canvas.drawLine(
                    x + (float) Math.cos(angle) * 14,
                    y + (float) Math.sin(angle) * 14,
                    x + (float) Math.cos(angle) * 20,
                    y + (float) Math.sin(angle) * 20,
                    paint);
        }
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x - 3, y - 3, 14,
                new int[]{Color.rgb(255, 252, 151), Color.rgb(255, 203, 36), Color.rgb(255, 132, 9)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, 10, paint);
        paint.setShader(null);
    }

    private void drawCloud(Canvas canvas, float centerX, float centerY, float scale, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Color.argb(115, 28, 71, 122));
        canvas.drawOval(new RectF(centerX - 33 * scale, centerY + 2 * scale,
                centerX + 35 * scale, centerY + 28 * scale), paint);
        paint.setColor(Color.WHITE);
        paint.setShader(new LinearGradient(centerX - 18 * scale, centerY - 25 * scale,
                centerX + 24 * scale, centerY + 25 * scale,
                Color.WHITE, color, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX - 19 * scale, centerY, 16 * scale, paint);
        canvas.drawCircle(centerX + 2 * scale, centerY - 10 * scale, 23 * scale, paint);
        canvas.drawCircle(centerX + 23 * scale, centerY + 1 * scale, 15 * scale, paint);
        canvas.drawRoundRect(new RectF(centerX - 35 * scale, centerY,
                centerX + 37 * scale, centerY + 22 * scale), 11 * scale, 11 * scale, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(82, 255, 255, 255));
        canvas.drawOval(new RectF(centerX - 15 * scale, centerY - 20 * scale,
                centerX + 3 * scale, centerY - 12 * scale), paint);
    }

    private void drawSnowflake(Canvas canvas, float x, float y, float radius, float alpha) {
        paint.setShader(null);
        paint.setColor(Color.rgb(226, 245, 255));
        paint.setAlpha((int) (255 * alpha));
        paint.setStrokeWidth(2.7f);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(i * 60);
            float dx = (float) Math.cos(angle) * radius;
            float dy = (float) Math.sin(angle) * radius;
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, paint);
        }
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
    }
}

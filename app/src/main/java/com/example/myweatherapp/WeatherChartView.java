package com.example.myweatherapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Lightweight temperature/precipitation chart drawn in a single view. */
public class WeatherChartView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private float[] temperatures = new float[0];
    private int[] precipitation = new int[0];
    private String[] labels = new String[0];
    private String unit = "°C";

    public WeatherChartView(Context context) {
        super(context);
        init();
    }

    public WeatherChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WeatherChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setContentDescription("График температуры и вероятности осадков");
    }

    public void setData(float[] temperatures, int[] precipitation, String[] labels, String unit) {
        this.temperatures = temperatures == null ? new float[0] : temperatures.clone();
        this.precipitation = precipitation == null ? new int[0] : precipitation.clone();
        this.labels = labels == null ? new String[0] : labels.clone();
        this.unit = unit == null ? "°C" : unit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = temperatures.length;
        if (count < 2) return;

        float left = dp(36);
        float right = getWidth() - dp(14);
        float top = dp(19);
        float chartBottom = getHeight() - dp(72);
        float rainTop = getHeight() - dp(59);
        float rainBottom = getHeight() - dp(31);
        float labelY = getHeight() - dp(8);
        float width = Math.max(1, right - left);

        float min = temperatures[0];
        float max = temperatures[0];
        for (float value : temperatures) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (max - min < 3f) {
            max += 1.5f;
            min -= 1.5f;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(35, 255, 255, 255));
        for (int row = 0; row < 3; row++) {
            float y = top + (chartBottom - top) * row / 2f;
            canvas.drawLine(left, y, right, y, paint);
        }

        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(dp(9));
        paint.setColor(Color.argb(190, 225, 241, 255));
        canvas.drawText(String.format(Locale.getDefault(), "%.0f%s", max, unit), left - dp(7), top + dp(3), paint);
        canvas.drawText(String.format(Locale.getDefault(), "%.0f%s", min, unit), left - dp(7), chartBottom + dp(3), paint);
        canvas.drawText("%", left - dp(8), rainBottom, paint);

        float step = width / (count - 1f);
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = left + step * i;
            ys[i] = chartBottom - (temperatures[i] - min) / (max - min) * (chartBottom - top);
        }
        linePath.reset();
        fillPath.reset();
        linePath.moveTo(xs[0], ys[0]);
        fillPath.moveTo(xs[0], chartBottom);
        fillPath.lineTo(xs[0], ys[0]);
        for (int i = 1; i < count; i++) {
            float midX = (xs[i - 1] + xs[i]) * 0.5f;
            linePath.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
            fillPath.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
        }
        fillPath.lineTo(right, chartBottom);
        fillPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, top, 0, chartBottom,
                Color.argb(105, 117, 212, 255), Color.argb(4, 117, 212, 255),
                Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(dp(3));
        paint.setColor(Color.rgb(170, 232, 255));
        canvas.drawPath(linePath, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < count; i++) {
            float x = xs[i];
            float y = ys[i];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(235, 250, 255));
            canvas.drawCircle(x, y, i == 0 ? dp(4.2f) : dp(2.7f), paint);
            if (i == 0 || i == count - 1 || temperatures[i] == min || temperatures[i] == max) {
                paint.setTextSize(dp(10));
                paint.setColor(Color.argb(230, 255, 255, 255));
                canvas.drawText(String.format(Locale.getDefault(), "%.0f°", temperatures[i]), x,
                        Math.max(dp(12), y - dp(8)), paint);
            }

            int chance = i < precipitation.length ? precipitation[i] : 0;
            float barHeight = (rainBottom - rainTop) * Math.max(0, Math.min(100, chance)) / 100f;
            float halfBar = Math.max(dp(3), Math.min(dp(7), step * 0.28f));
            paint.setColor(Color.argb(175, 93, 190, 255));
            canvas.drawRoundRect(x - halfBar, rainBottom - barHeight,
                    x + halfBar, rainBottom, dp(4), dp(4), paint);
            if (chance >= 30 && (i % 2 == 0 || i == count - 1)) {
                paint.setTextSize(dp(8));
                paint.setColor(Color.argb(210, 174, 229, 255));
                canvas.drawText(chance + "%", x, rainTop - dp(2), paint);
            }

            if (i < labels.length && (i == 0 || i == count - 1 || i % 3 == 0)) {
                paint.setTextSize(dp(9));
                paint.setColor(Color.argb(170, 255, 255, 255));
                canvas.drawText(labels[i], x, labelY, paint);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

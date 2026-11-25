package com.example.myweatherapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/** A rounded container that samples and blurs the real app background on Android 12+. */
public class LiquidGlassLayout extends LinearLayout {

    private static final String REFRACTION_SHADER =
            "uniform shader content;" +
            "uniform float2 size;" +
            "uniform float cornerRadius;" +
            "uniform float strength;" +
            "half4 main(float2 coord) {" +
            "  float2 halfSize = size * 0.5;" +
            "  float2 centered = coord - halfSize;" +
            "  float2 inner = halfSize - float2(cornerRadius);" +
            "  float2 q = abs(centered) - inner;" +
            "  float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius;" +
            "  float edge = smoothstep(-cornerRadius * 0.62, -1.5, sd);" +
            "  float2 direction = centered / max(length(centered), 1.0);" +
            "  float2 tangent = float2(-direction.y, direction.x);" +
            "  float bend = strength * edge * edge;" +
            "  float wave = sin((coord.x + coord.y) * 0.018) * edge * strength * 0.11;" +
            "  float2 sampleAt = coord - direction * bend + tangent * wave;" +
            "  half4 base = content.eval(sampleAt);" +
            "  half red = content.eval(sampleAt + direction * 1.8).r;" +
            "  half blue = content.eval(sampleAt - direction * 1.8).b;" +
            "  half alpha = half(0.08 + edge * 0.70);" +
            "  half3 refracted = half3(red, base.g, blue);" +
            "  return half4(refracted * alpha, alpha);" +
            "}";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path clipPath = new Path();
    private final Path innerRefractionPath = new Path();
    private final int[] ownLocation = new int[2];
    private final int[] backgroundLocation = new int[2];
    private RenderNode backdropNode;
    private RefractionEffect33 refractionEffect33;
    private float radius;
    private boolean running;
    private boolean refractionEnabled = true;
    private boolean animatedShimmer = true;
    private boolean lightweight;
    private boolean interactionPaused;
    private float refractionScale = 1f;
    private int glassTheme;
    private long animationStartedAt;

    public LiquidGlassLayout(Context context) {
        super(context);
        init();
    }

    public LiquidGlassLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiquidGlassLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        radius = dp(28);
        setWillNotDraw(false);
        setBackground(null);
        animationStartedAt = SystemClock.uptimeMillis();
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

    /** Lightweight mode for repeated forecast/favorite cards. */
    public void setRefractionEnabled(boolean enabled) {
        refractionEnabled = enabled;
        invalidate();
    }

    public void setAnimatedShimmer(boolean enabled) {
        animatedShimmer = enabled && !lightweight;
        invalidate();
    }

    /** Keeps the real refraction and chromatic edge, but avoids a per-frame redraw. */
    public void setLightweight(boolean enabled) {
        lightweight = enabled;
        if (enabled) animatedShimmer = false;
        invalidate();
    }

    /**
     * Pauses only the animated shimmer while the parent is moving.
     * The refraction and chromatic edge must remain visible at all times.
     */
    public void setInteractionPaused(boolean paused) {
        interactionPaused = paused;
        invalidate();
    }

    public void setRefractionScale(float scale) {
        refractionScale = Math.max(0.55f, Math.min(1.45f, scale));
        refractionEffect33 = null;
        invalidate();
    }

    public void setGlassTheme(int theme) {
        glassTheme = Math.max(0, Math.min(2, theme));
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        bounds.set(0, 0, getWidth(), getHeight());
        if (getWidth() > 0 && getHeight() > 0) {
            canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            drawBackdrop(canvas);
            drawGlassLayers(canvas);
            canvas.restore();
        }
        super.dispatchDraw(canvas);
        drawBorder(canvas);
        if (running && animatedShimmer && !interactionPaused && isShown()) {
            postInvalidateDelayed(48L);
        }
    }

    private void drawBackdrop(Canvas canvas) {
        // Keep the real backdrop/refraction during scrolling. interactionPaused
        // only suppresses the timer-driven shimmer invalidation below; hiding
        // this layer made the liquid-glass surface disappear while swiping.
        // Small repeated forecast cards keep the same translucent glass,
        // highlights and chromatic rim, but do not re-record the complete
        // screen background for every card on every scroll frame. With a
        // dozen hourly cards that recording was the main source of jank.
        if (!refractionEnabled || lightweight) return;
        View background = getRootView().findViewById(R.id.backgroundImage);
        if (background == null || background == this) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated()) {
            if (backdropNode == null) backdropNode = new RenderNode("liquid-glass-backdrop");
            backdropNode.setPosition(0, 0, getWidth(), getHeight());
            Canvas recordingCanvas = backdropNode.beginRecording(getWidth(), getHeight());
            getLocationInWindow(ownLocation);
            drawLayer(recordingCanvas, background);
            drawLayer(recordingCanvas, getRootView().findViewById(R.id.seasonOverlay));
            drawLayer(recordingCanvas, getRootView().findViewById(R.id.weatherScrim));
            drawLayer(recordingCanvas, getRootView().findViewById(R.id.ambientOrb));
            backdropNode.endRecording();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (refractionEffect33 == null) refractionEffect33 = new RefractionEffect33();
                backdropNode.setRenderEffect(refractionEffect33.create(
                        getWidth(), getHeight(), radius, dp(13f) * refractionScale, dp(2.2f)));
                backdropNode.setAlpha(1f);
                canvas.drawRenderNode(backdropNode);
            } else {
                backdropNode.setRenderEffect(RenderEffect.createBlurEffect(
                        dp(5), dp(5), Shader.TileMode.CLAMP));
                drawFallbackRefraction(canvas);
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void drawFallbackRefraction(Canvas canvas) {
        backdropNode.setAlpha(0.12f);
        canvas.drawRenderNode(backdropNode);

        float inset = dp(11);
        innerRefractionPath.reset();
        innerRefractionPath.addRoundRect(
                new RectF(inset, inset, getWidth() - inset, getHeight() - inset),
                Math.max(0, radius - inset), Math.max(0, radius - inset), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.clipOutPath(innerRefractionPath);
        canvas.scale(1.035f, 1.035f, getWidth() * 0.5f, getHeight() * 0.5f);
        backdropNode.setAlpha(0.44f);
        canvas.drawRenderNode(backdropNode);
        canvas.restore();
        backdropNode.setAlpha(1f);
    }

    private void drawLayer(Canvas canvas, @Nullable View layer) {
        if (layer == null || layer == this) return;
        layer.getLocationInWindow(backgroundLocation);
        canvas.save();
        canvas.translate(
                backgroundLocation[0] - ownLocation[0],
                backgroundLocation[1] - ownLocation[1]);
        layer.draw(canvas);
        canvas.restore();
    }

    private void drawGlassLayers(Canvas canvas) {
        float seconds = (SystemClock.uptimeMillis() - animationStartedAt) / 1000f;
        float shimmer = interactionPaused
                ? 0.56f
                : 0.5f + 0.5f * (float) Math.sin(seconds * 0.62f);
        paint.setStyle(Paint.Style.FILL);
        int[] glassColors = glassTheme == 1
                ? new int[]{Color.argb(20, 126, 255, 226), Color.argb(10, 135, 177, 255), Color.argb(6, 198, 132, 255)}
                : glassTheme == 2
                ? new int[]{Color.argb(21, 255, 214, 151), Color.argb(10, 255, 139, 176), Color.argb(5, 151, 153, 255)}
                : new int[]{Color.argb(14, 255, 255, 255), Color.argb(7, 235, 245, 255), Color.argb(3, 160, 190, 230)};
        paint.setShader(new LinearGradient(
                0, 0, getWidth(), getHeight(),
                glassColors,
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, radius, radius, paint);

        paint.setShader(new RadialGradient(
                getWidth() * 0.12f, getHeight() * 0.04f,
                Math.max(getWidth(), getHeight()) * 0.72f,
                new int[]{Color.argb(12, 255, 255, 255), Color.argb(3, 180, 226, 255), Color.TRANSPARENT},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, radius, radius, paint);

        paint.setShader(new LinearGradient(
                0, 0, getWidth() * 0.55f, getHeight() * 0.5f,
                Color.argb(26, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(dp(2), dp(2), getWidth() - dp(2), getHeight() * 0.30f),
                radius - dp(2), radius - dp(2), paint);
        paint.setShader(null);

        float shimmerX = getWidth() * (0.08f + shimmer * 0.72f);
        paint.setShader(new RadialGradient(
                shimmerX, -dp(4), Math.max(dp(70), getWidth() * 0.34f),
                new int[]{Color.argb(30, 255, 255, 255), Color.argb(9, 190, 231, 255), Color.TRANSPARENT},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(1.15f));
        paint.setShader(new LinearGradient(
                shimmerX - dp(82), 0, shimmerX + dp(82), 0,
                new int[]{Color.TRANSPARENT, Color.argb(155, 255, 255, 255), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawLine(radius * 0.72f, dp(1.7f), getWidth() - radius * 0.72f, dp(1.7f), paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setColor(Color.argb(26, 3, 17, 38));
        float inner = dp(3.5f);
        canvas.drawRoundRect(new RectF(inner, inner, getWidth() - inner, getHeight() - inner),
                radius - inner, radius - inner, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBorder(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.9f));
        int[] rimColors = glassTheme == 1
                ? new int[]{Color.argb(185, 178, 255, 226), Color.argb(105, 139, 183, 255), Color.argb(145, 226, 173, 255)}
                : glassTheme == 2
                ? new int[]{Color.argb(190, 255, 232, 184), Color.argb(105, 255, 147, 190), Color.argb(135, 174, 179, 255)}
                : new int[]{Color.argb(185, 255, 255, 255), Color.argb(76, 220, 238, 255), Color.argb(105, 255, 255, 255)};
        paint.setShader(new LinearGradient(
                0, 0, getWidth(), getHeight(),
                rimColors,
                null, Shader.TileMode.CLAMP));
        float inset = dp(0.75f);
        canvas.drawRoundRect(new RectF(inset, inset, getWidth() - inset, getHeight() - inset),
                radius, radius, paint);

        // A subtle iPhone-like dispersion line: colour separation is visible only
        // on the rim, while the centre stays neutral and readable.
        float chromaInset = dp(2.15f);
        paint.setStrokeWidth(dp(1.05f));
        paint.setShader(new LinearGradient(
                0, getHeight(), getWidth(), 0,
                new int[]{
                        Color.argb(105, 113, 210, 255),
                        Color.argb(90, 168, 125, 255),
                        Color.argb(80, 255, 132, 205),
                        Color.argb(86, 255, 216, 117),
                        Color.argb(102, 115, 230, 255)},
                new float[]{0f, 0.24f, 0.50f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(chromaInset, chromaInset,
                        getWidth() - chromaInset, getHeight() - chromaInset),
                Math.max(0, radius - chromaInset), Math.max(0, radius - chromaInset), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class RefractionEffect33 {
        private final RuntimeShader shader = new RuntimeShader(REFRACTION_SHADER);
        private RenderEffect cachedEffect;
        private float cachedWidth = -1;
        private float cachedHeight = -1;

        RenderEffect create(float width, float height, float cornerRadius,
                            float strength, float blurRadius) {
            if (cachedEffect != null && cachedWidth == width && cachedHeight == height) {
                return cachedEffect;
            }
            shader.setFloatUniform("size", width, height);
            shader.setFloatUniform("cornerRadius", cornerRadius);
            shader.setFloatUniform("strength", strength);
            RenderEffect blur = RenderEffect.createBlurEffect(
                    blurRadius, blurRadius, Shader.TileMode.CLAMP);
            RenderEffect lens = RenderEffect.createRuntimeShaderEffect(shader, "content");
            cachedWidth = width;
            cachedHeight = height;
            cachedEffect = RenderEffect.createChainEffect(lens, blur);
            return cachedEffect;
        }
    }
}

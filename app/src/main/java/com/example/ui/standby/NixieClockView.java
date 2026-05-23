package com.example.ui.standby;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class NixieClockView extends View {

    private Paint tubePaint;
    private Paint wirePaint;
    private Paint offDigitPaint;
    private Paint onDigitPaint;

    private String currentTime = "00:00:00";
    private String previousTime = "00:00:00";

    // Tracks how long each character index should flicker (timestamp in ms)
    private long[] flickerUntil = new long[10]; 
    private Random random = new Random();

    public NixieClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Background of the glass tube
        tubePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tubePaint.setColor(Color.parseColor("#1A1A1A"));
        tubePaint.setStyle(Paint.Style.FILL);

        // Thin anode mesh wires
        wirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wirePaint.setColor(Color.parseColor("#333333"));
        wirePaint.setStrokeWidth(2f);
        wirePaint.setStyle(Paint.Style.STROKE);

        // Inactive stacked digits inside the tube
        offDigitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        offDigitPaint.setColor(Color.parseColor("#2A0800")); // Faint burnt orange
        offDigitPaint.setTextAlign(Paint.Align.CENTER);

        // Active glowing digit
        onDigitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        onDigitPaint.setColor(Color.parseColor("#FF5722")); // Bright neon orange
        onDigitPaint.setTextAlign(Paint.Align.CENTER);
        
        // Use a thin/wireframe built-in Android font
        Typeface wireFont = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        offDigitPaint.setTypeface(wireFont);
        onDigitPaint.setTypeface(wireFont);
        
        // Neon glow effect
        onDigitPaint.setShadowLayer(15f, 0f, 0f, Color.parseColor("#FF3D00"));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setTime(String newTime) {
        if (newTime == null) return;

        if (newTime.length() != currentTime.length() || flickerUntil.length < newTime.length()) {
            flickerUntil = new long[newTime.length()];
        }

        // Determine which digits changed to trigger the flicker
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(newTime.length(), currentTime.length()); i++) {
            if (currentTime.charAt(i) != newTime.charAt(i)) {
                // Sputter/flicker for 200-400ms when changing
                flickerUntil[i] = now + 200 + random.nextInt(200); 
            }
        }
        
        this.previousTime = this.currentTime;
        this.currentTime = newTime;
        invalidate(); // Request redraw
    }

    public void setGlowColor(int color) {
        onDigitPaint.setColor(color);
        onDigitPaint.setShadowLayer(15f, 0f, 0f, color);
        // Maybe try to make inactive slightly darker version of color
        int r = Math.max(0, Color.red(color) - 200);
        int g = Math.max(0, Color.green(color) - 100);
        int b = Math.max(0, Color.blue(color) - 100);
        offDigitPaint.setColor(Color.rgb(r, g, b));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int length = Math.max(1, currentTime.length());
        float charWidth = width / (float) length;
        
        // Dynamic text sizing based on view bounds
        float textSize = height * 0.6f;
        offDigitPaint.setTextSize(textSize);
        onDigitPaint.setTextSize(textSize);
        
        float textY = (height / 2f) - ((onDigitPaint.descent() + onDigitPaint.ascent()) / 2f);
        long now = System.currentTimeMillis();
        boolean needsAnimationLoop = false;

        for (int i = 0; i < length; i++) {
            float startX = i * charWidth;
            float centerX = startX + (charWidth / 2f);

            char c = currentTime.charAt(i);

            // Skip rendering stacked digits for colons
            if (c != ':') {
                // 3. Draw stacked inactive digits (simulating depth)
                canvas.drawText("8", centerX, textY, offDigitPaint);
                canvas.drawText("0", centerX - 2, textY + 2, offDigitPaint);
            }

            // 4. Draw Active Digit with Flicker Logic
            int alpha = 255;
            if (now < flickerUntil[i]) {
                // Digit is currently in a flickering state
                alpha = 50 + random.nextInt(205); // Random alpha between 50 and 255
                needsAnimationLoop = true;
            }
            
            onDigitPaint.setAlpha(alpha);
            
            // Adjust shadow radius to pulse with the flicker
            float glowRadius = (alpha / 255f) * 20f;
            onDigitPaint.setShadowLayer(glowRadius, 0f, 0f, onDigitPaint.getColor());

            canvas.drawText(String.valueOf(c), centerX, textY, onDigitPaint);
        }

        // If any digit is still flickering, continuously request layout passes
        if (needsAnimationLoop) {
            postInvalidateDelayed(30); // ~30fps flicker refresh
        }
    }
}

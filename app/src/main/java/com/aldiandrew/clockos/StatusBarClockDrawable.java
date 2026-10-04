package com.aldiandrew.clockos;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;

import org.xmlpull.v1.XmlPullParser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class StatusBarClockDrawable extends Drawable {
    private static final String AUTO_NS =
            "http://schemas.android.com/apk/res-auto";

    private Resources resources;
    private float sizeSp = 14f;
    private int level;
    private ColorFilter colorFilter;

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
        }
    };

    public StatusBarClockDrawable() {
        resources = Resources.getSystem();
        paint.setDither(true);
        updateTypeface();
    }

    @Override
    public void inflate(
            Resources res,
            XmlPullParser parser,
            AttributeSet attrs,
            android.content.res.Resources.Theme theme
    ) {
        super.inflate(res, parser, attrs, theme);
        resources = res;

        if (attrs != null) {
            sizeSp = attrs.getAttributeFloatValue(
                    AUTO_NS,
                    "sizeSp",
                    14f
            );
        }

        updateTypeface();
        invalidateSelf();
    }

    @Override
    protected boolean onLevelChange(int newLevel) {
        level = newLevel;
        updateTypeface();
        invalidateSelf();
        return true;
    }

    @Override
    public boolean setState(int[] stateSet) {
        boolean changed = super.setState(stateSet);
        refreshTint();
        return changed;
    }

    @Override
    public void setTintList(ColorStateList tint) {
        super.setTintList(tint);
        refreshTint();
        invalidateSelf();
    }

    @Override
    public void setTint(int color) {
        super.setTint(color);
        colorFilter = new PorterDuffColorFilter(
                color,
                PorterDuff.Mode.SRC_IN
        );
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        colorFilter = filter;
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    @Override
    public void draw(Canvas canvas) {
        String text = formatNow();

        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp,
                resources.getDisplayMetrics()
        );

        paint.setTextSize(textSizePx);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        paint.setTypeface(currentTypeface());
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColorFilter(colorFilter);

        Rect bounds = getBounds();
        Paint.FontMetrics fm = paint.getFontMetrics();
        float width = paint.measureText(text);

        float x = bounds.left + Math.max(
                0f,
                (bounds.width() - width) / 2f
        );

        float baseline = bounds.top
                + (bounds.height() - fm.bottom - fm.top) / 2f;

        canvas.drawText(text, x, baseline, paint);
        scheduleNextTick();
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.max(
                1,
                Math.round(
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP,
                                24f,
                                resources.getDisplayMetrics()
                        )
                )
        );
    }

    @Override
    public int getIntrinsicWidth() {
        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp,
                resources.getDisplayMetrics()
        );

        paint.setTextSize(textSizePx);
        paint.setTypeface(currentTypeface());

        return Math.max(
                1,
                Math.round(paint.measureText(formatSample()))
        );
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }

    private String formatNow() {
        boolean format24 = (level & 0x01) != 0;
        boolean showSeconds = (level & 0x02) != 0;
        boolean showDate = (level & 0x04) != 0;
        boolean showDay = (level & 0x08) != 0;

        StringBuilder pattern = new StringBuilder();
        pattern.append(format24 ? "HH:mm" : "hh:mm a");

        if (showSeconds) {
            pattern.append(":ss");
        }

        if (showDate) {
            pattern.append(" dd/MM");
        }

        if (showDay) {
            pattern.append(" EEE");
        }

        return new SimpleDateFormat(
                pattern.toString(),
                Locale.getDefault()
        ).format(new Date());
    }

    private String formatSample() {
        boolean format24 = (level & 0x01) != 0;
        boolean showSeconds = (level & 0x02) != 0;
        boolean showDate = (level & 0x04) != 0;
        boolean showDay = (level & 0x08) != 0;

        StringBuilder sample = new StringBuilder();
        sample.append(format24 ? "23:59" : "11:59 PM");

        if (showSeconds) {
            sample.append(":59");
        }

        if (showDate) {
            sample.append(" 31/12");
        }

        if (showDay) {
            sample.append(" Wed");
        }

        return sample.toString();
    }

    private void updateTypeface() {
        paint.setTypeface(currentTypeface());
    }

    private Typeface currentTypeface() {
        int weightCode = (level >> 4) & 0x0F;

        int weight;
        switch (weightCode) {
            case 0:
                weight = 300;
                break;
            case 2:
                weight = 500;
                break;
            case 3:
                weight = 700;
                break;
            default:
                weight = 400;
                break;
        }

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return Typeface.create(
                    "sans-serif",
                    weight,
                    false
            );
        }

        return Typeface.create(
                "sans-serif",
                weight >= 600
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );
    }

    private void refreshTint() {
        ColorStateList tint = getTintList();

        if (tint != null) {
            int color = tint.getColorForState(
                    getState(),
                    tint.getDefaultColor()
            );

            colorFilter = new PorterDuffColorFilter(
                    color,
                    PorterDuff.Mode.SRC_IN
            );

            paint.setColorFilter(colorFilter);
        }
    }

    private void scheduleNextTick() {
        long now = System.currentTimeMillis();

        long next;
        if ((level & 0x02) != 0) {
            next = now + (1000L - (now % 1000L));
        } else {
            next = now + (60000L - (now % 60000L));
        }

        scheduleSelf(
                ticker,
                SystemClock.uptimeMillis()
                        + Math.max(250L, next - now)
        );
    }
}

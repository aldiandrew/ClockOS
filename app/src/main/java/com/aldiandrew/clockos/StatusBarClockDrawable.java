package com.aldiandrew.clockos;

import android.content.res.ColorStateList;
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
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class StatusBarClockDrawable extends Drawable {
    private static final String AUTO_NS =
            "http://schemas.android.com/apk/res-auto";

    private float sizeSp = 14f;
    private int level;
    private int alpha = 255;
    private ColorFilter colorFilter;
    private ColorStateList tintList;

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
        }
    };

    public StatusBarClockDrawable() {
        paint.setDither(true);
        updateTypeface();
    }

    @Override
    public void inflate(
            android.content.res.Resources res,
            XmlPullParser parser,
            AttributeSet attrs,
            android.content.res.Resources.Theme theme
    ) throws XmlPullParserException, IOException {
        super.inflate(res, parser, attrs, theme);

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
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public boolean setState(int[] stateSet) {
        boolean changed = super.setState(stateSet);
        refreshTint();
        return changed;
    }

    @Override
    public void setTintList(ColorStateList tint) {
        tintList = tint;
        super.setTintList(tint);
        refreshTint();
        invalidateSelf();
    }

    @Override
    public void setTint(int color) {
        tintList = ColorStateList.valueOf(color);
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
                getDensity()
        );

        paint.setTextSize(textSizePx);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
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
                                getDensity()
                        )
                )
        );
    }

    @Override
    public int getIntrinsicWidth() {
        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp,
                getDensity()
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

    private android.util.DisplayMetrics getDensity() {
        return android.content.res.Resources.getSystem().getDisplayMetrics();
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

        int style = Typeface.NORMAL;
        if (weightCode >= 3) {
            style = Typeface.BOLD;
        }

        return Typeface.create("sans-serif", style);
    }

    private void refreshTint() {
        if (tintList == null) {
            return;
        }

        int color = tintList.getColorForState(
                getState(),
                tintList.getDefaultColor()
        );

        colorFilter = new PorterDuffColorFilter(
                color,
                PorterDuff.Mode.SRC_IN
        );

        paint.setColorFilter(colorFilter);
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

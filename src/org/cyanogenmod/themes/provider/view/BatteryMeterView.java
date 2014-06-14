/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cyanogenmod.themes.provider.view;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import static org.cyanogenmod.themes.provider.util.SystemUiPreviewGenerator.SYSTEMUI_PACKAGE;

public class BatteryMeterView extends View {
    private static final String TAG = BatteryMeterView.class.getSimpleName();
    private static final boolean ENABLE_PERCENT = true;

    private static final String BATTERYMETER_COLOR_LEVELS = "batterymeter_color_levels";
    private static final String BATTERYMETER_COLOR_VALUES = "batterymeter_color_values";
    private static final String BATTERYMETER_FRAME_COLOR = "batterymeter_frame_color";
    private static final String BATTERYMETER_BOLT_COLOR = "batterymeter_bolt_color";
    private static final String BATTERYMETER_BOLT_POINTS = "batterymeter_bolt_points";
    private static final String STATUS_BAR_CLOCK_COLOR = "status_bar_clock_color";

    public static enum BatteryMeterMode {
        BATTERY_METER_GONE,
        BATTERY_METER_ICON_PORTRAIT,
        BATTERY_METER_ICON_LANDSCAPE,
        BATTERY_METER_CIRCLE,
        BATTERY_METER_TEXT
    }

    private final Runnable mInvalidate = new Runnable() {
        public void run() {
            invalidateIfVisible();
        }
    };

    private final Handler mHandler;

    protected BatteryMeterMode mMeterMode = null;
    protected boolean mShowPercent = false;
    protected boolean mAttached;

    private int mHeight;
    private int mWidth;

    final int[] mColors;
    final int[] mBoltPoints;
    final int mFrameColor;
    final int mBoltColor;
    final int mTextColor;

    private BatteryMeterDrawable mBatteryMeterDrawable;
    private final Object mLock = new Object();

    private Resources mResources;

    public BatteryMeterView(Context context, Resources res) {
        this(context, null, 0, res);
    }

    public BatteryMeterView(Context context, AttributeSet attrs, Resources res) {
        this(context, attrs, 0, res);
    }

    public BatteryMeterView(Context context, AttributeSet attrs, int defStyle, Resources res) {
        super(context, attrs, defStyle);
        mHandler = new Handler();

        TypedArray levels = res.obtainTypedArray(res.getIdentifier(BATTERYMETER_COLOR_LEVELS,
                "array", SYSTEMUI_PACKAGE));
        TypedArray colors = res.obtainTypedArray(res.getIdentifier(BATTERYMETER_COLOR_VALUES,
                "array", SYSTEMUI_PACKAGE));
        TypedArray boltPoints = res.obtainTypedArray(res.getIdentifier(BATTERYMETER_BOLT_POINTS,
                "array", SYSTEMUI_PACKAGE));

        mResources = res;

        int N = levels.length();
        mColors = new int[2*N];
        for (int i=0; i<N; i++) {
            mColors[2*i] = levels.getInt(i, 0);
            mColors[2*i+1] = colors.getColor(i, 0);
        }
        levels.recycle();
        colors.recycle();

        N = boltPoints.length();
        mBoltPoints = new int[N];
        for (int i = 0; i < N; i++) {
            mBoltPoints[i] = boltPoints.getInt(i, 0);
        }
        boltPoints.recycle();

        mShowPercent = ENABLE_PERCENT && 0 != Settings.System.getInt(
                context.getContentResolver(), "status_bar_show_battery_percent", 0);

        mMeterMode = BatteryMeterMode.BATTERY_METER_CIRCLE;
        mBatteryMeterDrawable = createBatteryMeterDrawable(mMeterMode);

        mFrameColor = res.getColor(res.getIdentifier(BATTERYMETER_FRAME_COLOR, "color",
                SYSTEMUI_PACKAGE));
        mBoltColor = res.getColor(res.getIdentifier(BATTERYMETER_BOLT_COLOR, "color",
                SYSTEMUI_PACKAGE));
        mTextColor = res.getColor(res.getIdentifier(STATUS_BAR_CLOCK_COLOR, "color",
                SYSTEMUI_PACKAGE));

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    protected BatteryMeterDrawable createBatteryMeterDrawable(BatteryMeterMode mode) {
        Resources res = mResources;
        switch (mode) {
            case BATTERY_METER_CIRCLE:
                return new CircleBatteryMeterDrawable(res);

            case BATTERY_METER_TEXT:
                return new TextBatteryMeterDrawable(res);

            case BATTERY_METER_ICON_LANDSCAPE:
                return new NormalBatteryMeterDrawable(res, true);

            default:
                return new NormalBatteryMeterDrawable(res, false);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mMeterMode == BatteryMeterMode.BATTERY_METER_CIRCLE) {
            height += (CircleBatteryMeterDrawable.STROKE_WITH / 3);
            width = height;
        } else if (mMeterMode == BatteryMeterMode.BATTERY_METER_TEXT) {
            width = (int)((TextBatteryMeterDrawable) mBatteryMeterDrawable).calculateMeasureWidth();
            onSizeChanged(width, height, 0, 0); // Force a size changed event
        } else if (mMeterMode.compareTo(BatteryMeterMode.BATTERY_METER_ICON_LANDSCAPE) == 0) {
            width = (int)(height * 1.2f);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mHeight = h;
        mWidth = w;
        synchronized (mLock) {
            if (mBatteryMeterDrawable != null) {
                mBatteryMeterDrawable.onSizeChanged(w, h, oldw, oldh);
            }
        }
    }

    protected void invalidateIfVisible() {
        if (getVisibility() == View.VISIBLE && mAttached) {
            if (mAttached) {
                postInvalidate();
            } else {
                invalidate();
            }
        }
    }

    public int getColorForLevel(int percent) {
        int thresh, color = 0;
        for (int i=0; i<mColors.length; i+=2) {
            thresh = mColors[i];
            color = mColors[i+1];
            if (percent <= thresh) return color;
        }
        return color;
    }

    public void setShowPercent(boolean show) {
        if (ENABLE_PERCENT) {
            mShowPercent = show;
            invalidateIfVisible();
        }
    }

    public void setMode(BatteryMeterMode mode) {
        if (mMeterMode == mode) {
            return;
        }

        mMeterMode = mode;
        if (mode == BatteryMeterMode.BATTERY_METER_GONE) {
            setVisibility(View.GONE);
            synchronized (mLock) {
                mBatteryMeterDrawable = null;
            }
        } else {
            synchronized (mLock) {
                if (mBatteryMeterDrawable != null) {
                    mBatteryMeterDrawable.onDispose();
                }
                mBatteryMeterDrawable = createBatteryMeterDrawable(mode);
            }
            if (mMeterMode == BatteryMeterMode.BATTERY_METER_ICON_PORTRAIT ||
                    mMeterMode == BatteryMeterMode.BATTERY_METER_ICON_LANDSCAPE) {
                ((NormalBatteryMeterDrawable)mBatteryMeterDrawable).loadBoltPoints(
                        mContext.getResources());
            }
            setVisibility(View.VISIBLE);
            postInvalidate();
            requestLayout();
        }
    }

    @Override
    public void draw(Canvas c) {
        synchronized (mLock) {
            if (mBatteryMeterDrawable != null) {
                mBatteryMeterDrawable.onDraw(c);
            }
        }
    }

    protected interface BatteryMeterDrawable {
        void onDraw(Canvas c);
        void onSizeChanged(int w, int h, int oldw, int oldh);
        void onDispose();
    }

    protected class NormalBatteryMeterDrawable implements BatteryMeterDrawable {

        public static final boolean SINGLE_DIGIT_PERCENT = false;

        public static final int FULL = 96;
        public static final int EMPTY = 4;

        public static final float SUBPIXEL = 0.4f;  // inset rects for softer edges

        private boolean mDisposed;

        protected final boolean mHorizontal;

        private Paint mFramePaint, mBatteryPaint, mWarningTextPaint, mTextPaint, mBoltPaint;
        private int mButtonHeight;
        private float mTextHeight;

        private final float[] mBoltPoints;
        private final Path mBoltPath = new Path();

        private final RectF mFrame = new RectF();
        private final RectF mButtonFrame = new RectF();
        private final RectF mClipFrame = new RectF();
        private final RectF mBoltFrame = new RectF();

        public NormalBatteryMeterDrawable(Resources res, boolean horizontal) {
            super();
            mHorizontal = horizontal;
            mDisposed = false;

            mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mFramePaint.setColor(mFrameColor);
            mFramePaint.setDither(true);
            mFramePaint.setStrokeWidth(0);
            mFramePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mFramePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));

            mBatteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBatteryPaint.setDither(true);
            mBatteryPaint.setStrokeWidth(0);
            mBatteryPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setColor(mBoltColor);
            Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
            mTextPaint.setTypeface(font);
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mWarningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mWarningTextPaint.setColor(mColors[1]);
            font = Typeface.create("sans-serif", Typeface.BOLD);
            mWarningTextPaint.setTypeface(font);
            mWarningTextPaint.setTextAlign(Paint.Align.CENTER);

            mBoltPaint = new Paint();
            mBoltPaint.setAntiAlias(true);
            mBoltPaint.setColor(mFrameColor);
            mBoltPoints = loadBoltPoints(res);
        }

        @Override
        public void onDraw(Canvas c) {
            if (mDisposed) return;

            final int level = 50;

            float drawFrac = (float) level / 100f;
            final int pt = getPaddingTop() + (mHorizontal ? (int)(mHeight * 0.20f) : 0);
            final int pl = getPaddingLeft();
            final int pr = getPaddingRight();
            final int pb = getPaddingBottom();
            int height = mHeight - pt - pb;
            int width = mWidth - pl - pr;

            mButtonHeight = (int) ((mHorizontal ? width : height) * 0.12f);

            mFrame.set(0, 0, width, height);
            mFrame.offset(pl, pt);

            if (mHorizontal) {
                mButtonFrame.set(
                        /*cover frame border of intersecting area*/
                        width - (mButtonHeight + 5) - mFrame.left,
                        mFrame.top + height * 0.25f,
                        mFrame.right,
                        mFrame.bottom - height * 0.25f);

                mButtonFrame.top += SUBPIXEL;
                mButtonFrame.bottom -= SUBPIXEL;
                mButtonFrame.right -= SUBPIXEL;
            } else {
                mButtonFrame.set(
                        mFrame.left + width * 0.25f,
                        mFrame.top,
                        mFrame.right - width * 0.25f,
                        mFrame.top + mButtonHeight + 5 /*cover frame border of intersecting area*/);

                mButtonFrame.top += SUBPIXEL;
                mButtonFrame.left += SUBPIXEL;
                mButtonFrame.right -= SUBPIXEL;
            }

            if (mHorizontal) {
                mFrame.right -= mButtonHeight;
            } else {
                mFrame.top += mButtonHeight;
            }
            mFrame.left += SUBPIXEL;
            mFrame.top += SUBPIXEL;
            mFrame.right -= SUBPIXEL;
            mFrame.bottom -= SUBPIXEL;

            // first, draw the battery shape
            c.drawRect(mFrame, mFramePaint);

            // fill 'er up
            final int color = getColorForLevel(level);
            mBatteryPaint.setColor(color);

            if (level >= FULL) {
                drawFrac = 1f;
            } else if (level <= EMPTY) {
                drawFrac = 0f;
            }

            c.drawRect(mButtonFrame, drawFrac == 1f ? mBatteryPaint : mFramePaint);

            mClipFrame.set(mFrame);
            if (mHorizontal) {
                mClipFrame.right -= (mFrame.width() * (1f - drawFrac));
            } else {
                mClipFrame.top += (mFrame.height() * (1f - drawFrac));
            }

            c.save(Canvas.CLIP_SAVE_FLAG);
            c.clipRect(mClipFrame);
            c.drawRect(mFrame, mBatteryPaint);
            c.restore();

                // draw the bolt
                final float bl = (int)(mFrame.left + mFrame.width() / (mHorizontal ? 9f : 4.5f));
                final float bt = (int)(mFrame.top + mFrame.height() / (mHorizontal ? 4.5f : 6f));
                final float br = (int)(mFrame.right - mFrame.width() / (mHorizontal ? 6f : 7f));
                final float bb = (int)(mFrame.bottom - mFrame.height() / (mHorizontal ? 7f : 10f));
                if (mBoltFrame.left != bl || mBoltFrame.top != bt
                        || mBoltFrame.right != br || mBoltFrame.bottom != bb) {
                    mBoltFrame.set(bl, bt, br, bb);
                    mBoltPath.reset();
                    mBoltPath.moveTo(
                            mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                    for (int i = 2; i < mBoltPoints.length; i += 2) {
                        mBoltPath.lineTo(
                                mBoltFrame.left + mBoltPoints[i] * mBoltFrame.width(),
                                mBoltFrame.top + mBoltPoints[i + 1] * mBoltFrame.height());
                    }
                    mBoltPath.lineTo(
                            mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                }
                c.drawPath(mBoltPath, mBoltPaint);
            if (mShowPercent) {
                final float nofull = mHorizontal ? 0.75f : 0.6f;
                final float single = mHorizontal ? 0.86f : 0.75f;
                mTextPaint.setTextSize(height *
                        (SINGLE_DIGIT_PERCENT ? single
                                : nofull));
                mTextHeight = -mTextPaint.getFontMetrics().ascent;

                final String str = String.valueOf(SINGLE_DIGIT_PERCENT ? (level/10) : level);
                final float x  = mWidth * 0.5f;
                final float y = pt + (height + mTextHeight) * 0.47f;

                c.drawText(str, x, y, mTextPaint);
            }
        }

        @Override
        public void onDispose() {
            mHandler.removeCallbacks(mInvalidate);
            mDisposed = true;
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            mWarningTextPaint.setTextSize(h * 0.75f);
        }

        private float[] loadBoltPoints(Resources res) {
            final int[] pts = BatteryMeterView.this.mBoltPoints;
            int maxX = 0, maxY = 0;
            for (int i = 0; i < pts.length; i += 2) {
                maxX = Math.max(maxX, pts[i]);
                maxY = Math.max(maxY, pts[i + 1]);
            }
            final float[] ptsF = new float[pts.length];
            for (int i = 0; i < pts.length; i += 2) {
                ptsF[i] = (float)pts[i] / maxX;
                ptsF[i + 1] = (float)pts[i + 1] / maxY;
            }
            return ptsF;
        }
    }

    protected class CircleBatteryMeterDrawable implements BatteryMeterDrawable {

        public static final float STROKE_WITH = 6.5f;

        private boolean mDisposed;

        private int     mAnimOffset;
        private boolean mIsAnimating;   // stores charge-animation status to reliably
                                        //remove callbacks

        private int     mCircleSize;    // draw size of circle
        private RectF   mRectLeft;      // contains the precalculated rect used in drawArc(),
                                        // derived from mCircleSize
        private float   mTextX, mTextY; // precalculated position for drawText() to appear centered

        private Paint   mTextPaint;
        private Paint   mFrontPaint;
        private Paint   mBackPaint;
        private Paint   mBoltPaint;

        private final RectF mBoltFrame = new RectF();
        private final float[] mBoltPoints;
        private final Path mBoltPath = new Path();

        public CircleBatteryMeterDrawable(Resources res) {
            super();
            mDisposed = false;

            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setColor(mTextColor);
            Typeface font = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            mTextPaint.setTypeface(font);
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mFrontPaint.setStrokeCap(Paint.Cap.BUTT);
            mFrontPaint.setDither(true);
            mFrontPaint.setStrokeWidth(0);
            mFrontPaint.setStyle(Paint.Style.STROKE);

            mBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBackPaint.setColor(mFrameColor);
            mBackPaint.setStrokeCap(Paint.Cap.BUTT);
            mBackPaint.setDither(true);
            mBackPaint.setStrokeWidth(0);
            mBackPaint.setStyle(Paint.Style.STROKE);
            mBackPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));

            mBoltPaint = new Paint();
            mBoltPaint.setAntiAlias(true);
            mBoltPaint.setColor(getColorForLevel(50));
            mBoltPoints = loadBoltPoints(res);
        }

        @Override
        public void onDraw(Canvas c) {
            if (mDisposed) return;

            if (mRectLeft == null) {
                initSizeBasedStuff();
            }

            drawCircle(c, mTextX, mRectLeft);
        }

        @Override
        public void onDispose() {
            mHandler.removeCallbacks(mInvalidate);
            mDisposed = true;
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            initSizeBasedStuff();
        }

        private float[] loadBoltPoints(Resources res) {
            final int[] pts = BatteryMeterView.this.mBoltPoints;
            int maxX = 0, maxY = 0;
            for (int i = 0; i < pts.length; i += 2) {
                maxX = Math.max(maxX, pts[i]);
                maxY = Math.max(maxY, pts[i + 1]);
            }
            final float[] ptsF = new float[pts.length];
            for (int i = 0; i < pts.length; i += 2) {
                ptsF[i] = (float)pts[i] / maxX;
                ptsF[i + 1] = (float)pts[i + 1] / maxY;
            }
            return ptsF;
        }

        private void drawCircle(Canvas canvas, float textX, RectF drawRect) {
            int level = 50;
            Paint paint;

            paint = mFrontPaint;
            paint.setColor(getColorForLevel(level));

            // draw thin gray ring first
            canvas.drawArc(drawRect, 270, 360, false, mBackPaint);
            // draw colored arc representing charge level
            canvas.drawArc(drawRect, 270 + mAnimOffset, 3.6f * level, false, paint);
            // if chosen by options, draw percentage text in the middle
            // always skip percentage when 100, so layout doesnt break

            // draw the bolt
            final float bl = (int)(drawRect.left + drawRect.width() / 3.2f);
            final float bt = (int)(drawRect.top + drawRect.height() / 4f);
            final float br = (int)(drawRect.right - drawRect.width() / 5.2f);
            final float bb = (int)(drawRect.bottom - drawRect.height() / 8f);
            if (mBoltFrame.left != bl || mBoltFrame.top != bt
                    || mBoltFrame.right != br || mBoltFrame.bottom != bb) {
                mBoltFrame.set(bl, bt, br, bb);
                mBoltPath.reset();
                mBoltPath.moveTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                for (int i = 2; i < mBoltPoints.length; i += 2) {
                    mBoltPath.lineTo(
                            mBoltFrame.left + mBoltPoints[i] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[i + 1] * mBoltFrame.height());
                }
                mBoltPath.lineTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
            }
            canvas.drawPath(mBoltPath, mBoltPaint);
        }

        /**
         * initializes all size dependent variables
         * sets stroke width and text size of all involved paints
         * YES! i think the method name is appropriate
         */
        private void initSizeBasedStuff() {
            mCircleSize = Math.min(getMeasuredWidth(), getMeasuredHeight());
            mTextPaint.setTextSize(mCircleSize / 2f);

            float strokeWidth = mCircleSize / STROKE_WITH;
            mFrontPaint.setStrokeWidth(strokeWidth);
            mBackPaint.setStrokeWidth(strokeWidth);

            // calculate rectangle for drawArc calls
            int pLeft = getPaddingLeft();
            mRectLeft = new RectF(pLeft + strokeWidth / 2.0f, 0 + strokeWidth / 2.0f, mCircleSize
                    - strokeWidth / 2.0f + pLeft, mCircleSize - strokeWidth / 2.0f);

            // calculate Y position for text
            Rect bounds = new Rect();
            mTextPaint.getTextBounds("99", 0, "99".length(), bounds);
            mTextX = mCircleSize / 2.0f + getPaddingLeft();
            // the +1dp at end of formula balances out rounding issues.works out on all resolutions
            mTextY = mCircleSize / 2.0f + (bounds.bottom - bounds.top) / 2.0f
                    - strokeWidth / 2.0f + getResources().getDisplayMetrics().density;
        }
    }

    protected class TextBatteryMeterDrawable implements BatteryMeterDrawable {

        private static final boolean DRAW_LEVEL = false;

        public static final int FULL = 96;
        public static final int EMPTY = 4;

        private boolean mDisposed;

        private float mTextX;
        private float mTextY;

        private boolean mOldPlugged = false;
        private int mOldLevel = -1;

        private boolean mIsAnimating;
        private int mAnimOffset;

        private Paint mBackPaint;
        private Paint mFrontPaint;

        public TextBatteryMeterDrawable(Resources res) {
            super();
            mDisposed = false;
            mIsAnimating = false;

            DisplayMetrics dm = res.getDisplayMetrics();

            mBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBackPaint.setTextAlign(Paint.Align.RIGHT);
            mBackPaint.setColor(mFrameColor);
            mBackPaint.setTextSize(16.0f * dm.density);

            mFrontPaint = new Paint(mBackPaint);
        }

        @Override
        public void onDraw(Canvas c) {
            if (mDisposed) return;

            int level = 50;
            boolean plugged = true;

            if (mOldLevel != level || mOldPlugged != plugged) {
                mOldLevel = level;
                mOldPlugged = plugged;

                postInvalidate();
                requestLayout();
                return;
            }

            mFrontPaint.setColor(getColorForLevel(level));

            // Is plugged? Then use the animation status
            drawWithLevel(c, mAnimOffset, getLevel(level));
        }

        private void drawWithLevel(Canvas c, int level, String levelTxt) {
            Rect bounds = getBounds(level);

            // Draw the background
            c.drawText(levelTxt, mTextX, mTextY, mBackPaint);

            // Draw the foreground
            c.save();
            c.clipRect(0.0f, mTextY - ((level * bounds.height()) / 100.0f), mTextX, mTextY);
            c.drawText(levelTxt, mTextX, mTextY, mFrontPaint);
            c.restore();
        }

        private void drawWithoutLevel(Canvas c, String levelTxt) {
            // We need to draw the overlay back paint to get the proper color
            c.drawText(levelTxt, mTextX, mTextY, mBackPaint);
            c.drawText(levelTxt, mTextX, mTextY, mFrontPaint);
        }

        @Override
        public void onDispose() {
            mHandler.removeCallbacks(mInvalidate);
            mDisposed = true;
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            Rect bounds = getBounds(50);
            float onedp = mContext.getResources().getDisplayMetrics().density * 0.5f;
            float height = h - getPaddingBottom() - getPaddingTop();

            mTextX = w;
            mTextY = h - getPaddingBottom() - (height / 2 - bounds.height() /2) + onedp;
        }

        protected float calculateMeasureWidth() {
            Rect bounds = getBounds(50);
            float onedp = mContext.getResources().getDisplayMetrics().density;
            return bounds.width() + getPaddingStart() + getPaddingEnd() + onedp;
        }

        private Rect getBounds(int level) {
            Rect bounds = new Rect();
            String levelTxt = getLevel(level);
            mBackPaint.getTextBounds(levelTxt, 0, levelTxt.length(), bounds);
            return bounds;
        }

        private String getLevel(int level) {
            if (level == -1) {
                return String.format("?", level);
            }
            return String.format("%s%%", level);
        }

        private void resetChargeAnimation() {
            if (mIsAnimating) {
                mIsAnimating = false;
                mAnimOffset = 0;
                mHandler.removeCallbacks(mInvalidate);
            }
        }
    }
}

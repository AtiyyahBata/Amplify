package com.amplify.app.ui.content;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * An ImageView you can pinch to zoom, drag around, and double-tap to zoom in or out.
 * It always starts fitted to the screen, so portrait and landscape pictures both
 * show in full.
 */
public class ZoomImageView extends AppCompatImageView {

    private static final float MAX_ZOOM = 5f;

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final RectF rect = new RectF();

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector tapDetector;

    private float baseScale = 1f;
    private float lastX;
    private float lastY;
    private int lastPointerCount = 0;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        zoomBy(
                                detector.getScaleFactor(),
                                detector.getFocusX(),
                                detector.getFocusY()
                        );
                        return true;
                    }
                }
        );

        tapDetector = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (currentScale() > baseScale * 1.05f) {
                            fitToView();
                        } else {
                            zoomBy(2.5f, e.getX(), e.getY());
                        }
                        return true;
                    }
                }
        );
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        fitToView();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    /** Shows the whole picture, centred. */
    private void fitToView() {
        Drawable drawable = getDrawable();
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        if (drawable == null || viewWidth == 0 || viewHeight == 0) {
            return;
        }

        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();

        if (drawableWidth <= 0 || drawableHeight <= 0) {
            return;
        }

        baseScale = Math.min(
                (float) viewWidth / drawableWidth,
                (float) viewHeight / drawableHeight
        );

        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate(
                (viewWidth - drawableWidth * baseScale) / 2f,
                (viewHeight - drawableHeight * baseScale) / 2f
        );
        setImageMatrix(matrix);
    }

    private float currentScale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        float current = currentScale();
        float target = Math.max(
                baseScale,
                Math.min(current * factor, baseScale * MAX_ZOOM)
        );
        float change = target / current;

        matrix.postScale(change, change, focusX, focusY);
        keepInside();
        setImageMatrix(matrix);
    }

    /** Stops the picture being dragged off screen, and centres it when smaller than the screen. */
    private void keepInside() {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        rect.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(rect);

        float dx = 0;
        float dy = 0;

        if (rect.width() <= getWidth()) {
            dx = (getWidth() - rect.width()) / 2f - rect.left;
        } else if (rect.left > 0) {
            dx = -rect.left;
        } else if (rect.right < getWidth()) {
            dx = getWidth() - rect.right;
        }

        if (rect.height() <= getHeight()) {
            dy = (getHeight() - rect.height()) / 2f - rect.top;
        } else if (rect.top > 0) {
            dy = -rect.top;
        } else if (rect.bottom < getHeight()) {
            dy = getHeight() - rect.bottom;
        }

        matrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        tapDetector.onTouchEvent(event);

        if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (lastPointerCount == 1
                            && currentScale() > baseScale * 1.01f) {
                        matrix.postTranslate(
                                event.getX() - lastX,
                                event.getY() - lastY
                        );
                        keepInside();
                        setImageMatrix(matrix);
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    break;

                default:
                    break;
            }
        }

        lastPointerCount = event.getPointerCount();
        return true;
    }
}

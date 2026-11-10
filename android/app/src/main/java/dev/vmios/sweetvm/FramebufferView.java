package dev.vmios.sweetvm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.nio.ByteBuffer;

/** Draws the 1125x2436 guest panel, letterboxed, from a double buffer. */
public final class FramebufferView extends View {
    private final Object lock = new Object();
    private Bitmap front;
    private Bitmap back;
    private boolean hasFrame;
    // Draw at the bitmap's intrinsic pixel size; never resample the guest image.
    private final Paint paint = new Paint(0);
    {
        // The guest framebuffer's byte order is RGBA while Android ARGB_8888
        // consumes BGRA bytes on little-endian devices. Swap only R/B at the
        // presentation stage; leave guest RAM and guest texture decoding intact.
        ColorMatrix rbSwap = new ColorMatrix(new float[] {
                0, 0, 1, 0, 0,
                0, 1, 0, 0, 0,
                1, 0, 0, 0, 0,
                0, 0, 0, 1, 0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(rbSwap));
    }

    public interface TouchListener {
        /** type: 0 up, 1 down, 2 move; x/y in the 1125x2436 guest panel. */
        void onGuestTouch(int type, int x, int y);
    }

    private TouchListener touchListener;

    public void setTouchListener(TouchListener l) {
        touchListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return true;
        }
        // The guest image is centered at native pixel size: one Android
        // canvas pixel maps to one guest framebuffer pixel.
        int left = (w - Payload.FB_W) / 2;
        int top = (h - Payload.FB_H) / 2;
        float gx = e.getX() - left;
        float gy = e.getY() - top;
        if (gx < 0 || gy < 0 || gx >= Payload.FB_W || gy >= Payload.FB_H) {
            return true;
        }
        int type;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                type = 1;
                break;
            case MotionEvent.ACTION_MOVE:
                type = 2;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                type = 0;
                break;
            default:
                return true;
        }
        TouchListener l = touchListener;
        if (l != null) {
            l.onGuestTouch(type, (int) gx, (int) gy);
        }
        return true;
    }

    public FramebufferView(Context c) {
        this(c, null);
    }

    public FramebufferView(Context c, AttributeSet a) {
        super(c, a);
        setBackgroundColor(Color.BLACK);
        front = Bitmap.createBitmap(Payload.FB_W, Payload.FB_H, Bitmap.Config.ARGB_8888);
        back = Bitmap.createBitmap(Payload.FB_W, Payload.FB_H, Bitmap.Config.ARGB_8888);
        front.setHasAlpha(false);
        back.setHasAlpha(false);
    }

    /** Called on the VM reader thread. */
    public void copyFromBuffer(ByteBuffer buffer) {
        buffer.position(0);
        buffer.limit(Payload.FB_STRIDE * Payload.FB_H);
        back.copyPixelsFromBuffer(buffer);
        back.setHasAlpha(false);
        synchronized (lock) {
            Bitmap t = front;
            front = back;
            back = t;
            hasFrame = true;
        }
        postInvalidate();
    }

    public boolean hasFrame() {
        synchronized (lock) {
            return hasFrame;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(Color.BLACK);
        synchronized (lock) {
            if (front == null) {
                return;
            }
            // Exact 1:1 presentation: never scale the 1125x2436 guest panel.
            // The remaining physical display area is opaque black.
            int left = (w - Payload.FB_W) / 2;
            int top = (h - Payload.FB_H) / 2;
            // x/y overload preserves the bitmap's intrinsic width and height.
            canvas.drawBitmap(front, left, top, paint);
        }
    }
}

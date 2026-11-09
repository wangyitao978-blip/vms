package dev.vmios.sweetvm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.nio.ByteBuffer;

/** Draws the guest framebuffer 1:1 in physical display pixels, centered. */
public final class FramebufferView extends View {
    private final Object lock = new Object();
    private Bitmap front;
    private Bitmap back;
    private boolean hasFrame;
    private boolean touchActive;
    // No filtering: one guest framebuffer pixel maps to one Android canvas pixel.
    private final Paint paint = new Paint(0);

    public interface TouchListener {
        /** type: 0 up, 1 down, 2 move; x/y are native guest framebuffer pixels. */
        void onGuestTouch(int type, int x, int y);
    }

    private TouchListener touchListener;

    public void setTouchListener(TouchListener l) {
        touchListener = l;
    }

    private int imageLeft() {
        return (getWidth() - Payload.FB_W) / 2;
    }

    private int imageTop() {
        return (getHeight() - Payload.FB_H) / 2;
    }

    private boolean insideImage(float x, float y) {
        return x >= imageLeft() && y >= imageTop()
                && x < imageLeft() + Payload.FB_W
                && y < imageTop() + Payload.FB_H;
    }

    private int clampX(float x) {
        return Math.max(0, Math.min(Payload.FB_W - 1, (int) x));
    }

    private int clampY(float y) {
        return Math.max(0, Math.min(Payload.FB_H - 1, (int) y));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() - imageLeft();
        float y = e.getY() - imageTop();
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!insideImage(e.getX(), e.getY())) {
                touchActive = false;
                return true;
            }
            touchActive = true;
            sendTouch(1, clampX(x), clampY(y));
            return true;
        }
        if (!touchActive) {
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            sendTouch(2, clampX(x), clampY(y));
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            sendTouch(0, clampX(x), clampY(y));
            touchActive = false;
        }
        return true;
    }

    private void sendTouch(int type, int x, int y) {
        TouchListener l = touchListener;
        if (l != null) {
            l.onGuestTouch(type, x, y);
        }
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
        canvas.drawColor(Color.BLACK);
        synchronized (lock) {
            if (front == null) {
                return;
            }
            float left = imageLeft();
            float top = imageTop();
            // Draw without a destination rectangle so Android cannot rescale
            // or interpolate the guest pixels.
            canvas.drawBitmap(front, left, top, paint);
        }
    }
}

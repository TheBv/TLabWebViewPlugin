package com.tlab.viewtobuffer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

@SuppressLint("ViewConstructor")
public class ViewToBufferLayout extends LinearLayout {

    private static final String TAG = "ViewToBufferLayout";
    private static final boolean DEBUG_RENDER = true;
    private final ViewToBufferRenderer mRenderer;

    public ViewToBufferLayout(Context context, ViewToBufferRenderer renderer) {
        super(context);
        mRenderer = renderer;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mRenderer == null) return;

        Canvas target = mRenderer.onDrawViewBegin();

        if (target != null) {
            View child = getChildCount() > 0 ? getChildAt(0) : null;
            if (DEBUG_RENDER && child != null) {
                int scrollX = child.getScrollX();
                int scrollY = child.getScrollY();
                if (scrollX != 0 || scrollY != 0) {
                    Log.d(TAG, "draw() - child scroll=(" + scrollX + "," + scrollY + ")" +
                          " | layout size=(" + getWidth() + "," + getHeight() + ")" +
                          " | child size=(" + child.getWidth() + "," + child.getHeight() + ")");
                }
            }
            super.draw(target);
        }

        mRenderer.onDrawViewEnd();
    }
}

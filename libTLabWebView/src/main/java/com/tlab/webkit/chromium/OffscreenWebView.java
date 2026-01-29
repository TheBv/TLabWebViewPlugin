package com.tlab.webkit.chromium;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Custom WebView for offscreen rendering.
 * 
 * The parent layout (ViewToBufferLayout/ViewToSurfaceLayout) handles scroll compensation
 * by tracking the "target" scroll position vs the actual View scroll position.
 * Any unwanted scroll (e.g., from input focus) is compensated during rendering.
 */
public class OffscreenWebView extends WebView {

    private static final String TAG = "OffscreenWebView";
    private static final boolean DEBUG_SCROLL = true;

    public OffscreenWebView(Context context) {
        super(context);
        setupScrollReset();
    }

    public OffscreenWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupScrollReset();
    }

    public OffscreenWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupScrollReset();
    }
    
    /**
     * Set up WebViewClient to reset scroll position on page loads
     */
    private void setupScrollReset() {
        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (DEBUG_SCROLL) {
                    Log.d(TAG, "onPageStarted - resetting stable scroll position to 0");
                }
                mStableScrollY = 0;
                super.onPageStarted(view, url, favicon);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                if (DEBUG_SCROLL) {
                    Log.d(TAG, "onPageFinished - ensuring stable scroll position is 0");
                }
                mStableScrollY = 0;
                super.onPageFinished(view, url);
            }
        });
    }
    
    // Track programmatic scrolling to distinguish user scrolling from focus-induced scrolling
    private boolean mIsProgrammaticScroll = false;
    private long mProgrammaticScrollEndTime = 0;
    private static final long PROGRAMMATIC_SCROLL_WINDOW_MS = 200; // Allow scroll events for 200ms after programmatic scroll
    
    // Track when we're reverting to prevent feedback loop
    private boolean mIsReverting = false;
    private int mStableScrollY = 0; // The last known good scroll position
    
    /**
     * Call this before initiating a programmatic scroll (from Unity buttons)
     */
    public void beginProgrammaticScroll() {
        mIsProgrammaticScroll = true;
        mProgrammaticScrollEndTime = System.currentTimeMillis() + PROGRAMMATIC_SCROLL_WINDOW_MS;
        if (DEBUG_SCROLL) {
            Log.d(TAG, "beginProgrammaticScroll");
        }
    }
    
    /**
     * Reset the stable scroll position (call when loading new content)
     */
    public void resetScrollPosition() {
        if (DEBUG_SCROLL) {
            Log.d(TAG, "resetScrollPosition - resetting stable scroll to current position: " + getScrollY());
        }
        mStableScrollY = getScrollY();
        mIsProgrammaticScroll = false; // Clear any pending programmatic scroll
    }
    
    /**
     * Check if we're within the programmatic scroll window
     */
    private boolean isInProgrammaticScrollWindow() {
        if (mIsProgrammaticScroll) {
            if (System.currentTimeMillis() < mProgrammaticScrollEndTime) {
                return true;
            } else {
                mIsProgrammaticScroll = false;
            }
        }
        return false;
    }
    
    // NOTE: We intentionally do NOT enable scroll on touch events.
    // The user scrolls via Unity buttons (ScrollBy/ScrollTo), not via touch.
    // Touch is only for clicking/tapping elements.
    // If touch-based scrolling is ever needed in the future, uncomment this:
    /*
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        
        if (action == MotionEvent.ACTION_DOWN) {
            mIsProgrammaticScroll = true;
            mProgrammaticScrollEndTime = System.currentTimeMillis() + 1000;
            if (DEBUG_SCROLL) {
                Log.d(TAG, "Touch DOWN - enabling scroll");
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mProgrammaticScrollEndTime = System.currentTimeMillis() + 500;
            if (DEBUG_SCROLL) {
                Log.d(TAG, "Touch UP - extending scroll window");
            }
        }
        
        return super.dispatchTouchEvent(event);
    }
    */
    
    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        int deltaY = t - oldt;
        int absDeltaY = Math.abs(deltaY);
        
        // If we're in the middle of reverting, ignore this callback
        if (mIsReverting) {
            if (DEBUG_SCROLL) {
                Log.d(TAG, "onScrollChanged during revert (ignored): " + oldt + " -> " + t);
            }
            super.onScrollChanged(l, t, oldl, oldt);
            return;
        }
        
        boolean allowScroll = isInProgrammaticScrollWindow();
        
        if (DEBUG_SCROLL) {
            if (absDeltaY > 5) {
                Log.d(TAG, "onScrollChanged: (" + oldl + "," + oldt + ") -> (" + l + "," + t + ")" +
                      " | delta=" + deltaY +
                      " | allowScroll=" + allowScroll +
                      " | stableY=" + mStableScrollY +
                      " | contentSize=(" + computeHorizontalScrollRange() + "," + computeVerticalScrollRange() + ")" +
                      " | viewSize=(" + getWidth() + "," + getHeight() + ")");
            }
        }
        
        // If in programmatic scroll window, accept the scroll and update stable position
        if (allowScroll) {
            mStableScrollY = t;
            super.onScrollChanged(l, t, oldl, oldt);
            return;
        }
        
        // If not in programmatic scroll window, revert ANY scroll (no matter how small)
        // But if the scroll went to 0 and we're far from 0, this might be a page reload
        if (absDeltaY > 0) {
            // If we scrolled to position 0 and we were far away, this is likely a page reset
            if (t == 0 && mStableScrollY > 100) {
                if (DEBUG_SCROLL) {
                    Log.d(TAG, "Detected scroll to 0 from " + mStableScrollY + " - accepting as page reset");
                }
                mStableScrollY = 0;
                super.onScrollChanged(l, t, oldl, oldt);
                return;
            }
            
            if (DEBUG_SCROLL) {
                Log.d(TAG, "REVERTING non-programmatic scroll from " + t + " back to " + mStableScrollY);
            }
            mIsReverting = true;
            scrollTo(l, mStableScrollY);
            mIsReverting = false;
            return;
        }
        
        // No scroll change, just pass through
        super.onScrollChanged(l, t, oldl, oldt);
    }
    
    /**
     * Override to prevent focus-induced scrolling - return false to not handle it
     */
    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (DEBUG_SCROLL) {
            Log.d(TAG, "requestChildRectangleOnScreen BLOCKED - rect=" + rectangle + 
                  " | currentScroll=(" + getScrollX() + "," + getScrollY() + ")");
        }
        return false; // Don't scroll
    }
    
    /**
     * Override to prevent focus-induced scrolling - return false to not handle it
     */
    @Override
    public boolean requestRectangleOnScreen(Rect rectangle) {
        if (DEBUG_SCROLL) {
            Log.d(TAG, "requestRectangleOnScreen BLOCKED");
        }
        return false; // Don't scroll
    }
    
    /**
     * Block fling scrolls which can cause unexpected movement
     */
    @Override
    public void flingScroll(int vx, int vy) {
        if (DEBUG_SCROLL) {
            Log.d(TAG, "flingScroll blocked: (" + vx + "," + vy + ")");
        }
        // Don't fling - could cause unexpected jumps
    }
}

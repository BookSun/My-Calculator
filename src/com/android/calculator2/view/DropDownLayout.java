package com.android.calculator2.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Scroller;

/**
 * Created by chyang on 15-6-12.
 */
public class DropDownLayout extends RelativeLayout{

    private ViewGroup historyLayout;
    private ViewGroup calculatorLayout;
    private Scroller mScroller;

    public DropDownLayout(Context context) {
        super(context);
        mScroller = new Scroller(context);
    }

    public DropDownLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroller = new Scroller(context);
    }

    public DropDownLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mScroller = new Scroller(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getChildCount() == 2) {
            historyLayout = (ViewGroup)getChildAt(0);
            calculatorLayout = (ViewGroup)getChildAt(1);
            historyLayout.measure(widthMeasureSpec, heightMeasureSpec);
            calculatorLayout.measure(widthMeasureSpec, heightMeasureSpec);
        } else {
            new RuntimeException("请添加二个布局");
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        calculatorLayout.layout(0, 0, calculatorLayout.getMeasuredWidth(), calculatorLayout.getMeasuredHeight());
        historyLayout.layout(0, -historyLayout.getChildAt(0).getHeight(), historyLayout.getMeasuredWidth(), historyLayout.getMeasuredHeight());
    }


    public void hiedView() {
        setDrawingCacheEnabled(true);
        final int newX = -1 * (getHeight()-(150));
        setScrollY(-calculatorLayout.getChildAt(0).getHeight());
        final int delta = newX - getScrollY();
        mScroller.startScroll(0, getScrollY(), 0, delta, Math.abs(delta)+300);
        invalidate();
    }

    public void showView() {
        setDrawingCacheEnabled(true);
        final int newX = 0 ;
        final int delta = newX - getScrollY();
        mScroller.startScroll(0, getScrollY(), 0, delta, Math.abs(delta)+300);
        invalidate();
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset() && !mScroller.isFinished()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }
}

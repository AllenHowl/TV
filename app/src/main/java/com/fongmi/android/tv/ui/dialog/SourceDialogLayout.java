package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

/** A bounded source viewport with a scrolling header for short screens and large fonts. */
public final class SourceDialogLayout extends LinearLayout {
    private final HeaderScrollView header;
    private final View footer;
    private final int minimumListHeight;

    public SourceDialogLayout(Context context, View controls, RecyclerView list, View footer, int minimumListHeight) {
        super(context);
        setOrientation(VERTICAL);
        this.footer = footer;
        this.minimumListHeight = minimumListHeight;
        header = new HeaderScrollView(context);
        header.addView(controls);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        addView(footer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int height = MeasureSpec.getSize(heightMeasureSpec);
            measureChildWithMargins(footer, widthMeasureSpec, 0, heightMeasureSpec, 0);
            header.maximumHeight = Math.max(0, height - getPaddingTop() - getPaddingBottom() - footer.getMeasuredHeight() - minimumListHeight);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static final class HeaderScrollView extends ScrollView {
        int maximumHeight = Integer.MAX_VALUE;
        HeaderScrollView(Context context) { super(context); }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? maximumHeight : Math.min(maximumHeight, MeasureSpec.getSize(heightMeasureSpec));
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        }
    }
}

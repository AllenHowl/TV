package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class, qualifiers = "zh-rCN-w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class SourceDialogLayoutTest {
    public static class Host extends FragmentActivity {
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.Theme_App);
            super.onCreate(state);
        }
    }

    private Dialog open(int type) {
        Host host = Robolectric.buildActivity(Host.class).setup().get();
        SourceDialog.show(host, type);
        host.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
        return ((SourceDialog) host.getSupportFragmentManager().findFragmentByTag("source_manager")).requireDialog();
    }

    private void layout(Dialog dialog) {
        View decor = dialog.getWindow().getDecorView();
        int width = dialog.getWindow().getAttributes().width;
        int height = dialog.getWindow().getAttributes().height;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private RecyclerView list(View view) {
        if (view instanceof RecyclerView) return (RecyclerView) view;
        if (view instanceof ViewGroup group) for (int i=0; i<group.getChildCount(); i++) {
            RecyclerView found = list(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private Button button(View view, int text) {
        if (view instanceof Button b && b.getText().toString().equals(view.getContext().getString(text))) return b;
        if (view instanceof ViewGroup group) for (int i=0; i<group.getChildCount(); i++) {
            Button found = button(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private RecyclerView assertVisible(Dialog dialog) {
        layout(dialog);
        View decor = dialog.getWindow().getDecorView();
        RecyclerView list = list(decor);
        assertNotNull(list);
        assertTrue("Source list height was " + list.getHeight(), list.getHeight() >= 76);
        assertTrue("No source rows were laid out", list.getChildCount() > 0);
        Rect visible = new Rect();
        assertTrue("First source row is clipped", list.getChildAt(0).getGlobalVisibleRect(visible));
        assertEquals(list.getChildAt(0).getHeight(), visible.height());
        for (int label : new int[]{R.string.source_add, R.string.source_close}) {
            Button action = button(decor, label);
            assertNotNull(action);
            assertTrue("Footer action is clipped", action.getGlobalVisibleRect(visible));
            assertEquals(action.getHeight(), visible.height());
        }
        return list;
    }

    @Test public void recommendedSourcesHaveVisibleRowsOnFirstOpen() {
        Dialog dialog = open(0);
        RecyclerView list = assertVisible(dialog);
        assertEquals(3, list.getAdapter().getItemCount());
        assertTrue("Source row cannot receive remote focus", list.getChildAt(0).requestFocus());
        assertNotNull("Remote down navigation has no target", list.getChildAt(0).focusSearch(View.FOCUS_DOWN));
        dialog.dismiss();
    }

    @Test public void completeCatalogCanScrollToTheLastSource() {
        Dialog dialog = open(0);
        RecyclerView list = assertVisible(dialog);
        button(dialog.getWindow().getDecorView(), R.string.source_show_all).performClick();
        layout(dialog);
        assertEquals(56, list.getAdapter().getItemCount());
        list.scrollToPosition(55);
        layout(dialog);
        assertNotNull("Last source was not reachable", list.findViewHolderForAdapterPosition(55));
        dialog.dismiss();
    }

    @Test public void liveTabShowsBothBundledSources() {
        Dialog dialog = open(0);
        button(dialog.getWindow().getDecorView(), R.string.source_live).performClick();
        RecyclerView list = assertVisible(dialog);
        assertEquals(2, list.getAdapter().getItemCount());
        assertTrue(button(dialog.getWindow().getDecorView(), R.string.source_follow).isShown());
        dialog.dismiss();
    }

    @Test @Config(qualifiers = "zh-rCN-w640dp-h360dp-land-mdpi")
    public void shortLandscapeKeepsSourcesAndFooterVisible() {
        Dialog dialog = open(0);
        assertVisible(dialog);
        dialog.dismiss();
    }

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    public void television720pKeepsSourcesVisible() {
        Dialog dialog = open(0);
        assertVisible(dialog);
        dialog.dismiss();
    }

    @Test @Config(qualifiers = "zh-rCN-w360dp-h640dp-port-mdpi")
    public void portraitKeepsSourcesAndFooterVisible() {
        Dialog dialog = open(0);
        assertVisible(dialog);
        dialog.dismiss();
    }

    @Test public void largeTextKeepsSourcesAndFooterVisible() {
        RuntimeEnvironment.setFontScale(1.5f);
        Dialog dialog = open(0);
        assertVisible(dialog);
        dialog.dismiss();
    }
}

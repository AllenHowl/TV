package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.event.ConfigEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import com.fongmi.android.tv.source.SourceCatalog;
import com.fongmi.android.tv.source.SourceDirectory;
import com.fongmi.android.tv.source.SourcePreferences;
import com.github.catvod.utils.Prefers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One source manager for both product flavors; the existing site selector stays separate. */
public class SourceDialog extends DialogFragment {
    private static final String TAG = "source_manager";
    private int type;
    private boolean busy;
    private boolean allSources;
    private Button filter;
    private TextView current;
    private TextView status;
    private EditText search;
    private SourceAdapter adapter;
    private Button vod;
    private Button live;
    private Button follow;

    public static void show(FragmentActivity activity, int type) {
        show(activity, type, false);
    }

    private static void show(FragmentActivity activity, int type, boolean welcome) {
        if (activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        SourceDialog dialog = new SourceDialog();
        Bundle args = new Bundle();
        args.putInt("type", type);
        args.putBoolean("welcome", welcome);
        dialog.setArguments(args);
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    public static void offer(FragmentActivity activity) {
        if (Prefers.getBoolean("source_welcome_seen") || !Config.vod().isEmpty() || !Config.live().isEmpty()) return;
        show(activity, 0, true);
    }

    private boolean tv() { return BuildConfig.FLAVOR.startsWith("leanback"); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private Button button(int label) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(tv() ? 56 : 48));
        return button;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        type = savedInstanceState == null ? requireArguments().getInt("type") : savedInstanceState.getInt("type");
        allSources = savedInstanceState != null && savedInstanceState.getBoolean("all");
        boolean welcome = requireArguments().getBoolean("welcome");
        LinearLayout header = column();
        TextView title = new TextView(requireContext());
        title.setText(welcome ? R.string.source_welcome : R.string.source_manage);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(8));
        header.addView(title);
        LinearLayout tabs = new LinearLayout(requireContext());
        vod = button(R.string.source_vod);
        live = button(R.string.source_live);
        tabs.addView(vod, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(live, new LinearLayout.LayoutParams(0, -2, 1));
        vod.setOnClickListener(view -> changeType(0));
        live.setOnClickListener(view -> changeType(1));
        header.addView(tabs);
        current = new TextView(requireContext());
        current.setPadding(0, dp(8), 0, dp(8));
        current.setTextSize(tv() ? 18 : 16);
        current.setSingleLine(true);
        current.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(current);
        search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setHint(R.string.source_search);
        search.setTextSize(16);
        LinearLayout searchRow = new LinearLayout(requireContext());
        searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        filter = button(R.string.source_show_all);
        filter.setOnClickListener(view -> { if (!busy) { allSources = !allSources; refresh(); } });
        filter.setTextSize(13);
        searchRow.addView(filter);
        header.addView(searchRow);
        status = new TextView(requireContext());
        status.setPadding(0, dp(8), 0, dp(8));
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setTextSize(13);
        header.addView(status);
        RecyclerView list = new RecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter = new SourceAdapter());
        list.setItemAnimator(null);
        follow = button(R.string.source_follow);
        follow.setOnClickListener(view -> followVod());
        LinearLayout actions = new LinearLayout(requireContext());
        actions.addView(follow, new LinearLayout.LayoutParams(0, -2, 1));
        Button custom = button(R.string.source_add);
        custom.setOnClickListener(view -> { if (!busy) addCustom(); });
        actions.addView(custom, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button(welcome ? R.string.source_later : R.string.source_close);
        close.setOnClickListener(view -> dismiss());
        actions.addView(close);
        SourceDialogLayout root = new SourceDialogLayout(requireContext(), header, list, actions, dp(tv() ? 152 : 136));
        root.setPadding(dp(20), dp(16), dp(20), dp(8));
        MaterialShapeDrawable background = new MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(dp(24)).build());
        background.setTint(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface));
        root.setBackground(background);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable s) {}
        });
        refresh();
        AppCompatDialog dialog = new AppCompatDialog(requireContext());
        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (tv()) vod.requestFocus();
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(dp(tv() ? 760 : 560), (int) (getResources().getDisplayMetrics().widthPixels * 0.94));
            int height = Math.min(dp(tv() ? 660 : 760), (int) (getResources().getDisplayMetrics().heightPixels * 0.92));
            getDialog().getWindow().setLayout(width, height);
        }
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (!busy) {
            CharSequence message = status.getText();
            refresh();
            status.setText(message);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("type", type);
        outState.putBoolean("all", allSources);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (getArguments() != null && getArguments().getBoolean("welcome")) Prefers.put("source_welcome_seen", true);
        super.onDismiss(dialog);
    }

    private void changeType(int selected) {
        if (busy) return;
        type = selected;
        search.setText("");
        refresh();
    }

    private void refresh() {
        if (!isAdded() || adapter == null) return;
        Config selected = SourceCatalog.current(type);
        String name = selected.isEmpty() ? getString(R.string.source_none) : selected.getDesc();
        if (type == 1 && SourcePreferences.followsVod()) name = getString(R.string.source_follow) + " · " + name;
        current.setText(getString(R.string.source_current, name));
        vod.setSelected(type == 0);
        live.setSelected(type == 1);
        vod.setEnabled(!busy && type != 0);
        live.setEnabled(!busy && type != 1);
        follow.setVisibility(type == 1 ? View.VISIBLE : View.GONE);
        filter.setText(allSources ? R.string.source_show_recommended : R.string.source_show_all);
        filter.setEnabled(!busy);
        String query = search.getText().toString().strip().toLowerCase(Locale.ROOT);
        adapter.items.clear();
        for (SourceDirectory.Entry entry : SourceCatalog.entries(type)) {
            if ((allSources || !query.isEmpty() || entry.recommended || !entry.builtin || entry.contains(selected.getUrl()))
                    && entry.name.toLowerCase(Locale.ROOT).contains(query)) adapter.items.add(entry);
        }
        adapter.notifyDataSetChanged();
        if (!busy) status.setText(adapter.items.isEmpty() ? R.string.source_empty : R.string.source_intro);
    }

    private void select(SourceDirectory.Entry entry, int line) {
        if (busy) return;
        SourceCatalog.rememberCurrent(type);
        load(SourceCatalog.target(entry, line), entry.id, line, false);
    }

    private void followVod() {
        if (busy) return;
        Config config = VodConfig.get().getConfig();
        if (config.isEmpty()) {
            status.setText(R.string.source_follow_empty);
            return;
        }
        SourceCatalog.rememberCurrent(1);
        load(Config.find(config.getUrl(), getString(R.string.source_follow), 1), null, 0, true);
    }

    private void load(Config target, String id, int line, boolean following) {
        busy = true;
        setCancelable(true);
        refresh();
        status.setText(R.string.source_loading);
        FragmentActivity host = requireActivity();
        Callback callback = new Callback() {
            @Override
            public void success() {
                if (id != null) Prefers.put("source_line_" + id, line);
                Prefers.put("source_welcome_seen", true);
                FragmentActivity receiver = host;
                if (host.isDestroyed() && App.activity() instanceof FragmentActivity replacement) receiver = replacement;
                if (target.getType() == 1 && !receiver.isDestroyed() && receiver instanceof LiveListener listener) {
                    listener.setLive(LiveConfig.get().getHome());
                }
                finish(true, "");
            }
            @Override
            public void error(String message) { finish(false, message); }
        };
        if (target.getType() == 0) VodConfig.load(target, callback);
        else if (following) LiveConfig.loadFollowing(target, callback);
        else LiveConfig.load(target, callback);
    }

    private void finish(boolean success, String message) {
        busy = false;
        setCancelable(true);
        if (!isAdded()) return;
        refresh();
        status.setText(success ? R.string.source_loaded : R.string.source_failed);
        if (!success && message != null && !message.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.source_failed).setMessage(message).setPositiveButton(android.R.string.ok, null).show();
        }
    }

    private void options(SourceDirectory.Entry entry) {
        if (busy) return;
        String[] lines = entry.lines.stream().map(item -> item.name).toArray(String[]::new);
        int preferred = Math.max(0, Math.min(Prefers.getInt("source_line_" + entry.id), lines.length - 1));
        int[] choice = {preferred};
        new MaterialAlertDialogBuilder(requireContext()).setTitle(entry.name)
                .setSingleChoiceItems(lines, preferred, (dialog, which) -> choice[0] = which)
                .setPositiveButton(R.string.source_use, (dialog, which) -> select(entry, choice[0]))
                .setNeutralButton(R.string.source_details, (dialog, which) -> new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.source_details)
                        .setMessage(getString(R.string.source_details_text, entry.name, entry.lines.get(choice[0]).url))
                        .setPositiveButton(android.R.string.ok, null).show())
                .setNegativeButton(R.string.source_close, null).show();
    }

    private void addCustom() {
        LinearLayout form = column();
        form.setPadding(dp(24), 0, dp(24), 0);
        EditText name = new EditText(requireContext());
        name.setHint(R.string.source_name);
        name.setSingleLine(true);
        EditText url = new EditText(requireContext());
        url.setHint(R.string.source_url);
        url.setSingleLine(true);
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(name); form.addView(url);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.source_add).setView(form)
                .setPositiveButton(R.string.source_use, null).setNegativeButton(R.string.source_close, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = url.getText().toString().strip();
            if (!value.matches("(?i)^(https?|file|assets|content)://.+")) { url.setError(getString(R.string.source_url_error)); return; }
            if (busy) return;
            SourceCatalog.rememberCurrent(type);
            Config target = Config.find(value, name.getText().toString().strip(), type);
            dialog.dismiss();
            load(target, null, 0, false);
        }));
        dialog.show();
    }

    private final class SourceAdapter extends RecyclerView.Adapter<SourceHolder> {
        final List<SourceDirectory.Entry> items = new ArrayList<>();
        @NonNull
        @Override
        public SourceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setMinimumHeight(dp(tv() ? 76 : 68));
            row.setFocusable(true);
            row.setClickable(true);
            android.util.TypedValue value = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            row.setBackgroundResource(value.resourceId);
            TextView label = new TextView(parent.getContext());
            label.setTextSize(tv() ? 19 : 16);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            Button options = button(R.string.source_options);
            options.setTextSize(13);
            row.addView(options);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new SourceHolder(row, label, options);
        }
        @Override
        public void onBindViewHolder(@NonNull SourceHolder holder, int position) {
            SourceDirectory.Entry entry = items.get(position);
            boolean selected = entry.contains(SourceCatalog.current(type).getUrl());
            String description = getString(selected ? R.string.source_active : entry.builtin ? R.string.source_builtin : R.string.source_custom);
            holder.label.setText((selected ? "● " : "") + entry.name + "\n" + description);
            holder.itemView.setContentDescription(entry.name + ", " + description);
            holder.itemView.setEnabled(!busy);
            holder.options.setEnabled(!busy);
            holder.options.setContentDescription(entry.name + ", " + getString(R.string.source_options));
            holder.itemView.setOnClickListener(view -> select(entry, Math.max(0, Math.min(Prefers.getInt("source_line_" + entry.id), entry.lines.size() - 1))));
            holder.options.setOnClickListener(view -> options(entry));
        }
        @Override
        public int getItemCount() { return items.size(); }
    }

    private static final class SourceHolder extends RecyclerView.ViewHolder {
        final TextView label;
        final Button options;
        SourceHolder(View row, TextView label, Button options) { super(row); this.label = label; this.options = options; }
    }
}

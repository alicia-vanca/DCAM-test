package com.dvid.dcam.feature.settings.presentation;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ProgressBar;
import android.os.StatFs;
import java.io.File;
import java.util.ArrayList;
import android.widget.SeekBar;
import android.widget.TextView;
import com.dvid.dcam.R;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Renders consistent settings controls for the settings presentation surface. */
public final class SettingsControlRenderer {
    private final Context context;

    public SettingsControlRenderer(Context context) {
        this.context = context;
    }

    public void render(
            LinearLayout parent,
            SettingsScreenModel model,
            BiConsumer<SettingId, Integer> onSelection,
            BiConsumer<SettingId, Integer> onNumber,
            BiConsumer<SettingId, Boolean> onBoolean,
            Consumer<SettingId> onAction) {
        for (SettingsSection section : model.getSections()) {
            section(parent, section.getTitle());
            for (SettingItem item : section.getItems()) {
                int childCount = parent.getChildCount();
                switch (item.getType()) {
                    case TEXT:
                        text(parent, item.getLabel(), item.getValue());
                        break;
                    case CHECKBOX:
                        checkbox(parent, item.getLabel(), item.isChecked(),
                                checked -> onBoolean.accept(item.getId(), checked));
                        break;
                    case CHOICE:
                        choice(parent, item.getLabel(), item.getOptions(), item.getSelectedIndex(),
                                selected -> onSelection.accept(item.getId(), selected));
                        break;
                    case SLIDER:
                        slider(parent, item.getLabel(), item.getMin(), item.getMax(),
                                item.getNumberValue(), item.getUnit(),
                                value -> onNumber.accept(item.getId(), value));
                        break;
                    case RADIO:
                        if (item.getId() == SettingId.DEFAULT_STORAGE) {
                            storageRadio(parent, item.getLabel(), item.getOptions(), item.getSelectedIndex(),
                                    selected -> onSelection.accept(item.getId(), selected));
                        } else {
                            radio(parent, item.getLabel(), item.getOptions(), item.getSelectedIndex(),
                                    selected -> onSelection.accept(item.getId(), selected));
                        }
                        break;
                    case ACTION:
                        action(parent, item.getLabel(), () -> {
                            if (onAction != null) onAction.accept(item.getId());
                        });
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported setting type " + item.getType());
                }
                if (!item.isEnabled() && parent.getChildCount() > childCount) {
                    View row = parent.getChildAt(parent.getChildCount() - 1);
                    row.setEnabled(false);
                    row.setAlpha(0.45f);
                    disableChildren(row);
                }
                if (item.getIndentLevel() > 0 && parent.getChildCount() > childCount) {
                    View row = parent.getChildAt(parent.getChildCount() - 1);
                    row.setPadding(row.getPaddingLeft() + dp(24 * item.getIndentLevel()),
                            row.getPaddingTop(), row.getPaddingRight(), row.getPaddingBottom());
                    row.setBackgroundColor(Color.rgb(22, 29, 37));
                }
            }
        }
    }

    private static void disableChildren(View view) {
        view.setEnabled(false);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            disableChildren(group.getChildAt(index));
        }
    }

    public void section(LinearLayout parent, String title) {
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(Color.rgb(142, 200, 255));
        heading.setTextSize(12);
        heading.setAllCaps(true);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(dp(4), dp(18), dp(4), dp(6));
        parent.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void text(LinearLayout parent, String label, String value) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView labelView = label(label);
        TextView valueView = value(value);
        row.addView(labelView, weighted());
        row.addView(valueView, wrap());
        parent.addView(row);
    }

    public void checkbox(
            LinearLayout parent, String label, boolean checked, Consumer<Boolean> onChanged) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView labelView = label(label);
        CheckBox checkBox = new CheckBox(context);
        checkBox.setChecked(checked);
        checkBox.setOnCheckedChangeListener((button, isChecked) -> {
            if (onChanged != null) onChanged.accept(isChecked);
        });
        row.addView(labelView, weighted());
        row.addView(checkBox, wrap());
        parent.addView(row);
    }

    public void choice(
            LinearLayout parent,
            String label,
            List<String> options,
            int selectedIndex,
            IntConsumer onSelected) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView labelView = label(label);
        int initialSelection = clamp(selectedIndex, 0, options.size() - 1);
        int[] currentSelection = { initialSelection };
        TextView selectedValue = value(choiceText(options.get(initialSelection)));
        selectedValue.setSingleLine(true);
        selectedValue.setEllipsize(TextUtils.TruncateAt.END);
        selectedValue.setMinHeight(dp(48));
        selectedValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        selectedValue.setPadding(dp(8), 0, 0, 0);
        selectedValue.setClickable(true);
        selectedValue.setFocusable(true);
        selectedValue.setOnClickListener(view -> showChoicePopup(
                selectedValue, options, currentSelection[0], position -> {
                    currentSelection[0] = position;
                    selectedValue.setText(choiceText(options.get(position)));
                    if (onSelected != null) onSelected.accept(position);
                }));
        row.addView(labelView, weighted());
        row.addView(selectedValue, wrap());
        parent.addView(row);
    }

    private void showChoicePopup(
            View anchor, List<String> options, int selectedIndex, IntConsumer onSelected) {
        ListView list = new ListView(context);
        list.setDivider(new ColorDrawable(Color.rgb(56, 69, 83)));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setBackgroundColor(Color.TRANSPARENT);
        DarkChoiceAdapter adapter = new DarkChoiceAdapter(context, options, selectedIndex);
        list.setAdapter(adapter);

        int width = choicePopupWidth(options);
        int height = choicePopupHeight(options.size());
        PopupWindow popup = new PopupWindow(
                list, width, height, true);
        popup.setBackgroundDrawable(context.getDrawable(R.drawable.bg_choice_popup));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        list.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            int position = action == MotionEvent.ACTION_CANCEL
                    ? ListView.INVALID_POSITION
                    : list.pointToPosition((int) event.getX(), (int) event.getY());
            adapter.setHighlightedIndex(position);
            if (action == MotionEvent.ACTION_UP) {
                if (position != ListView.INVALID_POSITION) onSelected.accept(position);
                popup.dismiss();
            }
            return true;
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            onSelected.accept(position);
            popup.dismiss();
        });
        popup.showAsDropDown(anchor, anchor.getWidth() - width, 0);
    }

    public void slider(
            LinearLayout parent,
            String label,
            int min,
            int max,
            int value,
            String unit,
            IntConsumer onChanged) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.VERTICAL);
        TextView valueView = value(formatValue(value, unit));
        LinearLayout titleLine = new LinearLayout(context);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleLine.addView(label(label), weighted());
        titleLine.addView(valueView, wrap());

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamp(value, min, max) - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                valueView.setText(formatValue(selected, unit));
                if (fromUser && onChanged != null) onChanged.accept(selected);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        row.addView(titleLine);
        row.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(row);
    }

    public void radio(
            LinearLayout parent,
            String label,
            List<String> options,
            int selectedIndex,
            IntConsumer onSelected) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(label(label));
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        int checkedIndex = clamp(selectedIndex, 0, options.size() - 1);
        for (int i = 0; i < options.size(); i++) {
            RadioButton button = new RadioButton(context);
            button.setText(options.get(i));
            button.setTextColor(Color.WHITE);
            button.setTextSize(14);
            button.setId(View.generateViewId());
            button.setTag(i);
            group.addView(button, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i == checkedIndex) group.check(button.getId());
        }
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            View checked = radioGroup.findViewById(checkedId);
            if (checked != null && onSelected != null) {
                onSelected.accept((Integer) checked.getTag());
            }
        });
        row.addView(group);
        parent.addView(row);
    }

    private void storageRadio(
            LinearLayout parent, String label, List<String> options, int selectedIndex,
            IntConsumer onSelected) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(label(label));
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        int checkedIndex = clamp(selectedIndex, 0, options.size() - 1);
        File[] roots = context.getExternalFilesDirs(null);
        List<RadioButton> buttons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            LinearLayout option = new LinearLayout(context);
            option.setOrientation(LinearLayout.VERTICAL);
            RadioButton button = new RadioButton(context);
            button.setText(options.get(i).split("\\n", 2)[0]);
            button.setTextColor(Color.WHITE);
            button.setTextSize(14);
            button.setId(View.generateViewId());
            button.setTag(i);
            buttons.add(button);
            button.setOnClickListener(view -> {
                for (RadioButton candidate : buttons) candidate.setChecked(candidate == view);
                if (onSelected != null) onSelected.accept((Integer) view.getTag());
            });
            option.addView(button);
            if (i < 2) addStorageBar(option, i == 0
                    ? (roots.length > 0 ? roots[0] : null)
                    : roots.length > 1 ? roots[1] : null);
            group.addView(option, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            button.setChecked(i == checkedIndex);
        }
        row.addView(group);
        parent.addView(row);
    }

    private void addStorageBar(LinearLayout option, File root) {
        long total = 0L;
        long free = 0L;
        if (root != null) {
            try {
                StatFs stats = new StatFs(root.getAbsolutePath());
                total = stats.getTotalBytes();
                free = stats.getAvailableBytes();
            } catch (RuntimeException ignored) {}
        }
        long used = Math.max(0L, total - free);
        TextView usage = value(root == null ? "Unavailable" : "Used " + formatStorage(used)
                                                              + " / " + formatStorage(total));
        usage.setPadding(dp(52), 0, dp(4), 0);
        option.addView(usage);
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(total <= 0L ? 0 : (int) Math.min(1000.0, used * 1000.0 / total));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        params.setMargins(dp(52), 0, dp(4), dp(6));
        option.addView(bar, params);
    }

    private String formatStorage(long bytes) {
        return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public void action(LinearLayout parent, String label, Runnable onClick) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> {
            if (onClick != null) onClick.run();
        });
        row.addView(label(label), weighted());
        row.addView(value(">"), wrap());
        parent.addView(row);
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(context);
        row.setBackgroundResource(R.drawable.bg_setting_card);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private TextView label(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Color.rgb(229, 237, 245));
        view.setTextSize(14);
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView value(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Color.rgb(204, 204, 204));
        view.setTextSize(13);
        view.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        view.setPadding(dp(12), 0, 0, 0);
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int choicePopupWidth(List<String> options) {
        TextView measureView = new TextView(context);
        measureView.setTextSize(14);
        float widestText = 0;
        for (String option : options) {
            widestText = Math.max(widestText, measureView.getPaint().measureText(option));
        }
        return Math.max(dp(96), (int) Math.ceil(widestText) + dp(34));
    }

    private int choicePopupHeight(int optionCount) {
        return Math.max(dp(48), optionCount * dp(48) + Math.max(0, optionCount - 1));
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static String formatValue(int value, String unit) {
        return unit == null || unit.isBlank() ? String.valueOf(value) : value + " " + unit;
    }

    private static String choiceText(String value) {
        return value + "  \u25BC";
    }

    private static final class DarkChoiceAdapter extends ArrayAdapter<String> {
        private final int selectedIndex;
        private int highlightedIndex = ListView.INVALID_POSITION;

        private DarkChoiceAdapter(Context context, List<String> values, int selectedIndex) {
            super(context, android.R.layout.simple_list_item_1, values);
            this.selectedIndex = selectedIndex;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setSingleLine(true);
            view.setTextColor(position == selectedIndex ? Color.rgb(142, 200, 255) : Color.WHITE);
            view.setTextSize(14);
            view.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            view.setBackgroundResource(R.drawable.bg_choice_option);
            view.setActivated(position == highlightedIndex);
            int horizontalPadding = Math.round(16 * getContext().getResources().getDisplayMetrics().density);
            int verticalPadding = Math.round(14 * getContext().getResources().getDisplayMetrics().density);
            view.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
            return view;
        }

        private void setHighlightedIndex(int position) {
            if (highlightedIndex == position) return;
            highlightedIndex = position;
            notifyDataSetChanged();
        }
    }
}

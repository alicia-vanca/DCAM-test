package com.dvid.dcam.app.ui.settings;

import android.content.Context;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;

import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.RadioGroup;
import java.util.ArrayList;
import android.widget.SeekBar;
import android.widget.TextView;
import com.dvid.dcam.R;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Renders consistent settings controls for the settings presentation surface. */
public final class SettingsControlRenderer {
    private final Context context;
    private final Map<String, View> renderedRows = new HashMap<>();
    private boolean refreshingRows;

    private static final class ChoiceRowState {
        private List<String> options;
        private int selectedIndex;

        private ChoiceRowState(List<String> options, int selectedIndex) {
            this.options = List.copyOf(options);
            this.selectedIndex = selectedIndex;
        }
    }

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
        render(parent, model, onSelection, onNumber, onBoolean, onAction, null);
    }

    public void render(
            LinearLayout parent,
            SettingsScreenModel model,
            BiConsumer<SettingId, Integer> onSelection,
            BiConsumer<SettingId, Integer> onNumber,
            BiConsumer<SettingId, Boolean> onBoolean,
            Consumer<SettingId> onAction,
            Predicate<SettingId> onSelectionAllowed) {
        renderItems(parent, model,
                (item, selected) -> onSelection.accept(item.getId(), selected),
                (item, value) -> onNumber.accept(item.getId(), value),
                (item, checked) -> onBoolean.accept(item.getId(), checked),
                onAction == null ? null : item -> onAction.accept(item.getId()), null,
                onSelectionAllowed == null
                        ? null : item -> onSelectionAllowed.test(item.getId()));
    }

    public void renderByStableId(
            LinearLayout parent,
            SettingsScreenModel model,
            BiConsumer<String, Integer> onSelection,
            BiConsumer<String, Integer> onNumber,
            BiConsumer<String, Boolean> onBoolean,
            Consumer<String> onAction) {
        renderItems(parent, model,
                (item, selected) -> onSelection.accept(item.getStableId(), selected),
                (item, value) -> onNumber.accept(item.getStableId(), value),
                (item, checked) -> onBoolean.accept(item.getStableId(), checked),
                onAction == null ? null : item -> onAction.accept(item.getStableId()), null, null);
    }

    public void renderByStableId(
            LinearLayout parent,
            SettingsScreenModel model,
            BiConsumer<String, Integer> onSelection,
            BiConsumer<String, Integer> onNumber,
            BiConsumer<String, Boolean> onBoolean,
            Consumer<String> onAction,
            Predicate<String> onSelectionAllowed) {
        renderItems(parent, model,
                (item, selected) -> onSelection.accept(item.getStableId(), selected),
                (item, value) -> onNumber.accept(item.getStableId(), value),
                (item, checked) -> onBoolean.accept(item.getStableId(), checked),
                onAction == null ? null : item -> onAction.accept(item.getStableId()),
                onSelectionAllowed == null
                        ? null : item -> onSelectionAllowed.test(item.getStableId()),
                onSelectionAllowed == null
                        ? null : item -> onSelectionAllowed.test(item.getStableId()));
    }

    private void renderItems(
            LinearLayout parent,
            SettingsScreenModel model,
            BiConsumer<SettingItem, Integer> onSelection,
            BiConsumer<SettingItem, Integer> onNumber,
            BiConsumer<SettingItem, Boolean> onBoolean,
            Consumer<SettingItem> onAction,
            Predicate<SettingItem> onChoiceOpen,
            Predicate<SettingItem> onSelectionAllowed) {
        renderedRows.clear();
        for (SettingsSection section : model.getSections()) {
            section(parent, section.getTitle());
            for (SettingItem item : section.getItems()) {
                int childCount = parent.getChildCount();
                switch (item.getType()) {
                    case TEXT:
                        text(parent, item.getLabel(), item.getValue());
                        break;
                    case CHECKBOX:
                        checkbox(parent, item.getLabel(), item.getDescription(),
                                item.isChecked(), checked -> onBoolean.accept(item, checked));
                        break;
                    case CHOICE:
                        choice(parent, item.getLabel(), item.getDescription(), item.getOptions(),
                                item.getSelectedIndex(),
                                selected -> onSelection.accept(item, selected),
                                onChoiceOpen == null ? null : () -> onChoiceOpen.test(item));
                        break;
                    case SLIDER:
                        slider(parent, item.getLabel(), item.getMin(), item.getMax(),
                                item.getNumberValue(), item.getUnit(),
                                value -> onNumber.accept(item, value));
                        break;
                    case RADIO:
                        radio(parent, item.getLabel(), item.getOptions(), item.getSelectedIndex(),
                                selected -> onSelection.accept(item, selected));
                        break;
                    case DESCRIBED_RADIO:
                        describedRadio(parent, item.getLabel(), item.getDescribedRadioOptions(),
                                item.getSelectedIndex(),
                                selected -> onSelection.accept(item, selected),
                                onSelectionAllowed == null
                                        ? null : () -> onSelectionAllowed.test(item),
                                onSelection, onChoiceOpen);
                        break;
                    case STORAGE_RADIO:
                        storageRadio(parent, item.getLabel(), item.getStorageOptions(),
                                item.getSelectedIndex(),
                                selected -> onSelection.accept(item, selected));
                        break;
                    case ACTION:
                        action(parent, item.getLabel(), () -> {
                            if (onAction != null) onAction.accept(item);
                        });
                        break;

                    default:
                        throw new IllegalArgumentException("Unsupported setting type "
                                + item.getType());
                }
                if (parent.getChildCount() > childCount) {
                    decorateRow(item, parent.getChildAt(parent.getChildCount() - 1));
                }
            }
        }
    }

    private void decorateRow(SettingItem item, View row) {
        rememberRow(item, row);
        if (!item.isEnabled()) {
            row.setEnabled(false);
            row.setAlpha(0.45f);
            disableChildren(row);
        }
        if (item.getIndentLevel() > 0) {
            row.setPadding(row.getPaddingLeft() + dp(24 * item.getIndentLevel()),
                    row.getPaddingTop(), row.getPaddingRight(), row.getPaddingBottom());
            row.setBackgroundColor(Color.rgb(22, 29, 37));
        }
    }

    private void rememberRow(SettingItem item, View row) {
        if (item.getStableId() != null) renderedRows.put(item.getStableId(), row);
    }

    public void refreshRows(SettingsScreenModel model) {
        refreshingRows = true;
        try {
            for (SettingsSection section : model.getSections()) {
                for (SettingItem item : section.getItems()) {
                    View row = renderedRows.get(item.getStableId());
                    if (row == null) continue;
                    refreshRowValues(row, item);
                    applyEnabledState(row, item);
                }
            }
        } finally {
            refreshingRows = false;
        }
    }

    public void refreshStorageUsage(SettingsScreenModel model) {
        refreshingRows = true;
        try {
            for (SettingsSection section : model.getSections()) {
                for (SettingItem item : section.getItems()) {
                    if (item.getType() != SettingItem.Type.STORAGE_RADIO) continue;
                    View row = renderedRows.get(item.getStableId());
                    if (row == null) continue;
                    refreshStorageRadio(row, item);
                    setStorageRadioOptionsEnabled(row, item);
                }
            }
        } finally {
            refreshingRows = false;
        }
    }

    private void applyEnabledState(View row, SettingItem item) {
        row.setEnabled(item.isEnabled());
        row.setAlpha(item.isEnabled() ? 1f : 0.45f);
        if (!item.isEnabled()) {
            setChildrenEnabled(row, false);
        } else if (item.getType() == SettingItem.Type.DESCRIBED_RADIO) {
            setChildrenEnabled(row, true);
            setDescribedRadioOptionsEnabled(row, item);
        } else if (item.getType() == SettingItem.Type.STORAGE_RADIO) {
            setChildrenEnabled(row, true);
            setStorageRadioOptionsEnabled(row, item);
        } else {
            setChildrenEnabled(row, true);
        }
    }

    private void refreshRowValues(View row, SettingItem item) {
        switch (item.getType()) {
            case TEXT:
                setText(textChild(row, 0), item.getLabel());
                setText(textChild(row, 1), item.getValue());
                break;
            case CHECKBOX:
                setText(textChild(row, 0), item.getLabel());
                View switchView = childAt(row, 1);
                if (switchView instanceof Switch) ((Switch) switchView).setChecked(item.isChecked());
                break;
            case CHOICE:
                refreshChoice(row, item);
                break;
            case SLIDER:
                refreshSlider(row, item);
                break;
            case RADIO:
                refreshRadio(row, item);
                break;
            case DESCRIBED_RADIO:
                refreshDescribedRadio(row, item);
                break;
            case STORAGE_RADIO:
                refreshStorageRadio(row, item);
                break;
            case ACTION:
                setText(textChild(row, 0), item.getLabel());
                break;
            default:
                break;
        }
    }

    private void refreshChoice(View row, SettingItem item) {
        View titleLine = childAt(row, 0);
        setText(textChild(titleLine, 0), item.getLabel());
        List<String> options = item.getOptions();
        if (options.isEmpty()) return;
        int selected = clamp(item.getSelectedIndex(), 0, options.size() - 1);
        setText(textChild(titleLine, 1), choiceText(options.get(selected)));
        if (row.getTag() instanceof ChoiceRowState) {
            ChoiceRowState state = (ChoiceRowState) row.getTag();
            state.options = List.copyOf(options);
            state.selectedIndex = selected;
        }
        if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 1) {
            setText(textChild(row, 1), item.getDescription());
        }
    }

    private void refreshSlider(View row, SettingItem item) {
        View titleLine = childAt(row, 0);
        setText(textChild(titleLine, 0), item.getLabel());
        int value = clamp(item.getNumberValue(), item.getMin(), item.getMax());
        setText(textChild(titleLine, 1), formatValue(value, item.getUnit()));
        View seekView = childAt(row, 1);
        if (!(seekView instanceof SeekBar)) return;
        SeekBar seekBar = (SeekBar) seekView;
        seekBar.setMax(item.getMax() - item.getMin());
        seekBar.setProgress(value - item.getMin());
    }

    private void refreshRadio(View row, SettingItem item) {
        setText(textChild(row, 0), item.getLabel());
        RadioGroup group = radioGroup(row);
        if (group == null) return;
        List<String> options = item.getOptions();
        int count = Math.min(group.getChildCount(), options.size());
        for (int index = 0; index < count; index++) {
            View child = group.getChildAt(index);
            if (child instanceof RadioButton) {
                ((RadioButton) child).setText(options.get(index));
            }
        }
        checkRadioIndex(group, item.getSelectedIndex());
    }

    private void refreshDescribedRadio(View row, SettingItem item) {
        setText(textChild(row, 0), item.getLabel());
        RadioGroup group = radioGroup(row);
        if (group == null) return;
        List<DescribedRadioOptionUiState> options = item.getDescribedRadioOptions();
        for (int childIndex = 0; childIndex < group.getChildCount(); childIndex++) {
            View child = group.getChildAt(childIndex);
            if (!(child instanceof RadioButton) || !(child.getTag() instanceof Integer)) continue;
            int optionIndex = (Integer) child.getTag();
            if (optionIndex < 0 || optionIndex >= options.size()) continue;
            DescribedRadioOptionUiState state = options.get(optionIndex);
            RadioButton button = (RadioButton) child;
            button.setText(describedRadioText(state));
            SettingItem nestedChoice = state.getNestedChoice();
            if (nestedChoice == null) continue;
            View nestedRow = renderedRows.get(nestedChoice.getStableId());
            if (nestedRow == null) continue;
            refreshChoice(nestedRow, nestedChoice);
            applyEnabledState(nestedRow, nestedChoice);
        }
        checkRadioIndex(group, item.getSelectedIndex());
    }

    private void refreshStorageRadio(View row, SettingItem item) {
        setText(textChild(row, 0), item.getLabel());
        ViewGroup group = storageGroup(row);
        if (group == null) return;
        List<StorageOptionUiState> options = item.getStorageOptions();
        int selected = options.isEmpty() ? -1
                : clamp(item.getSelectedIndex(), 0, options.size() - 1);
        int count = Math.min(group.getChildCount(), options.size());
        for (int index = 0; index < count; index++) {
            StorageOptionUiState state = options.get(index);
            View optionView = group.getChildAt(index);
            View firstLine = childAt(optionView, 0);
            View buttonView = childAt(firstLine, 0);
            if (!(buttonView instanceof RadioButton)) continue;
            RadioButton button = (RadioButton) buttonView;
            button.setText(state.getLabel());
            button.setChecked(index == selected);
            boolean auto = state.getLabel().equals(context.getString(R.string.storage_auto));
            if (!auto) {
                setText(textChild(firstLine, 1), state.getDetail());
                View progressView = childAt(optionView, 1);
                if (progressView instanceof android.widget.ProgressBar) {
                    ((android.widget.ProgressBar) progressView).setProgress(state.getUsedPercent());
                }
            } else {
                setText(textChild(optionView, 1), state.getDetail());
            }
        }
    }

    private static void checkRadioIndex(RadioGroup group, int selectedIndex) {
        List<RadioButton> buttons = new ArrayList<>();
        for (int childIndex = 0; childIndex < group.getChildCount(); childIndex++) {
            View child = group.getChildAt(childIndex);
            if (child instanceof RadioButton) buttons.add((RadioButton) child);
        }
        if (buttons.isEmpty()) return;
        int selected = clamp(selectedIndex, 0, buttons.size() - 1);
        group.check(buttons.get(selected).getId());
    }

    private static RadioGroup radioGroup(View row) {
        if (!(row instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) row;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof RadioGroup) return (RadioGroup) child;
        }
        return null;
    }

    private static ViewGroup storageGroup(View row) {
        View group = childAt(row, 1);
        return group instanceof ViewGroup ? (ViewGroup) group : null;
    }

    private static View childAt(View view, int index) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        return index >= 0 && index < group.getChildCount() ? group.getChildAt(index) : null;
    }

    private static TextView textChild(View view, int index) {
        View child = childAt(view, index);
        return child instanceof TextView ? (TextView) child : null;
    }

    private static void setText(TextView view, CharSequence text) {
        if (view != null) view.setText(text == null ? "" : text);
    }

    private static void setStorageRadioOptionsEnabled(View row, SettingItem item) {
        ViewGroup group = storageGroup(row);
        if (group == null) return;
        List<StorageOptionUiState> options = item.getStorageOptions();
        int count = Math.min(group.getChildCount(), options.size());
        for (int index = 0; index < count; index++) {
            View option = group.getChildAt(index);
            boolean enabled = options.get(index).isAvailable();
            option.setEnabled(enabled);
            option.setAlpha(enabled ? 1f : 0.45f);
            setChildrenEnabled(option, enabled);
        }
    }

    private void setDescribedRadioOptionsEnabled(View row, SettingItem item) {
        if (!(row instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) row;
        List<DescribedRadioOptionUiState> options = item.getDescribedRadioOptions();
        for (int childIndex = 0; childIndex < group.getChildCount(); childIndex++) {
            View child = group.getChildAt(childIndex);
            if (!(child instanceof RadioGroup)) continue;
            RadioGroup radioGroup = (RadioGroup) child;
            for (int optionViewIndex = 0;
                    optionViewIndex < radioGroup.getChildCount(); optionViewIndex++) {
                View option = radioGroup.getChildAt(optionViewIndex);
                if (!(option instanceof RadioButton) || !(option.getTag() instanceof Integer)) {
                    continue;
                }
                int optionIndex = (Integer) option.getTag();
                if (optionIndex < 0 || optionIndex >= options.size()) continue;
                DescribedRadioOptionUiState state = options.get(optionIndex);
                option.setEnabled(state.isEnabled());
                option.setAlpha(state.isEnabled() ? 1f : 0.45f);
                SettingItem nestedChoice = state.getNestedChoice();
                if (nestedChoice == null) continue;
                View nestedRow = renderedRows.get(nestedChoice.getStableId());
                if (nestedRow != null) applyEnabledState(nestedRow, nestedChoice);
            }
        }
    }

    private static void disableChildren(View view) {
        setChildrenEnabled(view, false);
    }

    private static void setChildrenEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setChildrenEnabled(group.getChildAt(index), enabled);
        }
    }

    public void section(LinearLayout parent, String title) {
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(Color.rgb(142, 200, 255));
        heading.setTextSize(12);
        heading.setAllCaps(true);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(dp(4), dp(8), dp(4), dp(4));
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
        checkbox(parent, label, null, checked, onChanged);
    }

    public void checkbox(
            LinearLayout parent, String label, String description, boolean checked,
            Consumer<Boolean> onChanged) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (description == null || description.isBlank()) {
            row.addView(label(label), weighted());
        } else {
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(label(label));
            TextView descriptionView = value(description);
            descriptionView.setTextSize(12);
            descriptionView.setGravity(Gravity.START);
            descriptionView.setPadding(0, dp(2), 0, 0);
            labels.addView(descriptionView);
            row.addView(labels, weighted());
        }
        Switch switchView = new Switch(context) {
            private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas canvas) {
                int trackColor = isEnabled()
                        ? (isChecked() ? Color.rgb(0, 150, 136) : Color.rgb(176, 176, 176))
                        : Color.rgb(158, 158, 158);
                trackPaint.setColor(trackColor);
                float trackWidth = dp(36);
                float trackHeight = dp(14);
                float left = (getWidth() - trackWidth) / 2f;
                float top = (getHeight() - trackHeight) / 2f;
                canvas.drawRoundRect(left, top, left + trackWidth, top + trackHeight,
                        trackHeight / 2f, trackHeight / 2f, trackPaint);
                super.onDraw(canvas);
            }
        };
        switchView.setContentDescription(label);
        switchView.setShowText(false);
        switchView.setSwitchMinWidth(dp(48));
        switchView.setThumbTintList(context.getColorStateList(R.color.settings_switch_thumb_tint));
        android.graphics.drawable.GradientDrawable transparentTrack =
                new android.graphics.drawable.GradientDrawable();
        transparentTrack.setColor(Color.TRANSPARENT);
        transparentTrack.setSize(dp(36), dp(14));
        transparentTrack.setCornerRadius(dp(7));
        switchView.setTrackDrawable(transparentTrack);
        switchView.setTrackTintList(null);
        switchView.setChecked(checked);
        switchView.setOnCheckedChangeListener((button, isChecked) -> {
            if (!refreshingRows && onChanged != null) onChanged.accept(isChecked);
        });
        row.addView(switchView, wrap());
        parent.addView(row);
    }

    public void choice(
            LinearLayout parent,
            String label,
            List<String> options,
            int selectedIndex,
            IntConsumer onSelected) {
        choice(parent, label, null, options, selectedIndex, onSelected);
    }

    public void choice(
            LinearLayout parent,
            String label,
            String description,
            List<String> options,
            int selectedIndex,
            IntConsumer onSelected) {
        choice(parent, label, description, options, selectedIndex, onSelected, null);
    }

    private void choice(
            LinearLayout parent,
            String label,
            String description,
            List<String> options,
            int selectedIndex,
            IntConsumer onSelected,
            BooleanSupplier canOpen) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleLine = new LinearLayout(context);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = label(label);
        int initialSelection = clamp(selectedIndex, 0, options.size() - 1);
        ChoiceRowState state = new ChoiceRowState(options, initialSelection);
        row.setTag(state);
        TextView selectedValue = value(choiceText(state.options.get(initialSelection)));
        selectedValue.setSingleLine(true);
        selectedValue.setEllipsize(TextUtils.TruncateAt.END);
        selectedValue.setMinHeight(dp(48));
        selectedValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        selectedValue.setPadding(dp(8), 0, 0, 0);
        selectedValue.setClickable(true);
        selectedValue.setFocusable(true);
        selectedValue.setOnClickListener(view -> {
            if (canOpen != null && !canOpen.getAsBoolean()) return;
            showChoicePopup(selectedValue, state.options, state.selectedIndex, position -> {
                if (position < 0 || position >= state.options.size()) return;
                state.selectedIndex = position;
                selectedValue.setText(choiceText(state.options.get(position)));
                if (onSelected != null) onSelected.accept(position);
            });
        });
        titleLine.addView(labelView, weighted());
        titleLine.addView(selectedValue, wrap());
        row.addView(titleLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (description != null && !description.isBlank()) {
            TextView descriptionView = value(description);
            descriptionView.setTextSize(12);
            descriptionView.setGravity(Gravity.START);
            descriptionView.setPadding(0, dp(2), 0, dp(2));
            row.addView(descriptionView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
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

        int desiredHeight = 0;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (int index = 0; index < adapter.getCount(); index++) {
            View option = adapter.getView(index, null, list);
            option.measure(widthSpec, heightSpec);
            desiredHeight += option.getMeasuredHeight();
        }
        desiredHeight += Math.max(0, adapter.getCount() - 1) * list.getDividerHeight();
        Rect popupPadding = new Rect();
        if (popup.getBackground() != null) popup.getBackground().getPadding(popupPadding);
        desiredHeight += popupPadding.top + popupPadding.bottom;
        Rect visibleFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int spaceAbove = anchorLocation[1] - visibleFrame.top;
        int spaceBelow = visibleFrame.bottom - anchorLocation[1] - anchor.getHeight();
        int visibleHeight = visibleFrame.bottom - visibleFrame.top;
        int maxHeight = Math.max(dp(48), Math.round(visibleHeight * 0.9f));
        int popupHeight = Math.min(desiredHeight, maxHeight);
        boolean overlapAnchor = desiredHeight > Math.max(spaceAbove, spaceBelow);
        boolean expandAbove = !overlapAnchor && desiredHeight > spaceBelow && spaceAbove > spaceBelow;
        int popupTop;
        if (overlapAnchor) {
            int anchorCenter = anchorLocation[1] + anchor.getHeight() / 2;
            popupTop = anchorCenter - popupHeight / 2;
            popupTop = clamp(popupTop, visibleFrame.top, visibleFrame.bottom - popupHeight);
        } else {
            popupTop = expandAbove
                    ? anchorLocation[1] - popupHeight
                    : anchorLocation[1] + anchor.getHeight();
        }
        popup.setHeight(popupHeight);
        View.OnAttachStateChangeListener anchorLifecycle = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}

            @Override public void onViewDetachedFromWindow(View view) {
                popup.dismiss();
            }
        };
        anchor.addOnAttachStateChangeListener(anchorLifecycle);
        popup.setOnDismissListener(() ->
                anchor.removeOnAttachStateChangeListener(anchorLifecycle));
        popup.showAsDropDown(
                anchor,
                anchor.getWidth() - width,
                popupTop - anchorLocation[1] - anchor.getHeight());
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
        group.setPadding(0, 0, 0, 0);
        int checkedIndex = clamp(selectedIndex, 0, options.size() - 1);
        for (int i = 0; i < options.size(); i++) {
            RadioButton button = new RadioButton(context);
            button.setText(options.get(i));
            styleRadioButton(button);
            button.setId(View.generateViewId());
            button.setTag(i);
            group.addView(button, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i == checkedIndex) group.check(button.getId());
        }
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            View checked = radioGroup.findViewById(checkedId);
            if (!refreshingRows && checked != null && onSelected != null) {
                onSelected.accept((Integer) checked.getTag());
            }
        });
        row.addView(group);
        parent.addView(row);
    }

    private void describedRadio(LinearLayout parent, String label,
            List<DescribedRadioOptionUiState> options, int selectedIndex,
            IntConsumer onSelected, BooleanSupplier canSelect,
            BiConsumer<SettingItem, Integer> onNestedSelection,
            Predicate<SettingItem> onNestedChoiceOpen) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(label(label));
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        int checkedIndex = clamp(selectedIndex, 0, options.size() - 1);
        for (int index = 0; index < options.size(); index++) {
            DescribedRadioOptionUiState state = options.get(index);
            SettingItem nestedChoice = state.getNestedChoice();
            RadioButton button = new RadioButton(context) {
                @Override public boolean performClick() {
                    if (canSelect != null && !canSelect.getAsBoolean()) return true;
                    return super.performClick();
                }
            };
            button.setId(View.generateViewId());
            button.setTag(index);
            button.setText(describedRadioText(state));
            styleRadioButton(button);
            button.setEnabled(state.isEnabled());
            button.setAlpha(state.isEnabled() ? 1f : 0.45f);
            group.addView(button, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (index == checkedIndex) group.check(button.getId());
            if (nestedChoice != null) {

                int childCount = group.getChildCount();
                choice(group, nestedChoice.getLabel(), null,
                        nestedChoice.getOptions(), nestedChoice.getSelectedIndex(), selected -> {
                            if (onNestedSelection != null) {
                                onNestedSelection.accept(nestedChoice, selected);
                            }
                        }, onNestedChoiceOpen == null
                                ? null : () -> onNestedChoiceOpen.test(nestedChoice));
                if (group.getChildCount() > childCount) {
                    decorateRow(nestedChoice,
                            group.getChildAt(group.getChildCount() - 1));
                }
            }
        }
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            View checked = radioGroup.findViewById(checkedId);
            if (!refreshingRows && checked != null && onSelected != null) {
                onSelected.accept((Integer) checked.getTag());
            }
        });
        row.addView(group);
        parent.addView(row);
    }


    private CharSequence describedRadioText(DescribedRadioOptionUiState state) {
        if (TextUtils.isEmpty(state.getDescription())) return state.getLabel();
        String text = state.getLabel() + "\n" + state.getDescription();
        SpannableString styledText = new SpannableString(text);
        int descriptionStart = state.getLabel().length() + 1;
        styledText.setSpan(new RelativeSizeSpan(0.82f), descriptionStart, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styledText.setSpan(new ForegroundColorSpan(Color.LTGRAY),
                descriptionStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styledText;
    }

    private void styleRadioButton(RadioButton button) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setIncludeFontPadding(false);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(0, 0, dp(8), 0);
    }

    private void storageRadio(
            LinearLayout parent, String label, List<StorageOptionUiState> options, int selectedIndex,
            IntConsumer onSelected) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(label(label));
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setPadding(0, 0, 0, 0);
        int checkedIndex = clamp(selectedIndex, 0, options.size() - 1);
        List<RadioButton> buttons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            StorageOptionUiState storage = options.get(i);
            LinearLayout option = new LinearLayout(context);
            option.setOrientation(LinearLayout.VERTICAL);

            LinearLayout firstLine = new LinearLayout(context);
            firstLine.setOrientation(LinearLayout.HORIZONTAL);
            firstLine.setGravity(Gravity.CENTER_VERTICAL);
            RadioButton button = new RadioButton(context);
            button.setText(storage.getLabel());
            button.setTextColor(Color.WHITE);
            button.setTextSize(14);
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setIncludeFontPadding(false);
            button.setMinHeight(dp(34));
            button.setMinimumHeight(dp(34));
            button.setTag(i);
            buttons.add(button);
            boolean auto = storage.getLabel().equals(context.getString(R.string.storage_auto));
            TextView detail = value(storage.getDetail());
            detail.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            detail.setTextColor(storage.isAvailable() ? Color.LTGRAY : Color.GRAY);
            button.setEnabled(storage.isAvailable());
            option.setEnabled(storage.isAvailable());
            option.setAlpha(storage.isAvailable() ? 1f : 0.45f);
            button.setOnClickListener(view -> {
                for (RadioButton candidate : buttons) candidate.setChecked(candidate == view);
                if (onSelected != null) onSelected.accept((Integer) view.getTag());
            });
            option.setClickable(storage.isAvailable());
            option.setFocusable(storage.isAvailable());
            option.setOnClickListener(view -> button.performClick());
            firstLine.addView(button, weighted());
            if (!auto) firstLine.addView(detail, wrap());
            option.addView(firstLine);

            android.widget.ProgressBar progress = null;
            TextView description = null;
            if (!auto) {
                progress = new android.widget.ProgressBar(
                        context, null, android.R.attr.progressBarStyleHorizontal);
                progress.setMax(100);
                progress.setProgress(storage.getUsedPercent());
                progress.setIndeterminate(false);
                progress.setEnabled(storage.isAvailable());
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
                progressParams.setMargins(0, 0, 0, 0);
                option.addView(progress, progressParams);
            }
            if (auto) {
                description = value(storage.getDetail());
                description.setGravity(Gravity.START);
                description.setPadding(0, 0, 0, 0);
                option.addView(description);
            }
            android.widget.ProgressBar alignedProgress = progress;
            TextView alignedDescription = description;
            option.addOnLayoutChangeListener((view, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                int contentLeft = button.getCompoundPaddingLeft();
                if (alignedProgress != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                            alignedProgress.getLayoutParams();
                    if (params.leftMargin != contentLeft) {
                        params.leftMargin = contentLeft;
                        alignedProgress.setLayoutParams(params);
                    }
                }
                if (alignedDescription != null
                        && alignedDescription.getPaddingLeft() != contentLeft) {
                    alignedDescription.setPadding(contentLeft,
                            alignedDescription.getPaddingTop(),
                            alignedDescription.getPaddingRight(),
                            alignedDescription.getPaddingBottom());
                }
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            optionParams.setMargins(0, dp(4), 0, dp(12));
            group.addView(option, optionParams);
            button.setChecked(i == checkedIndex);
        }
        row.addView(group);
        parent.addView(row);
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

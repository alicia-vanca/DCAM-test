package com.dvid.dcam.app.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.dvid.dcam.R;
import com.dvid.dcam.app.ui.navigation.MainNavigator;
import com.dvid.dcam.app.ui.settings.SettingsControlRenderer;
import com.dvid.dcam.app.ui.settings.SettingsScreenCatalog;
import com.dvid.dcam.app.ui.settings.SettingsScreenCoordinator;
import com.dvid.dcam.app.ui.settings.SettingsScreenModel;
import com.dvid.dcam.databinding.ScreenSettingsDetailBinding;

public final class SettingsDetailFragment extends Fragment {
    public static final String ARG_SCREEN = "screen";

    private ScreenSettingsDetailBinding binding;
    private SettingsScreenCoordinator settings;
    private SettingsScreenCoordinator.Subscription invalidationSubscription;
    private SettingsControlRenderer renderer;
    private MainNavigator navigator;
    private MainScreen screen;
    private TextView consoleView;
    private AlertDialog resetDialog;
    private AlertDialog removeDeviceOwnerDialog;

    public SettingsDetailFragment() {
        // Required by FragmentManager to recreate this fragment after process or configuration changes.
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ScreenSettingsDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        MainUiScope scope = MainUiScope.require(this);
        settings = scope.settings();
        navigator = scope.navigator();
        screen = requireScreen();
        renderer = new SettingsControlRenderer(requireContext());
        invalidationSubscription = settings.observeInvalidations(this::onInvalidated);
        renderScreen();
    }

    @Override public void onDestroyView() {
        if (invalidationSubscription != null) invalidationSubscription.close();
        invalidationSubscription = null;
        if (resetDialog != null) resetDialog.dismiss();
        resetDialog = null;
        if (removeDeviceOwnerDialog != null) removeDeviceOwnerDialog.dismiss();
        removeDeviceOwnerDialog = null;
        consoleView = null;
        renderer = null;
        navigator = null;
        settings = null;
        screen = null;
        binding = null;
        super.onDestroyView();
    }

    private void renderScreen() {
        binding.title.setText(settings.catalog().titleResource(screen));
        if (screen == MainScreen.ABOUT) {
            binding.title.setClickable(true);
            binding.title.setFocusable(true);
            binding.title.setOnClickListener(ignored -> settings.onAboutSecretTap());
        }
        SettingsScreenModel model = settings.model(screen);
        if (screen == MainScreen.RECORD_SETTINGS || screen == MainScreen.CAMERA_SETTINGS) {
            renderer.renderByStableId(binding.settingsList, model,
                    settings::selectCameraSetting, (id, value) -> {},
                    (id, checked) -> {}, null, settings::canOpenCameraSetting);
        } else if (screen == MainScreen.DEVELOPER_SETTINGS) {
            renderer.renderByStableId(binding.settingsList, model,
                    settings::selectDeveloperSetting, settings::updateDeveloperNumberSetting,
                    settings::updateDeveloperBooleanSetting,
                    settings::performDeveloperSettingAction,
                    settings::canSelectDeveloperSetting);
            addDeveloperActions();
        } else {
            renderer.render(binding.settingsList, model,
                    (id, selected) -> settings.selectSetting(screen, id, selected),
                    settings::updateNumberSetting, settings::updateBooleanSetting,
                    settings::performSettingAction, settings::canSelectSetting);
            if (screen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
                addButtonBindingActions();
            }
        }
    }

    private void onInvalidated(SettingsScreenCoordinator.Invalidation invalidation) {
        if (binding == null || settings == null || renderer == null) return;
        switch (invalidation) {
            case ROWS:
                renderer.refreshRows(settings.model(screen));
                break;
            case STORAGE_USAGE:
                if (screen == MainScreen.STORAGE_SETTINGS) {
                    renderer.refreshStorageUsage(settings.model(screen));
                }
                break;
            case CONSOLE:
                refreshConsole();
                break;
        }
    }

    private void addDeveloperActions() {
        Button bindings = new Button(requireContext());
        bindings.setText(R.string.edit_key_bindings);
        bindings.setOnClickListener(ignored ->
                navigator.navigate(MainScreen.DEVELOPER_BUTTON_BINDINGS));
        binding.settingsList.addView(bindings);

        Button users = new Button(requireContext());
        users.setText(R.string.manage_developer_users);
        users.setOnClickListener(ignored -> navigator.navigate(MainScreen.DEVELOPER_USERS));
        binding.settingsList.addView(users);

        Button removeDeviceOwner = new Button(requireContext());
        removeDeviceOwner.setText(R.string.remove_device_owner);
        removeDeviceOwner.setEnabled(settings.isDeviceOwner());
        removeDeviceOwner.setOnClickListener(ignored ->
                showRemoveDeviceOwnerConfirmation(removeDeviceOwner));
        binding.settingsList.addView(removeDeviceOwner);
    }

    private void addButtonBindingActions() {
        Button reset = new Button(requireContext());
        reset.setText("Reset device defaults");
        reset.setOnClickListener(ignored -> showResetButtonBindingsConfirmation());
        binding.settingsList.addView(reset);

        renderer.section(binding.settingsList, "Key event console");
        consoleView = new TextView(requireContext());
        consoleView.setBackgroundResource(R.drawable.bg_setting_card);
        consoleView.setMinHeight(dp(120));
        consoleView.setPadding(dp(14), dp(10), dp(14), dp(10));
        consoleView.setTextColor(Color.WHITE);
        consoleView.setTextSize(14);
        consoleView.setTypeface(Typeface.MONOSPACE);
        binding.settingsList.addView(consoleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        refreshConsole();
    }

    private void refreshConsole() {
        if (consoleView == null || settings == null) return;
        String text = settings.buttonConsoleText();
        consoleView.setText(text.isEmpty() ? "Waiting for key events" : text);
    }

    private void showResetButtonBindingsConfirmation() {
        if (!settings.hasButtonDefaults()) {
            resetDialog = new AlertDialog.Builder(requireContext())
                    .setTitle("No device defaults")
                    .setMessage("No default button preset exists for this device.")
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(ignored -> resetDialog = null)
                    .show();
            return;
        }
        resetDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Reset device defaults?")
                .setMessage("Replace current button bindings with device preset?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes,
                        (ignored, which) -> settings.resetButtonDefaults())
                .setOnDismissListener(ignored -> resetDialog = null)
                .show();
    }

    private void showRemoveDeviceOwnerConfirmation(Button button) {
        if (!settings.isDeviceOwner()) {
            button.setEnabled(false);
            FloatingNotice.show(requireContext(), R.string.remove_device_owner_unavailable);
            return;
        }
        removeDeviceOwnerDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.remove_device_owner_title)
                .setMessage(R.string.remove_device_owner_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove_device_owner, (ignored, which) -> {
                    SettingsScreenCoordinator.RemoveDeviceOwnerResult result =
                            settings.removeDeviceOwner();
                    if (result == SettingsScreenCoordinator.RemoveDeviceOwnerResult.REMOVED) {
                        button.setEnabled(false);
                        FloatingNotice.show(requireContext(), R.string.remove_device_owner_success);
                    } else if (result == SettingsScreenCoordinator.RemoveDeviceOwnerResult.FAILED) {
                        FloatingNotice.show(requireContext(), R.string.remove_device_owner_failed);
                    } else {
                        button.setEnabled(false);
                        FloatingNotice.show(requireContext(),
                                R.string.remove_device_owner_unavailable);
                    }
                })
                .setOnDismissListener(ignored -> removeDeviceOwnerDialog = null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private MainScreen requireScreen() {
        String name = requireArguments().getString(ARG_SCREEN);
        if (name == null) throw new IllegalArgumentException("Missing settings screen argument");
        final MainScreen parsedScreen;
        try {
            parsedScreen = MainScreen.valueOf(name);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid settings screen argument " + name, error);
        }
        if (!new SettingsScreenCatalog().isSettingsScreen(parsedScreen)) {
            throw new IllegalArgumentException("Route is not a settings screen: " + parsedScreen);
        }
        return parsedScreen;
    }

    public static Bundle arguments(MainScreen screen) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SCREEN, screen.name());
        return arguments;
    }
}

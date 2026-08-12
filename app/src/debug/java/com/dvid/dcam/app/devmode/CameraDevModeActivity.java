package com.dvid.dcam.app.devmode;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.dvid.dcam.app.AppComposition;
import com.dvid.dcam.app.CameraPipelineModeController;
import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import com.dvid.dcam.feature.device.application.usecase.DebugCameraBenchmarkUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class CameraDevModeActivity extends Activity {
    private static final String[] MODE_OPTIONS = {"AUTO", "A", "B"};
    private static final String[] SCOPE_OPTIONS = {"ALL_CAMERAS", "SINGLE_CAMERA"};
    private static final String[] PIPELINE_OPTIONS = {"A", "B", "BOTH"};
    private static final String[] CODEC_OPTIONS = {"H.264", "H.265"};
    private static final String[] ORDER_OPTIONS = {"A", "B"};
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Consumer<DebugCameraBenchmarkUseCase.Progress> progressObserver =
            value -> mainHandler.post(() -> showProgress(value));
    private final Consumer<DebugCameraBenchmarkUseCase.RunResult> resultObserver =
            value -> mainHandler.post(() -> showResult(value));
    private DebugCameraDevModeComposition composition;
    private AppComposition appComposition;
    private Spinner mode;
    private Spinner cameraScope;
    private Spinner cameraId;
    private Spinner pipeline;
    private Spinner codec;
    private Spinner initialOrder;
    private EditText measuredBlocks;
    private EditText cooldown;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView resultText;
    private Button runButton;
    private Button cancelButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        composition = DebugCameraDevModeComposition.create(this);
        appComposition = AppComposition.create(this);
        setTitle("Camera DevMode A/B");
        setContentView(buildContent());
        composition.benchmark().subscribeProgress(progressObserver);
        composition.benchmark().subscribeResult(resultObserver);
        restoreBenchmarkState();
        requestMissingPermissions();
        loadCameras();
    }

    @Override protected void onDestroy() {
        if (composition != null) {
            composition.benchmark().unsubscribeProgress(progressObserver);
            composition.benchmark().unsubscribeResult(resultObserver);
        }
        super.onDestroy();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column();
        root.setPadding(24, 24, 24, 24);
        root.addView(label("Persisted mode"));
        mode = spinner(MODE_OPTIONS);
        mode.setSelection(optionIndex(
                MODE_OPTIONS, appComposition.cameraPipelineMode().name()));
        mode.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override public void selected(int position) {
                DeveloperSettingsStore.Mode selected = DeveloperSettingsStore.Mode.valueOf(
                        mode.getSelectedItem().toString());
                if (selected != appComposition.cameraPipelineMode()) selectPipelineMode(selected);
            }
        });        root.addView(mode);

        root.addView(label("Codec"));
        codec = spinner(CODEC_OPTIONS);
        root.addView(codec);
        root.addView(label("Camera scope"));
        cameraScope = spinner(SCOPE_OPTIONS);
        root.addView(cameraScope);
        root.addView(label("Camera"));
        cameraId = spinner(new String[] {"loading"});
        root.addView(cameraId);
        root.addView(label("Pipeline"));
        pipeline = spinner(PIPELINE_OPTIONS);
        root.addView(pipeline);
        root.addView(label("Measured blocks"));
        measuredBlocks = numberField("1");
        root.addView(measuredBlocks);
        root.addView(label("Initial order"));
        initialOrder = spinner(ORDER_OPTIONS);
        root.addView(initialOrder);
        root.addView(label("Cooldown milliseconds"));
        cooldown = numberField("0");
        root.addView(cooldown);

        LinearLayout actions = row();
        runButton = button("Run");
        cancelButton = button("Cancel");
        Button exportButton = button("Export report");
        Button resetButton = button("Reset auto recommendation");
        cancelButton.setEnabled(false);
        actions.addView(runButton);
        actions.addView(cancelButton);
        actions.addView(exportButton);
        root.addView(actions);
        root.addView(resetButton);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(4);
        root.addView(progressBar);
        progressText = label("Idle");
        resultText = label("No report");
        root.addView(progressText);
        root.addView(resultText);

        runButton.setOnClickListener(ignored -> runBenchmark());
        cancelButton.setOnClickListener(ignored -> {
            composition.benchmark().cancel();
            progressText.setText("Cancel requested");
        });
        exportButton.setOnClickListener(ignored -> exportReport());
        resetButton.setOnClickListener(ignored -> {
            selectPipelineMode(DeveloperSettingsStore.Mode.AUTO);
            resultText.setText("AUTO selected");
        });        scroll.addView(root);
        return scroll;
    }

    private void selectPipelineMode(DeveloperSettingsStore.Mode requested) {
        if (appComposition == null) return;
        appComposition.selectCameraPipelineMode(requested, result -> {
            if (result == CameraPipelineModeController.Result.APPLIED) {
                mainHandler.post(() -> mode.setSelection(optionIndex(MODE_OPTIONS,
                        requested.name())));
            } else {
                mainHandler.post(() -> resultText.setText("Pipeline mode change rejected: "
                        + result.name()));
            }
        });
    }
    private void restoreBenchmarkState() {
        if (composition.benchmark().isRunning()) {
            runButton.setEnabled(false);
            cancelButton.setEnabled(true);
            composition.benchmark().latestProgress().ifPresent(this::showProgress);
        } else {
            composition.benchmark().latestResult().ifPresent(this::showResult);
        }
    }

    private void loadCameras() {
        composition.executor().execute(() -> {
            List<String> ids = composition.benchmark().cameraIds();
            mainHandler.post(() -> setCameraIds(ids));
        });
    }

    private void setCameraIds(List<String> ids) {
        List<String> values = ids.isEmpty() ? List.of("unavailable") : ids;
        cameraId.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values));
    }

    private void runBenchmark() {
        if (composition.benchmark().isRunning()) return;
        DebugCameraBenchmarkUseCase.Request request;
        try {
            request = new DebugCameraBenchmarkUseCase.Request(
                    DebugCameraBenchmarkUseCase.PipelineSelection.valueOf(
                            pipeline.getSelectedItem().toString()),
                    DebugCameraBenchmarkUseCase.Scope.valueOf(
                            cameraScope.getSelectedItem().toString()),
                    selectedCameraId(),
                    "H.264".equals(codec.getSelectedItem().toString())
                            ? DebugCameraBenchmarkUseCase.Codec.H264
                            : DebugCameraBenchmarkUseCase.Codec.H265,
                    parsePositive(measuredBlocks, "measured blocks"),
                    DebugCameraBenchmarkUseCase.InitialOrder.valueOf(
                            initialOrder.getSelectedItem().toString()),
                    parseNonNegative(cooldown, "cooldown"));
        } catch (IllegalArgumentException error) {
            resultText.setText(error.getMessage());
            return;
        }
        runButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setProgress(0);
        progressText.setText("Starting");
        composition.executor().execute(() -> composition.benchmark().execute(
                request, ignored -> {}));
    }

    private Optional<String> selectedCameraId() {
        Object selected = cameraId.getSelectedItem();
        if (selected == null || "unavailable".equals(selected.toString())
                || "loading".equals(selected.toString())) return Optional.empty();
        return Optional.of(selected.toString());
    }

    private void showProgress(DebugCameraBenchmarkUseCase.Progress value) {
        progressBar.setMax(Math.max(1, value.total()));
        progressBar.setProgress(value.completed());
        progressText.setText(value.stage() + " " + value.completed() + "/"
                + value.total() + " " + value.detail());
    }

    private void showResult(DebugCameraBenchmarkUseCase.RunResult result) {
        runButton.setEnabled(true);
        cancelButton.setEnabled(false);
        resultText.setText(result.status() + "\n" + result.summary()
                + result.reportPath().map(value -> "\nreport=" + value).orElse(""));
    }

    private void exportReport() {
        DebugCameraBenchmarkUseCase.ExportResult result = composition.benchmark().exportLastReport();
        resultText.setText(result.detail()
                + result.reportPath().map(value -> "\nreport=" + value).orElse(""));
    }

    private void requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.CAMERA);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 13);
    }

    private LinearLayout column() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        return value;
    }

    private LinearLayout row() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.HORIZONTAL);
        return value;
    }

    private TextView label(String text) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setPadding(0, 8, 0, 8);
        return value;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values));
        return spinner;
    }

    private EditText numberField(String value) {
        EditText field = new EditText(this);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        field.setText(value);
        return field;
    }

    private Button button(String text) {
        Button value = new Button(this);
        value.setText(text);
        return value;
    }

    private static int optionIndex(String[] options, String value) {
        for (int index = 0; index < options.length; index++) {
            if (options[index].equals(value)) return index;
        }
        return 0;
    }

    private static int parsePositive(EditText field, String name) {
        int value = Integer.parseInt(field.getText().toString().trim());
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static long parseNonNegative(EditText field, String name) {
        long value = Long.parseLong(field.getText().toString().trim());
        if (value < 0) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private abstract static class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        @Override public final void onItemSelected(android.widget.AdapterView<?> parent,
                View view, int position, long id) { selected(position); }
        abstract void selected(int position);
    }
}

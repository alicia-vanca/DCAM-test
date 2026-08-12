package com.dvid.dcam.app;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CameraCutoverSourceTest {

    @Test void debugPipelineSelectorReadsAndWritesActiveCameraMode() throws IOException {
        String composition = source("app/AppComposition.java");
        String activity = Files.readString(debugSourceRoot().resolve(
                "com/dvid/dcam/app/devmode/CameraDevModeActivity.java"));

        assertTrue(composition.contains("activeCameraId().map(cameraPipelineSettings::mode)"));
        assertTrue(activity.contains("if (selected != appComposition.cameraPipelineMode())"));
        assertTrue(activity.contains("appComposition.selectCameraPipelineMode(requested, result ->"));
    }

    @Test void deniedDeveloperPipelineSelectionKeepsCurrentRadioChecked() throws IOException {
        String activity = source("app/MainActivity.java");
        String renderer = source("app/ui/settings/SettingsControlRenderer.java");
        int guardStart = activity.indexOf("private boolean canSelectDeveloperSetting(");
        int guardEnd = activity.indexOf("private void selectDeveloperSetting(", guardStart);
        String guard = activity.substring(guardStart, guardEnd);
        int radioStart = renderer.indexOf("private void describedRadio(");
        int radioEnd = renderer.indexOf("private CharSequence describedRadioText(", radioStart);
        String radio = renderer.substring(radioStart, radioEnd);

        assertTrue(activity.contains("this::canSelectDeveloperSetting"));
        assertTrue(guard.contains("composition.canSelectCameraPipelineMode(cameraId.orElseThrow())"));
        assertTrue(guard.contains("FloatingNotice.show(this, R.string.camera_pipeline_busy);"));
        assertTrue(radio.contains("@Override public boolean performClick()"));
        assertTrue(radio.contains("if (canSelect != null && !canSelect.getAsBoolean()) return true;"));
        assertTrue(radio.contains("return super.performClick();"));
        assertFalse(radio.contains("setOnTouchListener"));
    }

    @Test void recordingBlocksOnlyActiveCameraPipelineSelection() throws IOException {
        String composition = source("app/AppComposition.java");
        int publicGuardStart = composition.indexOf(
                "public boolean canSelectCameraPipelineMode(String cameraId)");
        int publicGuardEnd = composition.indexOf(
                "private boolean cameraPipelineSelectionInFlight()", publicGuardStart);
        String publicGuard = composition.substring(publicGuardStart, publicGuardEnd);
        int backendGuardStart = composition.indexOf(
                "@Override public boolean canSwitch(String cameraId)");
        int backendGuardEnd = composition.indexOf(
                "@Override public Optional<CandidateKey> activeCandidate()", backendGuardStart);
        String backendGuard = composition.substring(backendGuardStart, backendGuardEnd);
        int recordingGuardStart = composition.indexOf(
                "private boolean cameraRecordingActive(String cameraId)");
        int recordingGuardEnd = composition.indexOf(
                "public OptionalInt cameraPipelineCaptureTupleCount", recordingGuardStart);
        String recordingGuard = composition.substring(recordingGuardStart, recordingGuardEnd);

        assertTrue(publicGuard.contains("cameraRecordingActive(cameraId)"));
        assertFalse(publicGuard.contains("cameraRecordingActive())"));
        assertTrue(backendGuard.contains("cameraRecordingActive(cameraId)"));
        assertFalse(backendGuard.contains("cameraRecordingActive()"));
        assertTrue(recordingGuard.contains("cameraRecordingActive()"));
        assertTrue(recordingGuard.contains(
                "activeCameraId().filter(cameraId::equals).isPresent()"));
    }

    @Test void inactivePipelineChangeUsesQuickScanWithoutRuntimeBinding() throws IOException {
        String composition = source("app/AppComposition.java");
        String service = source("platform/device/capability/CameraCapabilityService.java");
        int prepareStart = composition.indexOf(
                "@Override public void prepare(String cameraId, DeveloperSettingsStore.Mode mode,");
        int prepareEnd = composition.indexOf(
                "@Override public Optional<CandidateKey> resolve", prepareStart);
        String prepare = composition.substring(prepareStart, prepareEnd);
        int targetedStart = service.indexOf(
                "public void ensurePipelineEvidence(String cameraId,");
        int targetedEnd = service.indexOf("private void requestScan(", targetedStart);
        String targeted = service.substring(targetedStart, targetedEnd);

        assertTrue(prepare.contains("if (!activeCamera)"));
        assertTrue(prepare.contains(
                "cameraCapabilities.ensurePipelineEvidence(cameraId, pipeline, completion)"));
        assertTrue(targeted.contains("scanExecutor.execute"));
        assertTrue(targeted.contains("quickScanPipelineEvidence"));
        assertTrue(targeted.contains("targetedCatalog"));
        assertFalse(targeted.contains("runtimeOwner"));
        assertFalse(targeted.contains("releaseCamera"));
        assertFalse(targeted.contains("requestScan("));
    }

    @Test void appStartupBuildsBothPipelinesOnlyWhenXmlEvidenceIsMissing()
            throws IOException {
        String composition = source("app/AppComposition.java");
        String service = source("platform/device/capability/CameraCapabilityService.java");
        int startupStart = composition.indexOf(
                "public static synchronized CameraCapabilityService startCameraCapabilities(");
        int startupEnd = composition.indexOf(
                "public static synchronized CameraCapabilityService cameraCapabilities(",
                startupStart);
        String startup = composition.substring(startupStart, startupEnd);
        int requestStart = service.indexOf("public void requestStartupFastCollection(");
        int workerStart = service.indexOf("private void runStartupFastCollectionPreflight(",
                requestStart);
        int bootstrapStart = service.indexOf("public void requestBootstrapScan(", workerStart);
        int bootstrapEnd = service.indexOf(
                "public void ensurePipelineEvidence(", bootstrapStart);
        String request = service.substring(requestStart, workerStart);
        String worker = service.substring(workerStart, bootstrapStart);
        String bootstrap = service.substring(bootstrapStart, bootstrapEnd);

        assertTrue(startup.contains("service.requestStartupFastCollection();"));
        assertTrue(request.contains(
                "scanExecutor.execute(() -> runStartupFastCollectionPreflight(required))"));
        assertFalse(request.contains("new AndroidCameraCatalogSource"));
        assertTrue(worker.contains("store.load(CameraCapabilitySnapshotMapper.freshness(catalog))"));
        assertTrue(worker.contains("hasCompleteStartupFastCollection(loaded, required)"));
        assertTrue(worker.contains("finishCallbacksLocked();"));
        assertTrue(worker.contains("requestScan(required, null, null, null);"));
        assertTrue(bootstrap.contains("scanPipelinesForSelection(Optional.empty())"));
        assertFalse(bootstrap.contains("scanPipelinesForConfiguredSelections()"));
        assertTrue(service.contains("fastResults.size() != pipelines.size()"));
        assertTrue(service.contains(
                "if (startupFastCollectionInFlight || scanInFlight || scanAwaitingRuntimeRelease)"));
        assertTrue(service.contains("reason=scan_in_progress"));
    }
    @Test void capabilityRecheckAvoidsClickRerenderAndRestoresTerminalScroll()
            throws IOException {
        String activity = source("app/MainActivity.java");
        String renderer = source("app/ui/settings/SettingsControlRenderer.java");
        int action = activity.indexOf("if (id == SettingId.RECHECK_CAMERA_CAPABILITIES) {");
        int nextAction = activity.indexOf("if (id == SettingId.WIFI_CONNECT)", action);
        String actionBody = activity.substring(action, nextAction);

        assertFalse(activity.contains("rerenderCurrentScreenPreservingScroll"));
        assertFalse(actionBody.contains("render(latestState)"));
        assertTrue(activity.contains("refreshCurrentSettingsControls();"));
        assertTrue(activity.contains("settingsRenderer.refreshRows(settingsModel(screen));"));
        assertTrue(renderer.contains("public void refreshRows(SettingsScreenModel model)"));
        assertTrue(renderer.contains("if (!refreshingRows && onChanged != null)"));
        assertTrue(renderer.contains("ChoiceRowState state = new ChoiceRowState"));
        assertTrue(renderer.contains("state.options = List.copyOf(options)"));
        assertTrue(renderer.contains("ViewGroup group = storageGroup(row)"));
        int onStart = activity.indexOf("protected void onStart()");
        int onStop = activity.indexOf("protected void onStop()", onStart);
        int afterStop = activity.indexOf("private void subscribeCameraCapabilityRecheck", onStop);
        assertTrue(activity.substring(onStart, onStop)
                .contains("subscribeCameraCapabilityRecheck();"));
        assertTrue(activity.substring(onStop, afterStop)
                .contains("unsubscribeCameraCapabilityRecheck();"));
    }

    @Test void failedRecheckKeepsCaptureCommandsBlockedWithoutPendingRecording()
            throws IOException {
        String composition = source("app/AppComposition.java");

        assertTrue(composition.contains("this::cameraCaptureCommandsBlocked"));
        assertTrue(composition.contains(
                "cameraFlow.state() == CameraFlowCoordinator.State.UNAVAILABLE"));
        assertTrue(composition.contains(
                "recordingCamera, this::cameraPhotoCaptureAllowed"));
        assertTrue(composition.contains(
                "runtime.state() == CameraRuntimeState.RECORDING"));
    }

    @Test void cameraSwitchUsesCoordinatorIntentAndRuntimeRecordingState()
            throws IOException {
        String composition = source("app/AppComposition.java");

        assertTrue(composition.contains("private boolean cameraRecordingActive()"));
        assertTrue(composition.contains(
                "recordingCoordinator.currentMode() != RecordingMode.IDLE"));
        assertTrue(composition.contains(
                "operation == ProcessCameraRuntimeBackend.Operation.START_RECORDING"));
        assertTrue(composition.contains(
                "operation == ProcessCameraRuntimeBackend.Operation.STOP_RECORDING"));
        assertTrue(composition.contains("return cameraRecordingActive();"));
    }

    @Test void capabilityRecheckUsesRuntimeRecordingPolicy() throws IOException {
        String composition = source("app/AppComposition.java");
        String activity = source("app/MainActivity.java");

        String controller = source("app/CameraPipelineModeController.java");
        assertTrue(composition.contains(
                "public boolean cameraCapabilityRecheckControlEnabled()"));
        assertTrue(composition.contains(
                "if (!cameraCapabilityRecheckControlEnabled()) return false;"));
        assertTrue(activity.contains(
                "!composition.cameraCapabilityRecheckControlEnabled()"));
    }

    @Test void autoPipelineDescriptionUsesAutoDecisionInsteadOfCurrentSelection()
            throws IOException {
        String activity = source("app/MainActivity.java");
        int autoOption = activity.indexOf(
                "private DescribedRadioOptionUiState cameraPipelineAutoOption(");
        int nextOption = activity.indexOf(
                "private DescribedRadioOptionUiState cameraPipelineOption(", autoOption);
        String option = activity.substring(autoOption, nextOption);

        assertTrue(option.contains("composition.cameraPipelineAutoSelections()"));
        assertFalse(option.contains("composition.cameraPipelineSelections()"));
    }

    @Test void devModeComparisonPublishesSnapshotForAutoSelection() throws IOException {
        String engine = Files.readString(debugSourceRoot().resolve(
                "com/dvid/dcam/platform/camera/shared/benchmark/DebugCameraPipelineBenchmarkEngine.java"));
        String composition = Files.readString(debugSourceRoot().resolve(
                "com/dvid/dcam/app/devmode/DebugCameraDevModeComposition.java"));

        assertTrue(engine.contains("new CompareCameraPipelinesUseCase("));
        assertTrue(engine.contains("CompareCameraPipelinesUseCase.Result result = useCase.execute("));
        assertTrue(engine.contains("capabilityStore.requestWrite(snapshot)"));
        assertFalse(engine.contains("Snapshot ignoredSnapshot"));
        assertTrue(composition.contains("AppComposition.cameraCapabilities(checkedContext, logger)"));
    }

    @Test void forcedPipelineDoesNotRequireEveryCameraToSupportIt() throws IOException {
        String service = source("platform/device/capability/CameraCapabilityService.java");

        assertFalse(service.contains("if (!present) return null"));
        assertTrue(service.contains("selectCameraPipeline(camera, pipeline, selectionResolver)"));
        assertTrue(service.contains(": unavailableCamera(camera);"));
    }

    @Test void legacyCameraRuntimeAndRolloutValuesAreDeleted() throws IOException {
        Path root = sourceRoot().resolve("com/dvid/dcam");
        assertFalse(Files.exists(root.resolve("platform/camera/CameraXCameraGatewayImpl.java")));
        assertFalse(Files.exists(root.resolve("platform/camera/CameraXPreviewView.java")));
        assertFalse(Files.exists(root.resolve("platform/camera/Camera2CaptureController.java")));
        assertFalse(Files.exists(root.resolve("platform/camera/CameraBackendSelection.java")));
        assertFalse(Files.exists(root.resolve("platform/device/AndroidDeviceCapabilities.java")));
        String production = productionSources();
        assertFalse(production.contains("requiresCamera2"));
        assertFalse(production.contains("Mode.LEGACY"));
        assertFalse(production.contains("Mode.SHADOW"));
    }

    @Test void cameraXStorageBridgeAndDependenciesAreDeleted() throws IOException {
        Path root = sourceRoot().resolve("com/dvid/dcam");
        String mediaOutput = source("platform/storage/DcamMediaOutput.java");
        String mediaOutputImpl = source("platform/storage/DcamMediaOutputImpl.java");
        String build = Files.readString(appModuleRoot().resolve("build.gradle"));

        assertFalse(productionSources().contains("androidx.camera"));
        assertFalse(build.contains("androidx.camera"));
        assertFalse(Files.exists(root.resolve(
                "platform/storage/CaptureStorageFailureClassifier.java")));
        assertFalse(Files.exists(root.resolve("platform/storage/DcamMediaStore.java")));
        assertFalse(mediaOutput.contains("ImageCapture"));
        assertFalse(mediaOutput.contains("PendingRecording"));
        assertFalse(mediaOutput.contains("savedUri"));
        assertFalse(mediaOutputImpl.contains("usesPublicMediaStore"));
        assertTrue(mediaOutputImpl.contains("storage.prepareFile(mediaFile);"));
    }


    @Test void encryptionLegacyMigrationIsRemoved() throws IOException {
        String encryptionStore = source(
                "platform/config/AndroidMediaEncryptionPreferenceStoreImpl.java");

        assertFalse(encryptionStore.contains("migrateLegacyConfigs("));
        assertFalse(encryptionStore.contains("Files.readString("));
        assertFalse(encryptionStore.contains("Files.readAllBytes("));
    }

    @Test void cameraStartupUsesAndroidSupportedCollectionApis() throws IOException {
        String service = source("platform/device/capability/CameraCapabilityService.java");
        String resolver = source(
                "feature/device/application/usecase/ResolveCameraRuntimeSelectionUseCase.java");

        assertFalse(service.contains(".toList()"));
        assertFalse(resolver.contains(".toList()"));
    }

    private static String source(String suffix) throws IOException {
        try (var paths = Files.walk(sourceRoot())) {
            return Files.readString(paths
                    .filter(path -> path.toString().replace('\\', '/').endsWith(suffix))
                    .findFirst().orElseThrow());
        }
    }

    private static String productionSources() throws IOException {
        StringBuilder result = new StringBuilder();
        try (var paths = Files.walk(sourceRoot())) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(path)).append('\n');
            }
        }
        return result.toString();
    }


    private static Path sourceRoot() {
        Path app = Path.of("app/src/main/java");
        return Files.exists(app) ? app : Path.of("src/main/java");
    }

    private static Path appModuleRoot() {
        Path app = Path.of("app/build.gradle");
        return Files.exists(app) ? Path.of("app") : Path.of(".");
    }

    private static Path debugSourceRoot() {
        Path app = Path.of("app/src/debug/java");
        return Files.exists(app) ? app : Path.of("src/debug/java");
    }
}

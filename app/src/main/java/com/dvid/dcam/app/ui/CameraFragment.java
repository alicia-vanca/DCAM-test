package com.dvid.dcam.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.dvid.dcam.databinding.ScreenCameraBinding;

/** Persistent camera view; capture runtime and preview ownership stay in MainActivity. */
public final class CameraFragment extends Fragment implements CameraScreenView {
    private ScreenCameraBinding binding;
    private MainUiScope.CameraActions cameraActions;

    public CameraFragment() {}

    @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ScreenCameraBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        cameraActions = MainUiScope.require(this).cameraActions();
        binding.cameraSwitchAction.setOnClickListener(ignored ->
                cameraActions.onCameraSwitchRequested());
        new ViewModelProvider(requireActivity()).get(MainViewModel.class).state()
                .observe(getViewLifecycleOwner(), state -> {
                    if (binding == null) return;
                    binding.operatorId.setText(state.getOperatorSession() == null ? "USER —"
                            : "USER " + state.getOperatorSession().getFileUserId());
                });
        cameraActions.onCameraViewAttached(this);
    }

    @Override public void onDestroyView() {
        if (cameraActions != null) cameraActions.onCameraViewDetached(this);
        cameraActions = null;
        binding = null;
        super.onDestroyView();
    }

    @Override public FrameLayout previewContainer() { return requireBinding().previewContainer; }
    @Override public void setRootAlpha(float alpha) { requireBinding().getRoot().setAlpha(alpha); }
    @Override public void post(Runnable action) { requireBinding().getRoot().post(action); }
    @Override public void setIdentity(String value) { requireBinding().accountId.setText(value); }
    @Override public void setGps(boolean visible, String value) {
        requireBinding().gpsStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
        requireBinding().gpsStatus.setText(visible ? value : "");
    }
    @Override public void setCameraSwitch(boolean visible, boolean enabled) {
        requireBinding().cameraSwitchAction.setVisibility(visible ? View.VISIBLE : View.GONE);
        requireBinding().cameraSwitchAction.setEnabled(enabled);
        requireBinding().cameraSwitchAction.setAlpha(enabled ? 1f : 0.45f);
    }
    @Override public void setRecordingBadgeAlpha(float alpha) {
        requireBinding().recordingBadge.setAlpha(alpha);
    }
    @Override public void renderRecordingClock(String currentTime, boolean videoVisible,
            String videoDuration, boolean audioVisible, String audioDuration) {
        ScreenCameraBinding value = requireBinding();
        value.currentTime.setText(currentTime);
        value.videoRecordingStatus.setVisibility(videoVisible ? View.VISIBLE : View.GONE);
        value.audioRecordingStatus.setVisibility(audioVisible ? View.VISIBLE : View.GONE);
        value.recordingTimer.setText(videoDuration);
        value.audioRecordingTimer.setText(audioDuration);
    }

    private ScreenCameraBinding requireBinding() {
        if (binding == null) throw new IllegalStateException("Camera view is not created");
        return binding;
    }
}

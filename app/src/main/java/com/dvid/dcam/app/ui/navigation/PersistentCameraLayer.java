package com.dvid.dcam.app.ui.navigation;

import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.dvid.dcam.app.ui.CameraFragment;
import com.dvid.dcam.app.ui.CameraScreenView;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the one persistent camera Fragment below the replaceable foreground layer. */
public final class PersistentCameraLayer implements MainScreenRouter.CameraLayer {
    private final FragmentManager fragments;
    private final int containerId;
    private final String tag;
    private final View foregroundLayer;
    private final Supplier<CameraScreenView> cameraView;
    private final Consumer<CameraScreenView> restoredView;

    public PersistentCameraLayer(FragmentManager fragments, int containerId, String tag,
            View foregroundLayer, Supplier<CameraScreenView> cameraView,
            Consumer<CameraScreenView> restoredView) {
        this.fragments = Objects.requireNonNull(fragments, "fragments");
        this.containerId = containerId;
        this.tag = Objects.requireNonNull(tag, "tag");
        this.foregroundLayer = Objects.requireNonNull(foregroundLayer, "foregroundLayer");
        this.cameraView = Objects.requireNonNull(cameraView, "cameraView");
        this.restoredView = Objects.requireNonNull(restoredView, "restoredView");
    }

    @Override public boolean ensureCamera() {
        if (cameraView.get() != null) return true;
        if (fragments.isStateSaved()) return false;
        Fragment existing = fragments.findFragmentByTag(tag);
        if (existing == null) {
            fragments.beginTransaction().setReorderingAllowed(true)
                    .add(containerId, new CameraFragment(), tag).commitNow();
        } else if (!(existing instanceof CameraFragment)) {
            throw new IllegalStateException("Persistent camera tag belongs to "
                    + existing.getClass().getName());
        } else if (existing.isDetached()) {
            fragments.beginTransaction().setReorderingAllowed(true)
                    .attach(existing).commitNow();
        } else if (existing.getView() != null) {
            restoredView.accept((CameraFragment) existing);
        }
        return cameraView.get() != null;
    }

    @Override public void showCamera() {
        CameraScreenView view = cameraView.get();
        if (view != null) view.setRootAlpha(1f);
        foregroundLayer.setVisibility(View.GONE);
    }

    @Override public void coverCamera() {
        CameraScreenView view = cameraView.get();
        if (view != null) view.setRootAlpha(0f);
        foregroundLayer.setVisibility(View.VISIBLE);
    }
}

package com.dvid.dcam.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.dvid.dcam.app.ui.navigation.MainNavigator;
import com.dvid.dcam.databinding.ScreenFileExplorerBinding;

public final class FileExplorerFragment extends Fragment {
    private ScreenFileExplorerBinding binding;
    private MainNavigator.Registration backRegistration;

    public FileExplorerFragment() {}

    @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ScreenFileExplorerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        MainUiScope scope = MainUiScope.require(this);
        MainUiScope.MediaOpener mediaOpener = scope.mediaOpener();
        MainNavigator navigator = scope.navigator();
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        MediaBrowserRenderer renderer = new MediaBrowserRenderer(requireContext(),
                getLayoutInflater(), binding, viewModel::openMediaFolder,
                mediaOpener::openMedia);
        viewModel.state().observe(getViewLifecycleOwner(), state ->
                renderer.render(state.getMediaBrowser()));
        backRegistration = navigator.registerBackHandler(MainScreen.FILES,
                getViewLifecycleOwner(), () -> viewModel.navigateMediaUp()
                        ? MainNavigator.BackResult.HANDLED
                        : MainNavigator.BackResult.AT_ROOT);
    }

    @Override public void onDestroyView() {
        if (backRegistration != null) backRegistration.close();
        backRegistration = null;
        binding = null;
        super.onDestroyView();
    }
}

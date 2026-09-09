package com.dvid.dcam.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.dvid.dcam.databinding.ScreenDeveloperUsersBinding;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserSource;

public final class DeveloperUsersFragment extends Fragment {
    private ScreenDeveloperUsersBinding binding;

    public DeveloperUsersFragment() {}

    @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ScreenDeveloperUsersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        binding.saveAction.setOnClickListener(ignored -> viewModel.provisionUser(
                new UserProvisioningRequest(binding.userId.getText().toString(),
                        binding.displayName.getText().toString(),
                        binding.password.getText().toString(), UserSource.DEVELOPER)));
        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) return;
            binding.saveAction.setEnabled(!state.isAuthenticationBusy());
            binding.status.setText(state.isAuthenticationBusy() ? "Saving..."
                    : MainUiMessageFormatter.localized(requireContext(), state.getMessage()));
        });
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}

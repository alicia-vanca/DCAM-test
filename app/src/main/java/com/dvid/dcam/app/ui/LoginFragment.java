package com.dvid.dcam.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.dvid.dcam.R;
import com.dvid.dcam.databinding.ScreenLoginBinding;

public final class LoginFragment extends Fragment {
    private ScreenLoginBinding binding;
    private MainUiScope.DatabaseResetter databaseResetter;
    private AlertDialog resetDialog;

    public LoginFragment() {}

    @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ScreenLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        databaseResetter = MainUiScope.require(this).databaseResetter();
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        View.OnClickListener login = ignored ->
                viewModel.loginPassword(binding.password.getText().toString());
        binding.loginAction.setOnClickListener(login);
        binding.password.setOnEditorActionListener((editor, actionId, event) -> {
            login.onClick(editor);
            return true;
        });
        binding.resetDatabaseAction.setOnClickListener(ignored -> {
            if (resetDialog != null) resetDialog.dismiss();
            resetDialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.reset_login_database_title)
                        .setMessage(R.string.reset_login_database_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.reset, (dialog, which) ->
                                databaseResetter.resetDatabaseAndRestart())
                        .setOnDismissListener(dialog -> resetDialog = null)
                        .show();
        });
        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) return;
            binding.loginAction.setEnabled(!state.isAuthenticationBusy());
            binding.password.setEnabled(!state.isAuthenticationBusy());
            binding.status.setText(state.isAuthenticationBusy() ? "Loading..."
                    : MainUiMessageFormatter.localized(requireContext(), state.getMessage()));
        });
    }

    @Override public void onDestroyView() {
        if (resetDialog != null) resetDialog.dismiss();
        resetDialog = null;
        databaseResetter = null;
        binding = null;
        super.onDestroyView();
    }
}

package com.dvid.dcam.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.dvid.dcam.app.ui.navigation.MainNavigator;
import com.dvid.dcam.databinding.ScreenMenuBinding;
import java.util.ArrayList;
import java.util.List;

public final class MenuFragment extends Fragment {
    private ScreenMenuBinding binding;

    public MenuFragment() {}

    @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ScreenMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        MainUiScope scope = MainUiScope.require(this);
        MainUiScope.MenuDependencies menuDependencies = scope.menuDependencies();
        MainNavigator navigator = scope.navigator();
        List<View> visibleTiles = new ArrayList<>();
        for (MainMenuTile tile : menuDependencies.visibleTiles()) {
            View tileView = binding.getRoot().findViewById(tile.getViewId());
            tileView.setVisibility(View.VISIBLE);
            tileView.setOnClickListener(ignored -> navigator.navigate(tile.getScreen()));
            visibleTiles.add(tileView);
        }
        layoutVisibleTiles(visibleTiles);
    }

    private void layoutVisibleTiles(List<View> tiles) {
        GridLayout grid = binding.settingsGrid;
        grid.removeAllViews();
        grid.setColumnCount(Math.max(1, Math.min(3, tiles.size())));
        for (View tile : tiles) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(124);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            grid.addView(tile, params);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}

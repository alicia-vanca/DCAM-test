package com.dvid.dcam.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.dvid.dcam.R;
import com.dvid.dcam.app.ui.media.MediaBrowserState;
import com.dvid.dcam.databinding.ItemMediaEntryBinding;
import com.dvid.dcam.databinding.ScreenFileExplorerBinding;
import com.dvid.dcam.feature.media.domain.MediaEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class MediaBrowserRenderer {
    private final Context context;
    private final LayoutInflater inflater;
    private final ScreenFileExplorerBinding binding;
    private final Consumer<String> openFolder;
    private final Predicate<MediaEntry> openFile;
    private final MediaEntryAdapter entriesAdapter = new MediaEntryAdapter();

    public MediaBrowserRenderer(
            Context context,
            LayoutInflater inflater,
            ScreenFileExplorerBinding binding,
            Consumer<String> openFolder,
            Predicate<MediaEntry> openFile) {
        this.context = context;
        this.inflater = inflater;
        this.binding = binding;
        this.openFolder = openFolder;
        this.openFile = openFile;
        binding.entries.setAdapter(entriesAdapter);
    }

    public void render(MediaBrowserState state) {
        String path = state.getRelativePath();
        binding.path.setText(path.isEmpty()
                ? context.getString(R.string.media_root) : path.replace("/", " / "));
        if (state.isLoading()) {
            entriesAdapter.replace(Collections.emptyList());
            binding.status.setText(R.string.media_loading);
            return;
        }
        if (state.getError() != null) {
            entriesAdapter.replace(Collections.emptyList());
            binding.status.setText(state.getError());
            return;
        }
        if (state.getEntries().isEmpty()) {
            entriesAdapter.replace(Collections.emptyList());
            binding.status.setText(R.string.media_empty);
            return;
        }
        binding.status.setText("");
        entriesAdapter.replace(state.getEntries());
    }

    private final class MediaEntryAdapter extends BaseAdapter {
        private final List<MediaEntry> entries = new ArrayList<>();

        private void replace(List<MediaEntry> nextEntries) {
            entries.clear();
            entries.addAll(nextEntries);
            notifyDataSetChanged();
        }

        @Override public int getCount() {
            return entries.size();
        }

        @Override public MediaEntry getItem(int position) {
            return entries.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ItemMediaEntryBinding row;
            if (convertView == null) {
                row = ItemMediaEntryBinding.inflate(inflater, parent, false);
                convertView = row.getRoot();
                convertView.setTag(row);
            } else {
                row = (ItemMediaEntryBinding) convertView.getTag();
            }
            MediaEntry entry = getItem(position);
            row.icon.setImageResource(entry.isDirectory()
                    ? R.drawable.ic_settings_files : R.drawable.ic_media_file);
            row.name.setText(entry.getName());
            boolean showFolderCount = entry.isDirectory()
                    && entry.getRelativePath().contains("/") && entry.hasChildFileCount();
            row.folderCounts.setVisibility(showFolderCount ? View.VISIBLE : View.GONE);
            row.fileDetails.setVisibility(entry.isDirectory() ? View.GONE : View.VISIBLE);
            row.fileCount.setText(String.valueOf(entry.getChildFileCount()));
            row.fileDetails.setText(formatFileSize(entry.getSizeBytes()));
            row.getRoot().setOnClickListener(view -> {
                if (entry.isDirectory()) openFolder.accept(entry.getRelativePath());
                else if (!openFile.test(entry)) FloatingNotice.show(context, R.string.media_open_failed);
            });
            return convertView;
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024d);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024d * 1024d));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }
}

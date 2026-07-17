package com.dvid.dcam.app.shell;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.dvid.dcam.R;
import com.dvid.dcam.feature.media.presentation.MediaBrowserState;
import com.dvid.dcam.databinding.ItemMediaEntryBinding;
import com.dvid.dcam.databinding.ScreenFileExplorerBinding;
import com.dvid.dcam.feature.media.domain.MediaEntry;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class MediaBrowserRenderer {
    private final Context context;
    private final LayoutInflater inflater;
    private final ScreenFileExplorerBinding binding;
    private final Consumer<String> openFolder;
    private final Predicate<MediaEntry> openFile;

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
    }

    public void render(MediaBrowserState state) {
        binding.entries.removeAllViews();
        String path = state.getRelativePath();
        binding.path.setText(path.isEmpty()
                ? context.getString(R.string.media_root) : path.replace("/", " / "));
        if (state.isLoading()) {
            binding.status.setText(R.string.media_loading);
            return;
        }
        if (state.getError() != null) {
            binding.status.setText(state.getError());
            return;
        }
        if (state.getEntries().isEmpty()) {
            binding.status.setText(R.string.media_empty);
            return;
        }
        binding.status.setText("");
        for (MediaEntry entry : state.getEntries()) {
            ItemMediaEntryBinding row = ItemMediaEntryBinding.inflate(
                    inflater, binding.entries, false);
            row.icon.setImageResource(entry.isDirectory()
                    ? R.drawable.ic_settings_files : R.drawable.ic_media_file);
            row.name.setText(entry.getName());
            boolean mediaFolder = entry.isDirectory() && entry.getRelativePath().contains("/");
            row.folderCounts.setVisibility(mediaFolder ? View.VISIBLE : View.GONE);
            row.fileDetails.setVisibility(entry.isDirectory() ? View.GONE : View.VISIBLE);
            row.fileCount.setText(String.valueOf(entry.getChildFileCount()));
            row.fileDetails.setText(formatFileSize(entry.getSizeBytes()));
            row.getRoot().setOnClickListener(view -> {
                if (entry.isDirectory()) openFolder.accept(entry.getRelativePath());
                else if (!openFile.test(entry)) FloatingNotice.show(context, R.string.media_open_failed);
            });
            binding.entries.addView(row.getRoot());
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
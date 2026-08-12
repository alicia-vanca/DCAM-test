package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.databinding.ScreenFileExplorerBinding;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class MediaBrowserRendererTest {
    @Test void mediaEntriesUseVirtualizedList() throws Exception {
        assertEquals("android.widget.ListView",
                ScreenFileExplorerBinding.class.getField("entries").getType().getName());
        Class<?> adapter = Arrays.stream(MediaBrowserRenderer.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("MediaEntryAdapter"))
                .findFirst()
                .orElseThrow();
        assertEquals("android.widget.BaseAdapter", adapter.getSuperclass().getName());
    }
}

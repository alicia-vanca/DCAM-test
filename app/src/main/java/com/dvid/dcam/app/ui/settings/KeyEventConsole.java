package com.dvid.dcam.app.ui.settings;

import java.util.ArrayDeque;
import java.util.Deque;

final class KeyEventConsole {
    private final int capacity;
    private final Deque<String> lines = new ArrayDeque<>();

    KeyEventConsole(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    String append(String keyCode, String action) {
        if (lines.size() == capacity) lines.removeFirst();
        lines.addLast(keyCode + " " + action);
        return text();
    }

    String text() { return String.join("\n", lines); }
}

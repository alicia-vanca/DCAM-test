package com.dvid.dcam.app.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class KeyEventConsoleTest {
    @Test void keepsNewestEventsWithinCapacity() {
        KeyEventConsole console = new KeyEventConsole(2);
        console.append("F3", "DOWN");
        console.append("F3", "UP");

        assertEquals("F3 UP\nF4 DOWN", console.append("F4", "DOWN"));
    }
}

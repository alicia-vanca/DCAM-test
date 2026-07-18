package com.dvid.dcam.platform.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CameraXPreviewViewTest {
    @Test void storageWarningCannotReplaceBlockingError() {
        CameraXPreviewView.MessageState state = new CameraXPreviewView.MessageState();

        assertTrue(state.show(CameraXPreviewView.MessageOwner.ERROR));
        assertFalse(state.show(CameraXPreviewView.MessageOwner.STORAGE));
        assertFalse(state.clear(CameraXPreviewView.MessageOwner.STORAGE));
        assertTrue(state.clear(CameraXPreviewView.MessageOwner.ERROR));
        assertTrue(state.show(CameraXPreviewView.MessageOwner.STORAGE));
    }
    @Test void messageCleanupOnlyClearsCurrentMessageOwner() {
        CameraXPreviewView.MessageState state = new CameraXPreviewView.MessageState();

        state.show(CameraXPreviewView.MessageOwner.STORAGE);
        state.show(CameraXPreviewView.MessageOwner.STARTING);
        assertFalse(state.clear(CameraXPreviewView.MessageOwner.STORAGE));
        assertTrue(state.clear(CameraXPreviewView.MessageOwner.STARTING));
        assertFalse(state.clear(CameraXPreviewView.MessageOwner.STARTING));
    }
}

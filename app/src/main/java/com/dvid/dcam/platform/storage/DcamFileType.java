package com.dvid.dcam.platform.storage;

public enum DcamFileType {
    VIDEO("Video", "mp4", "", "video/mp4"),
    IMP("IMP", "mp4", "IMP", "video/mp4"),
    IMAGE("Image", "jpg", "", "image/jpeg"),
    AUDIO("Audio", "aac", "", "audio/aac");

    private final String folder, extension, marker, mimeType;

    DcamFileType(String folder, String extension, String marker, String mimeType) {
        this.folder = folder; this.extension = extension; this.marker = marker; this.mimeType = mimeType;
    }
    public String getFolder() { return folder; }
    public String getExtension() { return extension; }
    public String getMarker() { return marker; }
    public String getMimeType() { return mimeType; }
}

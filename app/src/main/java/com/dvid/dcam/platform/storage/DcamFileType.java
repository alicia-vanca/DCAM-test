package com.dvid.dcam.platform.storage;

public enum DcamFileType {
    VIDEO("Video", "mp4", "", "video/mp4"),
    IMP("IMP", "mp4", "IMP", "video/mp4"),
    IMAGE("Image", "jpg", "", "image/jpeg"),
    AUDIO("Audio", "aac", "", "audio/aac"),
    AUDIO_M4A("Audio", "m4a", "", "audio/mp4");

    private final String folder;
    private final String extension;
    private final String marker;
    private final String mimeType;

    DcamFileType(String folder, String extension, String marker, String mimeType) {
        this.folder = folder; this.extension = extension; this.marker = marker; this.mimeType = mimeType;
    }
    public String getFolder() { return folder; }
    public String getExtension() { return extension; }
    public String getMarker() { return marker; }
    public String getMimeType() { return mimeType; }
    public boolean isAudio() { return this == AUDIO || this == AUDIO_M4A; }
    public boolean isVideo() { return this == VIDEO || this == IMP; }
    public boolean usesFragmentedMp4Container() { return isVideo() || this == AUDIO_M4A; }
}

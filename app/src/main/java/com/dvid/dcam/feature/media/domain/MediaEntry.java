package com.dvid.dcam.feature.media.domain;

/** Read-only media-browser entry rooted inside DCAM-managed media storage. */
public final class MediaEntry {
    private final String name;
    private final String relativePath;
    private final boolean directory;
    private final long sizeBytes;
    private final long modifiedAtMillis;
    private final String mimeType;
    private final int childFileCount;

    public MediaEntry(String name, String relativePath, boolean directory,
                      long sizeBytes, long modifiedAtMillis, String mimeType,
                      int childFileCount) {
        this.name = name;
        this.relativePath = relativePath;
        this.directory = directory;
        this.sizeBytes = sizeBytes;
        this.modifiedAtMillis = modifiedAtMillis;
        this.mimeType = mimeType;
        this.childFileCount = childFileCount;
    }

    public String getName() { return name; }
    public String getRelativePath() { return relativePath; }
    public boolean isDirectory() { return directory; }
    public long getSizeBytes() { return sizeBytes; }
    public long getModifiedAtMillis() { return modifiedAtMillis; }
    public String getMimeType() { return mimeType; }
    public int getChildFileCount() { return childFileCount; }
    public boolean hasChildFileCount() { return childFileCount != -1; }
}

package com.dvid.dcam.platform.storage;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.media.application.port.MediaOpener;
import java.io.File;

/** Android implementation for opening a sandboxed DCAM media file. */
public final class AndroidMediaOpener implements MediaOpener {
    private final Context context;
    private final DcamStorage storage;
    private final Logger log;

    public AndroidMediaOpener(Context context, DcamStorage storage, Logger log) {
        this.context = context;
        this.storage = storage;
        this.log = log;
    }

    @Override public boolean open(String relativePath, String mimeType) {
        try {
            File file = resolveInsideRoot(relativePath);
            Uri uri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | IllegalArgumentException | SecurityException error) {
            log.warn("Could not open media " + relativePath, error);
            return false;
        } catch (Exception error) {
            log.error("Could not resolve media " + relativePath, error);
            return false;
        }
    }

    private File resolveInsideRoot(String relativePath) throws Exception {
        int slash = relativePath.indexOf('/');
        if (slash < 0) throw new SecurityException("Media path has no storage root");
        String location = relativePath.substring(0, slash);
        String mediaPath = relativePath.substring(slash + 1);
        File root = storage.mediaRootDirectory(location);
        File canonicalRoot = root.getCanonicalFile();
        File candidate = new File(canonicalRoot, mediaPath).getCanonicalFile();
        String rootPath = canonicalRoot.getPath() + File.separator;
        if (!candidate.getPath().startsWith(rootPath) || !candidate.isFile()) {
            throw new SecurityException("Media path is outside the managed root");
        }
        return candidate;
    }
}

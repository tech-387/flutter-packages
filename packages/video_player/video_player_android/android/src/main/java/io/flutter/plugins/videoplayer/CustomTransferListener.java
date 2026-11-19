package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.util.HashMap;
import java.util.Map;

@UnstableApi
class CustomTransferListener implements TransferListener {

    private static final String TAG = "CustomTransferListener";
    private final Context context;
    private final CustomLogger customLogger;
    private final JSONObjectCallback callback;

    // Per-transfer byte count and start time, only maintained while the VPM tag is loggable.
    private final Map<DataSpec, long[]> vpmTransfers = new HashMap<>();

    public CustomTransferListener(Context context, VideoPlayerLoggerOptions loggerOptions, JSONObjectCallback callback) {
        this.context = context;
        customLogger = new CustomLogger(TAG, loggerOptions.enableTransferListenerLogs);
        this.callback = callback;
    }

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        if (customLogger.isEnabled()) {
            customLogger.logD("Initializing: " + dataSpec.uri + ", isNetwork=" + isNetwork);
        }
    }

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        if (customLogger.isEnabled()) {
            customLogger.logD("Start: " + dataSpec.uri + ", isNetwork=" + isNetwork);
        }
        if (Vpm.isEnabled()) {
            synchronized (vpmTransfers) {
                vpmTransfers.put(dataSpec, new long[]{0L, SystemClock.elapsedRealtime()});
            }
        }
    }

    @Override
    public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
        if (customLogger.isEnabled()) {
            customLogger.logV("Bytes transferred: " + bytesTransferred + ", URI: " + dataSpec.uri);
        }
        if (Vpm.isEnabled()) {
            synchronized (vpmTransfers) {
                long[] transfer = vpmTransfers.get(dataSpec);
                if (transfer != null) {
                    transfer[0] += bytesTransferred;
                }
            }
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        if (customLogger.isEnabled()) {
            customLogger.logD("End: " + dataSpec.uri);
        }

        final Uri uri = dataSpec.uri;
        final String filename = uri.getLastPathSegment();

        if (Vpm.isEnabled()) {
            long[] transfer;
            synchronized (vpmTransfers) {
                transfer = vpmTransfers.remove(dataSpec);
            }
            if (transfer != null) {
                Vpm.log("xfer src=" + (isNetwork ? "net" : "cache") + " file=" + filename
                        + " bytes=" + transfer[0] + " ms=" + (SystemClock.elapsedRealtime() - transfer[1]));
            }
        }

        if (!isNetwork) return;

        if (filename == null || filename.isEmpty()) {
            if (customLogger.isEnabled()) {
                customLogger.logW("Filename is null or empty from URI: " + uri);
            }
            return;
        }

        // Only .ts segments are tracked; manifests/keys are ignored by CachedSegmentIndex.
        CachedSegmentIndex.record(context, filename, callback, customLogger);
    }
}

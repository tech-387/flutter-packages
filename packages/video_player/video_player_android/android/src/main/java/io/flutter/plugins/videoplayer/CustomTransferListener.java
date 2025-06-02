package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import android.net.Uri;

@UnstableApi
class CustomTransferListener implements TransferListener {

    private static final String TAG = "CustomTransferListener";
    private final Context context;
    private static final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final CustomLogger customLogger;
    private final JSONObjectCallback callback;

    public CustomTransferListener(Context context, VideoPlayerLoggerOptions loggerOptions, JSONObjectCallback callback) {
        this.context = context;
        customLogger = new CustomLogger(TAG, loggerOptions.enableTransferListenerLogs);
        this.callback = callback;
    }

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {

        customLogger.logD( "Initializing: " + dataSpec.uri + ", isNetwork=" + isNetwork);
    }

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        customLogger.logD( "Start: " + dataSpec.uri + ", isNetwork=" + isNetwork);
    }

    @Override
    public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
        customLogger.logV( "Bytes transferred: " + bytesTransferred + ", URI: " + dataSpec.uri);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        customLogger.logD( "End: " + dataSpec.uri);

        if (!isNetwork) return;


        final Uri uri = dataSpec.uri;
        final String filename = uri.getLastPathSegment();
        if (filename == null || filename.isEmpty()) {
            customLogger.logW( "Filename is null or empty from URI: " + uri);
            return;
        }

        ioExecutor.execute(() -> {
            try {
                Pattern pattern = Pattern.compile("([a-f0-9\\-]+)_([0-9]+)_([0-9]+)\\.ts");
                Matcher matcher = pattern.matcher(filename);

                if (!matcher.matches()) {
                    Log.w(TAG, "Filename did not match expected pattern: " + filename);
                    return;
                }

                String videoId = matcher.group(1);
                String variant = matcher.group(2);
                String segmentStr = matcher.group(3);

                if (videoId == null || variant == null || segmentStr == null) {
                    customLogger.logW( "Invalid group extraction from filename: " + filename);
                    return;
                }

                int segmentIndex = Integer.parseInt(segmentStr);

                customLogger.logD( "Video ID: " + videoId + ", Variant: " + variant + ", Segment: " + segmentIndex);

                File streamingDir = new File(context.getCacheDir(), "metadata");
                if (!streamingDir.exists()) {
                    streamingDir.mkdirs();
                }

                // Create individual file per video ID
                File videoMetaFile = new File(streamingDir, videoId + ".json");

                JSONObject metadata = videoMetaFile.exists()
                        ? new JSONObject(readFileCompat(videoMetaFile))
                        : new JSONObject();

                JSONObject videoObject = metadata.has(videoId)
                        ? metadata.getJSONObject(videoId)
                        : new JSONObject();

                JSONArray segments = videoObject.has(variant)
                        ? videoObject.getJSONArray(variant)
                        : new JSONArray();

                if (!jsonArrayContains(segments, segmentIndex)) {
                    segments.put(segmentIndex);
                    videoObject.put(variant, segments);
                    metadata.put(videoId, videoObject);

                    String metadataString = metadata.toString(2);

                    if (callback != null) {
                        callback.onResult(metadata);
                    }

                    try (FileWriter writer = new FileWriter(videoMetaFile)) {
                        writer.write(metadataString);
                    }
                }

            } catch (Exception e) {
                customLogger.logE( "Error processing transfer end", e);
            }
        });
    }

    // Helper to check if JSONArray contains a value
    private boolean jsonArrayContains(JSONArray array, int value) {
        for (int i = 0; i < array.length(); i++) {
            if (array.optInt(i) == value) return true;
        }
        return false;
    }

    private String readFileCompat(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        fis.close();
        return bos.toString(StandardCharsets.UTF_8.name());
    }
}
package io.flutter.plugins.videoplayer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.cache.CacheDataSink;

import org.json.JSONObject;

import java.util.Map;

@UnstableApi
public class CacheDataSourceFactory implements DataSource.Factory {
    private static final String TAG = "CustomCacheSource";
    private final Context context;
    private final long maxFileSize, maxCacheSize;
    private final String cacheDirectory;
    private final String assetUrl;
    private volatile JSONObject videoMetadataJson;
    // Uptime of the last attempt to read the metadata file while it was missing. Prefetch
    // (ExactHlsSegmentDownloader) may write the file after this player was created, so a missing
    // file is looked for again, at most once per METADATA_RETRY_INTERVAL_MS.
    private volatile long lastMetadataLoadAttemptMs = -1;
    private static final long METADATA_RETRY_INTERVAL_MS = 1000;
    private final CustomLogger customLogger;
    private final VideoPlayerLoggerOptions loggerOptions;

    private final DefaultHttpDataSource.Factory defaultHttpDataSourceFactory;
    private final CustomHttpDataSource.Factory customFactory;
    private final Map<String, String> httpHeaders;

    @OptIn(markerClass = UnstableApi.class)
    CacheDataSourceFactory(Context context, long maxCacheSize, long maxFileSize, String cacheDirectory, String assetUrl, VideoPlayerLoggerOptions loggerOptions, Map<String, String> httpHeaders) {
        super();
        this.context = context;
        this.maxCacheSize = maxCacheSize;
        this.maxFileSize = maxFileSize;
        this.cacheDirectory = cacheDirectory;
        this.assetUrl = assetUrl;
        this.loggerOptions = loggerOptions;
        this.httpHeaders = httpHeaders;

        defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory();
        defaultHttpDataSourceFactory.setUserAgent("ExoPlayer");
        defaultHttpDataSourceFactory.setAllowCrossProtocolRedirects(true);
        defaultHttpDataSourceFactory.setDefaultRequestProperties(httpHeaders);

        customFactory = new CustomDataSourceFactory(defaultHttpDataSourceFactory, loggerOptions);

        customLogger = new CustomLogger(TAG, loggerOptions.enableCacheDataSourceLogs);
    }

    /**
     * Reads this asset's metadata file once. Called lazily (not from the constructor, which runs on
     * the platform thread for every player) and at most once per factory.
     */
    private void loadVideoMetadata() {
        try {
            String videoId = CachedSegmentIndex.videoIdFromAssetUrl(assetUrl);
            if (videoId == null) {
                if (customLogger.isEnabled()) {
                    customLogger.logW("Video ID could not be extracted from assetUrl: " + assetUrl);
                }
                return;
            }

            JSONObject loaded = CachedSegmentIndex.readMetadata(context, videoId);
            if (loaded == null) {
                if (customLogger.isEnabled()) {
                    customLogger.logW("Metadata file not found for video ID: " + videoId);
                }
                return;
            }

            // A transfer-listener update may have arrived first; it already includes the file's
            // contents, so don't overwrite it.
            if (videoMetadataJson == null) {
                videoMetadataJson = loaded;
            }
            if (customLogger.isEnabled()) {
                customLogger.logD("Loaded metadata for video ID: " + videoId + " → " + loaded);
            }
        } catch (Exception e) {
            customLogger.logE("Failed to load video metadata: " + e.getMessage(), e);
        }
    }

    @Nullable
    public JSONObject getVideoMetadataJson() {
        JSONObject json = videoMetadataJson;
        if (json != null) {
            return json;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (lastMetadataLoadAttemptMs < 0
                || now - lastMetadataLoadAttemptMs >= METADATA_RETRY_INTERVAL_MS) {
            synchronized (this) {
                if (videoMetadataJson == null
                        && (lastMetadataLoadAttemptMs < 0
                                || now - lastMetadataLoadAttemptMs >= METADATA_RETRY_INTERVAL_MS)) {
                    lastMetadataLoadAttemptMs = now;
                    loadVideoMetadata();
                }
            }
        }
        return videoMetadataJson;
    }

    @OptIn(markerClass = UnstableApi.class)
    void setHeaders(Map<String, String> httpHeaders) {
        defaultHttpDataSourceFactory.setDefaultRequestProperties(httpHeaders);
    }

    @NonNull
    @OptIn(markerClass = UnstableApi.class)
    @Override
    public DataSource createDataSource() {
        DataSource dataSource = customFactory.createDataSource();
        TransferListener transferListener = new CustomTransferListener(
                context, loggerOptions,
                jsonObject -> {
                    videoMetadataJson = jsonObject;
                }
        );

        SimpleCache simpleCache = SimpleCacheSingleton.getInstance(context, maxCacheSize, cacheDirectory).simpleCache;
        CacheDataSource cacheDataSource = new CacheDataSource(simpleCache, dataSource,
                new FileDataSource(), new CacheDataSink(simpleCache, maxFileSize),
                CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null);


        cacheDataSource.addTransferListener(transferListener);

        return cacheDataSource;
    }


}



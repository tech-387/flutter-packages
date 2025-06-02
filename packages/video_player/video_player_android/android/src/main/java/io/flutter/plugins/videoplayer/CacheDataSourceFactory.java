package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.net.Uri;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;

@UnstableApi
public class CacheDataSourceFactory implements DataSource.Factory {
    private static final String TAG = "CustomCacheSource";
    private final Context context;
    private final long maxFileSize, maxCacheSize;
    private final String cacheDirectory;
    private final String assetUrl;
    private JSONObject videoMetadataJson;
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

        customFactory = new CustomDataSourceFactory(defaultHttpDataSourceFactory);

        customLogger = new CustomLogger(TAG, loggerOptions.enableCacheDataSourceLogs);

        loadVideoMetadata();
    }

    private void loadVideoMetadata() {
        try {
            Uri uri = Uri.parse(assetUrl);
            // Extract video ID from last path segment before the file
            String videoId = null;
            for (String segment : uri.getPathSegments()) {
                if (segment.endsWith(".m3u8")) {
                    String filename = segment.split("\\.")[0]; // remove .m3u8
                    int lastUnderscore = filename.lastIndexOf('_');
                    if (lastUnderscore > 0) {
                        videoId = filename.substring(0, lastUnderscore);
                    } else {
                        videoId = filename;
                    }
                    break;
                }
            }

            if (videoId == null) {
                customLogger.logW("Video ID could not be extracted from assetUrl: " + assetUrl);
                return;
            }

            File streamingDir = new File(context.getCacheDir(), "metadata");
            if (!streamingDir.exists()) {
                customLogger.logW("Streaming directory not yet created");
                return;
            }

            // Create individual file per video ID
            File metadataFile = new File(streamingDir, videoId + ".json");
            if (!metadataFile.exists()) {
                customLogger.logW("Metadata file not found for video ID: " + videoId);
                return;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }

            videoMetadataJson = new JSONObject(jsonBuilder.toString());
            customLogger.logD("Loaded metadata for video ID: " + videoId + " → " + videoMetadataJson);

        } catch (Exception e) {
            customLogger.logE("Failed to load video metadata: " + e.getMessage(), e);
        }
    }

    @Nullable
    public JSONObject getVideoMetadataJson() {
        if(videoMetadataJson == null) {
            loadVideoMetadata();
        }
        return videoMetadataJson;
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



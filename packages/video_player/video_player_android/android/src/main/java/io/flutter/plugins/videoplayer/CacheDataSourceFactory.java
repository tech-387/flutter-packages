package io.flutter.plugins.videoplayer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import java.util.Map;

class CacheDataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final long maxFileSize, maxCacheSize;
    private final String cacheDirectory;

    private final DefaultHttpDataSource.Factory defaultHttpDataSourceFactory;

    @OptIn(markerClass = UnstableApi.class)
    CacheDataSourceFactory(Context context, long maxCacheSize, long maxFileSize, String cacheDirectory) {
        super();
        this.context = context;
        this.maxCacheSize = maxCacheSize;
        this.maxFileSize = maxFileSize;
        this.cacheDirectory = cacheDirectory;

        defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory();
        defaultHttpDataSourceFactory.setUserAgent("ExoPlayer");
        defaultHttpDataSourceFactory.setAllowCrossProtocolRedirects(true);
    }

    @OptIn(markerClass = UnstableApi.class)
    void setHeaders(Map<String, String> httpHeaders) {
        defaultHttpDataSourceFactory.setDefaultRequestProperties(httpHeaders);
    }

    @NonNull
    @OptIn(markerClass = UnstableApi.class)
    @Override
    public DataSource createDataSource() {

        DefaultDataSource.Factory defaultDataSourceFactory = new DefaultDataSource.Factory(context, defaultHttpDataSourceFactory);

        SimpleCache simpleCache = SimpleCacheSingleton.getInstance(context, maxCacheSize, cacheDirectory).simpleCache;
        return new CacheDataSource(simpleCache, defaultDataSourceFactory.createDataSource(),
                new FileDataSource(), new CacheDataSink(simpleCache, maxFileSize),
                CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null);
    }
}

package io.flutter.plugins.videoplayer;
import android.content.Context;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.database.StandaloneDatabaseProvider;

import java.io.File;

@UnstableApi
public class SimpleCacheSingleton {
    LeastRecentlyUsedCacheEvictor evictor;
    SimpleCache simpleCache;

    private static SimpleCacheSingleton instance;

    private SimpleCacheSingleton(Context context, long maxCacheSize, String cacheDirectory) {
        evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
        simpleCache = new SimpleCache(new File(context.getCacheDir(), cacheDirectory), evictor, new StandaloneDatabaseProvider(context));
    }

    public synchronized static SimpleCacheSingleton getInstance(Context context, long maxCacheSize, String cacheDirectory) {
        if (instance == null) {
            synchronized (SimpleCacheSingleton.class) {
                if (instance == null)
                    instance = new SimpleCacheSingleton(context, maxCacheSize, cacheDirectory);
            }
        }
        return instance;
    }
}
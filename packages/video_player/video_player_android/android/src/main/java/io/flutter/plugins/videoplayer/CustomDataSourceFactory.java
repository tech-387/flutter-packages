package io.flutter.plugins.videoplayer;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;

import java.util.Map;

@UnstableApi
public class CustomDataSourceFactory implements HttpDataSource.Factory {

    private final DefaultHttpDataSource.Factory delegate;
    private final VideoPlayerLoggerOptions loggerOptions;

    public CustomDataSourceFactory(DefaultHttpDataSource.Factory delegate, VideoPlayerLoggerOptions loggerOptions) {
        this.delegate = delegate;
        this.loggerOptions = loggerOptions;
    }

    @Override
    public HttpDataSource createDataSource() {
        HttpDataSource base = delegate.createDataSource();
        return new CustomHttpDataSource(base, loggerOptions);
    }

    @Override
    public HttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
        HttpDataSource.RequestProperties requestProperties = new HttpDataSource.RequestProperties();
        requestProperties.clearAndSet(defaultRequestProperties);
        return this;
    }
}


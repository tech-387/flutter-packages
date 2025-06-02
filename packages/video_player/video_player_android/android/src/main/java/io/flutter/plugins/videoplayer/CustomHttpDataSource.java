package io.flutter.plugins.videoplayer;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UnstableApi
public class CustomHttpDataSource implements HttpDataSource {

    private static final String TAG = "CustomAdaptiveSelection";
    private final HttpDataSource wrapped;
    private final VideoPlayerLoggerOptions loggerOptions;
    private final CustomLogger customLogger;

    public CustomHttpDataSource(HttpDataSource wrapped, VideoPlayerLoggerOptions loggerOptions) {
        this.wrapped = wrapped;
        this.loggerOptions = loggerOptions;
        customLogger = new CustomLogger(TAG, loggerOptions.enableCacheDataSourceLogs);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        Uri uri = dataSpec.uri;

        customLogger.logD( "Opening URL: " + uri);

        Map<String, String> requestHeaders = dataSpec.httpRequestHeaders;
        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            customLogger.logD( "Request header: " + entry.getKey() + " = " + entry.getValue());
        }

        DataSpec modifiedSpec = dataSpec;

        if (uri.getLastPathSegment() != null && uri.getLastPathSegment().endsWith(".m3u8")) {
            customLogger.logD( "Detected .m3u8 file, stripping Range header");

            modifiedSpec = dataSpec.buildUpon()
                    .setHttpRequestHeaders(stripRange(requestHeaders))
                    .setPosition(0)
                    .setLength(C.LENGTH_UNSET)
                    .build();
        }

        long result = wrapped.open(modifiedSpec);

        Uri responseUri = getUri(); // or wrapped.getUri()
        Map<String, List<String>> responseHeaders = getResponseHeaders(); // or wrapped.getResponseHeaders()

        customLogger.logD( "Response from: " + responseUri);
        if (responseHeaders != null) {
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                customLogger.logD( "Response header: " + entry.getKey() + " = " + entry.getValue());
            }
        }

        return result;
    }

    private Map<String, String> stripRange(Map<String, String> headers) {
        if (headers == null) return null;
        Map<String, String> newHeaders = new HashMap<>(headers);
        newHeaders.remove("Range");
        return newHeaders;
    }

    @Override
    public Uri getUri() {
        return wrapped.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return wrapped.getResponseHeaders();
    }

    @Override
    public void close() throws HttpDataSourceException {
        wrapped.close();
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        return wrapped.read(buffer, offset, readLength);
    }

    @Override
    public void setRequestProperty(String name, String value) {
        wrapped.setRequestProperty(name, value);
    }

    @Override
    public void clearRequestProperty(String name) {
        wrapped.clearRequestProperty(name);
    }

    @Override
    public void clearAllRequestProperties() {
        wrapped.clearAllRequestProperties();
    }

    @Override
    public int getResponseCode() {
        return wrapped.getResponseCode();
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        wrapped.addTransferListener(transferListener);
    }
}

package io.flutter.plugins.videoplayer;

import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Passes everything through to {@code upstream}, adding the bytes it reads from the network to
 * {@link PlaybackMetrics}. Wraps the HTTP side of the cache, so cache hits are not counted.
 */
@UnstableApi
final class CountingDataSource implements DataSource {
  private final DataSource upstream;
  @Nullable private PlaybackMetrics.TransferStats stats;
  private String variant = "other";
  private long openedAtMs;

  CountingDataSource(@NonNull DataSource upstream) {
    this.upstream = upstream;
  }

  @NonNull
  static DataSource.Factory wrap(@NonNull DataSource.Factory factory) {
    return () -> new CountingDataSource(factory.createDataSource());
  }

  @Override
  public void addTransferListener(@NonNull TransferListener transferListener) {
    upstream.addTransferListener(transferListener);
  }

  @Override
  public long open(@NonNull DataSpec dataSpec) throws IOException {
    stats = PlaybackMetrics.isNetwork(dataSpec.uri) ? PlaybackMetrics.transfersFor(dataSpec.uri) : null;
    variant = PlaybackMetrics.variantOf(dataSpec.uri);
    openedAtMs = SystemClock.elapsedRealtime();
    if (stats != null) stats.onRequestStarted();
    return upstream.open(dataSpec);
  }

  @Override
  public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
    int read = upstream.read(buffer, offset, length);
    PlaybackMetrics.TransferStats current = stats;
    if (read > 0 && current != null) current.onBytes(variant, read);
    return read;
  }

  @Nullable
  @Override
  public Uri getUri() {
    return upstream.getUri();
  }

  @NonNull
  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return upstream.getResponseHeaders();
  }

  @Override
  public void close() throws IOException {
    PlaybackMetrics.TransferStats current = stats;
    stats = null;
    if (current != null) current.onRequestEnded(SystemClock.elapsedRealtime() - openedAtMs);
    upstream.close();
  }
}

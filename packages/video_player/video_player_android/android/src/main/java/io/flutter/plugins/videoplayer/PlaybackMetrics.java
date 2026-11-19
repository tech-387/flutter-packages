package io.flutter.plugins.videoplayer;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playback metrics for the app's developer tools: network bytes per video and per-player stats
 * (first frame, format, dropped frames, bytes read). Read-only: nothing here changes playback.
 *
 * <p>Network bytes are counted by {@link CountingDataSource} on the upstream (HTTP) side of the
 * cache, for playback and for preloading, and grouped by video: the id at the start of the file
 * name (the backend names files {@code <id>.m3u8}, {@code <id>_720.m3u8}, {@code
 * <id>_720_3.ts}), or else the URL's directory. Counters are cumulative for the process; the app
 * takes differences.
 *
 * <p>Exposed on the method channel {@link #CHANNEL}, the same on iOS.
 */
@UnstableApi
final class PlaybackMetrics {
  static final String CHANNEL = "buzzer/video_player_metrics";
  static final int VERSION = 1;

  private static final Pattern ID = Pattern.compile("^([0-9a-fA-F-]{16,})");
  private static final Pattern VARIANT = Pattern.compile("^[0-9a-fA-F-]{16,}_(\\d{3,4})(?:[_.]|$)");

  private static final ConcurrentHashMap<String, TransferStats> transfers =
      new ConcurrentHashMap<>();
  private static final Map<ExoPlayer, PlayerMetrics> players = new WeakHashMap<>();

  private PlaybackMetrics() {}

  /** The video a URL belongs to. Must match {@code FVPPlaybackMetrics} on iOS. */
  @NonNull
  static String keyOf(@NonNull Uri uri) {
    String last = uri.getLastPathSegment();
    if (last != null) {
      Matcher matcher = ID.matcher(last);
      if (matcher.find()) return matcher.group(1).toLowerCase(Locale.ROOT);
    }
    String path = uri.getPath() == null ? "" : uri.getPath();
    int slash = path.lastIndexOf('/');
    return uri.getHost() + (slash > 0 ? path.substring(0, slash) : "");
  }

  /** The variant a file belongs to ({@code 720} for {@code <id>_720_3.ts}), or {@code other}. */
  @NonNull
  static String variantOf(@Nullable Uri uri) {
    String last = uri == null ? null : uri.getLastPathSegment();
    if (last == null) return "other";
    Matcher matcher = VARIANT.matcher(last);
    return matcher.find() ? matcher.group(1) : "other";
  }

  static boolean isNetwork(@Nullable Uri uri) {
    String scheme = uri == null ? null : uri.getScheme();
    return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
  }

  @NonNull
  static TransferStats transfersFor(@NonNull Uri uri) {
    String key = keyOf(uri);
    TransferStats stats = transfers.get(key);
    if (stats == null) {
      stats = new TransferStats(key);
      TransferStats existing = transfers.putIfAbsent(key, stats);
      if (existing != null) stats = existing;
    }
    return stats;
  }

  /** Starts collecting stats for a new player. Call on the main thread. */
  @OptIn(markerClass = UnstableApi.class)
  static void attach(@NonNull ExoPlayer exoPlayer) {
    PlayerMetrics metrics = new PlayerMetrics();
    exoPlayer.addAnalyticsListener(metrics);
    synchronized (players) {
      players.put(exoPlayer, metrics);
    }
  }

  @Nullable
  static PlayerMetrics metricsOf(@NonNull ExoPlayer exoPlayer) {
    synchronized (players) {
      return players.get(exoPlayer);
    }
  }

  /** Network bytes of one video, from playback and preloading. */
  static final class TransferStats {
    final String key;
    private long bytes;
    private long requests;
    private long transferMs;
    private final Map<String, Long> bytesByVariant = new HashMap<>();

    TransferStats(String key) {
      this.key = key;
    }

    synchronized void onRequestStarted() {
      requests++;
    }

    synchronized void onBytes(String variant, int count) {
      bytes += count;
      Long previous = bytesByVariant.get(variant);
      bytesByVariant.put(variant, (previous == null ? 0 : previous) + count);
    }

    synchronized void onRequestEnded(long elapsedMs) {
      transferMs += elapsedMs;
    }

    synchronized Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("key", key);
      map.put("netBytes", bytes);
      map.put("netRequests", requests);
      map.put("netMs", transferMs);
      map.put("netVariants", new HashMap<>(bytesByVariant));
      return map;
    }
  }

  /** Stats of one player, from its analytics events. Updated and read on the main thread. */
  static final class PlayerMetrics implements AnalyticsListener {
    private long firstFrameEpochMs = -1;
    private long droppedFrames;
    private long formatChanges;
    private long readBytes;
    private long loads;
    private long bandwidthEstimate = -1;
    private final Map<String, Long> readBytesByVariant = new HashMap<>();

    @Override
    public void onRenderedFirstFrame(
        @NonNull EventTime eventTime, @NonNull Object output, long renderTimeMs) {
      if (firstFrameEpochMs < 0) firstFrameEpochMs = System.currentTimeMillis();
    }

    @Override
    public void onDroppedVideoFrames(
        @NonNull EventTime eventTime, int droppedFrames, long elapsedMs) {
      this.droppedFrames += droppedFrames;
    }

    @Override
    public void onVideoInputFormatChanged(
        @NonNull EventTime eventTime,
        @NonNull Format format,
        @Nullable androidx.media3.exoplayer.DecoderReuseEvaluation decoderReuseEvaluation) {
      formatChanges++;
    }

    @Override
    public void onLoadCompleted(
        @NonNull EventTime eventTime,
        @NonNull LoadEventInfo loadEventInfo,
        @NonNull MediaLoadData mediaLoadData) {
      loads++;
      readBytes += loadEventInfo.bytesLoaded;
      String variant = variantOf(loadEventInfo.uri);
      Long previous = readBytesByVariant.get(variant);
      readBytesByVariant.put(variant, (previous == null ? 0 : previous) + loadEventInfo.bytesLoaded);
    }

    @Override
    public void onBandwidthEstimate(
        @NonNull EventTime eventTime,
        int totalLoadTimeMs,
        long totalBytesLoaded,
        long bitrateEstimate) {
      bandwidthEstimate = bitrateEstimate;
    }

    Map<String, Object> toMap(@NonNull ExoPlayer exoPlayer) {
      Map<String, Object> map = new HashMap<>();
      Format format = exoPlayer.getVideoFormat();
      if (format != null) {
        map.put("width", format.width);
        map.put("height", format.height);
        // For HLS: the variant's BANDWIDTH (peak) and AVERAGE-BANDWIDTH.
        map.put("bitrate", format.peakBitrate != Format.NO_VALUE ? format.peakBitrate : format.bitrate);
        map.put("averageBitrate", format.averageBitrate);
        map.put("frameRate", (double) format.frameRate);
        map.put("codecs", format.codecs);
      }
      map.put("firstFrameEpochMs", firstFrameEpochMs);
      map.put("droppedFrames", droppedFrames);
      map.put("formatChanges", formatChanges);
      map.put("readBytes", readBytes);
      map.put("loads", loads);
      map.put("bandwidthEstimate", bandwidthEstimate);
      map.put("readVariants", new HashMap<>(readBytesByVariant));
      return map;
    }
  }

  /** Answers the app's metrics queries. */
  static final class Channel implements MethodChannel.MethodCallHandler {
    interface PlayerLookup {
      @Nullable
      VideoPlayer get(long playerId);
    }

    private final MethodChannel channel;
    private final PlayerLookup players;

    Channel(@NonNull BinaryMessenger messenger, @NonNull PlayerLookup players) {
      this.players = players;
      channel = new MethodChannel(messenger, CHANNEL);
      channel.setMethodCallHandler(this);
    }

    void dispose() {
      channel.setMethodCallHandler(null);
    }

    @Override
    public void onMethodCall(
        @NonNull io.flutter.plugin.common.MethodCall call, @NonNull MethodChannel.Result result) {
      switch (call.method) {
        case "version":
          result.success(VERSION);
          return;
        case "transferStats":
          {
            String url = call.argument("url");
            if (url == null) {
              result.success(null);
              return;
            }
            Uri uri = Uri.parse(url);
            TransferStats stats = transfers.get(keyOf(uri));
            if (stats == null) {
              Map<String, Object> empty = new HashMap<>();
              empty.put("key", keyOf(uri));
              empty.put("netBytes", 0L);
              empty.put("netRequests", 0L);
              empty.put("netMs", 0L);
              empty.put("netVariants", new HashMap<String, Long>());
              result.success(empty);
            } else {
              result.success(stats.toMap());
            }
            return;
          }
        case "playerStats":
          {
            Number id = call.argument("playerId");
            VideoPlayer player = id == null ? null : players.get(id.longValue());
            PlayerMetrics metrics = player == null ? null : metricsOf(player.exoPlayer);
            result.success(metrics == null ? null : metrics.toMap(player.exoPlayer));
            return;
          }
        default:
          result.notImplemented();
      }
    }
  }
}

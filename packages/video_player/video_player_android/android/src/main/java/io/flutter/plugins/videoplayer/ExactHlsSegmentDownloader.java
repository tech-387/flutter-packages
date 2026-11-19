// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.UriUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.exoplayer.offline.Downloader;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.ParsingLoadable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the manifest(s) and exactly the first {@code segmentCount} media segments of an HLS
 * asset into a {@link CacheDataSource}'s cache - no more, regardless of segment duration.
 *
 * <p>Media3's own {@link androidx.media3.exoplayer.hls.offline.HlsDownloader} can only bound a
 * download by {@code setDurationUs}, not by an exact segment count, which makes it imprecise for
 * short, fixed-size cache-priming preloads (a request for 1 segment could pull in 2-3 depending on
 * the stream's actual segment duration). This downloader instead parses the playlist itself -
 * mirroring the segment/init-segment/encryption-key resolution HlsDownloader does internally - and
 * caches only the requested number of segments.
 *
 * <p>Downloads run sequentially on the calling thread; callers should invoke {@link #download}
 * from a background executor.
 */
@UnstableApi
final class ExactHlsSegmentDownloader implements Downloader {
  private static final int BUFFER_SIZE_BYTES = 128 * 1024;

  /** Fraction of the bandwidth estimate a variant's bitrate may use; matches ABR's default. */
  static final double BANDWIDTH_FRACTION = 0.85;

  private static final Pattern VARIANT_LABEL_PATTERN = Pattern.compile("_([0-9]+)\\.m3u8$");

  private final Context context;

  private final CacheDataSource.Factory cacheDataSourceFactory;
  private final Uri playlistUri;
  private final int segmentCount;

  private volatile boolean canceled;
  @Nullable private volatile CacheWriter currentWriter;

  ExactHlsSegmentDownloader(
      Context context,
      CacheDataSource.Factory cacheDataSourceFactory,
      String playlistUri,
      int segmentCount) {
    this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    this.cacheDataSourceFactory = cacheDataSourceFactory;
    this.playlistUri = Uri.parse(playlistUri);
    this.segmentCount = Math.max(segmentCount, 1);
  }

  @Override
  public void download(@Nullable ProgressListener progressListener) throws IOException {
    if (canceled) {
      return;
    }
    final long startMs = SystemClock.elapsedRealtime();
    final boolean vpm = Vpm.isEnabled();
    try {
      downloadInternal(vpm);
    } finally {
      if (vpm) {
        Vpm.log(
            "prefetch end file=" + playlistUri.getLastPathSegment()
                + " ms=" + (SystemClock.elapsedRealtime() - startMs)
                + " yielded=" + yieldedMs
                + " canceled=" + (canceled ? 1 : 0));
      }
    }
  }

  private void downloadInternal(boolean vpm) throws IOException {
    CacheDataSource dataSource = cacheDataSourceFactory.createDataSourceForDownloading();
    HlsPlaylistParser playlistParser = new HlsPlaylistParser();

    HlsPlaylist playlist =
        ParsingLoadable.load(dataSource, playlistParser, playlistUri, C.DATA_TYPE_MANIFEST);

    List<DataSpec> dataSpecs = new ArrayList<>();
    dataSpecs.add(getCompressibleDataSpec(playlistUri));

    HlsMediaPlaylist mediaPlaylist;
    if (playlist instanceof HlsMultivariantPlaylist) {
      // Cache the variant ABR is most likely to pick at playback time: the highest-bitrate
      // variant that fits the current bandwidth estimate, else the lowest one. The segments
      // are recorded in CachedSegmentIndex below, so playback's ABR prefers them.
      List<HlsMultivariantPlaylist.Variant> variants = ((HlsMultivariantPlaylist) playlist).variants;
      long estimate = DefaultBandwidthMeter.getSingletonInstance(context).getBitrateEstimate();
      int[] bitrates = new int[variants.size()];
      for (int i = 0; i < bitrates.length; i++) {
        bitrates[i] = variants.get(i).format.bitrate;
      }
      int chosen = chooseVariantIndex(bitrates, estimate);
      Uri mediaPlaylistUri =
          chosen >= 0
              ? variants.get(chosen).url
              : ((HlsMultivariantPlaylist) playlist).mediaPlaylistUrls.get(0);
      if (vpm) {
        Vpm.log(
            "prefetch start file=" + playlistUri.getLastPathSegment()
                + " variant=" + variantLabel(mediaPlaylistUri)
                + " bps=" + (chosen >= 0 ? bitrates[chosen] : -1)
                + " est=" + estimate
                + " segs=" + segmentCount);
      }
      dataSpecs.add(getCompressibleDataSpec(mediaPlaylistUri));
      mediaPlaylist =
          (HlsMediaPlaylist)
              ParsingLoadable.load(
                  dataSource, playlistParser, mediaPlaylistUri, C.DATA_TYPE_MANIFEST);
    } else {
      mediaPlaylist = (HlsMediaPlaylist) playlist;
      if (vpm) {
        Vpm.log(
            "prefetch start file=" + playlistUri.getLastPathSegment()
                + " variant=media bps=-1 est=-1 segs=" + segmentCount);
      }
    }

    List<HlsMediaPlaylist.Segment> hlsSegments = mediaPlaylist.segments;
    int limit = Math.min(segmentCount, hlsSegments.size());
    HashSet<Uri> seenEncryptionKeyUris = new HashSet<>();
    @Nullable HlsMediaPlaylist.Segment lastInitSegment = null;
    for (int i = 0; i < limit; i++) {
      HlsMediaPlaylist.Segment segment = hlsSegments.get(i);
      HlsMediaPlaylist.Segment initSegment = segment.initializationSegment;
      if (initSegment != null && initSegment != lastInitSegment) {
        lastInitSegment = initSegment;
        addSegmentDataSpec(mediaPlaylist.baseUri, initSegment, seenEncryptionKeyUris, dataSpecs);
      }
      addSegmentDataSpec(mediaPlaylist.baseUri, segment, seenEncryptionKeyUris, dataSpecs);
    }

    // Pauses between reads while the video on screen is short of buffer (see NetworkPriority).
    // Holding off reading also holds off the download, as the socket's receive window fills.
    CacheWriter.ProgressListener yieldToPlayers =
        (requestLength, bytesCached, newBytesCached) -> waitForPlayers();
    for (DataSpec dataSpec : dataSpecs) {
      waitForPlayers();
      if (canceled) {
        break;
      }
      CacheWriter writer =
          new CacheWriter(dataSource, dataSpec, new byte[BUFFER_SIZE_BYTES], yieldToPlayers);
      currentWriter = writer;
      try {
        writer.cache();
      } finally {
        currentWriter = null;
      }
      // Fully cached (cache() returns normally; cancellation throws). Record media segments so
      // ABR at playback time knows this variant's segments are available.
      String fileName = dataSpec.uri.getLastPathSegment();
      if (fileName != null && fileName.endsWith(".ts")) {
        CachedSegmentIndex.record(context, fileName);
      }
    }
  }

  /** Total time this download waited for the video on screen, for the VPM log. */
  private long yieldedMs;

  private void waitForPlayers() {
    try {
      long waited = NetworkPriority.awaitPlayers(() -> canceled);
      if (waited > 0) {
        yieldedMs += waited;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void cancel() {
    canceled = true;
    CacheWriter writer = currentWriter;
    if (writer != null) {
      writer.cancel();
    }
  }

  /**
   * Chooses which variant to cache: the index of the highest-bitrate entry of {@code bitrates}
   * with {@code bitrate <= estimateBps * BANDWIDTH_FRACTION}, falling back to the lowest-bitrate
   * entry when none fits (or the estimate is unknown). Entries with an unknown bitrate
   * ({@code <= 0}) are only chosen when no entry has a known bitrate (then index 0). Returns -1
   * for an empty array.
   */
  @VisibleForTesting
  static int chooseVariantIndex(@NonNull int[] bitrates, long estimateBps) {
    if (bitrates.length == 0) {
      return -1;
    }
    double budget = estimateBps * BANDWIDTH_FRACTION;
    int bestFit = -1;
    int lowest = -1;
    for (int i = 0; i < bitrates.length; i++) {
      int bitrate = bitrates[i];
      if (bitrate <= 0) {
        continue;
      }
      if (lowest < 0 || bitrate < bitrates[lowest]) {
        lowest = i;
      }
      if (bitrate <= budget && (bestFit < 0 || bitrate > bitrates[bestFit])) {
        bestFit = i;
      }
    }
    if (bestFit >= 0) {
      return bestFit;
    }
    return lowest >= 0 ? lowest : 0;
  }

  /** The variant label from a media playlist URL ({@code <id>_720.m3u8} -> {@code 720}). */
  private static String variantLabel(Uri mediaPlaylistUri) {
    String name = mediaPlaylistUri.getLastPathSegment();
    if (name != null) {
      Matcher matcher = VARIANT_LABEL_PATTERN.matcher(name);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    return String.valueOf(name);
  }

  boolean isCanceled() {
    return canceled;
  }

  @Override
  public void remove() {
    // Tier A preloads aren't protected/exported content - the cache's own eviction policy
    // manages them, so there's nothing to explicitly remove here.
  }

  private static void addSegmentDataSpec(
      String baseUri,
      HlsMediaPlaylist.Segment segment,
      HashSet<Uri> seenEncryptionKeyUris,
      List<DataSpec> out) {
    if (segment.fullSegmentEncryptionKeyUri != null) {
      Uri keyUri = UriUtil.resolveToUri(baseUri, segment.fullSegmentEncryptionKeyUri);
      if (seenEncryptionKeyUris.add(keyUri)) {
        out.add(getCompressibleDataSpec(keyUri));
      }
    }
    Uri segmentUri = UriUtil.resolveToUri(baseUri, segment.url);
    out.add(new DataSpec(segmentUri, segment.byteRangeOffset, segment.byteRangeLength));
  }

  private static DataSpec getCompressibleDataSpec(Uri uri) {
    return new DataSpec.Builder().setUri(uri).setFlags(DataSpec.FLAG_ALLOW_GZIP).build();
  }
}

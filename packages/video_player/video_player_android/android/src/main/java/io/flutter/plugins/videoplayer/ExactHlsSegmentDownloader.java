// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.net.Uri;

import androidx.annotation.Nullable;
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
import androidx.media3.exoplayer.upstream.ParsingLoadable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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

  private final CacheDataSource.Factory cacheDataSourceFactory;
  private final Uri playlistUri;
  private final int segmentCount;

  private volatile boolean canceled;
  @Nullable private volatile CacheWriter currentWriter;

  ExactHlsSegmentDownloader(
      CacheDataSource.Factory cacheDataSourceFactory, String playlistUri, int segmentCount) {
    this.cacheDataSourceFactory = cacheDataSourceFactory;
    this.playlistUri = Uri.parse(playlistUri);
    this.segmentCount = Math.max(segmentCount, 1);
  }

  @Override
  public void download(@Nullable ProgressListener progressListener) throws IOException {
    if (canceled) {
      return;
    }
    CacheDataSource dataSource = cacheDataSourceFactory.createDataSourceForDownloading();
    HlsPlaylistParser playlistParser = new HlsPlaylistParser();

    HlsPlaylist playlist =
        ParsingLoadable.load(dataSource, playlistParser, playlistUri, C.DATA_TYPE_MANIFEST);

    List<DataSpec> dataSpecs = new ArrayList<>();
    dataSpecs.add(getCompressibleDataSpec(playlistUri));

    HlsMediaPlaylist mediaPlaylist;
    if (playlist instanceof HlsMultivariantPlaylist) {
      // Best-effort variant choice for a cache-priming preload: the first listed rendition,
      // which for standard HLS output is the first video variant. We don't try to match
      // whatever ABR would actually pick at playback time.
      Uri mediaPlaylistUri = ((HlsMultivariantPlaylist) playlist).mediaPlaylistUrls.get(0);
      dataSpecs.add(getCompressibleDataSpec(mediaPlaylistUri));
      mediaPlaylist =
          (HlsMediaPlaylist)
              ParsingLoadable.load(
                  dataSource, playlistParser, mediaPlaylistUri, C.DATA_TYPE_MANIFEST);
    } else {
      mediaPlaylist = (HlsMediaPlaylist) playlist;
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

    for (DataSpec dataSpec : dataSpecs) {
      if (canceled) {
        break;
      }
      CacheWriter writer = new CacheWriter(dataSource, dataSpec, new byte[BUFFER_SIZE_BYTES], null);
      currentWriter = writer;
      try {
        writer.cache();
      } finally {
        currentWriter = null;
      }
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

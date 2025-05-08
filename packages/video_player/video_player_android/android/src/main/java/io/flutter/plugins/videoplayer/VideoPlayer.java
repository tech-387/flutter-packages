// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_OFF;

import static com.google.common.net.HttpHeaders.USER_AGENT;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.extractor.DefaultExtractorsFactory;

import java.util.Map;

import io.flutter.view.TextureRegistry.SurfaceProducer;

/**
 * A class responsible for managing video playback using {@link ExoPlayer}.
 *
 * <p>It provides methods to control playback, adjust volume, and handle seeking.
 */
public abstract class VideoPlayer {
  @NonNull private final ExoPlayerProvider exoPlayerProvider;
  @NonNull private final MediaItem mediaItem;
  @NonNull private final VideoPlayerOptions options;
  @NonNull protected ExoPlayer exoPlayer;
  @Nullable private ExoPlayerState savedStateDuring;
  private static DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();

  @NonNull protected final VideoPlayerCallbacks videoPlayerEvents;
  @Nullable protected final SurfaceProducer surfaceProducer;


  @OptIn(markerClass = UnstableApi.class)
  public static ExoPlayer constructExoPlayer(
      @NonNull Context context,
      @NonNull VideoAsset asset,
      @NonNull VideoPlayerOptions options,
      @NonNull VideoPlayerBufferOptions videoPlayerBufferOptions
  ) {
    Log.i("Buffer", "minBufferMs="+videoPlayerBufferOptions.minBufferMs + ",maxBufferMs="+videoPlayerBufferOptions.maxBufferMs + ",bufferForPlaybackMs="+videoPlayerBufferOptions.bufferForPlaybackMs + ",bufferForPlaybackAfterRebufferMs=" + videoPlayerBufferOptions.bufferForPlaybackAfterRebufferMs);

    LoadControl loadControl = new DefaultLoadControl.Builder().setBufferDurationsMs(
            Math.toIntExact(videoPlayerBufferOptions.minBufferMs), Math.toIntExact(videoPlayerBufferOptions.maxBufferMs), Math.toIntExact(videoPlayerBufferOptions.bufferForPlaybackMs), Math.toIntExact(videoPlayerBufferOptions.bufferForPlaybackAfterRebufferMs)
    ).build();

    Log.i("Cache", "enableCache="+options.enableCache+ ",cacheDirectory="+options.cacheDirectory + ",maxCacheBytes="+options.maxCacheBytes + ",maxFileBytes="+options.maxFileBytes);

    ExoPlayer exoPlayer = new ExoPlayer.Builder(context,
            new DefaultRenderersFactory(context),
            new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory()),
            new DefaultTrackSelector(context),
            loadControl,
            DefaultBandwidthMeter.getSingletonInstance(context),
            new DefaultAnalyticsCollector(Clock.DEFAULT)
    ).build();

    Uri uri = Uri.parse(asset.assetUrl);

    if(asset instanceof HttpVideoAsset) {
      HttpVideoAsset httpVideoAsset = (HttpVideoAsset) asset;
      buildHttpDataSourceFactory(httpVideoAsset.httpHeaders);

      DataSource.Factory dataSourceFactory;
      if (isHTTP(uri) && options.enableCache) {
        CacheDataSourceFactory cacheDataSourceFactory =
                new CacheDataSourceFactory(
                        context,
                        options.maxCacheBytes,
                        options.maxFileBytes , options.cacheDirectory);
        if (!httpVideoAsset.httpHeaders.isEmpty()) {
          cacheDataSourceFactory.setHeaders(httpVideoAsset.httpHeaders);
        }
        dataSourceFactory = cacheDataSourceFactory;
      } else {
        dataSourceFactory = new DefaultDataSource.Factory(context);
      }

      MediaSource mediaSource = buildMediaSource(uri, dataSourceFactory, httpVideoAsset.streamingFormat);
      exoPlayer.setMediaSource(mediaSource);
    }

    return exoPlayer;
  }

  @OptIn(markerClass = UnstableApi.class)
  @VisibleForTesting
  public static void buildHttpDataSourceFactory(@NonNull Map<String, String> httpHeaders) {
    final boolean httpHeadersNotEmpty = !httpHeaders.isEmpty();
    final String userAgent =
            httpHeadersNotEmpty && httpHeaders.containsKey(USER_AGENT)
                    ? httpHeaders.get(USER_AGENT)
                    : "ExoPlayer";

    httpDataSourceFactory.setUserAgent(userAgent).setAllowCrossProtocolRedirects(true);

    if (httpHeadersNotEmpty) {
      httpDataSourceFactory.setDefaultRequestProperties(httpHeaders);
    }
  }

  @OptIn(markerClass = UnstableApi.class)
  private static MediaSource buildMediaSource(
          Uri uri, DataSource.Factory mediaDataSourceFactory, VideoAsset.StreamingFormat streamingFormat) {
    int type;
    if (streamingFormat == null) {
      type = Util.inferContentType(uri);
    } else {
      switch (streamingFormat) {
        case SMOOTH:
          type = C.CONTENT_TYPE_SS;
          break;
        case DYNAMIC_ADAPTIVE:
          type = C.CONTENT_TYPE_DASH;
          break;
        case HTTP_LIVE:
          type = C.CONTENT_TYPE_HLS;
          break;
        case UNKNOWN:
          type = C.CONTENT_TYPE_OTHER;
          break;
        default:
          type = -1;
          break;
      }
    }
    switch (type) {
      case C.CONTENT_TYPE_SS:
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mediaDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_DASH:
        return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mediaDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_HLS:
        return new HlsMediaSource.Factory(mediaDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_OTHER:
        return new ProgressiveMediaSource.Factory(mediaDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
      default:
      {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

  private static boolean isHTTP(Uri uri) {
    if (uri == null || uri.getScheme() == null) {
      return false;
    }
    String scheme = uri.getScheme();
    return scheme.equals("http") || scheme.equals("https");
  }

  /** A closure-compatible signature since {@link java.util.function.Supplier} is API level 24. */
  public interface ExoPlayerProvider {
    /**
     * Returns a new {@link ExoPlayer}.
     *
     * @return new instance.
     */
    @NonNull
    ExoPlayer get();
  }

  public VideoPlayer(
      @NonNull VideoPlayerCallbacks events,
      @NonNull MediaItem mediaItem,
      @NonNull VideoPlayerOptions options,
      @Nullable SurfaceProducer surfaceProducer,
      @NonNull ExoPlayerProvider exoPlayerProvider
      ) {
    this.videoPlayerEvents = events;
    this.mediaItem = mediaItem;
    this.options = options;
    this.exoPlayerProvider = exoPlayerProvider;
    this.surfaceProducer = surfaceProducer;
    this.exoPlayer = createVideoPlayer();
  }

  @NonNull
  protected ExoPlayer createVideoPlayer() {
    ExoPlayer exoPlayer = exoPlayerProvider.get();
    exoPlayer.setMediaItem(mediaItem);
    exoPlayer.prepare();
    exoPlayer.addListener(createExoPlayerEventListener(exoPlayer, surfaceProducer));
    setAudioAttributes(exoPlayer, options.mixWithOthers);

    return exoPlayer;
  }

  @NonNull
  protected abstract ExoPlayerEventListener createExoPlayerEventListener(
      @NonNull ExoPlayer exoPlayer, @Nullable SurfaceProducer surfaceProducer);

  void sendBufferingUpdate() {
    videoPlayerEvents.onBufferingUpdate(exoPlayer.getBufferedPosition());
  }

  private static void setAudioAttributes(ExoPlayer exoPlayer, boolean isMixMode) {
    exoPlayer.setAudioAttributes(
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
        !isMixMode);
  }

  void play() {
    exoPlayer.play();
  }

  void pause() {
    exoPlayer.pause();
  }

  void setLooping(boolean value) {
    exoPlayer.setRepeatMode(value ? REPEAT_MODE_ALL : REPEAT_MODE_OFF);
  }

  void setVolume(double value) {
    float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
    exoPlayer.setVolume(bracketedValue);
  }

  void setPlaybackSpeed(double value) {
    // We do not need to consider pitch and skipSilence for now as we do not handle them and
    // therefore never diverge from the default values.
    final PlaybackParameters playbackParameters = new PlaybackParameters(((float) value));

    exoPlayer.setPlaybackParameters(playbackParameters);
  }

  void seekTo(int location) {
    exoPlayer.seekTo(location);
  }

  long getPosition() {
    return exoPlayer.getCurrentPosition();
  }

  @NonNull
  public ExoPlayer getExoPlayer() {
    return exoPlayer;
  }

  public void dispose() {
    exoPlayer.release();
  }
}
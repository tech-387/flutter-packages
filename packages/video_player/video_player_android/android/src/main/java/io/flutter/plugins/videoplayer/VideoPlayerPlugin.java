// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LongSparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.Downloader;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.FlutterInjector;
import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugins.videoplayer.platformview.PlatformVideoViewFactory;
import io.flutter.plugins.videoplayer.platformview.PlatformViewVideoPlayer;
import io.flutter.plugins.videoplayer.texture.TextureVideoPlayer;
import io.flutter.view.TextureRegistry;
import kotlin.Result;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Android platform implementation of the VideoPlayerPlugin. */
public class VideoPlayerPlugin implements FlutterPlugin, AndroidVideoPlayerApi {
  private static final String TAG = "VideoPlayerPlugin";

  private final LongSparseArray<VideoPlayer> videoPlayers = new LongSparseArray<>();
  private FlutterState flutterState;
  private final VideoPlayerOptions sharedOptions = new VideoPlayerOptions();
  private long nextPlayerIdentifier = 1;

  @UnstableApi
  private final Map<String, Downloader> preloadDownloadersByUri = new ConcurrentHashMap<>();

  private final ExecutorService preloadExecutor = Executors.newFixedThreadPool(2);
  private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

  /** Register this with the v2 embedding for the plugin to respond to lifecycle callbacks. */
  public VideoPlayerPlugin() {}

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    final FlutterInjector injector = FlutterInjector.instance();
    this.flutterState =
        new FlutterState(
            binding.getApplicationContext(),
            binding.getBinaryMessenger(),
            injector.flutterLoader()::getLookupKeyForAsset,
            injector.flutterLoader()::getLookupKeyForAsset,
            binding.getTextureRegistry());
    flutterState.startListening(this, binding.getBinaryMessenger());

    binding
        .getPlatformViewRegistry()
        .registerViewFactory(
            "plugins.flutter.dev/video_player_android",
            new PlatformVideoViewFactory(videoPlayers::get));
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (flutterState == null) {
      Log.wtf(TAG, "Detached from the engine before registering to it.");
    }
    flutterState.stopListening(binding.getBinaryMessenger());
    flutterState = null;
    onDestroy();
  }

  private void disposeAllPlayers() {
    for (int i = 0; i < videoPlayers.size(); i++) {
      videoPlayers.valueAt(i).dispose();
    }
    videoPlayers.clear();
  }

  @OptIn(markerClass = UnstableApi.class)
  public void onDestroy() {
    // The whole FlutterView is being destroyed. Here we release resources acquired for all
    // instances
    // of VideoPlayer. Once https://github.com/flutter/flutter/issues/19358 is resolved this may
    // be replaced with just asserting that videoPlayers.isEmpty().
    // https://github.com/flutter/flutter/issues/20989 tracks this.
    disposeAllPlayers();
    for (Downloader downloader : preloadDownloadersByUri.values()) {
      downloader.cancel();
    }
    preloadDownloadersByUri.clear();
    preloadExecutor.shutdownNow();
  }

  @Override
  public void initialize() {
    disposeAllPlayers();
  }

  @OptIn(markerClass = UnstableApi.class)
  @Override
  public long createForPlatformView(@NonNull CreationOptions options) {
    final VideoAsset videoAsset = videoAssetWithOptions(options);

      BufferOptionsMessage bufferOptionsMessage = options.getBufferOptions();
      LoggerOptionsMessage loggerOptionsMessage = options.getLoggerOptions();

      VideoPlayerBufferOptions videoPlayerBufferOptions = new VideoPlayerBufferOptions(
              bufferOptionsMessage != null ? bufferOptionsMessage.getMinBufferMs() : 15000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMaxBufferMs() : 30000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getBufferForPlaybackMs() : 2000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getBufferForPlaybackAfterRebufferMs() : 2000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMinDurationForQualityIncreaseMs() : 3000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMaxDurationForQualityDecreaseMs() : 3000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMinDurationToRetainAfterDiscardMs() : 3000L,
              Math.toIntExact(bufferOptionsMessage != null ? bufferOptionsMessage.getMaxWidthToDiscard() : 1279),
              Math.toIntExact(bufferOptionsMessage != null ? bufferOptionsMessage.getMaxHeightToDiscard() : 719),
              bufferOptionsMessage != null ? bufferOptionsMessage.getBandwidthFraction() : 0.85f,
              bufferOptionsMessage != null ? bufferOptionsMessage.getBufferedFractionToLiveEdgeForQualityIncrease() : 0.75f
      );

      VideoPlayerLoggerOptions videoPlayerLoggerOptions = new VideoPlayerLoggerOptions(
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableTransferListenerLogs(),
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableBandwidthListenerLogs(),
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableAdaptiveTrackSelectionLogs(),
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableCacheDataSourceLogs()
      );


      long id = nextPlayerIdentifier++;
    final String streamInstance = Long.toString(id);
    VideoPlayerOptions playerOptions = new VideoPlayerOptions(sharedOptions);
    playerOptions.backBufferDurationMs = options.getBackBufferDurationMs();

    VideoPlayer videoPlayer =
        PlatformViewVideoPlayer.create(
            flutterState.applicationContext,
            VideoPlayerEventCallbacks.bindTo(flutterState.binaryMessenger, streamInstance),
            videoAsset,
            playerOptions,
                videoPlayerBufferOptions,
                videoPlayerLoggerOptions
                );

    registerPlayerInstance(videoPlayer, id);
    return id;
  }

  @OptIn(markerClass = UnstableApi.class)
  @Override
  public @NonNull TexturePlayerIds createForTextureView(@NonNull CreationOptions options) {
    final VideoAsset videoAsset = videoAssetWithOptions(options);

      BufferOptionsMessage bufferOptionsMessage = options.getBufferOptions();
      LoggerOptionsMessage loggerOptionsMessage = options.getLoggerOptions();

      VideoPlayerBufferOptions videoPlayerBufferOptions = new VideoPlayerBufferOptions(
              bufferOptionsMessage != null ? bufferOptionsMessage.getMinBufferMs() : 15000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMaxBufferMs() : 30000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getBufferForPlaybackMs() : 2000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getBufferForPlaybackAfterRebufferMs() : 2000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMinDurationForQualityIncreaseMs() : 3000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMaxDurationForQualityDecreaseMs() : 3000L,
              bufferOptionsMessage != null ? bufferOptionsMessage.getMinDurationToRetainAfterDiscardMs() : 3000L,
              Math.toIntExact(bufferOptionsMessage != null ? bufferOptionsMessage.getMaxWidthToDiscard() : 1279),
              Math.toIntExact(bufferOptionsMessage != null ? bufferOptionsMessage.getMaxHeightToDiscard() : 719),
              bufferOptionsMessage != null ? bufferOptionsMessage.getBandwidthFraction() : 0.85f,
              bufferOptionsMessage != null ? bufferOptionsMessage.getBufferedFractionToLiveEdgeForQualityIncrease() : 0.75f
      );

      VideoPlayerLoggerOptions videoPlayerLoggerOptions = new VideoPlayerLoggerOptions(
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableTransferListenerLogs(),
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableBandwidthListenerLogs(),
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableAdaptiveTrackSelectionLogs(),
              loggerOptionsMessage == null || loggerOptionsMessage.getEnableCacheDataSourceLogs()
      );

    long id = nextPlayerIdentifier++;
    final String streamInstance = Long.toString(id);
    TextureRegistry.SurfaceProducer handle = flutterState.textureRegistry.createSurfaceProducer();
    VideoPlayerOptions playerOptions = new VideoPlayerOptions(sharedOptions);
    playerOptions.backBufferDurationMs = options.getBackBufferDurationMs();

    VideoPlayer videoPlayer =
        TextureVideoPlayer.create(
            flutterState.applicationContext,
            VideoPlayerEventCallbacks.bindTo(flutterState.binaryMessenger, streamInstance),
            handle,
            videoAsset,
            playerOptions,
                videoPlayerBufferOptions,
                videoPlayerLoggerOptions
                );

    registerPlayerInstance(videoPlayer, id);
    return new TexturePlayerIds(id, handle.id());
  }

  private @NonNull VideoAsset videoAssetWithOptions(@NonNull CreationOptions options) {
    final @NonNull String uri = options.getUri();
    if (uri.startsWith("asset:")) {
      return VideoAsset.fromAssetUrl(uri);
    } else if (uri.startsWith("rtsp:")) {
      return VideoAsset.fromRtspUrl(uri);
    } else {
      VideoAsset.StreamingFormat streamingFormat = VideoAsset.StreamingFormat.UNKNOWN;
      PlatformVideoFormat formatHint = options.getFormatHint();
      if (formatHint != null) {
        switch (formatHint) {
          case SS:
            streamingFormat = VideoAsset.StreamingFormat.SMOOTH;
            break;
          case DASH:
            streamingFormat = VideoAsset.StreamingFormat.DYNAMIC_ADAPTIVE;
            break;
          case HLS:
            streamingFormat = VideoAsset.StreamingFormat.HTTP_LIVE;
            break;
        }
      }
      return VideoAsset.fromRemoteUrl(
          uri, streamingFormat, options.getHttpHeaders(), options.getUserAgent());
    }
  }

  private void registerPlayerInstance(VideoPlayer player, long id) {
    // Set up the instance-specific API handler, and make sure it is removed when the player is
    // disposed.
    BinaryMessenger messenger = flutterState.binaryMessenger;
    final String channelSuffix = Long.toString(id);
    VideoPlayerInstanceApi.Companion.setUp(messenger, player, channelSuffix);
    player.setDisposeHandler(
        () -> VideoPlayerInstanceApi.Companion.setUp(messenger, null, channelSuffix));

    videoPlayers.put(id, player);
  }

  @NonNull
  private VideoPlayer getPlayer(long playerId) {
    VideoPlayer player = videoPlayers.get(playerId);

    // Avoid a very ugly un-debuggable NPE that results in returning a null player.
    if (player == null) {
      String message = "No player found with playerId <" + playerId + ">";
      if (videoPlayers.size() == 0) {
        message += " and no active players created by the plugin.";
      }
      throw new IllegalStateException(message);
    }

    return player;
  }

  @Override
  public void dispose(long playerId) {
    VideoPlayer player = getPlayer(playerId);
    player.dispose();
    videoPlayers.remove(playerId);
  }

  @Override
  public void setMixWithOthers(boolean mixWithOthers) {
    sharedOptions.mixWithOthers = mixWithOthers;
  }

    @Override
    public void setCacheOptions(@NotNull CacheOptionsMessage msg) {
        sharedOptions.cacheDirectory = msg.getCacheDirectory();
        sharedOptions.maxCacheBytes = msg.getMaxCacheBytes();
        sharedOptions.maxFileBytes = msg.getMaxFileBytes();
        sharedOptions.enableCache = msg.getEnableCache();
    }

  // Data-only cache warming (Tier A): downloads exactly the first `segmentCount` HLS segments
  // into the same SimpleCache singleton and with the same upstream/cache-key configuration as
  // playback (see CacheDataSourceFactory), via ExactHlsSegmentDownloader - no renderers, no
  // decoder, no VideoPlayer/ExoPlayer instance is created. Runs on a background executor;
  // preloadDownloadersByUri lets cancelPreload() interrupt an in-flight download for a URI.
  @OptIn(markerClass = UnstableApi.class)
  @Override
  public void preloadIntoCache(
      @NonNull String uri,
      long segmentCount,
      @NonNull Map<String, String> httpHeaders,
      @NonNull Function1<? super Result<Unit>, Unit> callback) {
    if (!sharedOptions.enableCache) {
      ResultCompat.success(null, callback);
      return;
    }

    cancelPreload(uri);

    DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();
    httpDataSourceFactory.setUserAgent("ExoPlayer");
    httpDataSourceFactory.setAllowCrossProtocolRedirects(true);
    httpDataSourceFactory.setDefaultRequestProperties(httpHeaders);

    SimpleCache simpleCache =
        SimpleCacheSingleton.getInstance(
                flutterState.applicationContext,
                sharedOptions.maxCacheBytes,
                sharedOptions.cacheDirectory)
            .simpleCache;

    // No explicit CacheKeyFactory - defaults to URI-based keys, same as
    // CacheDataSourceFactory.createDataSource() (which also passes a null factory), so preload
    // and playback resolve to identical cache keys.
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setCacheWriteDataSinkFactory(
                new CacheDataSink.Factory()
                    .setCache(simpleCache)
                    .setFragmentSize(sharedOptions.maxFileBytes));

    Downloader downloader =
        new ExactHlsSegmentDownloader(cacheDataSourceFactory, uri, (int) segmentCount);

    preloadDownloadersByUri.put(uri, downloader);

    preloadExecutor.execute(
        () -> {
          Throwable error = null;
          try {
            downloader.download(null);
          } catch (InterruptedException e) {
            // Canceled via cancelPreload() - not an error condition.
            Thread.currentThread().interrupt();
          } catch (Exception e) {
            // CacheWriter surfaces cancellation as InterruptedIOException (an IOException),
            // not InterruptedException, so a canceled preload lands here rather than above.
            if (!((ExactHlsSegmentDownloader) downloader).isCanceled()) {
              error = e;
              Log.e(TAG, "preloadIntoCache failed for uri=" + uri, e);
            }
          } finally {
            preloadDownloadersByUri.remove(uri, downloader);
          }
          final Throwable finalError = error;
          mainThreadHandler.post(
              () -> {
                if (finalError != null) {
                  ResultCompat.failure(finalError, callback);
                } else {
                  ResultCompat.success(null, callback);
                }
              });
        });
  }

  @OptIn(markerClass = UnstableApi.class)
  @Override
  public void cancelPreload(@NonNull String uri) {
    Downloader downloader = preloadDownloadersByUri.remove(uri);
    if (downloader != null) {
      downloader.cancel();
    }
  }

  @Override
  public @NonNull String getLookupKeyForAsset(@NonNull String asset, @Nullable String packageName) {
    return packageName == null
        ? flutterState.keyForAsset.get(asset)
        : flutterState.keyForAssetAndPackageName.get(asset, packageName);
  }

  private interface KeyForAssetFn {
    String get(String asset);
  }

  private interface KeyForAssetAndPackageName {
    String get(String asset, String packageName);
  }

  private static final class FlutterState {
    final Context applicationContext;
    final BinaryMessenger binaryMessenger;
    final KeyForAssetFn keyForAsset;
    final KeyForAssetAndPackageName keyForAssetAndPackageName;
    final TextureRegistry textureRegistry;

    FlutterState(
        Context applicationContext,
        BinaryMessenger messenger,
        KeyForAssetFn keyForAsset,
        KeyForAssetAndPackageName keyForAssetAndPackageName,
        TextureRegistry textureRegistry) {
      this.applicationContext = applicationContext;
      this.binaryMessenger = messenger;
      this.keyForAsset = keyForAsset;
      this.keyForAssetAndPackageName = keyForAssetAndPackageName;
      this.textureRegistry = textureRegistry;
    }

    void startListening(VideoPlayerPlugin methodCallHandler, BinaryMessenger messenger) {
      AndroidVideoPlayerApi.Companion.setUp(messenger, methodCallHandler);
    }

    void stopListening(BinaryMessenger messenger) {
      AndroidVideoPlayerApi.Companion.setUp(messenger, null);
    }
  }
}
// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import static android.os.Looper.getMainLooper;
import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_OFF;

import static com.google.common.net.HttpHeaders.USER_AGENT;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
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
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.experimental.ExperimentalBandwidthMeter;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.extractor.DefaultExtractorsFactory;

import java.util.Map;

import io.flutter.view.TextureRegistry;

@UnstableApi
final class VideoPlayer implements TextureRegistry.SurfaceProducer.Callback {
    @NonNull
    private final ExoPlayerProvider exoPlayerProvider;
    @NonNull
    private final MediaItem mediaItem;
    @NonNull
    private final TextureRegistry.SurfaceProducer surfaceProducer;
    @NonNull
    private final VideoPlayerCallbacks videoPlayerEvents;
    @NonNull
    private final VideoPlayerOptions options;
    @NonNull
    private ExoPlayer exoPlayer;
    @Nullable
    private ExoPlayerState savedStateDuring;
    private static DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();

    /**
     * Creates a video player.
     *
     * @param context         application context.
     * @param events          event callbacks.
     * @param surfaceProducer produces a texture to render to.
     * @param asset           asset to play.
     * @param options         options for playback.
     * @return a video player instance.
     */
    @UnstableApi
    @NonNull
    static VideoPlayer create(
            @NonNull Context context,
            @NonNull VideoPlayerCallbacks events,
            @NonNull TextureRegistry.SurfaceProducer surfaceProducer,
            @NonNull VideoAsset asset,
            @NonNull VideoPlayerOptions options,
            @NonNull VideoPlayerBufferOptions bufferOptions,
            @NonNull VideoPlayerLoggerOptions loggerOptions
    ) {
        return new VideoPlayer(
                () -> {

                    Log.i("VideoPlayer", "create: minBufferMs=" + bufferOptions.minBufferMs + ",maxBufferMs=" + bufferOptions.maxBufferMs + ",bufferForPlaybackMs=" + bufferOptions.bufferForPlaybackMs + ",bufferForPlaybackAfterRebufferMs=" + bufferOptions.bufferForPlaybackAfterRebufferMs);
                    Log.i("VideoPlayer", "create: enableCache=" + options.enableCache + ",cacheDirectory=" + options.cacheDirectory + ",maxCacheBytes=" + options.maxCacheBytes + ",maxFileBytes=" + options.maxFileBytes);
                    Log.i("VideoPlayer", "create: enableTransferListenerLogs=" + loggerOptions.enableTransferListenerLogs + ",enableBandwidthListenerLogs=" + loggerOptions.enableBandwidthListenerLogs + ",enableAdaptiveTrackSelectionLogs=" + loggerOptions.enableAdaptiveTrackSelectionLogs + ",enableCacheDataSourceLogs=" + loggerOptions.enableCacheDataSourceLogs);

                    // Custom load control and buffering configuration.
                    LoadControl loadControl = new CustomLoadControl.Builder().setBufferDurationsMs(
                            Math.toIntExact(bufferOptions.minBufferMs), Math.toIntExact(bufferOptions.maxBufferMs), Math.toIntExact(bufferOptions.bufferForPlaybackMs), Math.toIntExact(bufferOptions.bufferForPlaybackAfterRebufferMs)
                    ).setPrioritizeTimeOverSizeThresholds(false).build();

                    // ExoPlayer provides an opportunity to switch to a software decoder if a primary one isn't available.
                    RenderersFactory renderersFactory = new DefaultRenderersFactory(context).setEnableDecoderFallback(true);
                    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory());

                    DefaultBandwidthMeter.Builder builder = BandwidthEstimatorSingleton.getInstance(context).bandwidthMeter;

                    DefaultBandwidthMeter bandwidthMeter = builder.build();

                    if(loggerOptions.enableBandwidthListenerLogs) {
                        Log.d("VideoPlayer", "initial estimate = " + bandwidthMeter.getBitrateEstimate());
                    }

                    // Create a Handler. This is used by ExoPlayer to dispatch the events
                    // back to your main thread (or whichever thread you create the Handler on).
                    Handler mainHandler = new Handler(getMainLooper()); // Using the main looper for UI safety

                    // Create custom bandwidth listener
                    CustomBandwidthListener customBandwidthListener = new CustomBandwidthListener(
                            bandwidthMeter, loggerOptions,
                            asset.assetUrl
                    );

                    // Add the listener
                    bandwidthMeter.addEventListener(mainHandler, customBandwidthListener);

                    DefaultTrackSelector trackSelector;
                    Uri uri = Uri.parse(asset.assetUrl);
                    if (isHTTP(uri) && options.enableCache) {
                        CacheDataSourceFactory cacheDataSourceFactory = new CacheDataSourceFactory(
                                context,
                                options.maxCacheBytes,
                                options.maxFileBytes, options.cacheDirectory, asset.assetUrl, loggerOptions);
                        mediaSourceFactory.setDataSourceFactory(cacheDataSourceFactory);
                        trackSelector = new DefaultTrackSelector(context,
                                new CustomAdaptiveTrackSelectionFactory(asset.assetUrl, cacheDataSourceFactory, loggerOptions, bufferOptions)
                        );
                    } else {
                        // Custom track selector and custom adaptive track selection factory.
                        trackSelector = new DefaultTrackSelector(context,
                                new CustomAdaptiveTrackSelectionFactory(asset.assetUrl, null, loggerOptions, bufferOptions)
                        );
                    }

                    Log.d("CustomBitrate", "bandwidthMeter.getBitrateEstimate=" + bandwidthMeter.getBitrateEstimate());

                    ExoPlayer exoPlayer = new ExoPlayer.Builder(context,
                            renderersFactory,
                            mediaSourceFactory,
                            trackSelector,
                            loadControl,
                            bandwidthMeter,
                            new DefaultAnalyticsCollector(Clock.DEFAULT)
                    ).build();

                    if (asset instanceof HttpVideoAsset) {
                        HttpVideoAsset httpVideoAsset = (HttpVideoAsset) asset;
                        buildHttpDataSourceFactory(httpVideoAsset.httpHeaders);
                        MediaSource mediaSource = buildMediaSource(uri, httpDataSourceFactory, httpVideoAsset.streamingFormat);
                        exoPlayer.setMediaSource(mediaSource);
                    }

                    exoPlayer.addAnalyticsListener(new EventLogger());

                    return exoPlayer;
                },
                events,
                surfaceProducer,
                asset.getMediaItem(),
                options,
                bufferOptions
        );
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
                return new HlsMediaSource.Factory(mediaDataSourceFactory).setAllowChunklessPreparation(true)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(mediaDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            default: {
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

    /**
     * A closure-compatible signature since {@link java.util.function.Supplier} is API level 24.
     */
    interface ExoPlayerProvider {
        /**
         * Returns a new {@link ExoPlayer}.
         *
         * @return new instance.
         */
        ExoPlayer get();
    }

    @VisibleForTesting
    VideoPlayer(
            @NonNull ExoPlayerProvider exoPlayerProvider,
            @NonNull VideoPlayerCallbacks events,
            @NonNull TextureRegistry.SurfaceProducer surfaceProducer,
            @NonNull MediaItem mediaItem,
            @NonNull VideoPlayerOptions options,
            @NonNull VideoPlayerBufferOptions bufferOptions
    ) {
        this.exoPlayerProvider = exoPlayerProvider;
        this.videoPlayerEvents = events;
        this.surfaceProducer = surfaceProducer;
        this.mediaItem = mediaItem;
        this.options = options;
        this.exoPlayer = createVideoPlayer();

        surfaceProducer.setCallback(this);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void onSurfaceAvailable() {
        if (savedStateDuring != null) {
            exoPlayer = createVideoPlayer();
            savedStateDuring.restore(exoPlayer);
            savedStateDuring = null;
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    // TODO(bparrishMines): Replace with onSurfaceCleanup once available on stable. See
    // https://github.com/flutter/flutter/issues/161256.
    @SuppressWarnings({"deprecation", "removal"})
    public void onSurfaceDestroyed() {
        // Intentionally do not call pause/stop here, because the surface has already been released
        // at this point (see https://github.com/flutter/flutter/issues/156451).
        savedStateDuring = ExoPlayerState.save(exoPlayer);
        exoPlayer.release();
    }

    private ExoPlayer createVideoPlayer() {
        ExoPlayer exoPlayer = exoPlayerProvider.get();
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();

        exoPlayer.setVideoSurface(surfaceProducer.getSurface());

        boolean wasInitialized = savedStateDuring != null;
        exoPlayer.addListener(new ExoPlayerEventListener(exoPlayer, videoPlayerEvents, wasInitialized));
        setAudioAttributes(exoPlayer, options.mixWithOthers);

        return exoPlayer;
    }

    void sendBufferingUpdate() {
        videoPlayerEvents.onBufferingUpdate(exoPlayer.getBufferedPosition());
    }

    private static void setAudioAttributes(ExoPlayer exoPlayer, boolean isMixMode) {
        // ExoPlayer uses the audio manager and can request focus on your behalf. However, you need to explicitly do s
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

    void dispose() {
        exoPlayer.release();
        surfaceProducer.release();

        // TODO(matanlurey): Remove when embedder no longer calls-back once released.
        // https://github.com/flutter/flutter/issues/156434.
        surfaceProducer.setCallback(null);
    }
}
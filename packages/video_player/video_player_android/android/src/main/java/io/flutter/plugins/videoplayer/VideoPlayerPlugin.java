// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugins.videoplayer.Messages.AndroidVideoPlayerApi;
import io.flutter.plugins.videoplayer.Messages.CreateMessage;
import io.flutter.view.TextureRegistry;

/**
 * Android platform implementation of the VideoPlayerPlugin.
 */
public class VideoPlayerPlugin implements FlutterPlugin, AndroidVideoPlayerApi {
    private static final String TAG = "VideoPlayerPlugin";
    private final LongSparseArray<VideoPlayer> videoPlayers = new LongSparseArray<>();
    private final VideoPlayerOptions options = new VideoPlayerOptions();
    private FlutterState flutterState;

    /**
     * Register this with the v2 embedding for the plugin to respond to lifecycle callbacks.
     */
    public VideoPlayerPlugin() {
    }

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

    @OptIn(markerClass = UnstableApi.class)
    private void disposeAllPlayers() {
        for (int i = 0; i < videoPlayers.size(); i++) {
            videoPlayers.valueAt(i).dispose();
        }
        videoPlayers.clear();
    }

    public void onDestroy() {
        // The whole FlutterView is being destroyed. Here we release resources acquired for all
        // instances
        // of VideoPlayer. Once https://github.com/flutter/flutter/issues/19358 is resolved this may
        // be replaced with just asserting that videoPlayers.isEmpty().
        // https://github.com/flutter/flutter/issues/20989 tracks this.
        disposeAllPlayers();
    }

    public void initialize() {
        disposeAllPlayers();
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public @NonNull Long create(@NonNull CreateMessage arg) {
        TextureRegistry.SurfaceProducer handle = flutterState.textureRegistry.createSurfaceProducer();
        EventChannel eventChannel =
                new EventChannel(
                        flutterState.binaryMessenger, "flutter.io/videoPlayer/videoEvents" + handle.id());

        Messages.BufferOptionsMessage bufferOptionsMessage = arg.getBufferOptions();
        Messages.LoggerOptionsMessage loggerOptionsMessage = arg.getLoggerOptions();


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
                loggerOptionsMessage != null ? loggerOptionsMessage.getEnableTransferListenerLogs() : true,
                loggerOptionsMessage != null ? loggerOptionsMessage.getEnableBandwidthListenerLogs() : true,
                loggerOptionsMessage != null ? loggerOptionsMessage.getEnableAdaptiveTrackSelectionLogs() : true,
                loggerOptionsMessage != null ? loggerOptionsMessage.getEnableCacheDataSourceLogs() : true
        );

        final VideoAsset videoAsset;
        if (arg.getAsset() != null) {
            String assetLookupKey;
            if (arg.getPackageName() != null) {
                assetLookupKey =
                        flutterState.keyForAssetAndPackageName.get(arg.getAsset(), arg.getPackageName());
            } else {
                assetLookupKey = flutterState.keyForAsset.get(arg.getAsset());
            }
            videoAsset = VideoAsset.fromAssetUrl("asset:///" + assetLookupKey);
        } else if (arg.getUri().startsWith("rtsp://")) {
            videoAsset = VideoAsset.fromRtspUrl(arg.getUri());
        } else {
            VideoAsset.StreamingFormat streamingFormat = VideoAsset.StreamingFormat.UNKNOWN;
            String formatHint = arg.getFormatHint();
            if (formatHint != null) {
                switch (formatHint) {
                    case "ss":
                        streamingFormat = VideoAsset.StreamingFormat.SMOOTH;
                        break;
                    case "dash":
                        streamingFormat = VideoAsset.StreamingFormat.DYNAMIC_ADAPTIVE;
                        break;
                    case "hls":
                        streamingFormat = VideoAsset.StreamingFormat.HTTP_LIVE;
                        break;
                }
            }
            videoAsset = VideoAsset.fromRemoteUrl(arg.getUri(), streamingFormat, arg.getHttpHeaders());
        }
        videoPlayers.put(
                handle.id(),
                VideoPlayer.create(
                        flutterState.applicationContext,
                        VideoPlayerEventCallbacks.bindTo(eventChannel),
                        handle,
                        videoAsset,
                        options,
                        videoPlayerBufferOptions,
                        videoPlayerLoggerOptions
                ));
        return handle.id();
    }

    @OptIn(markerClass = UnstableApi.class)
    @NonNull
    private VideoPlayer getPlayer(long textureId) {
        VideoPlayer player = videoPlayers.get(textureId);

        // Avoid a very ugly un-debuggable NPE that results in returning a null player.
        if (player == null) {
            String message = "No player found with textureId <" + textureId + ">";
            if (videoPlayers.size() == 0) {
                message += " and no active players created by the plugin.";
            }
            throw new IllegalStateException(message);
        }

        return player;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void dispose(@NonNull Long textureId) {
        VideoPlayer player = getPlayer(textureId);
        player.dispose();
        videoPlayers.remove(textureId);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void setLooping(@NonNull Long textureId, @NonNull Boolean looping) {
        VideoPlayer player = getPlayer(textureId);
        player.setLooping(looping);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void setVolume(@NonNull Long textureId, @NonNull Double volume) {
        VideoPlayer player = getPlayer(textureId);
        player.setVolume(volume);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void setPlaybackSpeed(@NonNull Long textureId, @NonNull Double speed) {
        VideoPlayer player = getPlayer(textureId);
        player.setPlaybackSpeed(speed);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void play(@NonNull Long textureId) {
        VideoPlayer player = getPlayer(textureId);
        player.play();
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public @NonNull Long position(@NonNull Long textureId) {
        VideoPlayer player = getPlayer(textureId);
        long position = player.getPosition();
        player.sendBufferingUpdate();
        return position;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void seekTo(@NonNull Long textureId, @NonNull Long position) {
        VideoPlayer player = getPlayer(textureId);
        player.seekTo(position.intValue());
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void pause(@NonNull Long textureId) {
        VideoPlayer player = getPlayer(textureId);
        player.pause();
    }

    @Override
    public void setMixWithOthers(@NonNull Boolean mixWithOthers) {
        options.mixWithOthers = mixWithOthers;
    }

    @Override
    public void setCacheOptions(Messages.CacheOptionsMessage msg) {
        options.cacheDirectory = msg.getCacheDirectory();
        options.maxCacheBytes = msg.getMaxCacheBytes();
        options.maxFileBytes = msg.getMaxFileBytes();
        options.enableCache = msg.getEnableCache();
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
            AndroidVideoPlayerApi.setUp(messenger, methodCallHandler);
        }

        void stopListening(BinaryMessenger messenger) {
            AndroidVideoPlayerApi.setUp(messenger, null);
        }
    }
}

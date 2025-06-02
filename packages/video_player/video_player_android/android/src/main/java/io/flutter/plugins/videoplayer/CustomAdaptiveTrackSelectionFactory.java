package io.flutter.plugins.videoplayer;

import androidx.annotation.OptIn;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import com.google.common.collect.ImmutableList;

@UnstableApi
public class CustomAdaptiveTrackSelectionFactory extends AdaptiveTrackSelection.Factory {

    private final String assetUrl;
    private final CacheDataSourceFactory cacheDataSourceFactory;
    private final VideoPlayerLoggerOptions loggerOptions;
    private final VideoPlayerBufferOptions bufferOptions;

    public CustomAdaptiveTrackSelectionFactory(String assetUrl, CacheDataSourceFactory cacheDataSourceFactory, VideoPlayerLoggerOptions loggerOptions, VideoPlayerBufferOptions bufferOptions) {
        this.assetUrl = assetUrl;
        this.cacheDataSourceFactory = cacheDataSourceFactory;
        this.loggerOptions = loggerOptions;
        this.bufferOptions = bufferOptions;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected AdaptiveTrackSelection createAdaptiveTrackSelection(
            TrackGroup group,
            int[] tracks,
            int type,
            BandwidthMeter bandwidthMeter,
            ImmutableList<AdaptiveTrackSelection.AdaptationCheckpoint> adaptationCheckpoints) {

        return new CustomAdaptiveTrackSelection(
                group,
                tracks,
                type,
                bandwidthMeter,
                3_000,
                3_000,
                2_100,
                1279,
                719,
                0.85f,
                0.75f,
                adaptationCheckpoints,
                Clock.DEFAULT,
                assetUrl,
                cacheDataSourceFactory,
                loggerOptions
        );  // Pass assetUrl here
    }
}
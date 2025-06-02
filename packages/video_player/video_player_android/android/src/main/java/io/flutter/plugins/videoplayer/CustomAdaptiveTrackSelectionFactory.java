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
                bufferOptions.minDurationForQualityIncreaseMs,
                bufferOptions.maxDurationForQualityDecreaseMs,
                bufferOptions.minDurationToRetainAfterDiscardMs,
                bufferOptions.maxWidthToDiscard,
                bufferOptions.maxHeightToDiscard,
                (float) bufferOptions.bandwidthFraction,
                (float) bufferOptions.bufferedFractionToLiveEdgeForQualityIncrease,
                adaptationCheckpoints,
                Clock.DEFAULT,
                assetUrl,
                cacheDataSourceFactory,
                loggerOptions
        );  // Pass assetUrl here
    }
}
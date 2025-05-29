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

    public CustomAdaptiveTrackSelectionFactory(String assetUrl, CacheDataSourceFactory cacheDataSourceFactory) {
        this.assetUrl = assetUrl;
        this.cacheDataSourceFactory = cacheDataSourceFactory;
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
                2_000,
                4_000,
                2_000,
                1279,
                719,
                0.7f,
                0.75f,
                adaptationCheckpoints,
                Clock.DEFAULT,
                assetUrl,
                cacheDataSourceFactory
                );  // Pass assetUrl here
    }
}
package io.flutter.plugins.videoplayer;

import androidx.annotation.Nullable;

public class VideoPlayerBufferOptions {
    public Long minBufferMs = 15000L;
    public Long maxBufferMs = 30000L;
    public Long bufferForPlaybackMs = 2000L;
    public Long bufferForPlaybackAfterRebufferMs = 2000L;
    public Long minDurationForQualityIncreaseMs = 3000L;
    public Long maxDurationForQualityDecreaseMs = 3000L;
    public Long minDurationToRetainAfterDiscardMs = 3000L;
    public int maxWidthToDiscard = 1279;
    public int maxHeightToDiscard = 719;
    public double bandwidthFraction = 0.85F;
    public double bufferedFractionToLiveEdgeForQualityIncrease = 0.75f;
    /**
     * Buffer limits while playing; when both are null, minBufferMs/maxBufferMs apply to every
     * player and paused players aren't capped (see RoleAwareLoadControl).
     */
    @Nullable public Long playingMinBufferMs = null;
    @Nullable public Long playingMaxBufferMs = null;


    // Constructor
    public VideoPlayerBufferOptions(Long minBufferMs,
                                    Long maxBufferMs,
                                    Long bufferForPlaybackMs,
                                    Long bufferForPlaybackAfterRebufferMs,
                                    Long minDurationForQualityIncreaseMs,
                                    Long maxDurationForQualityDecreaseMs,
                                    Long minDurationToRetainAfterDiscardMs,
                                    int maxWidthToDiscard,
                                    int maxHeightToDiscard,
                                    double bandwidthFraction,
                                    double bufferedFractionToLiveEdgeForQualityIncrease,
                                    @Nullable Long playingMinBufferMs,
                                    @Nullable Long playingMaxBufferMs
    ) {
        this.minBufferMs = minBufferMs;
        this.maxBufferMs = maxBufferMs;
        this.bufferForPlaybackMs = bufferForPlaybackMs;
        this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
        this.minDurationForQualityIncreaseMs = minDurationForQualityIncreaseMs;
        this.maxDurationForQualityDecreaseMs = maxDurationForQualityDecreaseMs;
        this.minDurationToRetainAfterDiscardMs = minDurationToRetainAfterDiscardMs;
        this.maxWidthToDiscard = maxWidthToDiscard;
        this.maxHeightToDiscard = maxHeightToDiscard;
        this.bandwidthFraction = bandwidthFraction;
        this.bufferedFractionToLiveEdgeForQualityIncrease = bufferedFractionToLiveEdgeForQualityIncrease;
        this.playingMinBufferMs = playingMinBufferMs;
        this.playingMaxBufferMs = playingMaxBufferMs;
    }

    VideoPlayerBufferOptions() {
    }
}

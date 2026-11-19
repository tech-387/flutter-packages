package io.flutter.plugins.videoplayer;

import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;

@UnstableApi
public class CustomAdaptiveTrackSelection extends AdaptiveTrackSelection {

    private static final String TAG = "CustomAdaptiveSelection";
    private final String assetUrl;
    private final CacheDataSourceFactory cacheDataSourceFactory;
    private final CustomLogger customLogger;

    @Nullable private final String videoId;

    private long currentChunkIndex = 1;

    public CustomAdaptiveTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter, String assetUrl, CacheDataSourceFactory cacheDataSourceFactory, VideoPlayerLoggerOptions loggerOptions) {
        super(group, tracks, bandwidthMeter);
        this.assetUrl = assetUrl;
        this.videoId = CachedSegmentIndex.videoIdFromAssetUrl(assetUrl);
        this.cacheDataSourceFactory = cacheDataSourceFactory;
        this.customLogger = new CustomLogger(TAG, loggerOptions.enableAdaptiveTrackSelectionLogs);
    }

    protected CustomAdaptiveTrackSelection(TrackGroup group, int[] tracks, int type, BandwidthMeter bandwidthMeter, long minDurationForQualityIncreaseMs, long maxDurationForQualityDecreaseMs, long minDurationToRetainAfterDiscardMs, int maxWidthToDiscard, int maxHeightToDiscard, float bandwidthFraction, float bufferedFractionToLiveEdgeForQualityIncrease, List<AdaptationCheckpoint> adaptationCheckpoints, Clock clock, String assetUrl, CacheDataSourceFactory cacheDataSourceFactory, VideoPlayerLoggerOptions loggerOptions) {
        super(group, tracks, type, bandwidthMeter, minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs, minDurationToRetainAfterDiscardMs, maxWidthToDiscard, maxHeightToDiscard, bandwidthFraction, bufferedFractionToLiveEdgeForQualityIncrease, adaptationCheckpoints, clock);
        this.assetUrl = assetUrl;
        this.videoId = CachedSegmentIndex.videoIdFromAssetUrl(assetUrl);
        this.cacheDataSourceFactory = cacheDataSourceFactory;
        this.customLogger = new CustomLogger(TAG, loggerOptions.enableAdaptiveTrackSelectionLogs);
    }


    @Override
    public void updateSelectedTrack(
            long playbackPositionUs,
            long bufferedDurationUs,
            long availableDurationUs,
            List<? extends MediaChunk> queue,
            MediaChunkIterator[] mediaChunkIterators) {

        // Track the index of the next chunk to be loaded; canSelectFormat() uses it to check
        // whether that segment is already cached for a candidate format.
        if (!queue.isEmpty()) {
            currentChunkIndex = queue.get(queue.size() - 1).getNextChunkIndex();
        } else {
            currentChunkIndex = 1;
        }

        if (customLogger.isEnabled()) {
            Format currentFormat = getSelectedFormat();
            customLogger.logD("updateSelectedTrack: playbackPositionUs=" + playbackPositionUs
                    + ", bufferedDurationUs=" + bufferedDurationUs
                    + ", availableDurationUs=" + availableDurationUs
                    + ", currentFormat=" + currentFormat.width + "x" + currentFormat.height
                    + "@" + currentFormat.bitrate + ", queueSize=" + queue.size()
                    + ", currentChunkIndex=" + currentChunkIndex);
        }

        super.updateSelectedTrack(playbackPositionUs, bufferedDurationUs, availableDurationUs, queue, mediaChunkIterators);
    }

    @Override
    protected boolean canSelectFormat(Format format, int trackBitrate, long effectiveBitrate) {
        if (customLogger.isEnabled()) {
            customLogger.logD("canSelectFormat: id=" + format.id + ", width=" + format.width
                    + ", height=" + format.height + ", bitrate=" + format.bitrate
                    + ", trackBitrate=" + trackBitrate + ", effectiveBitrate=" + effectiveBitrate
                    + ", currentChunkIndex=" + currentChunkIndex + ", asset=" + assetUrl);
        }

        try {
            JSONObject metadataJson = null;
            if (cacheDataSourceFactory != null) {
                metadataJson = cacheDataSourceFactory.getVideoMetadataJson();
            }

            if (metadataJson != null && videoId != null && metadataJson.has(videoId)) {
                JSONObject videoEntry = metadataJson.getJSONObject(videoId);
                // The next segment is cached for this format: select it regardless of bandwidth,
                // so playback keeps using the variant that is already on disk.
                if (isSegmentCached(videoEntry, format.width, format.height, currentChunkIndex)) {
                    if (customLogger.isEnabled()) {
                        customLogger.logD("Segment " + currentChunkIndex + " is cached for " + format.width + "x" + format.height);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            customLogger.logE("Error checking cache metadata: " + e.getMessage(), e);
        }

        // AdaptiveTrackSelection already falls back to the lowest-bitrate format when no format
        // is selectable, so no special handling of the lowest format is needed here.
        boolean result = super.canSelectFormat(format, trackBitrate, effectiveBitrate);
        if (customLogger.isEnabled()) {
            customLogger.logD("super.canSelectFormat returned: " + result);
        }
        return result;
    }

    /**
     * Whether {@code chunkIndex} is recorded as cached for the variant matching a format of the
     * given size. Variant labels in segment file names are the video's short side (720, 540), so
     * the lookup tries the format's width first and then {@code min(width, height)}, which
     * matches the label even when a lower variant is encoded at the same width as a higher one.
     */
    @VisibleForTesting
    static boolean isSegmentCached(JSONObject videoEntry, int width, int height, long chunkIndex) {
        return hasSegment(videoEntry.optJSONArray(String.valueOf(width)), chunkIndex)
                || hasSegment(videoEntry.optJSONArray(String.valueOf(Math.min(width, height))), chunkIndex);
    }

    private static boolean hasSegment(@Nullable JSONArray segments, long chunkIndex) {
        if (segments == null) {
            return false;
        }
        for (int i = 0; i < segments.length(); i++) {
            if (segments.optLong(i, -1) == chunkIndex) {
                return true;
            }
        }
        return false;
    }
}

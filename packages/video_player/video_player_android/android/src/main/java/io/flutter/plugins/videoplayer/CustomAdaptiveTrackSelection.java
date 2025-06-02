package io.flutter.plugins.videoplayer;

import android.net.Uri;

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

import java.util.List;
import java.util.Objects;

@UnstableApi
public class CustomAdaptiveTrackSelection extends AdaptiveTrackSelection {

    private static final String TAG = "CustomAdaptiveSelection";
    private final String assetUrl;
    private final CacheDataSourceFactory cacheDataSourceFactory;
    private final CustomLogger customLogger;

    private long currentChunkIndex = 1;

    public CustomAdaptiveTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter, String assetUrl, CacheDataSourceFactory cacheDataSourceFactory, VideoPlayerLoggerOptions loggerOptions) {
        super(group, tracks, bandwidthMeter);
        this.assetUrl = assetUrl;
        this.cacheDataSourceFactory = cacheDataSourceFactory;
        this.customLogger = new CustomLogger(TAG, loggerOptions.enableAdaptiveTrackSelectionLogs);
    }

    protected CustomAdaptiveTrackSelection(TrackGroup group, int[] tracks, int type, BandwidthMeter bandwidthMeter, long minDurationForQualityIncreaseMs, long maxDurationForQualityDecreaseMs, long minDurationToRetainAfterDiscardMs, int maxWidthToDiscard, int maxHeightToDiscard, float bandwidthFraction, float bufferedFractionToLiveEdgeForQualityIncrease, List<AdaptationCheckpoint> adaptationCheckpoints, Clock clock, String assetUrl, CacheDataSourceFactory cacheDataSourceFactory, VideoPlayerLoggerOptions loggerOptions) {
        super(group, tracks, type, bandwidthMeter, minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs, minDurationToRetainAfterDiscardMs, maxWidthToDiscard, maxHeightToDiscard, bandwidthFraction, bufferedFractionToLiveEdgeForQualityIncrease, adaptationCheckpoints, clock);
        this.assetUrl = assetUrl;
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

        customLogger.logD("----- updateSelectedTrack() -----");

        // Print timing info
        customLogger.logD("playbackPositionUs: " + playbackPositionUs);
        customLogger.logD("bufferedDurationUs: " + bufferedDurationUs);
        customLogger.logD("availableDurationUs: " + availableDurationUs);

        // Print selected format before update
        Format currentFormat = getSelectedFormat();
        customLogger.logD("Current selected format: height=" + currentFormat.height +
                ", width=" + currentFormat.width +
                ", bitrate=" + currentFormat.bitrate +
                ", id=" + currentFormat.id);

        // Log media chunk queue
        if (!queue.isEmpty()) {
            customLogger.logD("Queue size: " + queue.size());
            for (int i = 0; i < queue.size(); i++) {
                MediaChunk chunk = queue.get(i);
                customLogger.logD("Chunk[" + i + "]: startTimeUs=" + chunk.startTimeUs +
                        ", endTimeUs=" + chunk.endTimeUs +
                        ", chunkIndex=" + chunk.getNextChunkIndex() +
                        ", selectionReason=" + chunk.trackSelectionReason
                );

                // Update current chunk index.
                currentChunkIndex = chunk.getNextChunkIndex();
            }
        } else {
            currentChunkIndex = 1;
            customLogger.logD("Queue is empty or null");
        }

        // Print info about each MediaChunkIterator
        customLogger.logD("mediaChunkIterators.length = " + mediaChunkIterators.length);
        for (int i = 0; i < mediaChunkIterators.length; i++) {
            MediaChunkIterator it = mediaChunkIterators[i];
            if (it != null && it.next()) {
                customLogger.logD("Iterator[" + i + "]: chunkStartTimeUs=" + it.getChunkStartTimeUs() +
                        ", chunkEndTimeUs=" + it.getChunkEndTimeUs());
            } else {
                customLogger.logD("Iterator[" + i + "] is empty or null");
            }
        }



        // You can add custom decision logic here before calling super
        // e.g., avoid switching to low bitrate if high bitrate is cached, etc.

        // Finally, call the default behavior
        super.updateSelectedTrack(playbackPositionUs, bufferedDurationUs, availableDurationUs, queue, mediaChunkIterators);
    }

    @Override
    protected boolean canSelectFormat(Format format, int trackBitrate, long effectiveBitrate) {
        customLogger.logD("----- canSelectFormat() called -----");

        customLogger.logD("  asset.url: " + assetUrl);

        customLogger.logD("Format details:");
        customLogger.logD("  id: " + format.id);
        customLogger.logD("  sampleMimeType: " + format.sampleMimeType);
        customLogger.logD("  bitrate: " + format.bitrate);
        customLogger.logD("  width: " + format.width);
        customLogger.logD("  height: " + format.height);
        customLogger.logD("  channelCount: " + format.channelCount);
        customLogger.logD("  language: " + format.language);

        customLogger.logD("trackBitrate: " + trackBitrate);
        customLogger.logD("effectiveBitrate: " + effectiveBitrate);
        customLogger.logD("currentChunkIndex: " + currentChunkIndex);

        try {
            JSONObject metadataJson = null;
            if (cacheDataSourceFactory != null) {
                metadataJson = cacheDataSourceFactory.getVideoMetadataJson();
            }

            if (metadataJson != null) {
                // Extract videoId from assetUrl
                Uri uri = Uri.parse(assetUrl);
                String videoId = null;
                for (String segment : uri.getPathSegments()) {
                    if (segment.endsWith(".m3u8")) {
                        String filename = segment.split("\\.")[0];
                        int lastUnderscore = filename.lastIndexOf('_');
                        videoId = (lastUnderscore > 0) ? filename.substring(0, lastUnderscore) : filename;
                        break;
                    }
                }

                if (videoId != null && metadataJson.has(videoId)) {
                    JSONObject videoEntry = metadataJson.getJSONObject(videoId);

                    int currentWidth = format.width;
                    String currentKey = String.valueOf(currentWidth);

                    // 1. Check if the current format has the current segment cached.
                    if (videoEntry.has(currentKey)) {
                        JSONArray segments = videoEntry.getJSONArray(currentKey);
                        for (int i = 0; i < segments.length(); i++) {
                            if (segments.getInt(i) == currentChunkIndex) {
                                customLogger.logD("✅ Current segment " + currentChunkIndex + " is cached for width=" + currentWidth);
                                return true;
                            }
                        }
                    }

                } else {
                    customLogger.logD("⚠️ No metadata found for videoId: " + videoId);
                }
            } else {
                customLogger.logD("⚠️ Metadata JSON is null");
            }
        } catch (Exception e) {
            customLogger.logE("Error checking cache metadata: " + e.getMessage(), e);
        }

        boolean result = super.canSelectFormat(format, trackBitrate, effectiveBitrate);

        // If super.canSelectFormat returns false and this is last resolution (360p) then return true at this point.
        if(!result && Objects.equals(format.id, "2")) {
            customLogger.logD("returning true as this is last format");
            return true;
        }

        customLogger.logD("super.canSelectFormat returned: " + result);

        return result;
    }
}

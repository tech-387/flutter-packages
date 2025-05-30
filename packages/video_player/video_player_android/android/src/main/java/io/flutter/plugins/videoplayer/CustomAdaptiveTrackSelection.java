package io.flutter.plugins.videoplayer;

import android.net.Uri;
import android.util.Log;

import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@UnstableApi
public class CustomAdaptiveTrackSelection extends AdaptiveTrackSelection {

    private static final String TAG = "CustomAdaptiveSelection";
    private final String assetUrl;
    private final CacheDataSourceFactory cacheDataSourceFactory;
    private final CustomLogger customLogger;

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
                        ", chunkIndex=" + chunk.getNextChunkIndex());
            }
        } else {
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

                    // 1. Check if the current format has any cached segments.
                    if (videoEntry.has(currentKey)) {
                        JSONArray segments = videoEntry.getJSONArray(currentKey);
                        if (segments.length() > 0) {
                            customLogger.logD("✅ Cached segments found for current format width=" + currentWidth);
                            return true;
                        }
                    }

                    // 2. Check if any higher width format has cached segments.
                    List<Integer> availableWidths = new ArrayList<>();
                    Iterator<String> keys = videoEntry.keys();
                    while (keys.hasNext()) {
                        try {
                            int w = Integer.parseInt(keys.next());
                            availableWidths.add(w);
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    Collections.sort(availableWidths);
                    for (int width : availableWidths) {
                        if (width > currentWidth) {
                            JSONArray higherSegments = videoEntry.getJSONArray(String.valueOf(width));
                            if (higherSegments.length() > 0) {
                                customLogger.logD("✅ Cached segments found for HIGHER format width=" + width);
                                return true;
                            }
                        }
                    }

                    customLogger.logD("⚠️ No cached segments for current or higher formats.");
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
        customLogger.logD("super.canSelectFormat returned: " + result);
        return result;
    }

    @Override
    public boolean shouldCancelChunkLoad(long playbackPositionUs, Chunk loadingChunk, List<? extends MediaChunk> queue) {

        return super.shouldCancelChunkLoad(playbackPositionUs, loadingChunk, queue);
    }
}

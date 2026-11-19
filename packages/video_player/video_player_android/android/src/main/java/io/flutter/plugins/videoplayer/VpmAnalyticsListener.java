package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.MediaLoadData;

/** Emits the {@code fmt} and {@code bw} diagnostic lines of the {@code VPM} log tag. */
@UnstableApi
final class VpmAnalyticsListener implements AnalyticsListener {
  private static final double BANDWIDTH_CHANGE_FRACTION = 0.2;

  private final String file;
  private long lastLoggedBandwidth = -1;

  VpmAnalyticsListener(@NonNull String file) {
    this.file = file;
  }

  @Override
  public void onDownstreamFormatChanged(
      @NonNull EventTime eventTime, @NonNull MediaLoadData mediaLoadData) {
    if (!Vpm.isEnabled()) {
      return;
    }
    Format format = mediaLoadData.trackFormat;
    if (format == null
        || (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO
            && mediaLoadData.trackType != C.TRACK_TYPE_DEFAULT)) {
      return;
    }
    Vpm.log(
        "fmt file=" + file + " h=" + format.height + " w=" + format.width
            + " bps=" + format.bitrate + " reason=" + mediaLoadData.trackSelectionReason);
  }

  @Override
  public void onBandwidthEstimate(
      @NonNull EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    if (!Vpm.isEnabled()) {
      return;
    }
    if (lastLoggedBandwidth > 0
        && Math.abs(bitrateEstimate - lastLoggedBandwidth)
            <= lastLoggedBandwidth * BANDWIDTH_CHANGE_FRACTION) {
      return;
    }
    lastLoggedBandwidth = bitrateEstimate;
    Vpm.log("bw bps=" + bitrateEstimate);
  }
}

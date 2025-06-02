package io.flutter.plugins.videoplayer;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

// Use ExoPlayer's Log for consistency and @UnstableApi compatibility

import java.util.Locale; // For formatting numbers consistently

// @UnstableApi is an annotation from ExoPlayer indicating that the API might change.
// It's good practice to keep it if the class directly uses an @UnstableApi component.
@UnstableApi
public class CustomBandwidthListener implements BandwidthMeter.EventListener {

    // Define a TAG for easier log filtering
    private static final String TAG = "CustomBandwidthListener";

    public static Long lastBitrateEstimate;
    private final CustomLogger customLogger;
    private final String assetUrl;

    public CustomBandwidthListener(BandwidthMeter meter, VideoPlayerLoggerOptions loggerOptions, String assetUrl) {
        // Get the initial bitrate estimate
        long initialBitrateEstimate = meter.getBitrateEstimate();
        this.assetUrl = assetUrl;

        customLogger = new CustomLogger(TAG, loggerOptions.enableBandwidthListenerLogs);

        if (lastBitrateEstimate == null) {
            customLogger.logD("Setting lastBitrate estimate to " + initialBitrateEstimate);
            lastBitrateEstimate = initialBitrateEstimate;
        }

        // Convert it to Mbps
        double initialBitrateMbps = initialBitrateEstimate / 1_000_000.0;

        // Log the initial estimate in a readable format
        customLogger.logD(String.format(Locale.US, "Initial bandwidth estimate: %.2f Mbps" + "(" + assetUrl + ")", initialBitrateMbps));
    }

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        lastBitrateEstimate = bitrate;

        // Divide by 1,000,000 to convert bits to Megabits
        double bitrateMbps = bitrate / 1_000_000.0;
        // Convert bytes to KB or MB for better readability if needed
        double bytesMb = bytes / (1024.0 * 1024.0); // Convert bytes to Megabytes

        customLogger.logD(String.format(Locale.US,
                "Bandwidth Sample: Elapsed Time = %d ms, Bytes Transferred = %.2f MB, Bitrate = %.2f Mbps" + "("+assetUrl+")",
                elapsedMs, bytesMb, bitrateMbps));
    }
}
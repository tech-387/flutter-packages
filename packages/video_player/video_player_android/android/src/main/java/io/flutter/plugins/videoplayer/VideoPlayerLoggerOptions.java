package io.flutter.plugins.videoplayer;

public class VideoPlayerLoggerOptions {
    public Boolean enableTransferListenerLogs;
    public Boolean enableBandwidthListenerLogs;
    public Boolean enableAdaptiveTrackSelectionLogs;
    public Boolean enableCacheDataSourceLogs;

    // Constructor
    public VideoPlayerLoggerOptions(Boolean enableTransferListenerLogs, Boolean enableBandwidthListenerLogs, Boolean enableAdaptiveTrackSelectionLogs, Boolean enableCacheDataSourceLogs ) {
        this.enableTransferListenerLogs = enableTransferListenerLogs;
        this.enableBandwidthListenerLogs = enableBandwidthListenerLogs;
        this.enableAdaptiveTrackSelectionLogs = enableAdaptiveTrackSelectionLogs;
        this.enableCacheDataSourceLogs = enableCacheDataSourceLogs;
    }

    VideoPlayerLoggerOptions() {}
}

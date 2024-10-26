package io.flutter.plugins.videoplayer;

public class VideoPlayerBufferOptions {
    public Long minBufferMs = 15000L;
    public Long maxBufferMs = 30000L;
    public Long bufferForPlaybackMs = 2000L;
    public Long bufferForPlaybackAfterRebufferMs = 2000L;

    // Constructor
    public VideoPlayerBufferOptions(Long minBufferMs, Long maxBufferMs, Long bufferForPlaybackMs, Long bufferForPlaybackAfterRebufferMs ) {
        this.minBufferMs = minBufferMs;
        this.maxBufferMs = maxBufferMs;
        this.bufferForPlaybackMs = bufferForPlaybackMs;
        this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
    }

    VideoPlayerBufferOptions() {}
}

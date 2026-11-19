package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

/**
 * A {@link LoadControl} that buffers less for a paused player than for a playing one.
 *
 * <p>Wraps a delegate (a {@code DefaultLoadControl} built with the <em>playing</em> buffer limits)
 * and stops loading as soon as a player that is not {@code playWhenReady} (e.g. a preloaded video
 * waiting off-screen) has buffered at least {@code preloadBufferUs}. Once the player is asked to
 * play, loading is delegated and the larger playing limits apply, with a target that grows with
 * the position played (see {@link #PLAYING_START_BUFFER_US}).
 *
 * <p>A paused player also stops loading while the playing one is short of buffer (see {@link
 * NetworkPriority}).
 *
 * <p>Only the non-deprecated {@link LoadControl} methods are forwarded; the deprecated overloads
 * are never invoked by ExoPlayer and their defaults only call the forwarded ones.
 */
@UnstableApi
final class RoleAwareLoadControl implements LoadControl {
  private final LoadControl delegate;
  private final long preloadBufferUs;

  RoleAwareLoadControl(@NonNull LoadControl delegate, long preloadBufferUs) {
    this.delegate = delegate;
    this.preloadBufferUs = preloadBufferUs;
  }

  /**
   * Buffer a player aims for when it starts playing. The target grows by the position played, up
   * to the delegate's playing maximum: most feed videos are flicked past within seconds, and
   * every second buffered beyond what is watched is downloaded for nothing.
   */
  static final long PLAYING_START_BUFFER_US = 4_000_000;

  @Override
  public boolean shouldContinueLoading(Parameters parameters) {
    if (!parameters.playWhenReady) {
      if (parameters.bufferedDurationUs >= preloadBufferUs) {
        return false;
      }
      // The video on screen is short of buffer: leave it the bandwidth.
      if (NetworkPriority.isForegroundStarved()) {
        return false;
      }
    } else if (parameters.bufferedDurationUs
        >= playingBufferTargetUs(parameters.playbackPositionUs, preloadBufferUs)) {
      return false;
    }
    return delegate.shouldContinueLoading(parameters);
  }

  /** The playing buffer target at {@code positionUs}; never below the paused cap. */
  @VisibleForTesting
  static long playingBufferTargetUs(long positionUs, long preloadBufferUs) {
    return Math.max(preloadBufferUs, PLAYING_START_BUFFER_US + Math.max(0, positionUs));
  }

  @Override
  public void onPrepared(PlayerId playerId) {
    delegate.onPrepared(playerId);
  }

  @Override
  public void onTracksSelected(
      Parameters parameters, TrackGroupArray trackGroups, ExoTrackSelection[] trackSelections) {
    delegate.onTracksSelected(parameters, trackGroups, trackSelections);
  }

  @Override
  public void onStopped(PlayerId playerId) {
    delegate.onStopped(playerId);
  }

  @Override
  public void onReleased(PlayerId playerId) {
    delegate.onReleased(playerId);
  }

  @Override
  public Allocator getAllocator(PlayerId playerId) {
    return delegate.getAllocator(playerId);
  }

  @Override
  public long getBackBufferDurationUs(PlayerId playerId) {
    return delegate.getBackBufferDurationUs(playerId);
  }

  @Override
  public boolean retainBackBufferFromKeyframe(PlayerId playerId) {
    return delegate.retainBackBufferFromKeyframe(playerId);
  }

  @Override
  public boolean shouldContinuePreloading(
      PlayerId playerId, Timeline timeline, MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
    return delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs);
  }

  /**
   * Buffer needed to resume after a stall. Resuming at the configured 0.5 s on a network slower
   * than the video's bitrate stalls again half a second later, over and over; waiting for a little
   * more makes fewer, longer pauses. Playback still resumes at once when the rest of the video is
   * buffered (ExoPlayer does not ask then).
   */
  static final long REBUFFER_RESUME_US = 2_000_000;

  @Override
  public boolean shouldStartPlayback(Parameters parameters) {
    if (parameters.rebuffering && parameters.bufferedDurationUs < REBUFFER_RESUME_US) {
      return false;
    }
    return delegate.shouldStartPlayback(parameters);
  }
}

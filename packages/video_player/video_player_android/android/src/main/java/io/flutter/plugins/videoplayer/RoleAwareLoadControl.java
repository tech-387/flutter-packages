package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
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
 * play, loading is delegated and the larger playing limits apply.
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

  @Override
  public boolean shouldContinueLoading(Parameters parameters) {
    if (!parameters.playWhenReady && parameters.bufferedDurationUs >= preloadBufferUs) {
      return false;
    }
    return delegate.shouldContinueLoading(parameters);
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

  @Override
  public boolean shouldStartPlayback(Parameters parameters) {
    return delegate.shouldStartPlayback(parameters);
  }
}

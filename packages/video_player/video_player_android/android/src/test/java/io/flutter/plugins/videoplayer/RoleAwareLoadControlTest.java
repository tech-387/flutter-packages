package io.flutter.plugins.videoplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class RoleAwareLoadControlTest {
  private static final long PRELOAD_BUFFER_US = 3_000_000L;

  private LoadControl delegate;
  private RoleAwareLoadControl loadControl;

  @Before
  public void setUp() {
    delegate = mock(LoadControl.class);
    loadControl = new RoleAwareLoadControl(delegate, PRELOAD_BUFFER_US);
  }

  @After
  public void tearDown() {
    NetworkPriority.setForegroundStarvedForTest(false);
  }

  private static LoadControl.Parameters params(boolean playWhenReady, long bufferedUs) {
    return params(playWhenReady, bufferedUs, /* positionUs= */ 0);
  }

  private static LoadControl.Parameters params(
      boolean playWhenReady, long bufferedUs, long positionUs) {
    return params(playWhenReady, bufferedUs, positionUs, /* rebuffering= */ false);
  }

  private static LoadControl.Parameters params(
      boolean playWhenReady, long bufferedUs, long positionUs, boolean rebuffering) {
    return new LoadControl.Parameters(
        PlayerId.UNSET,
        Timeline.EMPTY,
        new MediaPeriodId(new Object()),
        positionUs,
        bufferedUs,
        /* playbackSpeed= */ 1f,
        playWhenReady,
        rebuffering,
        /* targetLiveOffsetUs= */ 0,
        /* lastRebufferRealtimeMs= */ 0);
  }

  @Test
  public void pausedAtOrAboveCapStopsLoadingWithoutAskingDelegate() {
    LoadControl.Parameters above = params(false, PRELOAD_BUFFER_US + 1);
    LoadControl.Parameters atCap = params(false, PRELOAD_BUFFER_US);
    when(delegate.shouldContinueLoading(above)).thenReturn(true);
    when(delegate.shouldContinueLoading(atCap)).thenReturn(true);

    assertFalse(loadControl.shouldContinueLoading(above));
    assertFalse(loadControl.shouldContinueLoading(atCap));
    verify(delegate, never()).shouldContinueLoading(above);
  }

  @Test
  public void pausedBelowCapDelegates() {
    LoadControl.Parameters below = params(false, PRELOAD_BUFFER_US - 1);
    when(delegate.shouldContinueLoading(below)).thenReturn(true);
    assertTrue(loadControl.shouldContinueLoading(below));

    when(delegate.shouldContinueLoading(below)).thenReturn(false);
    assertFalse(loadControl.shouldContinueLoading(below));
  }

  @Test
  public void pausedBelowCapStopsWhileTheForegroundIsStarved() {
    LoadControl.Parameters below = params(false, 0);
    when(delegate.shouldContinueLoading(below)).thenReturn(true);
    NetworkPriority.setForegroundStarvedForTest(true);
    assertFalse(loadControl.shouldContinueLoading(below));

    NetworkPriority.setForegroundStarvedForTest(false);
    assertTrue(loadControl.shouldContinueLoading(below));
  }

  @Test
  public void playingIgnoresAStarvedForeground() {
    LoadControl.Parameters playing = params(true, 0);
    when(delegate.shouldContinueLoading(playing)).thenReturn(true);
    NetworkPriority.setForegroundStarvedForTest(true);
    assertTrue(loadControl.shouldContinueLoading(playing));
  }

  @Test
  public void playingDelegatesBelowTheTargetAndStopsAtIt() {
    long start = RoleAwareLoadControl.PLAYING_START_BUFFER_US;
    LoadControl.Parameters below = params(true, start - 1);
    LoadControl.Parameters atTarget = params(true, start);
    when(delegate.shouldContinueLoading(below)).thenReturn(true);
    when(delegate.shouldContinueLoading(atTarget)).thenReturn(true);

    assertTrue(loadControl.shouldContinueLoading(below));
    assertFalse(loadControl.shouldContinueLoading(atTarget));
    verify(delegate, never()).shouldContinueLoading(atTarget);
  }

  @Test
  public void playingTargetGrowsWithThePositionPlayed() {
    long start = RoleAwareLoadControl.PLAYING_START_BUFFER_US;
    LoadControl.Parameters later = params(true, start + 5_000_000L, /* positionUs= */ 6_000_000L);
    when(delegate.shouldContinueLoading(later)).thenReturn(true);
    assertTrue(loadControl.shouldContinueLoading(later));

    assertEquals(start, RoleAwareLoadControl.playingBufferTargetUs(0, PRELOAD_BUFFER_US));
    assertEquals(
        start + 6_000_000L,
        RoleAwareLoadControl.playingBufferTargetUs(6_000_000L, PRELOAD_BUFFER_US));
    // Never below the paused cap.
    assertEquals(
        start * 2, RoleAwareLoadControl.playingBufferTargetUs(0, start * 2));
  }

  @Test
  public void afterAStallWaitsForMoreBufferBeforeResuming() {
    long resume = RoleAwareLoadControl.REBUFFER_RESUME_US;
    LoadControl.Parameters short_ = params(true, resume - 1, 0, /* rebuffering= */ true);
    LoadControl.Parameters enough = params(true, resume, 0, /* rebuffering= */ true);
    LoadControl.Parameters firstStart = params(true, 500_000L, 0, /* rebuffering= */ false);
    when(delegate.shouldStartPlayback(short_)).thenReturn(true);
    when(delegate.shouldStartPlayback(enough)).thenReturn(true);
    when(delegate.shouldStartPlayback(firstStart)).thenReturn(true);

    assertFalse(loadControl.shouldStartPlayback(short_));
    assertTrue(loadControl.shouldStartPlayback(enough));
    assertTrue(loadControl.shouldStartPlayback(firstStart));
  }

  @Test
  public void otherMethodsAreForwarded() {
    PlayerId playerId = PlayerId.UNSET;
    Allocator allocator = mock(Allocator.class);
    when(delegate.getAllocator(playerId)).thenReturn(allocator);
    when(delegate.getBackBufferDurationUs(playerId)).thenReturn(123L);
    when(delegate.retainBackBufferFromKeyframe(playerId)).thenReturn(true);
    LoadControl.Parameters p = params(true, 0);
    when(delegate.shouldStartPlayback(p)).thenReturn(true);
    MediaPeriodId periodId = new MediaPeriodId(new Object());
    when(delegate.shouldContinuePreloading(playerId, Timeline.EMPTY, periodId, 5L)).thenReturn(true);

    assertEquals(allocator, loadControl.getAllocator(playerId));
    assertEquals(123L, loadControl.getBackBufferDurationUs(playerId));
    assertTrue(loadControl.retainBackBufferFromKeyframe(playerId));
    assertTrue(loadControl.shouldStartPlayback(p));
    assertTrue(loadControl.shouldContinuePreloading(playerId, Timeline.EMPTY, periodId, 5L));

    TrackGroupArray groups = TrackGroupArray.EMPTY;
    ExoTrackSelection[] selections = new ExoTrackSelection[0];
    loadControl.onPrepared(playerId);
    loadControl.onTracksSelected(p, groups, selections);
    loadControl.onStopped(playerId);
    loadControl.onReleased(playerId);
    verify(delegate).onPrepared(playerId);
    verify(delegate).onTracksSelected(p, groups, selections);
    verify(delegate).onStopped(playerId);
    verify(delegate).onReleased(playerId);
  }
}

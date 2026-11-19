package io.flutter.plugins.videoplayer;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** Tests {@link VideoPlayer#resolveLoadControlMs}. */
public final class LoadControlBufferPlanTest {
  private static VideoPlayerBufferOptions options(
      long min, long max, long forPlayback, long afterRebuffer, Long playingMin, Long playingMax) {
    return new VideoPlayerBufferOptions(
        min, max, forPlayback, afterRebuffer, 3000L, 3000L, 3000L, 1279, 719, 0.85, 0.75,
        playingMin, playingMax);
  }

  @Test
  public void withoutPlayingValuesBehaviourIsUnchangedAndNoCap() {
    assertArrayEquals(
        new int[] {2500, 3000, 0},
        VideoPlayer.resolveLoadControlMs(options(2500, 3000, 500, 500, null, null)));
  }

  @Test
  public void playingValuesBecomeLimitsAndMaxBufferIsTheCap() {
    assertArrayEquals(
        new int[] {8000, 15000, 3000},
        VideoPlayer.resolveLoadControlMs(options(2500, 3000, 500, 500, 8000L, 15000L)));
  }

  @Test
  public void onlyOnePlayingValueFallsBackToConfiguredOther() {
    assertArrayEquals(
        new int[] {2500, 12000, 3000},
        VideoPlayer.resolveLoadControlMs(options(2500, 3000, 500, 500, null, 12000L)));
  }

  @Test
  public void clampsInsteadOfViolatingDefaultLoadControlAssertions() {
    // playing min below the playback start threshold, playing max below playing min.
    assertArrayEquals(
        new int[] {2000, 2000, 3000},
        VideoPlayer.resolveLoadControlMs(options(2500, 3000, 2000, 1000, 100L, 50L)));
    // cap is never below the playback start threshold.
    assertArrayEquals(
        new int[] {5000, 9000, 4000},
        VideoPlayer.resolveLoadControlMs(options(1000, 1000, 4000, 4000, 5000L, 9000L)));
  }
}

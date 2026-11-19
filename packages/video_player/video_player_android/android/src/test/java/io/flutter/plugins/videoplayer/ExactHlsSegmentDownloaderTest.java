package io.flutter.plugins.videoplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ExactHlsSegmentDownloaderTest {
  // Production ladder, highest first: 720p ~4 Mbps, 540p ~2.5 Mbps.
  private static final int[] LADDER = {4_000_000, 2_500_000};

  @Test
  public void picksHighestVariantThatFitsEstimate() {
    // 8 Mbps * 0.85 = 6.8 Mbps budget -> both fit, highest wins.
    assertEquals(0, ExactHlsSegmentDownloader.chooseVariantIndex(LADDER, 8_000_000));
  }

  @Test
  public void picksLowerVariantWhenHigherDoesNotFit() {
    // 3.5 Mbps * 0.85 = 2.975 Mbps -> only the 2.5 Mbps variant fits.
    assertEquals(1, ExactHlsSegmentDownloader.chooseVariantIndex(LADDER, 3_500_000));
  }

  @Test
  public void appliesBandwidthFractionAtTheBoundary() {
    int[] bitrates = {1_000_000};
    // 1_000_000 / 0.85 = 1_176_470.6: just below does not fit, at/above fits (single variant
    // is the fallback anyway, so use two variants to observe the choice).
    int[] two = {1_000_000, 500_000};
    assertEquals(1, ExactHlsSegmentDownloader.chooseVariantIndex(two, 1_176_470));
    assertEquals(0, ExactHlsSegmentDownloader.chooseVariantIndex(two, 1_176_471));
    assertEquals(0, ExactHlsSegmentDownloader.chooseVariantIndex(bitrates, 1));
  }

  @Test
  public void fallsBackToLowestBitrateWhenNothingFits() {
    assertEquals(1, ExactHlsSegmentDownloader.chooseVariantIndex(LADDER, 1_000_000));
    assertEquals(1, ExactHlsSegmentDownloader.chooseVariantIndex(LADDER, 0));
  }

  @Test
  public void worksWithAscendingOrder() {
    int[] ascending = {2_500_000, 4_000_000};
    assertEquals(1, ExactHlsSegmentDownloader.chooseVariantIndex(ascending, 8_000_000));
    assertEquals(0, ExactHlsSegmentDownloader.chooseVariantIndex(ascending, 1_000_000));
  }

  @Test
  public void ignoresUnknownBitratesUnlessAllUnknown() {
    int[] withUnknown = {-1, 2_500_000};
    assertEquals(1, ExactHlsSegmentDownloader.chooseVariantIndex(withUnknown, 100));
    assertEquals(0, ExactHlsSegmentDownloader.chooseVariantIndex(new int[] {-1, -1}, 5_000_000));
  }

  @Test
  public void emptyReturnsMinusOne() {
    assertEquals(-1, ExactHlsSegmentDownloader.chooseVariantIndex(new int[0], 5_000_000));
  }
}

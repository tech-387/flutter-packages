package io.flutter.plugins.videoplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class CustomAdaptiveTrackSelectionTest {
  private static JSONObject entry() throws Exception {
    return new JSONObject()
        .put("720", new JSONArray().put(1).put(2))
        .put("540", new JSONArray().put(3));
  }

  @Test
  public void matchesByWidthFirst() throws Exception {
    assertTrue(CustomAdaptiveTrackSelection.isSegmentCached(entry(), 720, 1280, 2));
    assertTrue(CustomAdaptiveTrackSelection.isSegmentCached(entry(), 540, 960, 3));
  }

  @Test
  public void fallsBackToShortSideForLandscapeFormats() throws Exception {
    // 1280x720: width key "1280" is absent, short side "720" is present.
    assertTrue(CustomAdaptiveTrackSelection.isSegmentCached(entry(), 1280, 720, 1));
    assertFalse(CustomAdaptiveTrackSelection.isSegmentCached(entry(), 1280, 720, 3));
  }

  @Test
  public void notCachedWhenSegmentOrVariantMissing() throws Exception {
    assertFalse(CustomAdaptiveTrackSelection.isSegmentCached(entry(), 720, 1280, 3));
    assertFalse(CustomAdaptiveTrackSelection.isSegmentCached(entry(), 360, 640, 1));
  }
}

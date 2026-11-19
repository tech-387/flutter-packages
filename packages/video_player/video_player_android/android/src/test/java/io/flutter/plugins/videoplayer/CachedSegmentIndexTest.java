package io.flutter.plugins.videoplayer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class CachedSegmentIndexTest {
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private static final String ID = "3f2a9c1e-1b2d-4c5e-9a7b-0123456789ab";

  @Test
  public void parsesSegmentFileName() {
    assertArrayEquals(
        new String[] {ID, "720", "00003"},
        CachedSegmentIndex.parseSegmentFileName(ID + "_720_00003.ts"));
    assertNull(CachedSegmentIndex.parseSegmentFileName(ID + "_720.m3u8"));
    assertNull(CachedSegmentIndex.parseSegmentFileName("key.bin"));
  }

  @Test
  public void extractsVideoIdFromAssetUrl() {
    assertEquals(
        ID, CachedSegmentIndex.videoIdFromAssetUrl("https://cdn.example.com/a/" + ID + "_720.m3u8"));
    assertEquals(
        ID, CachedSegmentIndex.videoIdFromAssetUrl("https://cdn.example.com/a/" + ID + ".m3u8"));
    assertNull(CachedSegmentIndex.videoIdFromAssetUrl("https://cdn.example.com/a/video.mp4"));
  }

  private File touch(File dir, String name, long ageMs, long nowMs) throws IOException {
    File f = new File(dir, name);
    assertTrue(f.createNewFile());
    assertTrue(f.setLastModified(nowMs - ageMs));
    return f;
  }

  @Test
  public void cleanupDeletesFilesOlderThanMaxAge() throws IOException {
    File dir = tmp.newFolder("metadata");
    long now = 10_000_000_000L;
    long day = 24L * 60 * 60 * 1000;
    File old = touch(dir, "old.json", 8 * day, now);
    File fresh = touch(dir, "fresh.json", 1 * day, now);

    int deleted = CachedSegmentIndex.cleanup(dir, now, 7 * day, 2000);

    assertEquals(1, deleted);
    assertFalse(old.exists());
    assertTrue(fresh.exists());
  }

  @Test
  public void cleanupTrimsOldestBeyondMaxFiles() throws IOException {
    File dir = tmp.newFolder("metadata");
    long now = 10_000_000_000L;
    long hour = 60L * 60 * 1000;
    File a = touch(dir, "a.json", 5 * hour, now);
    File b = touch(dir, "b.json", 4 * hour, now);
    File c = touch(dir, "c.json", 3 * hour, now);
    File d = touch(dir, "d.json", 2 * hour, now);

    int deleted = CachedSegmentIndex.cleanup(dir, now, 100 * hour, 2);

    assertEquals(2, deleted);
    assertFalse(a.exists());
    assertFalse(b.exists());
    assertTrue(c.exists());
    assertTrue(d.exists());
  }

  @Test
  public void cleanupOnMissingDirectoryIsNoOp() {
    assertEquals(0, CachedSegmentIndex.cleanup(new File(tmp.getRoot(), "nope"), 0, 1, 1));
  }
}

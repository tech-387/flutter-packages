package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Per-video record of which HLS segments are in the cache, persisted as
 * {@code <cacheDir>/metadata/<videoId>.json} = {@code {videoId: {"<label>": [segmentIndexes]}}}.
 *
 * <p>Written by playback ({@link CustomTransferListener}) and by cache-priming preloads
 * ({@link ExactHlsSegmentDownloader}); read by {@link CustomAdaptiveTrackSelection} so ABR
 * prefers a variant whose next segment is already cached. All writes go through one shared
 * single-thread executor, so concurrent writers can't interleave read-modify-write cycles.
 */
final class CachedSegmentIndex {
  private static final String TAG = "CachedSegmentIndex";
  static final String METADATA_DIR = "metadata";
  static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
  static final int MAX_FILES = 2000;

  private static final Pattern SEGMENT_PATTERN =
      Pattern.compile("([a-f0-9\\-]+)_([0-9]+)_([0-9]+)\\.ts");

  private static final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "CachedSegmentIndex");
            thread.setDaemon(true);
            return thread;
          });

  private CachedSegmentIndex() {}

  /** Records {@code segmentFileName} (e.g. {@code <uuid>_720_00003.ts}) asynchronously. */
  static void record(@NonNull Context context, @NonNull String segmentFileName) {
    record(context, segmentFileName, null, null);
  }

  /**
   * Records {@code segmentFileName} asynchronously. Non-segment file names are ignored. When the
   * segment was not yet known, {@code callback} receives the updated metadata (on the executor
   * thread) after it has been written.
   */
  static void record(
      @NonNull Context context,
      @NonNull String segmentFileName,
      @Nullable JSONObjectCallback callback,
      @Nullable CustomLogger logger) {
    if (!segmentFileName.endsWith(".ts")) {
      // Manifests/playlists (.m3u8) and keys also reach callers but aren't segments.
      return;
    }
    executor.execute(
        () -> {
          try {
            recordNow(context, segmentFileName, callback, logger);
          } catch (Exception e) {
            if (logger != null) {
              logger.logE("Error recording segment " + segmentFileName, e);
            }
          }
        });
  }

  /** Splits a segment file name into {videoId, variantLabel, segmentNumber}, or null. */
  @VisibleForTesting
  @Nullable
  static String[] parseSegmentFileName(@NonNull String fileName) {
    Matcher matcher = SEGMENT_PATTERN.matcher(fileName);
    if (!matcher.matches()) {
      return null;
    }
    return new String[] {matcher.group(1), matcher.group(2), matcher.group(3)};
  }

  private static void recordNow(
      Context context,
      String segmentFileName,
      @Nullable JSONObjectCallback callback,
      @Nullable CustomLogger logger)
      throws Exception {
    String[] parts = parseSegmentFileName(segmentFileName);
    if (parts == null) {
      if (logger != null && logger.isEnabled()) {
        logger.logW("Filename did not match expected pattern: " + segmentFileName);
      }
      return;
    }
    String videoId = parts[0];
    String variant = parts[1];
    int segmentIndex = Integer.parseInt(parts[2]);

    File dir = new File(context.getCacheDir(), METADATA_DIR);
    if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
      return;
    }
    File file = new File(dir, videoId + ".json");

    JSONObject metadata = file.exists() ? new JSONObject(readFile(file)) : new JSONObject();
    JSONObject videoObject =
        metadata.has(videoId) ? metadata.getJSONObject(videoId) : new JSONObject();
    JSONArray segments =
        videoObject.has(variant) ? videoObject.getJSONArray(variant) : new JSONArray();

    if (jsonArrayContains(segments, segmentIndex)) {
      return;
    }
    segments.put(segmentIndex);
    videoObject.put(variant, segments);
    metadata.put(videoId, videoObject);

    // Write to a temp file and rename, so a concurrent reader never sees a partial file.
    File tmp = new File(dir, videoId + ".json.tmp");
    try (FileOutputStream out = new FileOutputStream(tmp)) {
      out.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    if (!tmp.renameTo(file)) {
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      return;
    }
    if (callback != null) {
      callback.onResult(metadata);
    }
  }

  /**
   * Deletes stale metadata files in the background: files older than {@link #MAX_AGE_MS}, then
   * the oldest ones beyond {@link #MAX_FILES}. Runs on the shared writer executor so it can't
   * race with a write.
   */
  static void scheduleCleanup(@NonNull Context context) {
    final File dir = new File(context.getCacheDir(), METADATA_DIR);
    executor.execute(
        () -> {
          try {
            cleanup(dir, System.currentTimeMillis(), MAX_AGE_MS, MAX_FILES);
          } catch (Exception ignored) {
            // Best-effort housekeeping.
          }
        });
  }

  /** Returns the number of files deleted. */
  @VisibleForTesting
  static int cleanup(File dir, long nowMs, long maxAgeMs, int maxFiles) {
    File[] files = dir.listFiles();
    if (files == null) {
      return 0;
    }
    int deleted = 0;
    int remaining = 0;
    File[] kept = new File[files.length];
    for (File file : files) {
      if (nowMs - file.lastModified() > maxAgeMs) {
        if (file.delete()) {
          deleted++;
          continue;
        }
      }
      kept[remaining++] = file;
    }
    if (remaining > maxFiles) {
      File[] survivors = Arrays.copyOf(kept, remaining);
      Arrays.sort(survivors, Comparator.comparingLong(File::lastModified));
      for (int i = 0; i < remaining - maxFiles; i++) {
        if (survivors[i].delete()) {
          deleted++;
        }
      }
    }
    return deleted;
  }

  /**
   * Extracts the video id from an HLS asset URL: the part of the {@code .m3u8} path segment before
   * the last underscore (e.g. {@code <id>_720.m3u8} or {@code <id>.m3u8} -> {@code <id>}).
   */
  @Nullable
  static String videoIdFromAssetUrl(@Nullable String assetUrl) {
    if (assetUrl == null) {
      return null;
    }
    Uri uri = Uri.parse(assetUrl);
    for (String segment : uri.getPathSegments()) {
      if (segment.endsWith(".m3u8")) {
        String filename = segment.split("\\.")[0];
        int lastUnderscore = filename.lastIndexOf('_');
        return lastUnderscore > 0 ? filename.substring(0, lastUnderscore) : filename;
      }
    }
    return null;
  }

  @Nullable
  static JSONObject readMetadata(Context context, String videoId) throws Exception {
    File file = new File(new File(context.getCacheDir(), METADATA_DIR), videoId + ".json");
    if (!file.exists()) {
      return null;
    }
    return new JSONObject(readFile(file));
  }

  private static boolean jsonArrayContains(JSONArray array, int value) {
    for (int i = 0; i < array.length(); i++) {
      if (array.optInt(i) == value) {
        return true;
      }
    }
    return false;
  }

  private static String readFile(File file) throws IOException {
    try (FileInputStream in = new FileInputStream(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }
}

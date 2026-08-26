// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

public abstract class ExoPlayerEventListener implements Player.Listener {
  private static final String TAG = "DurationInit";
  static final long DURATION_UNSET_INITIALIZATION_TIMEOUT_MS = 2000;
  // Upper bound on total time spent waiting for a valid duration before giving up and
  // initializing anyway, so a video that never reports one doesn't block forever.
  static final long DURATION_UNSET_MAX_WAIT_MS = 8000;
  private boolean isInitialized = false;
  private boolean isWaitingForValidDuration = false;
  private long waitStartTimeMs = 0;
  // Set when ExoPlayer can't determine a duration but the media item is a local content:// or
  // file:// URI, so MediaMetadataRetriever was able to read one directly from the file's
  // metadata instead. See getEffectiveDurationMs().
  @Nullable private Long resolvedLocalDurationMs;
  private boolean triedLocalDurationFallback = false;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Runnable initializationFallback = this::onInitializationFallback;
  protected final ExoPlayer exoPlayer;
  protected final VideoPlayerCallbacks events;

  private void onInitializationFallback() {
    if (isInitialized || !isWaitingForValidDuration) {
      return;
    }
    long elapsed = SystemClock.elapsedRealtime() - waitStartTimeMs;
    if (hasValidDuration()) {
      Log.i(TAG, "fallback fired but duration is now valid (elapsedMs=" + elapsed
          + ", durationMs=" + exoPlayer.getDuration() + "); sending initialized normally");
      maybeSendInitialized();
      return;
    }
    if (elapsed < DURATION_UNSET_MAX_WAIT_MS) {
      Log.i(TAG, "fallback fired, duration still unset after elapsedMs=" + elapsed
          + "; retrying for up to " + DURATION_UNSET_MAX_WAIT_MS + "ms total");
      mainHandler.postDelayed(initializationFallback, DURATION_UNSET_INITIALIZATION_TIMEOUT_MS);
      return;
    }
    Log.w(TAG, "giving up waiting for valid duration after elapsedMs=" + elapsed
        + "; initializing with duration=" + exoPlayer.getDuration());
    isWaitingForValidDuration = false;
    isInitialized = true;
    sendInitialized();
  }

  protected enum RotationDegrees {
    ROTATE_0(0),
    ROTATE_90(90),
    ROTATE_180(180),
    ROTATE_270(270);

    private final int degrees;

    RotationDegrees(int degrees) {
      this.degrees = degrees;
    }

    public static RotationDegrees fromDegrees(int degrees) {
      for (RotationDegrees rotationDegrees : RotationDegrees.values()) {
        if (rotationDegrees.degrees == degrees) {
          return rotationDegrees;
        }
      }
      throw new IllegalArgumentException("Invalid rotation degrees specified: " + degrees);
    }

    public int getDegrees() {
      return this.degrees;
    }
  }

  public ExoPlayerEventListener(
      @NonNull ExoPlayer exoPlayer, @NonNull VideoPlayerCallbacks events) {
    this.exoPlayer = exoPlayer;
    this.events = events;
  }

  protected abstract void sendInitialized();

  /** Cancels pending initialization callbacks when the player is disposed. */
  public void dispose() {
    isWaitingForValidDuration = false;
    mainHandler.removeCallbacks(initializationFallback);
  }

  private boolean hasValidDuration() {
    return exoPlayer.getDuration() != C.TIME_UNSET;
  }

  private boolean shouldWaitForValidDuration() {
    return !exoPlayer.isCurrentMediaItemLive() && !exoPlayer.isCurrentMediaItemDynamic();
  }

  /**
   * Returns the duration subclasses should report in {@code sendInitialized()}: ExoPlayer's own
   * value when it has one, otherwise the value read via {@link MediaMetadataRetriever} for local
   * files (see {@link #resolvedLocalDurationMs}), otherwise ExoPlayer's (possibly unset) value.
   */
  protected long getEffectiveDurationMs() {
    long duration = exoPlayer.getDuration();
    if (duration != C.TIME_UNSET) {
      return duration;
    }
    return resolvedLocalDurationMs != null ? resolvedLocalDurationMs : duration;
  }

  /**
   * For a local {@code content://}/{@code file://} media item, reads the duration directly from
   * the file's metadata via {@link MediaMetadataRetriever}. Unlike ExoPlayer's extractor-based
   * duration, this doesn't depend on the container reporting a duration ExoPlayer can parse, so
   * it covers local files where {@link #hasValidDuration()} never becomes true (e.g. some
   * camera-recorded videos picked from the gallery). Returns null for remote URIs, or if the
   * retriever couldn't determine a duration either.
   */
  @Nullable
  private Long tryGetLocalFileDurationMs() {
    MediaItem mediaItem = exoPlayer.getCurrentMediaItem();
    if (mediaItem == null || mediaItem.localConfiguration == null) {
      return null;
    }
    Uri uri = mediaItem.localConfiguration.uri;
    String scheme = uri.getScheme();
    if (!"content".equals(scheme) && !"file".equals(scheme)) {
      return null;
    }
    Context context = ApplicationContextHolder.get();
    if (context == null) {
      return null;
    }
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(context, uri);
      String durationStr =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
      return durationMs > 0 ? durationMs : null;
    } catch (Exception e) {
      Log.w(TAG, "MediaMetadataRetriever duration fallback failed for " + uri, e);
      return null;
    } finally {
      try {
        retriever.release();
      } catch (Exception ignored) {
        // release() failures aren't actionable here.
      }
    }
  }

  private void maybeSendInitialized() {
    if (isInitialized) {
      return;
    }

    if (!hasValidDuration() && shouldWaitForValidDuration()) {
      if (!triedLocalDurationFallback) {
        triedLocalDurationFallback = true;
        resolvedLocalDurationMs = tryGetLocalFileDurationMs();
        if (resolvedLocalDurationMs != null) {
          Log.i(TAG, "ExoPlayer duration unset but MediaMetadataRetriever read durationMs="
              + resolvedLocalDurationMs + " from local file; initializing immediately");
          isWaitingForValidDuration = false;
          isInitialized = true;
          mainHandler.removeCallbacks(initializationFallback);
          sendInitialized();
          return;
        }
      }
      if (!isWaitingForValidDuration) {
        isWaitingForValidDuration = true;
        waitStartTimeMs = SystemClock.elapsedRealtime();
        Log.i(TAG, "duration unset (durationMs=" + exoPlayer.getDuration()
            + ", playbackState=" + exoPlayer.getPlaybackState() + "); waiting up to "
            + DURATION_UNSET_MAX_WAIT_MS + "ms for a valid one");
        mainHandler.postDelayed(initializationFallback, DURATION_UNSET_INITIALIZATION_TIMEOUT_MS);
      }
      return;
    }

    if (isWaitingForValidDuration) {
      Log.i(TAG, "valid duration arrived after elapsedMs="
          + (SystemClock.elapsedRealtime() - waitStartTimeMs)
          + " (durationMs=" + exoPlayer.getDuration() + ")");
    }
    isWaitingForValidDuration = false;
    isInitialized = true;
    mainHandler.removeCallbacks(initializationFallback);
    sendInitialized();
  }

  @Override
  public void onPlaybackStateChanged(final int playbackState) {
    PlatformPlaybackState platformState = PlatformPlaybackState.UNKNOWN;
    switch (playbackState) {
      case Player.STATE_BUFFERING:
        platformState = PlatformPlaybackState.BUFFERING;
        break;
      case Player.STATE_READY:
        platformState = PlatformPlaybackState.READY;
        maybeSendInitialized();
        break;
      case Player.STATE_ENDED:
        platformState = PlatformPlaybackState.ENDED;
        break;
      case Player.STATE_IDLE:
        platformState = PlatformPlaybackState.IDLE;
        break;
    }
    events.onPlaybackStateChanged(platformState);
  }

  @Override
  public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
    if (isWaitingForValidDuration) {
      Log.i(TAG, "onTimelineChanged reason=" + reason
          + " playbackState=" + exoPlayer.getPlaybackState()
          + " timelineEmpty=" + timeline.isEmpty()
          + " windowCount=" + timeline.getWindowCount()
          + " exoPlayerDurationMs=" + exoPlayer.getDuration());
      if (!timeline.isEmpty()) {
        Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
        Log.i(TAG, "window[0] durationUs=" + window.durationUs
            + " isPlaceholder=" + window.isPlaceholder
            + " isDynamic=" + window.isDynamic
            + " isLive=" + window.isLive());
      }
    }
    if (isWaitingForValidDuration && exoPlayer.getPlaybackState() == Player.STATE_READY) {
      maybeSendInitialized();
    }
  }

  @Override
  public void onPlayerError(@NonNull final PlaybackException error) {
    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
      // See
      // https://exoplayer.dev/live-streaming.html#behindlivewindowexception-and-error_code_behind_live_window
      exoPlayer.seekToDefaultPosition();
      exoPlayer.prepare();
    } else {
      events.onError("VideoError", "Video player had error " + error, null);
    }
  }

  @Override
  public void onIsPlayingChanged(boolean isPlaying) {
    events.onIsPlayingStateUpdate(isPlaying);
  }

  @Override
  public void onTracksChanged(@NonNull Tracks tracks) {
    // Find the currently selected audio track and notify
    String selectedAudioTrackId = findSelectedAudioTrackId(tracks);
    events.onAudioTrackChanged(selectedAudioTrackId);

    // Find the currently selected video track and notify
    String selectedVideoTrackId = findSelectedVideoTrackId(tracks);
    events.onVideoTrackChanged(selectedVideoTrackId);
  }

  /**
   * Finds the ID of the currently selected audio track.
   *
   * @param tracks The current tracks
   * @return The track ID in format "groupIndex_trackIndex", or null if no audio track is selected
   */
  @Nullable
  private String findSelectedAudioTrackId(@NonNull Tracks tracks) {
    // Keep this ID format in sync with android_video_player.dart::_parseAndroidTrackId.
    int groupIndex = 0;
    for (Tracks.Group group : tracks.getGroups()) {
      if (group.getType() == C.TRACK_TYPE_AUDIO && group.isSelected()) {
        // Find the selected track within this group
        for (int i = 0; i < group.length; i++) {
          if (group.isTrackSelected(i)) {
            return groupIndex + "_" + i;
          }
        }
      }
      groupIndex++;
    }
    return null;
  }

  /**
   * Finds the ID of the currently selected video track.
   *
   * @param tracks The current tracks
   * @return The track ID in format "groupIndex_trackIndex", or null if no video track is selected
   */
  @Nullable
  private String findSelectedVideoTrackId(@NonNull Tracks tracks) {
    // Keep this ID format in sync with android_video_player.dart::_parseAndroidTrackId.
    int groupIndex = 0;
    for (Tracks.Group group : tracks.getGroups()) {
      if (group.getType() == C.TRACK_TYPE_VIDEO && group.isSelected()) {
        // Find the selected track within this group
        for (int i = 0; i < group.length; i++) {
          if (group.isTrackSelected(i)) {
            return groupIndex + "_" + i;
          }
        }
      }
      groupIndex++;
    }
    return null;
  }
}

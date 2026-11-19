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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

public abstract class ExoPlayerEventListener implements Player.Listener {
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
  private boolean firstFrameSent = false;
  private final long createTimeMs = SystemClock.elapsedRealtime();
  // VPM diagnostics: stall tracking (BUFFERING after the first READY while playing).
  private boolean hasBeenReady = false;
  private long stallStartMs = -1;
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
      maybeSendInitialized();
      return;
    }
    if (elapsed < DURATION_UNSET_MAX_WAIT_MS) {
      mainHandler.postDelayed(initializationFallback, DURATION_UNSET_INITIALIZATION_TIMEOUT_MS);
      return;
    }
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
    } catch (Exception ignored) {
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
        mainHandler.postDelayed(initializationFallback, DURATION_UNSET_INITIALIZATION_TIMEOUT_MS);
      }
      return;
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
        if (hasBeenReady && stallStartMs < 0 && exoPlayer.getPlayWhenReady() && Vpm.isEnabled()) {
          stallStartMs = SystemClock.elapsedRealtime();
          Vpm.log("stall start");
        }
        break;
      case Player.STATE_READY:
        platformState = PlatformPlaybackState.READY;
        hasBeenReady = true;
        if (stallStartMs >= 0) {
          if (Vpm.isEnabled()) {
            Vpm.log("stall end ms=" + (SystemClock.elapsedRealtime() - stallStartMs));
          }
          stallStartMs = -1;
        }
        maybeSendInitialized();
        break;
      case Player.STATE_ENDED:
        platformState = PlatformPlaybackState.ENDED;
        stallStartMs = -1;
        break;
      case Player.STATE_IDLE:
        platformState = PlatformPlaybackState.IDLE;
        stallStartMs = -1;
        break;
    }
    events.onPlaybackStateChanged(platformState);
  }

  @Override
  public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
    if (isWaitingForValidDuration && exoPlayer.getPlaybackState() == Player.STATE_READY) {
      maybeSendInitialized();
    }
  }

  @Override
  public void onRenderedFirstFrame() {
    if (firstFrameSent) {
      return;
    }
    firstFrameSent = true;
    final long sinceCreateMs = SystemClock.elapsedRealtime() - createTimeMs;
    if (Vpm.isEnabled()) {
      Vpm.log("firstFrame id=" + currentFileName() + " sinceCreateMs=" + sinceCreateMs);
    }
    events.onFirstFrameRendered(sinceCreateMs);
  }

  @Override
  public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
    if (videoSize.width <= 0 || videoSize.height <= 0) {
      return;
    }
    // The format being rendered, so the bitrate matches the HLS variant on screen.
    Format format = exoPlayer.getVideoFormat();
    int bitrate = format == null ? 0 : Math.max(format.bitrate, 0);
    if (Vpm.isEnabled()) {
      Vpm.log(
          "videoSize id="
              + currentFileName()
              + " "
              + videoSize.width
              + "x"
              + videoSize.height
              + " bitrate="
              + bitrate);
    }
    events.onVideoSizeChanged(videoSize.width, videoSize.height, bitrate);
  }

  /** The last path segment of the current media item's URI, for diagnostic logs. */
  @NonNull
  String currentFileName() {
    MediaItem mediaItem = exoPlayer.getCurrentMediaItem();
    if (mediaItem == null || mediaItem.localConfiguration == null) {
      return "?";
    }
    String name = mediaItem.localConfiguration.uri.getLastPathSegment();
    return name != null ? name : "?";
  }

  @Override
  public void onPlayerError(@NonNull final PlaybackException error) {
    if (Vpm.isEnabled()) {
      String message = String.valueOf(error.getMessage()).replace('\n', ' ');
      Vpm.log("error code=" + error.errorCode + " msg=" + message);
    }
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

// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Callbacks representing events invoked by {@link VideoPlayer}.
 *
 * <p>In the actual plugin, this will always be {@link VideoPlayerEventCallbacks}, which creates the
 * expected events to send back through the plugin channel. In tests methods can be overridden in
 * order to assert results.
 *
 * <p>See {@link androidx.media3.common.Player.Listener} for details.
 */
public interface VideoPlayerCallbacks {
  void onInitialized(int width, int height, long durationInMs, int rotationCorrectionInDegrees);

  void onPlaybackStateChanged(@NonNull PlatformPlaybackState state);

  void onError(@NonNull String code, @Nullable String message, @Nullable Object details);

  void onIsPlayingStateUpdate(boolean isPlaying);

  void onAudioTrackChanged(@Nullable String selectedTrackId);

  void onVideoTrackChanged(@Nullable String selectedTrackId);

  /** The first video frame was rendered to the output; sent at most once per player. */
  void onFirstFrameRendered(long sinceCreateMs);

  /**
   * The size of the rendered video changed, e.g. adaptive streaming switched HLS variant.
   * [bitrate] is the declared bitrate of the rendered format in bits per second, or 0.
   */
  void onVideoSizeChanged(int width, int height, int bitrate);
}

// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Gives the video on screen first claim on the network, players second and data-only preloads
 * what is left.
 *
 * <ul>
 *   <li>While a playing player is loading and has less than {@link #STARVED_BUFFER_MS} buffered,
 *       {@link #isForegroundStarved()} is true: paused players stop loading ({@link
 *       RoleAwareLoadControl}).
 *   <li>Data-only preloads ({@link ExactHlsSegmentDownloader}) pause between reads while {@link
 *       #shouldPreloadsWait()}: a playing player is starved, or a player is still initializing
 *       (buffering before it was first ready). The video the user lands on is initializing too,
 *       and on a slow network two preloads otherwise take most of the bandwidth its first segment
 *       needs.
 * </ul>
 *
 * <p>On a slow network the neighbours otherwise take a share of the bandwidth the current video
 * needs, and it stalls or never starts.
 *
 * <p>Players are sampled on the main thread, where ExoPlayer may be read, every {@link
 * #SAMPLE_INTERVAL_MS} while any is registered. A load control alone cannot tell: it is not asked
 * while a segment is downloading, so its view would go stale exactly when it matters.
 */
final class NetworkPriority {
  /** A playing player with less than this buffered gets the network to itself. */
  static final long STARVED_BUFFER_MS = 3000;

  static final long SAMPLE_INTERVAL_MS = 150;

  /** Longest a preload read waits in one go, so a stuck player cannot block preloads forever. */
  static final long MAX_PRELOAD_WAIT_MS = 20000;

  private static final Set<ExoPlayer> players =
      Collections.newSetFromMap(new IdentityHashMap<>());
  // Created on first use, so the class loads without a main looper (JVM unit tests).
  @Nullable private static Handler handler;
  private static final Runnable sample = NetworkPriority::sample;
  private static boolean isSampling;
  private static volatile boolean starved;
  private static volatile boolean initializing;

  private NetworkPriority() {}

  @MainThread
  static void register(@NonNull ExoPlayer player) {
    players.add(player);
    if (!isSampling) {
      isSampling = true;
      if (handler == null) {
        handler = new Handler(Looper.getMainLooper());
      }
      handler.post(sample);
    }
  }

  @MainThread
  static void unregister(@NonNull ExoPlayer player) {
    players.remove(player);
    if (players.isEmpty()) {
      starved = false;
      initializing = false;
    }
  }

  /** Whether the playing video needs all the bandwidth right now. Any thread. */
  static boolean isForegroundStarved() {
    return starved;
  }

  /** Whether data-only preloads should leave the network to the players. Any thread. */
  static boolean shouldPreloadsWait() {
    return starved || initializing;
  }

  /**
   * Blocks while {@link #shouldPreloadsWait()}, for at most {@link #MAX_PRELOAD_WAIT_MS}, or until
   * {@code canceled} says so. Returns the time waited.
   */
  static long awaitPlayers(@NonNull Canceled canceled) throws InterruptedException {
    if (!shouldPreloadsWait()) {
      return 0;
    }
    final long start = System.nanoTime();
    while (shouldPreloadsWait() && !canceled.isCanceled()) {
      if (elapsedMsSince(start) >= MAX_PRELOAD_WAIT_MS) {
        break;
      }
      Thread.sleep(SAMPLE_INTERVAL_MS);
    }
    return elapsedMsSince(start);
  }

  private static long elapsedMsSince(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  @VisibleForTesting
  static void setForegroundStarvedForTest(boolean value) {
    starved = value;
  }

  @VisibleForTesting
  static void setInitializingForTest(boolean value) {
    initializing = value;
  }

  interface Canceled {
    boolean isCanceled();
  }

  @MainThread
  private static void sample() {
    boolean anyStarved = false;
    boolean anyInitializing = false;
    for (ExoPlayer player : players) {
      final int state = player.getPlaybackState();
      if (!anyStarved
          && isStarved(
              player.getPlayWhenReady(), player.isLoading(), state, player.getTotalBufferedDuration())) {
        anyStarved = true;
      }
      if (!anyInitializing && isInitializing(state, player.isLoading(), player.getDuration())) {
        anyInitializing = true;
      }
    }
    starved = anyStarved;
    initializing = anyInitializing;
    if (players.isEmpty()) {
      isSampling = false;
      return;
    }
    if (handler != null) {
      handler.postDelayed(sample, SAMPLE_INTERVAL_MS);
    }
  }

  /**
   * Whether a player is loading toward its first ready state. A paused player that was ready stays
   * {@code STATE_READY}, and its duration is known once it was prepared, so buffering with an
   * unknown duration means it is still initializing.
   */
  @VisibleForTesting
  static boolean isInitializing(int playbackState, boolean isLoading, long durationMs) {
    return playbackState == Player.STATE_BUFFERING
        && isLoading
        && durationMs == androidx.media3.common.C.TIME_UNSET;
  }

  @VisibleForTesting
  static boolean isStarved(
      boolean playWhenReady, boolean isLoading, int playbackState, long bufferedMs) {
    return playWhenReady
        && isLoading
        && playbackState != Player.STATE_ENDED
        && playbackState != Player.STATE_IDLE
        && bufferedMs < STARVED_BUFFER_MS;
  }
}

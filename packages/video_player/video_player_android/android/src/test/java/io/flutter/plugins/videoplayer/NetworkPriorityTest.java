// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;
import org.junit.After;
import org.junit.Test;

public final class NetworkPriorityTest {
  private static final long LOW = NetworkPriority.STARVED_BUFFER_MS - 1;
  private static final long ENOUGH = NetworkPriority.STARVED_BUFFER_MS;

  @After
  public void tearDown() {
    NetworkPriority.setForegroundStarvedForTest(false);
    NetworkPriority.setInitializingForTest(false);
  }

  @Test
  public void aPlayingLoadingPlayerShortOfBufferIsStarved() {
    assertTrue(NetworkPriority.isStarved(true, true, Player.STATE_READY, LOW));
    assertTrue(NetworkPriority.isStarved(true, true, Player.STATE_BUFFERING, 0));
  }

  @Test
  public void enoughBufferPausedNotLoadingOrFinishedIsNotStarved() {
    assertFalse(NetworkPriority.isStarved(true, true, Player.STATE_READY, ENOUGH));
    assertFalse(NetworkPriority.isStarved(false, true, Player.STATE_BUFFERING, 0));
    assertFalse(NetworkPriority.isStarved(true, false, Player.STATE_READY, 0));
    assertFalse(NetworkPriority.isStarved(true, true, Player.STATE_ENDED, 0));
    assertFalse(NetworkPriority.isStarved(true, true, Player.STATE_IDLE, 0));
  }

  @Test
  public void awaitReturnsAtOnceWhenNotStarved() throws InterruptedException {
    assertEquals(0, NetworkPriority.awaitPlayers(() -> false));
  }

  @Test
  public void awaitStopsWhenCanceled() throws InterruptedException {
    NetworkPriority.setForegroundStarvedForTest(true);
    long waited = NetworkPriority.awaitPlayers(() -> true);
    assertTrue(waited < NetworkPriority.MAX_PRELOAD_WAIT_MS);
  }

  @Test
  public void aPlayerBufferingWithoutADurationIsInitializing() {
    long unset = androidx.media3.common.C.TIME_UNSET;
    assertTrue(NetworkPriority.isInitializing(Player.STATE_BUFFERING, true, unset));
    assertFalse(NetworkPriority.isInitializing(Player.STATE_BUFFERING, true, 10_000));
    assertFalse(NetworkPriority.isInitializing(Player.STATE_BUFFERING, false, unset));
    assertFalse(NetworkPriority.isInitializing(Player.STATE_READY, true, unset));
  }

  @Test
  public void preloadsWaitForAStarvedOrInitializingPlayer() {
    assertFalse(NetworkPriority.shouldPreloadsWait());
    NetworkPriority.setInitializingForTest(true);
    assertTrue(NetworkPriority.shouldPreloadsWait());
    assertFalse(NetworkPriority.isForegroundStarved());
    NetworkPriority.setInitializingForTest(false);
    NetworkPriority.setForegroundStarvedForTest(true);
    assertTrue(NetworkPriority.shouldPreloadsWait());
  }

}

// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import androidx.annotation.Nullable;

/**
 * Holds the Flutter engine's application context so code that doesn't otherwise have a
 * {@link Context} on hand (e.g. {@link ExoPlayerEventListener}, which is constructed without
 * one) can reach it. Safe to hold statically since it is always an application context, set by
 * {@link VideoPlayerPlugin#onAttachedToEngine}.
 */
final class ApplicationContextHolder {
  @Nullable private static Context applicationContext;

  private ApplicationContextHolder() {}

  static void set(@Nullable Context context) {
    applicationContext = context;
  }

  @Nullable
  static Context get() {
    return applicationContext;
  }
}

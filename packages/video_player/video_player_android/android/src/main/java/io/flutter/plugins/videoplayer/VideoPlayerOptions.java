// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

public class VideoPlayerOptions {
  public boolean mixWithOthers;

  public boolean enableCache = false;
  public String cacheDirectory = "streaming";
  public Long maxCacheBytes = 1024 * 1024 * 1024L;
  public Long maxFileBytes = 1024 * 1024 * 100L;
}

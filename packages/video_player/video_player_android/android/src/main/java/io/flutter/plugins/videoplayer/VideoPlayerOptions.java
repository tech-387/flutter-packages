// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

public class VideoPlayerOptions {
  public boolean mixWithOthers;

  public boolean enableCache = true;
  public String cacheDirectory = "streaming";
  public Long maxCacheBytes = 1024 * 1024 * 1024L;
  public Long maxFileBytes = 1024 * 1024 * 100L;
}

// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer

/**
 * Java-compatible helper for invoking the `(Result<T>) -> Unit` callbacks that pigeon generates
 * for `@async` Kotlin host API methods. `kotlin.Result` is an inline class with no direct Java
 * equivalent, so async pigeon methods implemented in Java (like [VideoPlayerPlugin]) go through
 * this instead of constructing a `Result` directly.
 */
@Suppress("UNCHECKED_CAST")
class ResultCompat {
  companion object {
    @JvmStatic
    fun <T> success(value: T, callback: Any) {
      val castedCallback: (Result<T>) -> Unit = callback as (Result<T>) -> Unit
      castedCallback(Result.success(value))
    }

    @JvmStatic
    fun <T> failure(exception: Throwable, callback: Any) {
      val castedCallback: (Result<T>) -> Unit = callback as (Result<T>) -> Unit
      castedCallback(Result.failure(exception))
    }
  }
}

package io.flutter.plugins.videoplayer;

import android.util.Log;

/**
 * Diagnostic logging under the {@code VPM} tag. Disabled unless enabled on the device with
 * {@code adb shell setprop log.tag.VPM DEBUG}. Callers must guard string building with
 * {@link #isEnabled()} so nothing is built when disabled.
 */
final class Vpm {
  static final String TAG = "VPM";

  private Vpm() {}

  static boolean isEnabled() {
    return Log.isLoggable(TAG, Log.DEBUG);
  }

  /** Logs {@code message}. Only call when {@link #isEnabled()} is true. */
  static void log(String message) {
    Log.d(TAG, message);
  }
}

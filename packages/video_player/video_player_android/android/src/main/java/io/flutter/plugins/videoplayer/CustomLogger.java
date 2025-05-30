package io.flutter.plugins.videoplayer;

import android.util.Log;

public class CustomLogger {

    private final String tag;
    private final Boolean enableLogs;

    public CustomLogger(String tag, Boolean enableLogs) {
        this.tag = tag;
        this.enableLogs = enableLogs;
    }

    public void logD(String message) {
        if(!enableLogs) {
            return;
        }
        Log.d(tag, message);
    }

    public void logW(String message) {
        if(!enableLogs) {
            return;
        }
        Log.w(tag, message);
    }

    public void logE(String message, Throwable e) {
        if(!enableLogs) {
            return;
        }
        Log.e(tag, message, e);
    }

    public void logV(String message) {
        if(!enableLogs) {
            return;
        }
        Log.v(tag, message);
    }

    public void logV(String message, Throwable e) {
        if(!enableLogs) {
            return;
        }
        Log.v(tag, message, e);
    }
}

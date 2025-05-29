package io.flutter.plugins.videoplayer;

import android.util.Log;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;

@UnstableApi
abstract class CustomLoadControl extends DefaultLoadControl {


    @Override
    public boolean shouldContinueLoading(Parameters parameters) {
        boolean result = super.shouldContinueLoading(parameters);

        Log.d("CustomLoadControl", "playbackPositionUs="+ parameters.playbackPositionUs + ", bufferedDurationUs=" + parameters.bufferedDurationUs + ",rebuffering=" + parameters.rebuffering + ",shouldContinueLoading="+result);

        return result;
    }

    @Override
    public boolean shouldStartPlayback(Parameters parameters) {
        boolean result = super.shouldStartPlayback(parameters);

        Log.d("CustomLoadControl", "shouldStartPlayback="+result);


        return result;
    }
}

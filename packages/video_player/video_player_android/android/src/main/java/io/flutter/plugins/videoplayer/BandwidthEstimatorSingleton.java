package io.flutter.plugins.videoplayer;

import android.content.Context;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.experimental.ExperimentalBandwidthMeter;

@UnstableApi
public class BandwidthEstimatorSingleton {
    DefaultBandwidthMeter.Builder bandwidthMeter;

    private static volatile BandwidthEstimatorSingleton instance;

    private BandwidthEstimatorSingleton(Context context) {
        bandwidthMeter = new DefaultBandwidthMeter.Builder(context);
    }

    public synchronized static BandwidthEstimatorSingleton getInstance(Context context) {
        if (instance == null) {
            synchronized (BandwidthEstimatorSingleton.class) {
                if (instance == null)
                    instance = new BandwidthEstimatorSingleton(context);
            }
        }
        return instance;
    }
}
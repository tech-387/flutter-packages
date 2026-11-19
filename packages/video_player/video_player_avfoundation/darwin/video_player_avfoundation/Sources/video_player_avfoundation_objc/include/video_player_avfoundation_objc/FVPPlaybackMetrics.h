#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Network bytes per video, for the app's developer tools. Read-only: nothing here changes
/// playback.
///
/// Downloads made by the cache proxy (playback and prefetch) are grouped by video: the id at the
/// start of the file name (the backend names files `<id>.m3u8`, `<id>_720.m3u8`,
/// `<id>_720_3.ts`), or else the URL's directory. Must match `PlaybackMetrics.java` on Android.
/// Counters are cumulative for the process; the app takes differences.
NS_SWIFT_NAME(PlaybackMetrics)
@interface FVPPlaybackMetrics : NSObject

@property(class, nonatomic, readonly) FVPPlaybackMetrics *sharedInstance NS_SWIFT_NAME(shared);

/// Version of the metrics channel API, the same on Android.
@property(class, nonatomic, readonly) NSInteger version;

+ (NSString *)keyForURL:(NSURL *)url;
+ (NSString *)variantForURL:(NSURL *)url;

- (void)recordTransferForURL:(NSURL *)url bytes:(int64_t)bytes durationMs:(int64_t)durationMs;

/// `key`, `netBytes`, `netRequests`, `netMs` and `netVariants` (bytes by variant).
- (NSDictionary<NSString *, id> *)transferStatsForURL:(NSURL *)url NS_SWIFT_NAME(transferStats(for:));

@end

NS_ASSUME_NONNULL_END

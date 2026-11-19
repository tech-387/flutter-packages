//
//  FVPVideoPlayerCacheManager.m
//  video_player_avfoundation
//
//  Created by Imran Spahić on 27. 2. 2024..
//

#import "./include/video_player_avfoundation_objc/FVPVideoPlayerCacheManager.h"
#import "./include/video_player_avfoundation_objc/FVPVideoPlayerOptions.h"

@import Network;
@import QuartzCore;
#import <os/lock.h>

#import <SJMediaCacheServer/MCSAssetManager.h>
#import <SJMediaCacheServer/SJMediaCacheServer.h>

os_log_t FVPVideoPipelineLog(void) {
  static os_log_t log;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    log = os_log_create("video_player", "VPM");
  });
  return log;
}

// SJMediaCacheServer proxies remote HTTP(S) media (including HLS) through a local server so
// segments are cached to disk; it isn't meant to intercept local file URLs (e.g. bundled
// assets), so those are left untouched.
static BOOL FVPURLIsCacheableRemoteResource(NSURL *url) {
  NSString *scheme = url.scheme.lowercaseString;
  return [scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"];
}

/// Bumped whenever the variant selection policy changes in a way that makes previously cached
/// assets undesirable. SJMediaCacheServer pins the variant it selected for an asset when it first
/// writes the asset's proxy playlist, so assets cached under an older policy would keep their old
/// variant for as long as they stay cached. Version 1: SJ's default (always the middle variant,
/// which is the LOWER of the two variants our backend produces) was replaced by bandwidth-aware
/// selection.
static NSInteger const FVPVariantPolicyVersion = 1;
static NSString *const FVPVariantPolicyVersionKey = @"FVPVideoPlayerCacheVariantPolicyVersion";

/// Upstream responses smaller than this are dominated by latency (playlists, keys) and are not
/// used for the throughput estimate.
static int64_t const FVPMinBytesForThroughputSample = 200 * 1024;

/// Only this share of the estimated throughput is considered available for one video, to leave
/// room for estimate errors and for prefetches running in parallel.
static double const FVPBandwidthFraction = 0.8;

/// Cached assets not read for this long are evicted.
static NSTimeInterval const FVPCacheMaxDiskAge = 7 * 24 * 60 * 60;

/// The cache shrinks to keep at least this much free disk space on the device.
static NSUInteger const FVPCacheReservedFreeDiskSpace = 512 * 1024 * 1024;

/// Prefetch (cache warming) tasks allowed to run at once. SJMediaCacheServer defaults to 1, which
/// cannot keep a data window of 6-8 videos warm ahead of a user swiping every few seconds.
static NSInteger const FVPMaxConcurrentPrefetchCount = 2;

@interface FVPVideoPlayerCacheManager ()
@property(nonatomic, strong) FVPVideoPlayerOptions *videoPlayerOptions;
/// Outstanding data-only cache-warming prefetches, keyed by the original asset URL string, so
/// `cancelPreloadForURL:` can cancel a specific in-flight prefetch. These never back a
/// VideoPlayerController/decoder; see `preloadURL:segmentCount:httpHeaders:completion:`.
@property(nonatomic, strong) NSMutableDictionary<NSString *, id<MCSPrefetchTask>> *prefetchTasksByURL;
@end

@implementation FVPVideoPlayerCacheManager {
  os_unfair_lock _networkLock;
  /// Exponentially weighted moving average of upstream throughput, in bits per second. 0 while
  /// unknown. Guarded by _networkLock.
  double _estimatedBitsPerSecond;
  /// Guarded by _networkLock.
  FVPNetworkKind _networkKind;
  nw_path_monitor_t _pathMonitor;
}

- (instancetype)init {
  self = [super init];
  if (self) {
    _networkLock = OS_UNFAIR_LOCK_INIT;
    _networkKind = FVPNetworkKindUnknown;
    // Leave enableAirPlaySupport at its library default (YES, i.e. proxy served from the
    // device's real LAN IP rather than localhost). HLSAssetParser's hls_restoreOriginalUrl:
    // (used to resolve variant/nested playlist URLs back out of an already-proxied HLS
    // playlist) only recognizes the literal string "localhost" or the actual resolved LAN IP
    // as proxy host prefixes -- it has no case for the literal "127.0.0.1" that
    // SJMediaCacheServer.m falls back to when AirPlay support is disabled. Forcing
    // localhost-only here breaks HLS (m3u8) caching, even though simple FILE-asset caching
    // still works.
    _videoPlayerOptions = [[FVPVideoPlayerOptions alloc] init];
    _prefetchTasksByURL = [NSMutableDictionary dictionary];

    SJMediaCacheServer *server = SJMediaCacheServer.shared;
    server.maxConcurrentPrefetchCount = FVPMaxConcurrentPrefetchCount;
    server.cacheMaxDiskAge = FVPCacheMaxDiskAge;
    server.cacheReservedFreeDiskSpace = FVPCacheReservedFreeDiskSpace;

    [self startNetworkMonitoring];
    [self installVariantSelection];
    [self purgeCacheFromOlderVariantPolicyIfNeeded];
  }
  return self;
}

- (void)dealloc {
  if (_pathMonitor != nil) {
    nw_path_monitor_cancel(_pathMonitor);
  }
}

#pragma mark - Variant selection

/// SJMediaCacheServer proxies exactly one variant of a multivariant playlist, so AVPlayer cannot
/// switch quality during playback. Without a handler SJ always picks `streams[count / 2]`; with
/// the two variants our backend produces (720 first, 540 second) that is always the lower one.
/// Pick the best variant the network can sustain instead.
- (void)installVariantSelection {
  __weak typeof(self) weakSelf = self;
  MCSAssetManager.shared.variantStreamSelectionHandler =
      ^id<HLSVariantStream> _Nullable(NSArray<id<HLSVariantStream>> *variantStreams,
                                      NSURL *originalURL, NSURL *currentURL) {
        typeof(self) strongSelf = weakSelf;
        if (strongSelf == nil || variantStreams.count == 0) {
          return nil;
        }
        NSMutableArray<NSNumber *> *peak = [NSMutableArray arrayWithCapacity:variantStreams.count];
        NSMutableArray<NSNumber *> *average =
            [NSMutableArray arrayWithCapacity:variantStreams.count];
        for (id<HLSVariantStream> stream in variantStreams) {
          [peak addObject:@(stream.bandwidth)];
          [average addObject:@(stream.averageBandwidth)];
        }
        double estimate = 0;
        FVPNetworkKind kind = FVPNetworkKindUnknown;
        [strongSelf readEstimate:&estimate kind:&kind];
        NSInteger index =
            [FVPVideoPlayerCacheManager indexOfPreferredVariantWithBandwidths:peak
                                                            averageBandwidths:average
                                                       estimatedBitsPerSecond:estimate
                                                                  networkKind:kind];
        id<HLSVariantStream> selected = variantStreams[(NSUInteger)index];
        if (os_log_debug_enabled(FVPVideoPipelineLog())) {
          NSMutableArray<NSString *> *ladder =
              [NSMutableArray arrayWithCapacity:variantStreams.count];
          for (id<HLSVariantStream> stream in variantStreams) {
            [ladder addObject:[NSString stringWithFormat:@"%@@%lu", stream.resolution ?: @"?",
                                                         (unsigned long)stream.bandwidth]];
          }
          os_log_debug(FVPVideoPipelineLog(),
                       "variant res=%{public}@ bw=%lu of=%lu est=%.0f kind=%ld file=%{public}@ "
                       "ladder=%{public}@",
                       selected.resolution ?: @"?", (unsigned long)selected.bandwidth,
                       (unsigned long)variantStreams.count, estimate, (long)kind,
                       originalURL.lastPathComponent, [ladder componentsJoinedByString:@","]);
        }
        return selected;
      };

  SJMediaCacheServer.shared.metricsHandler =
      ^(NSURLSession *session, NSURLSessionTask *task, NSURLSessionTaskMetrics *metrics) {
        int64_t bytes = task.countOfBytesReceived;
        NSTimeInterval seconds = metrics.taskInterval.duration;
        os_log_debug(FVPVideoPipelineLog(), "upstream file=%{public}@ bytes=%lld ms=%.0f",
                     task.originalRequest.URL.lastPathComponent, bytes, seconds * 1000);
        if (task.error != nil || bytes < FVPMinBytesForThroughputSample || seconds <= 0.02) {
          return;
        }
        [weakSelf addThroughputSampleBitsPerSecond:(double)bytes * 8.0 / seconds];
      };
}

+ (NSInteger)indexOfPreferredVariantWithBandwidths:(NSArray<NSNumber *> *)bandwidths
                                 averageBandwidths:(NSArray<NSNumber *> *)averageBandwidths
                            estimatedBitsPerSecond:(double)estimatedBitsPerSecond
                                       networkKind:(FVPNetworkKind)networkKind {
  NSUInteger count = bandwidths.count;
  if (count == 0) {
    return 0;
  }
  NSUInteger highest = 0;
  NSUInteger lowest = 0;
  for (NSUInteger i = 1; i < count; i++) {
    if (bandwidths[i].unsignedIntegerValue > bandwidths[highest].unsignedIntegerValue) {
      highest = i;
    }
    if (bandwidths[i].unsignedIntegerValue < bandwidths[lowest].unsignedIntegerValue) {
      lowest = i;
    }
  }
  if (networkKind == FVPNetworkKindConstrained) {
    // Low Data Mode: the user asked to save data.
    return (NSInteger)lowest;
  }
  if (estimatedBitsPerSecond <= 0) {
    // Nothing measured yet. Our variants need about 2-4 Mbps, which Wi-Fi and LTE/5G sustain
    // almost always. A wrong guess costs a stall; a needlessly low guess costs the quality of
    // the whole video, because the choice is permanent for a cached asset.
    return (NSInteger)highest;
  }
  double budget = estimatedBitsPerSecond * FVPBandwidthFraction;
  NSInteger best = -1;
  for (NSUInteger i = 0; i < count; i++) {
    double peak = bandwidths[i].doubleValue;
    double average = i < averageBandwidths.count ? averageBandwidths[i].doubleValue : 0;
    // AVERAGE-BANDWIDTH is what a stream needs once a few seconds are buffered; the peak
    // BANDWIDTH would rule out the higher variant on most real networks.
    double needed = average > 0 ? average : peak;
    if (needed <= budget && (best < 0 || peak > bandwidths[(NSUInteger)best].doubleValue)) {
      best = (NSInteger)i;
    }
  }
  return best >= 0 ? best : (NSInteger)lowest;
}

- (void)addThroughputSampleBitsPerSecond:(double)sample {
  os_unfair_lock_lock(&_networkLock);
  _estimatedBitsPerSecond =
      _estimatedBitsPerSecond <= 0 ? sample : 0.7 * _estimatedBitsPerSecond + 0.3 * sample;
  double estimate = _estimatedBitsPerSecond;
  os_unfair_lock_unlock(&_networkLock);
  os_log_debug(FVPVideoPipelineLog(), "bw sample=%.0f est=%.0f", sample, estimate);
}

- (void)readEstimate:(double *)estimate kind:(FVPNetworkKind *)kind {
  os_unfair_lock_lock(&_networkLock);
  *estimate = _estimatedBitsPerSecond;
  *kind = _networkKind;
  os_unfair_lock_unlock(&_networkLock);
}

- (void)startNetworkMonitoring {
  _pathMonitor = nw_path_monitor_create();
  nw_path_monitor_set_queue(_pathMonitor, dispatch_get_global_queue(QOS_CLASS_UTILITY, 0));
  __weak typeof(self) weakSelf = self;
  nw_path_monitor_set_update_handler(_pathMonitor, ^(nw_path_t path) {
    typeof(self) strongSelf = weakSelf;
    if (strongSelf == nil) {
      return;
    }
    FVPNetworkKind kind;
    if (nw_path_get_status(path) != nw_path_status_satisfied) {
      kind = FVPNetworkKindUnknown;
    } else if (nw_path_is_constrained(path)) {
      kind = FVPNetworkKindConstrained;
    } else if (nw_path_is_expensive(path)) {
      kind = FVPNetworkKindExpensive;
    } else {
      kind = FVPNetworkKindUnconstrained;
    }
    os_unfair_lock_lock(&strongSelf->_networkLock);
    BOOL changed = strongSelf->_networkKind != kind;
    strongSelf->_networkKind = kind;
    // Throughput measured on the previous network says nothing about the new one.
    if (changed) {
      strongSelf->_estimatedBitsPerSecond = 0;
    }
    os_unfair_lock_unlock(&strongSelf->_networkLock);
    // Cellular paths report updates every few seconds without a change of kind.
    if (changed) {
      os_log_debug(FVPVideoPipelineLog(), "net kind=%ld", (long)kind);
    }
  });
  nw_path_monitor_start(_pathMonitor);
}

/// Removes assets cached under an older variant policy (see FVPVariantPolicyVersion). Runs off
/// the main thread: SJMediaCacheServer creates and removes assets under the same lock, so a
/// player created meanwhile waits for the purge instead of losing its files mid-playback.
- (void)purgeCacheFromOlderVariantPolicyIfNeeded {
  NSUserDefaults *defaults = NSUserDefaults.standardUserDefaults;
  if ([defaults integerForKey:FVPVariantPolicyVersionKey] >= FVPVariantPolicyVersion) {
    return;
  }
  dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
    [SJMediaCacheServer.shared removeAllCaches];
    // Marked done only after the purge, so an app killed mid-purge retries on the next launch.
    [defaults setInteger:FVPVariantPolicyVersion forKey:FVPVariantPolicyVersionKey];
    os_log_debug(FVPVideoPipelineLog(), "purged cache for variant policy %ld",
                 (long)FVPVariantPolicyVersion);
  });
}

#pragma mark - Cache

- (void)setEnableCache:(BOOL)enableCache
         cacheDirectory:(NSString *)cacheDirectory
          maxCacheBytes:(int64_t)maxCacheBytes
           maxFileBytes:(int64_t)maxFileBytes {
  self.videoPlayerOptions.enableCache = enableCache;
  self.videoPlayerOptions.cacheDirectory = cacheDirectory;
  self.videoPlayerOptions.maxCacheBytes = maxCacheBytes;
  self.videoPlayerOptions.maxFileBytes = maxFileBytes;
  SJMediaCacheServer.shared.cacheMaxDiskSize = maxCacheBytes;
}

- (NSURL *)resolvedURLForURL:(NSURL *)url {
  NSURL *resolvedURL = url;
  BOOL cacheable = self.videoPlayerOptions.enableCache && FVPURLIsCacheableRemoteResource(url);
  if (cacheable) {
    NSURL *proxyURL = [SJMediaCacheServer.shared proxyURLFromURL:url];
    if (proxyURL) {
      resolvedURL = proxyURL;
    }
  }
  if (os_log_type_enabled(FVPVideoPipelineLog(), OS_LOG_TYPE_DEBUG)) {
    // isFullyStoredAssetForURL: reads the asset's cache state, so only ask while debugging.
    BOOL stored = cacheable && [SJMediaCacheServer.shared isFullyStoredAssetForURL:url];
    os_log_debug(FVPVideoPipelineLog(), "resolve cache=%d stored=%d file=%{public}@", cacheable,
                 stored, url.lastPathComponent);
  }
  return resolvedURL;
}

// Warms the shared SJMediaCacheServer disk cache for `uri` without creating an AVPlayerItem or
// allocating a decoder: prefetch writes into the exact same cache resolvedURLForURL: reads from
// (same original URL, same proxy/cache-key resolution), so a subsequent player is a cache hit.
- (void)preloadURL:(NSString *)uri
       segmentCount:(NSInteger)segmentCount
        httpHeaders:(NSDictionary<NSString *, NSString *> *)httpHeaders
         completion:(void (^)(NSError *_Nullable))completion {
  NSURL *assetURL = [NSURL URLWithString:uri];
  if (!self.videoPlayerOptions.enableCache || !FVPURLIsCacheableRemoteResource(assetURL)) {
    completion(nil);
    return;
  }

  for (NSString *field in httpHeaders) {
    [SJMediaCacheServer.shared setHTTPHeaderField:field
                                         withValue:httpHeaders[field]
                                       forAssetURL:assetURL
                                            ofType:MCSDataTypeHLS];
  }

  [self.prefetchTasksByURL[uri] cancel];
  __weak typeof(self) weakSelf = self;
  CFTimeInterval start = CACurrentMediaTime();
  NSString *file = assetURL.lastPathComponent;
  os_log_debug(FVPVideoPipelineLog(), "prefetch start file=%{public}@ segs=%ld", file,
               (long)segmentCount);
  // Weak: the task retains this completion block until it finishes. Read on the main queue only,
  // after the assignment below has run.
  __block __weak id<MCSPrefetchTask> weakTask = nil;
  id<MCSPrefetchTask> task = [SJMediaCacheServer.shared
      prefetchWithURL:assetURL
      prefetchFileCount:(NSUInteger)MAX(segmentCount, 1)
             progress:nil
           completion:^(NSError *_Nullable prefetchError) {
             os_log_debug(FVPVideoPipelineLog(), "prefetch end file=%{public}@ ms=%.0f err=%ld",
                          file, (CACurrentMediaTime() - start) * 1000, (long)prefetchError.code);
             // SJMediaCacheServer completes on its own queue; the task map is only touched on
             // the main thread, where the plugin's preload and cancel calls arrive.
             dispatch_async(dispatch_get_main_queue(), ^{
               typeof(self) strongSelf = weakSelf;
               // A newer prefetch of the same URL may have replaced this one.
               if (strongSelf != nil && strongSelf.prefetchTasksByURL[uri] == weakTask) {
                 [strongSelf.prefetchTasksByURL removeObjectForKey:uri];
               }
               completion(prefetchError);
             });
           }];
  weakTask = task;
  if (task) {
    self.prefetchTasksByURL[uri] = task;
  } else {
    completion(nil);
  }
}

- (void)cancelPreloadForURL:(NSString *)uri {
  [self.prefetchTasksByURL[uri] cancel];
  [self.prefetchTasksByURL removeObjectForKey:uri];
}

@end

//
//  FVPVideoPlayerCacheManager.m
//  video_player_avfoundation
//
//  Created by Imran Spahić on 27. 2. 2024..
//

#import "./include/video_player_avfoundation_objc/FVPVideoPlayerCacheManager.h"
#import "./include/video_player_avfoundation_objc/FVPVideoPlayerOptions.h"

#import <SJMediaCacheServer/SJMediaCacheServer.h>

// SJMediaCacheServer proxies remote HTTP(S) media (including HLS) through a local server so
// segments are cached to disk; it isn't meant to intercept local file URLs (e.g. bundled
// assets), so those are left untouched.
static BOOL FVPURLIsCacheableRemoteResource(NSURL *url) {
  NSString *scheme = url.scheme.lowercaseString;
  return [scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"];
}

@interface FVPVideoPlayerCacheManager ()
@property(nonatomic, strong) FVPVideoPlayerOptions *videoPlayerOptions;
/// Outstanding data-only cache-warming prefetches, keyed by the original asset URL string, so
/// `cancelPreloadForURL:` can cancel a specific in-flight prefetch. These never back a
/// VideoPlayerController/decoder; see `preloadURL:segmentCount:httpHeaders:completion:`.
@property(nonatomic, strong) NSMutableDictionary<NSString *, id<MCSPrefetchTask>> *prefetchTasksByURL;
@end

@implementation FVPVideoPlayerCacheManager

- (instancetype)init {
  self = [super init];
  if (self) {
    [SJMediaCacheServer.shared setEnabledConsoleLog:true];
    // TEMP diagnostics: verbose logging to help verify disk cache hit/miss behavior (see
    // [FVP CACHE DEBUG] logs in resolvedURLForURL:).
    SJMediaCacheServer.shared.logOptions = MCSLogOptionAll;
    SJMediaCacheServer.shared.logLevel = MCSLogLevelDebug;
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
  }
  return self;
}

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
  BOOL isFullyStoredBeforePlay = NO;
  NSURL *resolvedURL = url;
  if (self.videoPlayerOptions.enableCache && FVPURLIsCacheableRemoteResource(url)) {
    isFullyStoredBeforePlay = [SJMediaCacheServer.shared isFullyStoredAssetForURL:url];
    NSURL *proxyURL = [SJMediaCacheServer.shared proxyURLFromURL:url];
    if (proxyURL) {
      resolvedURL = proxyURL;
    }
  }
  NSLog(@"[FVP CACHE DEBUG] enableCache=%d isFullyStoredBeforePlay=%d originalURL=%@ finalURL=%@",
        self.videoPlayerOptions.enableCache, isFullyStoredBeforePlay, url, resolvedURL);
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
  id<MCSPrefetchTask> task = [SJMediaCacheServer.shared
      prefetchWithURL:assetURL
      prefetchFileCount:(NSUInteger)MAX(segmentCount, 1)
             progress:nil
           completion:^(NSError *_Nullable prefetchError) {
             [weakSelf.prefetchTasksByURL removeObjectForKey:uri];
             completion(prefetchError);
           }];
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

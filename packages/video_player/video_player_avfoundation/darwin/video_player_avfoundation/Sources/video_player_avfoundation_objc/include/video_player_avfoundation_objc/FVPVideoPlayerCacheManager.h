//
//  FVPVideoPlayerCacheManager.h
//  video_player_avfoundation
//
//  Created by Imran Spahić on 27. 2. 2024..
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Encapsulates the plugin's SJMediaCacheServer-backed HTTP(S)/HLS proxy caching and
/// controller-less cache preload. Isolating the dependency to this Objective-C target means the
/// Swift plugin entry point never needs direct visibility into SJMediaCacheServer's module.
NS_SWIFT_NAME(VideoPlayerCacheManager)
@interface FVPVideoPlayerCacheManager : NSObject

- (instancetype)init NS_DESIGNATED_INITIALIZER;

/// Applies cache configuration from the plugin's setCacheOptions pigeon call.
- (void)setEnableCache:(BOOL)enableCache
         cacheDirectory:(NSString *)cacheDirectory
          maxCacheBytes:(int64_t)maxCacheBytes
           maxFileBytes:(int64_t)maxFileBytes NS_SWIFT_NAME(setEnableCache(_:cacheDirectory:maxCacheBytes:maxFileBytes:));

/// Returns the URL that should be used to build the AVURLAsset for `url`: the SJMediaCacheServer
/// local proxy URL when caching is enabled and `url` is a cacheable http(s) resource, otherwise
/// `url` unchanged.
- (NSURL *)resolvedURLForURL:(NSURL *)url NS_SWIFT_NAME(resolvedURL(for:));

/// Warms the shared disk cache for `uri` without creating an AVPlayerItem or allocating a
/// decoder. `completion` is invoked with a non-nil error only on failure.
- (void)preloadURL:(NSString *)uri
       segmentCount:(NSInteger)segmentCount
        httpHeaders:(NSDictionary<NSString *, NSString *> *)httpHeaders
         completion:(void (^)(NSError *_Nullable))completion
    NS_SWIFT_NAME(preloadURL(_:segmentCount:httpHeaders:completion:));

- (void)cancelPreloadForURL:(NSString *)uri NS_SWIFT_NAME(cancelPreload(forURL:));

@end

NS_ASSUME_NONNULL_END

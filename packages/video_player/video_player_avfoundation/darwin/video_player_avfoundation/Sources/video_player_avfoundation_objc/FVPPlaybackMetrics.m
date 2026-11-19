#import "./include/video_player_avfoundation_objc/FVPPlaybackMetrics.h"

@interface FVPTransferStats : NSObject
@property(nonatomic) int64_t bytes;
@property(nonatomic) int64_t requests;
@property(nonatomic) int64_t durationMs;
@property(nonatomic, strong) NSMutableDictionary<NSString *, NSNumber *> *bytesByVariant;
@end

@implementation FVPTransferStats
- (instancetype)init {
  self = [super init];
  if (self) {
    _bytesByVariant = [NSMutableDictionary dictionary];
  }
  return self;
}
@end

@implementation FVPPlaybackMetrics {
  NSMutableDictionary<NSString *, FVPTransferStats *> *_transfers;
}

+ (FVPPlaybackMetrics *)sharedInstance {
  static FVPPlaybackMetrics *instance;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    instance = [[FVPPlaybackMetrics alloc] init];
  });
  return instance;
}

+ (NSInteger)version {
  return 1;
}

- (instancetype)init {
  self = [super init];
  if (self) {
    _transfers = [NSMutableDictionary dictionary];
  }
  return self;
}

+ (nullable NSString *)firstMatchOf:(NSString *)pattern in:(NSString *)string {
  NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:pattern
                                                                         options:0
                                                                           error:nil];
  NSTextCheckingResult *match = [regex firstMatchInString:string
                                                  options:0
                                                    range:NSMakeRange(0, string.length)];
  if (match == nil || match.numberOfRanges < 2) return nil;
  return [string substringWithRange:[match rangeAtIndex:1]];
}

+ (NSString *)keyForURL:(NSURL *)url {
  NSString *last = url.lastPathComponent ?: @"";
  NSString *identifier = [self firstMatchOf:@"^([0-9a-fA-F-]{16,})" in:last];
  if (identifier != nil) return identifier.lowercaseString;
  NSString *path = url.path ?: @"";
  NSRange slash = [path rangeOfString:@"/" options:NSBackwardsSearch];
  NSString *directory =
      (slash.location != NSNotFound && slash.location > 0) ? [path substringToIndex:slash.location]
                                                           : @"";
  return [NSString stringWithFormat:@"%@%@", url.host ?: @"null", directory];
}

+ (NSString *)variantForURL:(NSURL *)url {
  NSString *last = url.lastPathComponent ?: @"";
  return [self firstMatchOf:@"^[0-9a-fA-F-]{16,}_(\\d{3,4})(?:[_.]|$)" in:last] ?: @"other";
}

- (void)recordTransferForURL:(NSURL *)url bytes:(int64_t)bytes durationMs:(int64_t)durationMs {
  NSString *key = [FVPPlaybackMetrics keyForURL:url];
  NSString *variant = [FVPPlaybackMetrics variantForURL:url];
  @synchronized(self) {
    FVPTransferStats *stats = _transfers[key];
    if (stats == nil) {
      stats = [[FVPTransferStats alloc] init];
      _transfers[key] = stats;
    }
    stats.bytes += bytes;
    stats.requests += 1;
    stats.durationMs += durationMs;
    stats.bytesByVariant[variant] = @(stats.bytesByVariant[variant].longLongValue + bytes);
  }
}

- (NSDictionary<NSString *, id> *)transferStatsForURL:(NSURL *)url {
  NSString *key = [FVPPlaybackMetrics keyForURL:url];
  @synchronized(self) {
    FVPTransferStats *stats = _transfers[key];
    return @{
      @"key" : key,
      @"netBytes" : @(stats.bytes),
      @"netRequests" : @(stats.requests),
      @"netMs" : @(stats.durationMs),
      @"netVariants" : stats == nil ? @{} : [stats.bytesByVariant copy],
    };
  }
}

@end

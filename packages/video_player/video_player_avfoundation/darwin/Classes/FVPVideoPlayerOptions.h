//
//  FVPVideoPlayerOptions.h
//  video_player_avfoundation
//
//  Created by Imran Spahić on 27. 2. 2024..
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface FVPVideoPlayerOptions : NSObject

@property(nonatomic, assign) NSString* cacheDirectory;
@property(nonatomic, assign) NSInteger maxCacheBytes;
@property(nonatomic, assign) NSInteger maxFileBytes;
@property(nonatomic, assign) BOOL enableCache;

@end

NS_ASSUME_NONNULL_END

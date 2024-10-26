//
//  FVPVideoPlayerBufferOptions.h
//  video_player_avfoundation
//
//  Created by Imran Spahić on 27. 2. 2024..
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface FVPVideoPlayerBufferOptions : NSObject

@property(nonatomic, assign) NSInteger  preferredForwardBufferDuration;
@property(nonatomic, assign) BOOL  canUseNetworkResourcesForLiveStreamingWhilePaused;
@property(nonatomic, assign) BOOL  automaticallyWaitsToMinimizeStalling;

- (instancetype) initWithPreferredForwardBufferDuration:(NSInteger)preferredForwardBufferDuration
      canUseNetworkResourcesForLiveStreamingWhilePaused:(BOOL)canUseNetworkResourcesForLiveStreamingWhilePaused
                   automaticallyWaitsToMinimizeStalling:(BOOL)automaticallyWaitsToMinimizeStalling;

@end

NS_ASSUME_NONNULL_END


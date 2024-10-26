//
//  FVPVideoPlayerBufferOptions.m
//  video_player_avfoundation
//
//  Created by Imran Spahić on 27. 2. 2024..
//

#import "FVPVideoPlayerBufferOptions.h"

@implementation FVPVideoPlayerBufferOptions

- (instancetype)initWithPreferredForwardBufferDuration:(NSInteger)preferredForwardBufferDuration canUseNetworkResourcesForLiveStreamingWhilePaused:(BOOL)canUseNetworkResourcesForLiveStreamingWhilePaused automaticallyWaitsToMinimizeStalling:(BOOL)automaticallyWaitsToMinimizeStalling {
    if(self = [super init]) {
        _preferredForwardBufferDuration = preferredForwardBufferDuration;
        _canUseNetworkResourcesForLiveStreamingWhilePaused = canUseNetworkResourcesForLiveStreamingWhilePaused;
        _automaticallyWaitsToMinimizeStalling = automaticallyWaitsToMinimizeStalling;
    }
    return self;
}
@end

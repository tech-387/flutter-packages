// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Autogenerated from Pigeon (v22.6.0), do not edit directly.
// See also: https://pub.dev/packages/pigeon

#import "./include/video_player_avfoundation/messages.g.h"

#if TARGET_OS_OSX
#import <FlutterMacOS/FlutterMacOS.h>
#else
#import <Flutter/Flutter.h>
#endif

#if !__has_feature(objc_arc)
#error File requires ARC to be enabled.
#endif

static NSArray<id> *wrapResult(id result, FlutterError *error) {
  if (error) {
    return @[
      error.code ?: [NSNull null], error.message ?: [NSNull null], error.details ?: [NSNull null]
    ];
  }
  return @[ result ?: [NSNull null] ];
}

static id GetNullableObjectAtIndex(NSArray<id> *array, NSInteger key) {
  id result = array[key];
  return (result == [NSNull null]) ? nil : result;
}

@interface FVPCreationOptions ()
+ (FVPCreationOptions *)fromList:(NSArray<id> *)list;
+ (nullable FVPCreationOptions *)nullableFromList:(NSArray<id> *)list;
- (NSArray<id> *)toList;
@end

@interface FVPMixWithOthersMessage ()
+ (FVPMixWithOthersMessage *)fromList:(NSArray<id> *)list;
+ (nullable FVPMixWithOthersMessage *)nullableFromList:(NSArray<id> *)list;
- (NSArray<id> *)toList;
@end

@interface FVPCacheOptionsMessage ()
+ (FVPCacheOptionsMessage *)fromList:(NSArray<id> *)list;
+ (nullable FVPCacheOptionsMessage *)nullableFromList:(NSArray<id> *)list;
- (NSArray<id> *)toList;
@end

@interface FVPBufferOptionsMessage ()
+ (FVPBufferOptionsMessage *)fromList:(NSArray<id> *)list;
+ (nullable FVPBufferOptionsMessage *)nullableFromList:(NSArray<id> *)list;
- (NSArray<id> *)toList;
@end

@implementation FVPCreationOptions
+ (instancetype)makeWithAsset:(nullable NSString *)asset
    uri:(nullable NSString *)uri
    packageName:(nullable NSString *)packageName
    formatHint:(nullable NSString *)formatHint
    httpHeaders:(NSDictionary<NSString *, NSString *> *)httpHeaders
    bufferOptions:(nullable FVPBufferOptionsMessage *)bufferOptions {
  FVPCreationOptions* pigeonResult = [[FVPCreationOptions alloc] init];
  pigeonResult.asset = asset;
  pigeonResult.uri = uri;
  pigeonResult.packageName = packageName;
  pigeonResult.formatHint = formatHint;
  pigeonResult.httpHeaders = httpHeaders;
  pigeonResult.bufferOptions = bufferOptions;
  return pigeonResult;
}
+ (FVPCreationOptions *)fromList:(NSArray<id> *)list {
  FVPCreationOptions *pigeonResult = [[FVPCreationOptions alloc] init];
  pigeonResult.asset = GetNullableObjectAtIndex(list, 0);
  pigeonResult.uri = GetNullableObjectAtIndex(list, 1);
  pigeonResult.packageName = GetNullableObjectAtIndex(list, 2);
  pigeonResult.formatHint = GetNullableObjectAtIndex(list, 3);
  pigeonResult.httpHeaders = GetNullableObjectAtIndex(list, 4);
  pigeonResult.bufferOptions = GetNullableObjectAtIndex(list, 5);
  return pigeonResult;
}
+ (nullable FVPCreationOptions *)nullableFromList:(NSArray<id> *)list {
  return (list) ? [FVPCreationOptions fromList:list] : nil;
}
- (NSArray<id> *)toList {
  return @[
    self.asset ?: [NSNull null],
    self.uri ?: [NSNull null],
    self.packageName ?: [NSNull null],
    self.formatHint ?: [NSNull null],
    self.httpHeaders ?: [NSNull null],
    self.bufferOptions ?: [NSNull null],
  ];
}
@end

@implementation FVPMixWithOthersMessage
+ (instancetype)makeWithMixWithOthers:(BOOL )mixWithOthers {
  FVPMixWithOthersMessage* pigeonResult = [[FVPMixWithOthersMessage alloc] init];
  pigeonResult.mixWithOthers = mixWithOthers;
  return pigeonResult;
}
+ (FVPMixWithOthersMessage *)fromList:(NSArray<id> *)list {
  FVPMixWithOthersMessage *pigeonResult = [[FVPMixWithOthersMessage alloc] init];
  pigeonResult.mixWithOthers = [GetNullableObjectAtIndex(list, 0) boolValue];
  return pigeonResult;
}
+ (nullable FVPMixWithOthersMessage *)nullableFromList:(NSArray<id> *)list {
  return (list) ? [FVPMixWithOthersMessage fromList:list] : nil;
}
- (NSArray<id> *)toList {
  return @[
    @(self.mixWithOthers),
  ];
}
@end

@implementation FVPCacheOptionsMessage
+ (instancetype)makeWithEnableCache:(BOOL )enableCache
    cacheDirectory:(NSString *)cacheDirectory
    maxCacheBytes:(NSInteger )maxCacheBytes
    maxFileBytes:(NSInteger )maxFileBytes {
  FVPCacheOptionsMessage* pigeonResult = [[FVPCacheOptionsMessage alloc] init];
  pigeonResult.enableCache = enableCache;
  pigeonResult.cacheDirectory = cacheDirectory;
  pigeonResult.maxCacheBytes = maxCacheBytes;
  pigeonResult.maxFileBytes = maxFileBytes;
  return pigeonResult;
}
+ (FVPCacheOptionsMessage *)fromList:(NSArray<id> *)list {
  FVPCacheOptionsMessage *pigeonResult = [[FVPCacheOptionsMessage alloc] init];
  pigeonResult.enableCache = [GetNullableObjectAtIndex(list, 0) boolValue];
  pigeonResult.cacheDirectory = GetNullableObjectAtIndex(list, 1);
  pigeonResult.maxCacheBytes = [GetNullableObjectAtIndex(list, 2) integerValue];
  pigeonResult.maxFileBytes = [GetNullableObjectAtIndex(list, 3) integerValue];
  return pigeonResult;
}
+ (nullable FVPCacheOptionsMessage *)nullableFromList:(NSArray<id> *)list {
  return (list) ? [FVPCacheOptionsMessage fromList:list] : nil;
}
- (NSArray<id> *)toList {
  return @[
    @(self.enableCache),
    self.cacheDirectory ?: [NSNull null],
    @(self.maxCacheBytes),
    @(self.maxFileBytes),
  ];
}
@end

@implementation FVPBufferOptionsMessage
+ (instancetype)makeWithPreferredForwardBufferDuration:(NSInteger )preferredForwardBufferDuration
    canUseNetworkResourcesForLiveStreamingWhilePaused:(BOOL )canUseNetworkResourcesForLiveStreamingWhilePaused
    automaticallyWaitsToMinimizeStalling:(BOOL )automaticallyWaitsToMinimizeStalling {
  FVPBufferOptionsMessage* pigeonResult = [[FVPBufferOptionsMessage alloc] init];
  pigeonResult.preferredForwardBufferDuration = preferredForwardBufferDuration;
  pigeonResult.canUseNetworkResourcesForLiveStreamingWhilePaused = canUseNetworkResourcesForLiveStreamingWhilePaused;
  pigeonResult.automaticallyWaitsToMinimizeStalling = automaticallyWaitsToMinimizeStalling;
  return pigeonResult;
}
+ (FVPBufferOptionsMessage *)fromList:(NSArray<id> *)list {
  FVPBufferOptionsMessage *pigeonResult = [[FVPBufferOptionsMessage alloc] init];
  pigeonResult.preferredForwardBufferDuration = [GetNullableObjectAtIndex(list, 0) integerValue];
  pigeonResult.canUseNetworkResourcesForLiveStreamingWhilePaused = [GetNullableObjectAtIndex(list, 1) boolValue];
  pigeonResult.automaticallyWaitsToMinimizeStalling = [GetNullableObjectAtIndex(list, 2) boolValue];
  return pigeonResult;
}
+ (nullable FVPBufferOptionsMessage *)nullableFromList:(NSArray<id> *)list {
  return (list) ? [FVPBufferOptionsMessage fromList:list] : nil;
}
- (NSArray<id> *)toList {
  return @[
    @(self.preferredForwardBufferDuration),
    @(self.canUseNetworkResourcesForLiveStreamingWhilePaused),
    @(self.automaticallyWaitsToMinimizeStalling),
  ];
}
@end

@interface FVPMessagesPigeonCodecReader : FlutterStandardReader
@end
@implementation FVPMessagesPigeonCodecReader
- (nullable id)readValueOfType:(UInt8)type {
  switch (type) {
    case 129: 
      return [FVPCreationOptions fromList:[self readValue]];
    case 130: 
      return [FVPMixWithOthersMessage fromList:[self readValue]];
    case 131: 
      return [FVPCacheOptionsMessage fromList:[self readValue]];
    case 132: 
      return [FVPBufferOptionsMessage fromList:[self readValue]];
    default:
      return [super readValueOfType:type];
  }
}
@end

@interface FVPMessagesPigeonCodecWriter : FlutterStandardWriter
@end
@implementation FVPMessagesPigeonCodecWriter
- (void)writeValue:(id)value {
  if ([value isKindOfClass:[FVPCreationOptions class]]) {
    [self writeByte:129];
    [self writeValue:[value toList]];
  } else if ([value isKindOfClass:[FVPMixWithOthersMessage class]]) {
    [self writeByte:130];
    [self writeValue:[value toList]];
  } else if ([value isKindOfClass:[FVPCacheOptionsMessage class]]) {
    [self writeByte:131];
    [self writeValue:[value toList]];
  } else if ([value isKindOfClass:[FVPBufferOptionsMessage class]]) {
    [self writeByte:132];
    [self writeValue:[value toList]];
  } else {
    [super writeValue:value];
  }
}
@end

@interface FVPMessagesPigeonCodecReaderWriter : FlutterStandardReaderWriter
@end
@implementation FVPMessagesPigeonCodecReaderWriter
- (FlutterStandardWriter *)writerWithData:(NSMutableData *)data {
  return [[FVPMessagesPigeonCodecWriter alloc] initWithData:data];
}
- (FlutterStandardReader *)readerWithData:(NSData *)data {
  return [[FVPMessagesPigeonCodecReader alloc] initWithData:data];
}
@end

NSObject<FlutterMessageCodec> *FVPGetMessagesCodec(void) {
  static FlutterStandardMessageCodec *sSharedObject = nil;
  static dispatch_once_t sPred = 0;
  dispatch_once(&sPred, ^{
    FVPMessagesPigeonCodecReaderWriter *readerWriter = [[FVPMessagesPigeonCodecReaderWriter alloc] init];
    sSharedObject = [FlutterStandardMessageCodec codecWithReaderWriter:readerWriter];
  });
  return sSharedObject;
}
void SetUpFVPAVFoundationVideoPlayerApi(id<FlutterBinaryMessenger> binaryMessenger, NSObject<FVPAVFoundationVideoPlayerApi> *api) {
  SetUpFVPAVFoundationVideoPlayerApiWithSuffix(binaryMessenger, api, @"");
}

void SetUpFVPAVFoundationVideoPlayerApiWithSuffix(id<FlutterBinaryMessenger> binaryMessenger, NSObject<FVPAVFoundationVideoPlayerApi> *api, NSString *messageChannelSuffix) {
  messageChannelSuffix = messageChannelSuffix.length > 0 ? [NSString stringWithFormat: @".%@", messageChannelSuffix] : @"";
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.initialize", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(initialize:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(initialize:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        FlutterError *error;
        [api initialize:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.create", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(createWithOptions:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(createWithOptions:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        FVPCreationOptions *arg_creationOptions = GetNullableObjectAtIndex(args, 0);
        FlutterError *error;
        NSNumber *output = [api createWithOptions:arg_creationOptions error:&error];
        callback(wrapResult(output, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.dispose", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(disposePlayer:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(disposePlayer:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 0) integerValue];
        FlutterError *error;
        [api disposePlayer:arg_textureId error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.setLooping", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(setLooping:forPlayer:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(setLooping:forPlayer:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        BOOL arg_isLooping = [GetNullableObjectAtIndex(args, 0) boolValue];
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 1) integerValue];
        FlutterError *error;
        [api setLooping:arg_isLooping forPlayer:arg_textureId error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.setVolume", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(setVolume:forPlayer:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(setVolume:forPlayer:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        double arg_volume = [GetNullableObjectAtIndex(args, 0) doubleValue];
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 1) integerValue];
        FlutterError *error;
        [api setVolume:arg_volume forPlayer:arg_textureId error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.setPlaybackSpeed", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(setPlaybackSpeed:forPlayer:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(setPlaybackSpeed:forPlayer:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        double arg_speed = [GetNullableObjectAtIndex(args, 0) doubleValue];
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 1) integerValue];
        FlutterError *error;
        [api setPlaybackSpeed:arg_speed forPlayer:arg_textureId error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.play", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(playPlayer:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(playPlayer:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 0) integerValue];
        FlutterError *error;
        [api playPlayer:arg_textureId error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.getPosition", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(positionForPlayer:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(positionForPlayer:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 0) integerValue];
        FlutterError *error;
        NSNumber *output = [api positionForPlayer:arg_textureId error:&error];
        callback(wrapResult(output, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.seekTo", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(seekTo:forPlayer:completion:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(seekTo:forPlayer:completion:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        NSInteger arg_position = [GetNullableObjectAtIndex(args, 0) integerValue];
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 1) integerValue];
        [api seekTo:arg_position forPlayer:arg_textureId completion:^(FlutterError *_Nullable error) {
          callback(wrapResult(nil, error));
        }];
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.pause", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(pausePlayer:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(pausePlayer:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        NSInteger arg_textureId = [GetNullableObjectAtIndex(args, 0) integerValue];
        FlutterError *error;
        [api pausePlayer:arg_textureId error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.setMixWithOthers", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(setMixWithOthers:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(setMixWithOthers:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        FVPMixWithOthersMessage *arg_msg = GetNullableObjectAtIndex(args, 0);
        FlutterError *error;
        [api setMixWithOthers:arg_msg error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
  {
    FlutterBasicMessageChannel *channel =
      [[FlutterBasicMessageChannel alloc]
        initWithName:[NSString stringWithFormat:@"%@%@", @"dev.flutter.pigeon.video_player_avfoundation.AVFoundationVideoPlayerApi.setCacheOptions", messageChannelSuffix]
        binaryMessenger:binaryMessenger
        codec:FVPGetMessagesCodec()];
    if (api) {
      NSCAssert([api respondsToSelector:@selector(setCacheOptions:error:)], @"FVPAVFoundationVideoPlayerApi api (%@) doesn't respond to @selector(setCacheOptions:error:)", api);
      [channel setMessageHandler:^(id _Nullable message, FlutterReply callback) {
        NSArray<id> *args = message;
        FVPCacheOptionsMessage *arg_msg = GetNullableObjectAtIndex(args, 0);
        FlutterError *error;
        [api setCacheOptions:arg_msg error:&error];
        callback(wrapResult(nil, error));
      }];
    } else {
      [channel setMessageHandler:nil];
    }
  }
}
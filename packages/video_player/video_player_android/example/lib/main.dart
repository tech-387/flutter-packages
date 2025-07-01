// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// ignore_for_file: public_member_api_docs

import 'dart:developer';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:video_player_platform_interface/video_player_platform_interface.dart';

import 'mini_controller.dart';
import 'package:collection/collection.dart';

void main() {
  runApp(
    MaterialApp(
      home: _App(),
    ),
  );
}

class _App extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: const ValueKey<String>('home_page'),
      appBar: AppBar(
        title: const Text('Video player example'),
      ),
      body: _BumbleBeeRemoteVideo(),
    );
  }
}

class _ButterFlyAssetVideo extends StatefulWidget {
  @override
  _ButterFlyAssetVideoState createState() => _ButterFlyAssetVideoState();
}

class _ButterFlyAssetVideoState extends State<_ButterFlyAssetVideo> {
  late MiniController _controller;

  @override
  void initState() {
    super.initState();
    _controller = MiniController.asset('assets/Butterfly-209.mp4');

    _controller.addListener(() {
      setState(() {});
    });
    _controller.initialize().then((_) => _controller.play());
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          Container(
            padding: const EdgeInsets.only(top: 20.0),
          ),
          const Text('With assets mp4'),
          Container(
            padding: const EdgeInsets.all(20),
            child: AspectRatio(
              aspectRatio: _controller.value.aspectRatio,
              child: Stack(
                alignment: Alignment.bottomCenter,
                children: <Widget>[
                  VideoPlayer(_controller),
                  _ControlsOverlay(controller: _controller),
                  VideoProgressIndicator(_controller),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BumbleBeeRemoteVideo extends StatefulWidget {
  @override
  _BumbleBeeRemoteVideoState createState() => _BumbleBeeRemoteVideoState();
}

class _BumbleBeeRemoteVideoState extends State<_BumbleBeeRemoteVideo> {
  final List<MiniController> controllers = <MiniController>[
    // 0.5 fmp4 segments, 720p, 18 seconds video
    // MiniController.network(
    //     'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/output_fmp4_500ms/output.m3u8'),

    // 0.5s and 8s fmp4 segments, 720p, 18 seconds video
    // MiniController.network(
    //     'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/output_fmp4/output.m3u8'),

    // 1s fmp4 segments, 720p, 18 seconds video
    // MiniController.network(
    //     'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/output_fmp4_1s/output.m3u8'),

    // // 0.5 and 8s segments, 720p, 18 seconds video
    // MiniController.network(
    //     'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/output_half_second/output.m3u8',
    //     videoPlayerBufferOptions: const VideoPlayerBufferOptions(
    //       maxBufferMs: 2400,
    //       minBufferMs: 2400,
    //       bufferForPlaybackMs: 500,
    //       bufferForPlaybackAfterRebufferMs: 500,
    //     )),

    // // All variants, 18 seconds video
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/d45945d3-d43c-4dd7-bb8c-6f0f9f8b4c5c/d45945d3-d43c-4dd7-bb8c-6f0f9f8b4c5c.m3u8',
    //   videoPlayerBufferOptions: const VideoPlayerBufferOptions(
    //     maxBufferMs: 3600,
    //     minBufferMs: 3600,
    //     bufferForPlaybackMs: 500,
    //     bufferForPlaybackAfterRebufferMs: 500,
    //   ),
    // ),

    // All variants, 7 seconds video
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/cdee0496-ea71-4979-bc43-18dcb4bf1436/cdee0496-ea71-4979-bc43-18dcb4bf1436.m3u8',
    //   videoPlayerBufferOptions: const VideoPlayerBufferOptions(
    //     maxBufferMs: 2500,
    //     minBufferMs: 2500,
    //     bufferForPlaybackMs: 500,
    //     bufferForPlaybackAfterRebufferMs: 500,
    //   ),
    // ),

    // All variants, 30 seconds video, crf 24
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/7fb8504e-e90c-4d96-988a-2d47e3f11710/7fb8504e-e90c-4d96-988a-2d47e3f11710.m3u8',
    //   videoPlayerBufferOptions: const VideoPlayerBufferOptions(
    //     maxBufferMs: 2500,
    //     minBufferMs: 2500,
    //     bufferForPlaybackMs: 500,
    //     bufferForPlaybackAfterRebufferMs: 500,
    //   ),
    // ),

    // All variants, 30 seconds video, crf 32
    MiniController.network(
      'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/b47ddcd1-9f67-4087-9991-8ee946016284/b47ddcd1-9f67-4087-9991-8ee946016284.m3u8',
      videoPlayerBufferOptions: const VideoPlayerBufferOptions(
        maxBufferMs: 2500,
        minBufferMs: 2500,
        bufferForPlaybackMs: 500,
        bufferForPlaybackAfterRebufferMs: 500,
      ),
    ),

    // // // All variants 2, 18 seconds video
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/8330a7dc-4e1b-48e2-bfb3-e6dad49bf3e9/8330a7dc-4e1b-48e2-bfb3-e6dad49bf3e9.m3u8',
    //   videoPlayerBufferOptions: const VideoPlayerBufferOptions(
    //     maxBufferMs: 2400,
    //     minBufferMs: 2400,
    //     bufferForPlaybackMs: 500,
    //     bufferForPlaybackAfterRebufferMs: 500,
    //   ),
    // ),

    // // All variants 3,  18 seconds video
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/85bad694-53b6-4d64-8cc4-bf2c5acf80f9/85bad694-53b6-4d64-8cc4-bf2c5acf80f9.m3u8',
    //   videoPlayerBufferOptions: const VideoPlayerBufferOptions(
    //     maxBufferMs: 2400,
    //     minBufferMs: 2400,
    //     bufferForPlaybackMs: 500,
    //     bufferForPlaybackAfterRebufferMs: 500,
    //   ),
    // ),

    // 720p, 18 seconds video
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/43bade14-28b8-4d19-8fbf-551464604de9/43bade14-28b8-4d19-8fbf-551464604de9.m3u8',
    // ),

    // // 540p, 18 seconds video
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/e9bda753-ac6b-4c1d-a7d6-32e4da27b9c0/e9bda753-ac6b-4c1d-a7d6-32e4da27b9c0.m3u8',
    // ),

    // // 360p, 18 seconds video
    // MiniController.network(
    //   'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/caf76025-9975-4594-ae9b-982b47a12779/caf76025-9975-4594-ae9b-982b47a12779.m3u8',
    // ),
  ];

  @override
  void initState() {
    super.initState();

    // controllers.take(1).forEachIndexed((int index, MiniController controller) {
    //   controller.addListener(() {
    //     setState(() {});
    //   });
    //   final Stopwatch stopwatch = Stopwatch()..start();
    //   controller.initialize().then((value) {
    //     log('Controller $index initialized after ${stopwatch.elapsedMilliseconds}ms ✅');
    //   });
    // });
  }

  void onPageChanged(int index) {
    log('Page changed to $index');
    final MiniController? controller = controllers.elementAtOrNull(index);
    if (controller != null && controller.value.isInitialized) {
      log('Controller $index is already initialized');
      return;
    }
    controllers.elementAtOrNull(index)?.initialize();
  }

  @override
  void dispose() {
    for (final MiniController controller in controllers) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // return PageView.builder(
    //   scrollDirection: Axis.vertical,
    //   onPageChanged: onPageChanged,
    //   itemBuilder: (BuildContext context, int index) {
    //     return Container(
    //       padding: const EdgeInsets.all(20),
    //       child: AspectRatio(
    //         aspectRatio: controllers[index].value.aspectRatio,
    //         child: Stack(
    //           alignment: Alignment.bottomCenter,
    //           children: <Widget>[
    //             VideoPlayer(controllers[index]),
    //             _ControlsOverlay(controller: controllers[index]),
    //             VideoProgressIndicator(controllers[index]),
    //           ],
    //         ),
    //       ),
    //     );
    //   },
    //   itemCount: controllers.length,
    // );
    return Scaffold(
      floatingActionButton: FloatingActionButton(onPressed: () {
        Navigator.of(context)
            .push(CupertinoPageRoute(builder: (BuildContext context) {
          return const VideoPage(
              videoUrl:
                  'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/b47ddcd1-9f67-4087-9991-8ee946016284/b47ddcd1-9f67-4087-9991-8ee946016284.m3u8');
        }));
      }),
    );
  }
}

class _RtspRemoteVideo extends StatefulWidget {
  @override
  _RtspRemoteVideoState createState() => _RtspRemoteVideoState();
}

class _RtspRemoteVideoState extends State<_RtspRemoteVideo> {
  MiniController? _controller;

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  String? _validateRtspUrl(String? value) {
    if (value == null || !value.startsWith('rtsp://')) {
      return 'Enter a valid RTSP URL';
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          Container(padding: const EdgeInsets.only(top: 20.0)),
          const Text('With RTSP streaming'),
          Padding(
            padding: const EdgeInsets.all(20.0),
            child: TextFormField(
              autovalidateMode: AutovalidateMode.onUserInteraction,
              decoration: const InputDecoration(label: Text('RTSP URL')),
              validator: _validateRtspUrl,
              textInputAction: TextInputAction.done,
              onFieldSubmitted: (String value) {
                if (_validateRtspUrl(value) == null) {
                } else {
                  setState(() {
                    _controller?.dispose();
                    _controller = null;
                  });
                }
              },
            ),
          ),
          if (_controller != null)
            Container(
              padding: const EdgeInsets.all(20),
              child: AspectRatio(
                aspectRatio: _controller!.value.aspectRatio,
                child: Stack(
                  alignment: Alignment.bottomCenter,
                  children: <Widget>[
                    VideoPlayer(_controller!),
                    _ControlsOverlay(controller: _controller!),
                    VideoProgressIndicator(_controller!),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _ControlsOverlay extends StatelessWidget {
  const _ControlsOverlay({required this.controller});

  static const List<double> _examplePlaybackRates = <double>[
    0.25,
    0.5,
    1.0,
    1.5,
    2.0,
    3.0,
    5.0,
    10.0,
  ];

  final MiniController controller;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 50),
          reverseDuration: const Duration(milliseconds: 200),
          child: controller.value.isPlaying
              ? const SizedBox.shrink()
              : const ColoredBox(
                  color: Colors.black26,
                  child: Center(
                    child: Icon(
                      Icons.play_arrow,
                      color: Colors.white,
                      size: 100.0,
                      semanticLabel: 'Play',
                    ),
                  ),
                ),
        ),
        GestureDetector(
          onTap: () {
            controller.value.isPlaying ? controller.pause() : controller.play();
          },
        ),
        Align(
          alignment: Alignment.topRight,
          child: PopupMenuButton<double>(
            initialValue: controller.value.playbackSpeed,
            tooltip: 'Playback speed',
            onSelected: (double speed) {
              controller.setPlaybackSpeed(speed);
            },
            itemBuilder: (BuildContext context) {
              return <PopupMenuItem<double>>[
                for (final double speed in _examplePlaybackRates)
                  PopupMenuItem<double>(
                    value: speed,
                    child: Text('${speed}x'),
                  )
              ];
            },
            child: Padding(
              padding: const EdgeInsets.symmetric(
                // Using less vertical padding as the text is also longer
                // horizontally, so it feels like it would need more spacing
                // horizontally (matching the aspect ratio of the video).
                vertical: 12,
                horizontal: 16,
              ),
              child: Text('${controller.value.playbackSpeed}x'),
            ),
          ),
        ),
      ],
    );
  }
}

class VideoPage extends StatefulWidget {
  const VideoPage({super.key, required this.videoUrl});
  final String videoUrl;

  @override
  State<VideoPage> createState() => _VideoPageState();
}

class _VideoPageState extends State<VideoPage> {
  late MiniController controller;

  @override
  void initState() {
    super.initState();
    controller = MiniController.network(
      'https://d27yc5cqcdhniy.cloudfront.net/private/users/54388498-0061-7014-c547-0365916f14c4/clips/b47ddcd1-9f67-4087-9991-8ee946016284/b47ddcd1-9f67-4087-9991-8ee946016284.m3u8',
      videoPlayerBufferOptions: const VideoPlayerBufferOptions(
        maxBufferMs: 2500,
        minBufferMs: 2500,
        bufferForPlaybackMs: 500,
        bufferForPlaybackAfterRebufferMs: 500,
      ),
    );
    controller.initialize().then((value) {
      log('Controller initialized ✅');
      controller.play();
    });
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return VideoPlayer(controller);
  }
}

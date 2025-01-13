// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// ignore_for_file: public_member_api_docs

import 'dart:developer';

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
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        key: const ValueKey<String>('home_page'),
        appBar: AppBar(
          title: const Text('Video player example'),
          bottom: const TabBar(
            isScrollable: true,
            tabs: <Widget>[
              Tab(
                icon: Icon(Icons.cloud),
                text: 'Remote',
              ),
              Tab(icon: Icon(Icons.insert_drive_file), text: 'Asset'),
            ],
          ),
        ),
        body: TabBarView(
          children: <Widget>[
            _BumbleBeeRemoteVideo(),
            _ButterFlyAssetVideo(),
          ],
        ),
      ),
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
    _controller.initialize().then((_) => setState(() {}));
    _controller.play();
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
    MiniController.network(
      'https://d2a3buv484g5ek.cloudfront.net/private/users/e899488b-4bac-48c0-9333-ea7bacc6f56a/clips/75654d70-9d85-4f02-8a64-72043cc36010/75654d70-9d85-4f02-8a64-72043cc36010.m3u8',
    ),
    MiniController.network(
      'https://d2a3buv484g5ek.cloudfront.net/private/users/e899488b-4bac-48c0-9333-ea7bacc6f56a/clips/156a998f-e4c6-4625-ac68-8fe53ad8744f/156a998f-e4c6-4625-ac68-8fe53ad8744f.m3u8',
    ),
    MiniController.network(
      'https://d2a3buv484g5ek.cloudfront.net/private/users/e899488b-4bac-48c0-9333-ea7bacc6f56a/clips/bfb536ce-27bd-48e1-9fa1-9b9723bb01b0/bfb536ce-27bd-48e1-9fa1-9b9723bb01b0.m3u8',
    ),
    MiniController.network(
      'https://d2a3buv484g5ek.cloudfront.net/private/users/e899488b-4bac-48c0-9333-ea7bacc6f56a/clips/42b041c4-c116-4f97-955f-3e47f329872f/42b041c4-c116-4f97-955f-3e47f329872f.m3u8',
    ),
    MiniController.network(
      'https://d2a3buv484g5ek.cloudfront.net/private/users/e899488b-4bac-48c0-9333-ea7bacc6f56a/clips/cb420205-6ec8-4119-9346-465d84bed9ee/cb420205-6ec8-4119-9346-465d84bed9ee.m3u8',
    ),
  ];

  @override
  void initState() {
    super.initState();

    controllers.take(3).forEachIndexed((int index, MiniController controller) {
      controller.addListener(() {
        setState(() {});
      });
      controller.initialize().then((value) {
        log('Controller $index initialized ✅');
      });
    });
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
    return PageView.builder(
      itemBuilder: (BuildContext context, int index) {
        return Container(
          padding: const EdgeInsets.all(20),
          child: AspectRatio(
            aspectRatio: controllers[index].value.aspectRatio,
            child: Stack(
              alignment: Alignment.bottomCenter,
              children: <Widget>[
                VideoPlayer(controllers[index]),
                _ControlsOverlay(controller: controllers[index]),
                VideoProgressIndicator(controllers[index]),
              ],
            ),
          ),
        );
      },
      itemCount: controllers.length,
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
              : Container(
                  color: Colors.black26,
                  child: const Center(
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

// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camerax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraControl;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.view.TextureRegistry;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CameraAndroidCameraxPluginTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ActivityPluginBinding activityPluginBinding;
  @Mock FlutterPluginBinding flutterPluginBinding;

  @Test
  public void onAttachedToActivity_setsLifecycleOwnerAsActivityIfLifecycleOwnerAsNeeded() {
    final Activity mockActivity =
        mock(Activity.class, withSettings().extraInterfaces(LifecycleOwner.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onAttachedToActivity(activityPluginBinding);

    assertNotNull(plugin.proxyApiRegistrar);
    assertEquals(plugin.proxyApiRegistrar.getActivity(), mockActivity);
    assertEquals(plugin.proxyApiRegistrar.getLifecycleOwner(), mockActivity);
  }

  @Test
  public void
      onAttachedToActivity_setsLifecycleOwnerAsProxyLifecycleProviderIfActivityNotLifecycleOwnerAsNeeded() {
    final Activity mockActivity = mock(Activity.class);
    when(mockActivity.getApplication()).thenReturn(mock(Application.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onAttachedToActivity(activityPluginBinding);

    assertNotNull(plugin.proxyApiRegistrar);
    assertTrue(plugin.proxyApiRegistrar.getLifecycleOwner() instanceof ProxyLifecycleProvider);
  }

  @Test
  public void onAttachedToActivity_setsActivityAsNeededAndPermissionsRegistry() {
    final Activity mockActivity =
        mock(Activity.class, withSettings().extraInterfaces(LifecycleOwner.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onAttachedToActivity(activityPluginBinding);

    assertNotNull(plugin.proxyApiRegistrar);
    assertEquals(plugin.proxyApiRegistrar.getActivity(), mockActivity);
    assertEquals(plugin.proxyApiRegistrar.getLifecycleOwner(), mockActivity);
    assertNotNull(plugin.proxyApiRegistrar.getPermissionsRegistry());
  }

  @Test
  public void
      onDetachedFromActivityForConfigChanges_removesReferencesToActivityPluginBindingAndActivity() {
    final Activity mockActivity =
        mock(Activity.class, withSettings().extraInterfaces(LifecycleOwner.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onAttachedToActivity(activityPluginBinding);
    plugin.onDetachedFromActivity();

    assertNotNull(plugin.proxyApiRegistrar);
    assertNull(plugin.proxyApiRegistrar.getActivity());
    assertNull(plugin.proxyApiRegistrar.getLifecycleOwner());
  }

  @Test
  public void
      onDetachedFromActivityForConfigChanges_setsContextReferencesBasedOnFlutterPluginBinding() {
    final Context mockContext = mock(Context.class);
    final Activity mockActivity =
        mock(Activity.class, withSettings().extraInterfaces(LifecycleOwner.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mockContext);
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onAttachedToActivity(activityPluginBinding);
    plugin.onDetachedFromActivityForConfigChanges();

    assertNotNull(plugin.proxyApiRegistrar);
    assertEquals(plugin.proxyApiRegistrar.getContext(), mockContext);
    assertNull(plugin.proxyApiRegistrar.getActivity());
    assertNull(plugin.proxyApiRegistrar.getLifecycleOwner());
  }

  @Test
  public void
      onReattachedToActivityForConfigChanges_setsLifecycleOwnerAsActivityIfLifecycleOwnerAsNeeded() {
    final Activity mockActivity =
        mock(Activity.class, withSettings().extraInterfaces(LifecycleOwner.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onAttachedToActivity(activityPluginBinding);

    assertNotNull(plugin.proxyApiRegistrar);
    assertEquals(plugin.proxyApiRegistrar.getActivity(), mockActivity);
    assertEquals(plugin.proxyApiRegistrar.getLifecycleOwner(), mockActivity);
  }

  @Test
  public void
      onReattachedToActivityForConfigChanges_setsLifecycleOwnerAsProxyLifecycleProviderIfActivityNotLifecycleOwnerAsNeeded() {
    final Activity mockActivity = mock(Activity.class);
    when(mockActivity.getApplication()).thenReturn(mock(Application.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onReattachedToActivityForConfigChanges(activityPluginBinding);

    assertNotNull(plugin.proxyApiRegistrar);
    assertTrue(plugin.proxyApiRegistrar.getLifecycleOwner() instanceof ProxyLifecycleProvider);
  }

  @Test
  public void onReattachedToActivityForConfigChanges_setsActivityAndPermissionsRegistryAsNeeded() {
    final Activity mockActivity =
        mock(Activity.class, withSettings().extraInterfaces(LifecycleOwner.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onReattachedToActivityForConfigChanges(activityPluginBinding);

    assertNotNull(plugin.proxyApiRegistrar);
    assertEquals(plugin.proxyApiRegistrar.getActivity(), mockActivity);
    assertEquals(plugin.proxyApiRegistrar.getLifecycleOwner(), mockActivity);
    assertNotNull(plugin.proxyApiRegistrar.getPermissionsRegistry());
  }

  @Test
  public void onDetachedFromActivity_setsContextReferencesBasedOnFlutterPluginBinding() {
    final Context mockContext = mock(Context.class);
    final Activity mockActivity =
        mock(Activity.class, withSettings().extraInterfaces(LifecycleOwner.class));

    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mockContext);
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));
    when(activityPluginBinding.getActivity()).thenReturn(mockActivity);

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    plugin.onAttachedToActivity(activityPluginBinding);
    plugin.onDetachedFromActivity();

    assertNotNull(plugin.proxyApiRegistrar);
    assertEquals(plugin.proxyApiRegistrar.getContext(), mockContext);
    assertNull(plugin.proxyApiRegistrar.getActivity());
    assertNull(plugin.proxyApiRegistrar.getLifecycleOwner());
  }

  @Test
  public void onDetachedFromEngine_releasesSurfaceProducers() {
    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mock(BinaryMessenger.class));
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    final ProxyApiRegistrar proxyApiRegistrar = spy(plugin.proxyApiRegistrar);
    plugin.proxyApiRegistrar = proxyApiRegistrar;

    plugin.onDetachedFromEngine(flutterPluginBinding);

    verify(proxyApiRegistrar).releaseSurfaceProducers();
    assertNull(plugin.proxyApiRegistrar);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void onDetachedFromEngine_doesNotReplyWithFocusMeteringResultThatCompletesAfterDetach() {
    final BinaryMessenger mockMessenger = mock(BinaryMessenger.class);
    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mockMessenger);
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    final ProxyApiRegistrar proxyApiRegistrar = plugin.proxyApiRegistrar;

    final CameraControl cameraControl = mock(CameraControl.class);
    final FocusMeteringAction action = mock(FocusMeteringAction.class);
    final ListenableFuture<FocusMeteringResult> focusMeteringResultFuture =
        mock(ListenableFuture.class);
    when(cameraControl.startFocusAndMetering(action)).thenReturn(focusMeteringResultFuture);
    proxyApiRegistrar.getInstanceManager().addDartCreatedInstance(cameraControl, 0);
    proxyApiRegistrar.getInstanceManager().addDartCreatedInstance(action, 1);

    final BinaryMessenger.BinaryReply reply = mock(BinaryMessenger.BinaryReply.class);
    try (MockedStatic<Futures> mockedFutures = Mockito.mockStatic(Futures.class)) {
      sendMessageFromDart(
          mockMessenger,
          proxyApiRegistrar,
          "dev.flutter.pigeon.camera_android_camerax.CameraControl.startFocusAndMetering",
          Arrays.asList(cameraControl, action),
          reply);
      final ArgumentCaptor<FutureCallback<FocusMeteringResult>> futureCallbackCaptor =
          ArgumentCaptor.forClass(FutureCallback.class);
      mockedFutures.verify(
          () ->
              Futures.addCallback(
                  eq(focusMeteringResultFuture), futureCallbackCaptor.capture(), any()));

      plugin.onDetachedFromEngine(flutterPluginBinding);
      futureCallbackCaptor.getValue().onSuccess(mock(FocusMeteringResult.class));
    }

    verify(reply, never()).reply(any());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void onDetachedFromEngine_doesNotReplyWithProcessCameraProviderThatResolvesAfterDetach()
      throws Exception {
    final BinaryMessenger mockMessenger = mock(BinaryMessenger.class);
    when(flutterPluginBinding.getBinaryMessenger()).thenReturn(mockMessenger);
    when(flutterPluginBinding.getApplicationContext()).thenReturn(mock(Context.class));
    when(flutterPluginBinding.getTextureRegistry()).thenReturn(mock(TextureRegistry.class));

    final CameraAndroidCameraxPlugin plugin = new CameraAndroidCameraxPlugin();
    plugin.onAttachedToEngine(flutterPluginBinding);
    final ProxyApiRegistrar proxyApiRegistrar = plugin.proxyApiRegistrar;

    final ListenableFuture<ProcessCameraProvider> processCameraProviderFuture =
        mock(ListenableFuture.class);
    final BinaryMessenger.BinaryReply reply = mock(BinaryMessenger.BinaryReply.class);
    try (MockedStatic<ProcessCameraProvider> mockedProcessCameraProvider =
        Mockito.mockStatic(ProcessCameraProvider.class)) {
      mockedProcessCameraProvider
          .when(() -> ProcessCameraProvider.getInstance(any()))
          .thenReturn(processCameraProviderFuture);
      when(processCameraProviderFuture.get()).thenReturn(mock(ProcessCameraProvider.class));

      sendMessageFromDart(
          mockMessenger,
          proxyApiRegistrar,
          "dev.flutter.pigeon.camera_android_camerax.ProcessCameraProvider.getInstance",
          null,
          reply);
      final ArgumentCaptor<Runnable> listenerCaptor = ArgumentCaptor.forClass(Runnable.class);
      verify(processCameraProviderFuture).addListener(listenerCaptor.capture(), any());

      plugin.onDetachedFromEngine(flutterPluginBinding);
      listenerCaptor.getValue().run();
    }

    verify(reply, never()).reply(any());
  }

  /** Delivers a Pigeon message to the handler the plugin registered for {@code channelName}. */
  private static void sendMessageFromDart(
      BinaryMessenger messenger,
      ProxyApiRegistrar proxyApiRegistrar,
      String channelName,
      @Nullable List<Object> arguments,
      BinaryMessenger.BinaryReply reply) {
    final ArgumentCaptor<BinaryMessenger.BinaryMessageHandler> handlerCaptor =
        ArgumentCaptor.forClass(BinaryMessenger.BinaryMessageHandler.class);
    verify(messenger).setMessageHandler(eq(channelName), handlerCaptor.capture());

    final ByteBuffer message = proxyApiRegistrar.getCodec().encodeMessage(arguments);
    if (message != null) {
      message.rewind();
    }
    handlerCaptor.getValue().onMessage(message, reply);
  }
}

/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.springml.roadsigndetection;

import static net.springml.roadsigndetection.MainActivity.currentLocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.location.Location;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.util.Size;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import net.springml.potholesdetection.R;
import net.springml.roadsigndetection.OverlayView.DrawCallback;
import net.springml.roadsigndetection.env.ImageUtils;
import net.springml.roadsigndetection.env.Logger;
import net.springml.roadsigndetection.tracking.MultiBoxTracker;

import android.media.MediaPlayer;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  private MediaPlayer mp;
  private boolean notifShown =false;

  public static int TF_OD_API_INPUT_SIZE = 320;
  private static final boolean TF_OD_API_MODEL_QUANTIZED = true;
  public static String TF_OD_API_MODEL_FILE = "";
  public static String TF_OD_API_LABELS_FILE = "";
  public static Location loc;
  private String currModel;
  // Minimum detection confidence to track a detection.
  public static float MINIMUM_CONFIDENCE_TF_OD_API = 0.0f;

  private static final boolean MAINTAIN_ASPECT = false;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(480, 800);

  private Integer sensorOrientation;

  private Classifier detector;

  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;


  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector = TFLiteObjectDetectionAPIModel.create(
              getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE, TF_OD_API_MODEL_QUANTIZED);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      LOGGER.e(e, "Exception initializing classifier!");
      Toast toast =
              Toast.makeText(
                      getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                    previewWidth, previewHeight,
                    cropSize, cropSize,
                    sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                tracker.draw(canvas);
                if (isDebug()) {
                  tracker.drawDebug(canvas);
                }
              }
            });

    addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                if (!isDebug()) {
                  return;
                }
                final Bitmap copy = cropCopyBitmap;
                if (copy == null) {
                  return;
                }

                final int backgroundColor = Color.argb(100, 0, 0, 0);
                canvas.drawColor(backgroundColor);

                final Matrix matrix = new Matrix();
                final float scaleFactor = 2;
                matrix.postScale(scaleFactor, scaleFactor);
                matrix.postTranslate(
                        canvas.getWidth() - copy.getWidth() * scaleFactor,
                        canvas.getHeight() - copy.getHeight() * scaleFactor);
                canvas.drawBitmap(copy, matrix, new Paint());
              }
            });
  }

  OverlayView trackingOverlay;

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    byte[] originalLuminance = getLuminance();
    tracker.onFrame(
            previewWidth,
            previewHeight,
            getLuminanceStride(),
            sensorOrientation,
            originalLuminance,
            timestamp);
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    if (luminanceCopy == null) {
      luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

    runInBackground(
            new Runnable() {
              @Override
              public void run() {
                LOGGER.i("Running detection on image " + currTimestamp);
                final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.BLUE);
                paint.setStyle(Style.STROKE);
                paint.setStrokeWidth(2.5f);

                final List<Classifier.Recognition> mappedRecognitions =
                        new LinkedList<Classifier.Recognition>();

                for (final Classifier.Recognition result : results) {
                  final RectF location = result.getLocation();
                  if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                    canvas.drawRect(location, paint);

                    cropToFrameTransform.mapRect(location);
                    result.setLocation(location);
                    mappedRecognitions.add(result);
                  }
                }

                tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);

//                if (tracker.trackedObjects.size() == 0) {
//                  if (mp.isPlaying()) {
//                    mp.pause();
//                    mp.seekTo(0);
//                  }
//                } else {
//                  if (!mp.isPlaying()) {
//                    mp.start();
//                  }
//                }
                trackingOverlay.postInvalidate();

                requestRender();
                computingDetection = false;
                if (tracker.trackedObjects.size() != 0) {
                  runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                      SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                      Date date = new Date();
                      if (notifShown == false) {
                        final TextView locationtext = findViewById(R.id.location);
                        System.out.println("cur"+ currModel+Objects.equals(currModel, "pothole"));
                        if(Objects.equals(currModel, "pothole")) {
                          System.out.println("hereeeeeee");
                          locationtext.setText("Pothole found near " + currentLocation + " at " + formatter.format(date));
                          locationtext.setVisibility(View.VISIBLE);
                        }
                        else if(Objects.equals(currModel, "manhole")) {
                          locationtext.setText("Manhole found near " + currentLocation + " at " + formatter.format(date));
                          locationtext.setVisibility(View.VISIBLE);
                        }
//                        locationtext.postDelayed(new Runnable() {
//                          @Override
//                          public void run() {
//                            locationtext.setVisibility(View.GONE);
//                          }
//                        }, 6000);
                        notifShown = true;
                      }
                    }
                  });

                }
              }
            });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onSetDebug(final boolean debug) {
    detector.enableStatLogging(debug);
  }


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();
    currModel = intent.getStringExtra("key");

  }
  @Override
  public synchronized void onResume() {

    super.onResume();
  }

  @Override
  public synchronized void onStop() {
    final TextView locationtext = findViewById(R.id.location);
    locationtext.setVisibility(View.GONE);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    final TextView locationtext = findViewById(R.id.location);
    locationtext.setVisibility(View.GONE);

    super.onDestroy();
  }
}

package com.example.audioapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

import android.util.SparseIntArray;
import java.util.ArrayList;
import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private ImageReader imageReader;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private final int REQUEST_CAMERA_PERMISSION = 1;
    private String cameraId;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();

        startBackgroundThread();
    }

    // Starts a background thread and its Handler.
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    // Stops the background thread and its Handler.
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // This method is now public and can be called from FirstFragment to start the camera preview
    public void startCameraPreview() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        openCamera();
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0];
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted, handle it here, possibly request permissions
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    // Check again here, because the user might have denied the permission in the meantime
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    try {
                        manager.openCamera(cameraId, stateCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Failed to open camera.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                }
            });
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to access camera.", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Camera permission not granted", Toast.LENGTH_SHORT).show();
        }
    }



    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            Surface surface = surfaceHolder.getSurface();
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (null == cameraDevice) return;
                    captureSession = session;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Failed to configure camera.", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    // Call this method to take a picture
    public void takePicture() {
        if (cameraDevice == null) return;

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && jpegSizes.length > 0) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(surfaceView.getHolder().getSurface());

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "JPEG_" + timeStamp + ".jpg";
            final File file = new File(getExternalFilesDir(null), fileName);

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes, File file) throws IOException {
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(bytes);
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    showToast("Saved: " + file);
                    startCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    showToast("Failed to configure capture session.");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void showToast(final String text) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
        closeCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (surfaceHolder.getSurface().isValid()) {
            openCamera();
        } else {
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    openCamera();
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {}
            });
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraPreview();
            } else {
                Toast.makeText(this, "Camera permission is necessary", Toast.LENGTH_LONG).show();
            }
        }
    }
}

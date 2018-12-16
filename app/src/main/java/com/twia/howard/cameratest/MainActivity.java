package com.twia.howard.cameratest;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.SoundPool;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private String mCameraId;
    private Size mPreviewSize;
    private Size mCaptureSize;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private CameraDevice mCameraDevice;
    private TextureView mTextureView;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession mCameraCaptureSession;
    private String saveFileName;
    private CardView cardViewOK, carViewDel, cardViewCapture, light;
    private ImageView picView, imageFlash;
    private String filePath;
    private SoundPool soundPool;
    private int mSoundId;
    private ProgressBar progressBar;
    private int mFlashMode = 2;
    private int fileSerialNum = 0;//檔案序號
    //獲取相機的管理者CameraManager
    CameraManager manager;

    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //全屏无状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //不關螢幕
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        setContentView(R.layout.activity_main);

        init();
    }

    public void init() {
        mTextureView = findViewById(R.id.textureView);
        cardViewOK = findViewById(R.id.cardViewOK);
        picView = findViewById(R.id.picView);
        carViewDel = findViewById(R.id.cardDel);
        cardViewCapture = findViewById(R.id.cardViewCapture);
        light = findViewById(R.id.light);
        imageFlash = findViewById(R.id.imageFlash);
        cardViewOK.setVisibility(View.GONE);
        picView.setVisibility(View.INVISIBLE);
        carViewDel.setVisibility(View.GONE);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        fileSerialNum = 0;

        //音效初始化
        soundPool = new SoundPool.Builder().setMaxStreams(1).build();
        mSoundId = soundPool.load(this, R.raw.camera, 1);

        try {
            saveFileName = getIntent().getStringExtra("saveFileName");
        } catch (Exception e) {
            e.printStackTrace();
        }


        light.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mFlashMode) {
                    case 0:
                        mFlashMode = 1;
                        imageFlash.setImageDrawable(getDrawable(R.drawable.baseline_flash_on_black_48));
                        break;
                    case 1:
                        mFlashMode = 2;
                        imageFlash.setImageDrawable(getDrawable(R.drawable.baseline_flash_auto_black_48));
                        break;
                    case 2:
                        mFlashMode = 0;
                        imageFlash.setImageDrawable(getDrawable(R.drawable.baseline_flash_off_black_48));
                        break;
                }
                switchFlashMode();
            }
        });

    }


    private void switchFlashMode() {
        // 獲取手機方向
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        // 根據設備方向計算設置照片的方向
        mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
        //自動對焦
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        switch (mFlashMode) {
            case 0: //關閉閃光
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case 1: //啟用閃光
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
                break;
            case 2:  //自動閃光
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
        }

        updatePreview();
    }


    private TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //當SurefaceTexture可用的時候，設置相機參數並打開相機
            setupCamera(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    //設置相機參數
    private void setupCamera(int width, int height) {
        try {
            manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            //遍歷所有相機
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                //此處默認打開後置相機
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                //獲取StreamConfigurationMap，它是管理相機支持的所有輸出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                //根據TextureView的尺寸設置預覽尺寸
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                //獲取相機支持的最大拍照尺寸
                mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                    @Override
                    public int compare(Size lhs, Size rhs) {
                        return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                    }
                });
                //此ImageReader用於拍照所需
                setupImageReader();
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //選擇sizeMap中大於並且最接近width和height的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    //需要一個子線程的looper，因為camera2是全程異步的
    private void startCameraThread() {
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }


    //開啟相機
    private void openCamera() {
        //獲取相機的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //檢查權限
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打開相機，第一個參數指示打開哪個相機，第二個參數stateCallback為相機的狀態回調接口，第三個參數用來確定Callback在哪個線程執行，為null的話就在當前線程執行
            manager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Toast.makeText(MainActivity.this, "相機啟動失敗", Toast.LENGTH_SHORT).show();
        }
    };

    private void updatePreview() {
        try {
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //開啟相機預覽
    private void startPreview() {
        //獲取SurfaceTexture對象用於預覽相機圖像
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        //設置預覽的尺寸
        surfaceTexture.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
        Surface surface = new Surface(surfaceTexture);

        try {
            //獲得圖像信息，參數代表是預覽圖像
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface); //設置頁面預覽的位置
            //創建會話
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            if (null == mCameraDevice) return;
            // 當攝像頭已經準備好時，開始顯示預覽
            mCameraCaptureSession = session;
            mCaptureRequest = mCaptureRequestBuilder.build();
            switchFlashMode();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };


    private void setupImageReader() {
        //2代表ImageReader中最多可以獲取兩幀圖像流
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                final byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                image.close();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Glide.with(MainActivity.this).load(data).into(picView);
                        picView.setVisibility(View.VISIBLE);

                    }
                });

                mCameraHandler.post(new imageSaver(data));
            }
        }, mCameraHandler);
    }


    public void takePicture(View view) {
        try {
            mCameraCaptureSession.stopRepeating();

            progressBar.setVisibility(View.VISIBLE);
            cardViewCapture.setVisibility(View.INVISIBLE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());

            //執行拍照
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mCameraHandler);
            soundPool.play(mSoundId, 1, 1, 1, 0, 1); //播放快門聲
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void savePicture(View view) {
        //finish();
        cardViewOK.setVisibility(View.INVISIBLE);
        picView.setVisibility(View.INVISIBLE);
        carViewDel.setVisibility(View.INVISIBLE);
        cardViewCapture.setVisibility(View.VISIBLE);
        unLockFocus();
    }

    public void exit(View view) {
        onBackPressed();
    }

    public void delPicture(View view) {
        try {
            unLockFocus();
            new File(filePath).delete();
            //Utility.rescanSDCard(this, filePath);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            picView.setImageBitmap(null);
            cardViewOK.setVisibility(View.GONE);
            picView.setVisibility(View.INVISIBLE);
            carViewDel.setVisibility(View.GONE);
            cardViewCapture.setVisibility(View.VISIBLE);
            fileSerialNum--;
        }
    }


    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            try {


                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        carViewDel.setVisibility(View.VISIBLE);
                        cardViewOK.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    private void unLockFocus() {
        try {
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onPause() {

        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        mCameraHandler=null;
        super.onPause();
    }

    //儲存相片
    public class imageSaver implements Runnable {

        private byte[] data;

        imageSaver(byte[] image) {
            data = image;
        }

        @Override
        public void run() {
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "Picture" + File.separator;
            File mImageFile = new File(path);
            if (!mImageFile.exists()) {
                mImageFile.mkdir();
            }
            fileSerialNum++;

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");// HH:mm:ss
            Date date = new Date(System.currentTimeMillis());

            saveFileName=simpleDateFormat.format(date);
            filePath = path + saveFileName + "_" + fileSerialNum + ".jpg";
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(filePath);
                fos.write(data, 0, data.length);
                //Utility.rescanSDCard(MainActivity.this, filePath);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            startCameraThread();
            if (mTextureView.isAvailable()) {
                setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
                openCamera();
            } else {
                mTextureView.setSurfaceTextureListener(mTextureListener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}


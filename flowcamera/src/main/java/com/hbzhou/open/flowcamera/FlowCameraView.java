package com.hbzhou.open.flowcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCapture.OutputFileOptions;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.CameraView;
import androidx.camera.view.video.OnVideoSavedCallback;
import androidx.camera.view.video.OutputFileResults;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.hbzhou.open.flowcamera.listener.ClickListener;
import com.hbzhou.open.flowcamera.listener.FlowCameraListener;
import com.hbzhou.open.flowcamera.listener.OnVideoPlayPrepareListener;
import com.hbzhou.open.flowcamera.listener.TypeListener;
import com.hbzhou.open.flowcamera.util.LogUtil;
import com.hbzhou.open.flowcamera.util.ScreenUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * author hbzhou
 * date 2019/12/16 10:09
 * 自定义WeChat 拍照拍小视频View
 */
public class FlowCameraView extends FrameLayout {
    //闪关灯状态
    private static final int TYPE_FLASH_AUTO = 0x021;
    private static final int TYPE_FLASH_ON = 0x022;
    private static final int TYPE_FLASH_OFF = 0x023;
    private int type_flash = TYPE_FLASH_OFF;

    // 选择拍照 拍视频 或者都有
    public static final int BUTTON_STATE_ONLY_CAPTURE = 0x101;      //只能拍照
    public static final int BUTTON_STATE_ONLY_RECORDER = 0x102;     //只能录像
    public static final int BUTTON_STATE_BOTH = 0x103;
    //回调监听
    private FlowCameraListener flowCameraListener;
    private ClickListener leftClickListener;

    private Context mContext;
    private androidx.camera.view.CameraView mVideoView;
    private ImageView mPhoto;
    private ImageView mSwitchCamera;
    private ImageView mFlashLamp;
    private CaptureLayout mCaptureLayout;
    private MediaPlayer mMediaPlayer;
    private TextureView mTextureView;

    private File videoFile;
    private File photoFile;


    //切换摄像头按钮的参数
    private int iconSrc;        //图标资源
    private int iconLeft;       //左图标
    private int iconRight;      //右图标
    private int duration;      //录制时间
    private long recordTime = 0;

    public FlowCameraView(Context context) {
        this(context, null);
    }

    public FlowCameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowCameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.FlowCameraView, defStyleAttr, 0);
        iconSrc = a.getResourceId(R.styleable.FlowCameraView_iconSrc, R.drawable.ic_camera);
        iconLeft = a.getResourceId(R.styleable.FlowCameraView_iconLeft, 0);
        iconRight = a.getResourceId(R.styleable.FlowCameraView_iconRight, 0);
        duration = a.getInteger(R.styleable.FlowCameraView_duration_max, 10 * 1000);       //没设置默认为10s
        a.recycle();
        initView();
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    public void initView() {
        setWillNotDraw(false);
        View view = LayoutInflater.from(mContext).inflate(R.layout.flow_camera_view, this);
        mVideoView = view.findViewById(R.id.video_preview);
        mTextureView = view.findViewById(R.id.mVideo);
        mPhoto = view.findViewById(R.id.image_photo);
        mSwitchCamera = view.findViewById(R.id.image_switch);
        mSwitchCamera.setImageResource(iconSrc);
        mFlashLamp = view.findViewById(R.id.image_flash);
        setFlashRes();
        mFlashLamp.setOnClickListener(v -> {
            type_flash++;
            if (type_flash > 0x023)
                type_flash = TYPE_FLASH_AUTO;
            setFlashRes();
        });
        mVideoView.enableTorch(true);
        // 设置支持拍照和拍视频
        mVideoView.setCaptureMode(CameraView.CaptureMode.MIXED);

        mCaptureLayout = view.findViewById(R.id.capture_layout);
        mCaptureLayout.setDuration(duration);
        mCaptureLayout.setIconSrc(iconLeft, iconRight);
        //切换摄像头
        mSwitchCamera.setOnClickListener(v -> mVideoView.toggleCamera());
        //拍照 录像
        mCaptureLayout.setCaptureLisenter(new CaptureListener() {
            @Override
            public void takePictures() {
                mSwitchCamera.setVisibility(INVISIBLE);
                mFlashLamp.setVisibility(INVISIBLE);
                //mVideoView.setCaptureMode(CameraView.CaptureMode.IMAGE);

                // 修复了前置摄像头拍照后  预览左右镜像的问题
                Integer lensFacing = mVideoView.getCameraLensFacing();
                if (lensFacing == null) {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                }
                OutputFileOptions.Builder outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile = initTakePicPath(mContext));
                ImageCapture.Metadata metadata = new ImageCapture.Metadata();
                metadata.setReversedHorizontal(CameraSelector.LENS_FACING_FRONT == lensFacing);
                outputFileOptions.setMetadata(metadata);

                //测试新版本 CameraView
                mVideoView.takePicture(outputFileOptions.build(), ContextCompat.getMainExecutor(mContext), new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        if (!photoFile.exists()) {
                            Toast.makeText(mContext, "图片保存出错!", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Glide.with(mContext)
                                .load(photoFile)
                                .into(mPhoto);
                        mPhoto.setVisibility(View.VISIBLE);
                        mCaptureLayout.startTypeBtnAnimator();

                        // If the folder selected is an external media directory, this is unnecessary
                        // but otherwise other apps will not be able to access our images unless we
                        // scan them using [MediaScannerConnection]
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        if (flowCameraListener != null) {
                            flowCameraListener.onError(exception.getImageCaptureError(), Objects.requireNonNull(exception.getMessage()), exception.getCause());
                        }
                    }
                });
            }

            @SuppressLint("UnsafeExperimentalUsageError")
            @Override
            public void recordStart() {
                mSwitchCamera.setVisibility(INVISIBLE);
                mFlashLamp.setVisibility(INVISIBLE);

                mVideoView.startRecording(videoFile = initStartRecordingPath(mContext), ContextCompat.getMainExecutor(mContext), new OnVideoSavedCallback() {
                    @Override
                    public void onVideoSaved(@NonNull OutputFileResults outputFileResults) {
                        if (recordTime < 1500 && videoFile.exists() && videoFile.delete()) {
                            return;
                        }
                        mTextureView.setVisibility(View.VISIBLE);
                        mCaptureLayout.startTypeBtnAnimator();
                        transformsTextureView(mTextureView);
                        if (mTextureView.isAvailable()) {
                            startVideoPlay(videoFile, () ->
                                    mVideoView.setVisibility(View.GONE)
                            );
                        } else {
                            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                                @Override
                                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                                    startVideoPlay(videoFile, () ->
                                            mVideoView.setVisibility(View.GONE)
                                    );
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
                            });
                        }
                    }

                    @Override
                    public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                        if (flowCameraListener != null) {
                            flowCameraListener.onError(videoCaptureError, message, cause);
                        }
                    }
                });

//                mVideoView.startRecording(videoFile = initStartRecordingPath(mContext), ContextCompat.getMainExecutor(mContext), new VideoCapture.OnVideoSavedCallback() {
//                    @Override
//                    public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
//                        if (recordTime < 1500 && videoFile.exists() && videoFile.delete()) {
//                            return;
//                        }
//                        mTextureView.setVisibility(View.VISIBLE);
//                        mCaptureLayout.startTypeBtnAnimator();
//                        transformsTextureView(mTextureView);
//                        if (mTextureView.isAvailable()) {
//                            startVideoPlay(videoFile, () ->
//                                    mVideoView.setVisibility(View.GONE)
//                            );
//                        } else {
//                            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
//                                @Override
//                                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
//                                    startVideoPlay(videoFile, () ->
//                                            mVideoView.setVisibility(View.GONE)
//                                    );
//                                }
//
//                                @Override
//                                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
//
//                                }
//
//                                @Override
//                                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
//                                    return false;
//                                }
//
//                                @Override
//                                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//
//                                }
//                            });
//                        }
//
//                    }
//
//                    @Override
//                    public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
//                        if (flowCameraListener != null) {
//                            flowCameraListener.onError(videoCaptureError, message, cause);
//                        }
//                    }
//                });
            }

            @Override
            public void recordShort(final long time) {
                recordTime = time;
                mSwitchCamera.setVisibility(VISIBLE);
                mFlashLamp.setVisibility(VISIBLE);
                mCaptureLayout.resetCaptureLayout();
                mCaptureLayout.setTextWithAnimation("录制时间过短");
                mVideoView.stopRecording();
            }

            @Override
            public void recordEnd(long time) {
                recordTime = time;
                mVideoView.stopRecording();
            }

            @Override
            public void recordZoom(float zoom) {

            }

            @Override
            public void recordError() {
                if (flowCameraListener != null) {
                    flowCameraListener.onError(0, "未知原因!", null);
                }
            }
        });
        //确认 取消
        mCaptureLayout.setTypeLisenter(new TypeListener() {
            @Override
            public void cancel() {
                stopVideoPlay();
                resetState();
            }

            @Override
            public void confirm() {
                if (videoFile != null && videoFile.exists()) {
                    stopVideoPlay();
                    if (flowCameraListener != null) {
                        flowCameraListener.recordSuccess(videoFile);
                    }
                    scanPhotoAlbum(videoFile);
                } else if (photoFile != null && photoFile.exists()) {
                    mPhoto.setVisibility(INVISIBLE);
                    if (flowCameraListener != null) {
                        flowCameraListener.captureSuccess(photoFile);
                    }
                    scanPhotoAlbum(photoFile);
                }
            }
        });
        mCaptureLayout.setLeftClickListener(() -> {
            if (leftClickListener != null) {
                leftClickListener.onClick();
            }
        });
    }

    /**
     * 预览自拍视频时 旋转TextureView 解决左右镜像的问题
     *
     * @param textureView
     */
    private void transformsTextureView(TextureView textureView) {
        Matrix matrix = new Matrix();
        int screenHeight = ScreenUtils.getScreenHeight(mContext);
        int screenWidth = ScreenUtils.getScreenWidth(mContext);
        int lensFacing = 1;
        if (mVideoView.getCameraLensFacing() != null) {
            lensFacing = mVideoView.getCameraLensFacing();
        }
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1, 1, 1f * screenWidth / 2, 1f * screenHeight / 2);
        } else {
            matrix.postScale(1, 1, 1f * screenWidth / 2, 1f * screenHeight / 2);
        }
        textureView.setTransform(matrix);
    }

    /**
     * 当确认保存此文件时才去扫描相册更新并显示视频和图片
     *
     * @param dataFile
     */
    private void scanPhotoAlbum(File dataFile) {
        if (dataFile == null) {
            return;
        }
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(dataFile.getAbsolutePath().substring(dataFile.getAbsolutePath().lastIndexOf(".") + 1));
        MediaScannerConnection.scanFile(
                mContext, new String[]{dataFile.getAbsolutePath()}, new String[]{mimeType}, null);
    }

    public File initTakePicPath(Context context) {
        return new File(context.getExternalMediaDirs()[0], System.currentTimeMillis() + ".jpeg");
    }

    public File initStartRecordingPath(Context context) {
        return new File(context.getExternalMediaDirs()[0], System.currentTimeMillis() + ".mp4");
    }

    /**************************************************
     * 对外提供的API                     *
     **************************************************/

    public void setFlowCameraListener(FlowCameraListener flowCameraListener) {
        this.flowCameraListener = flowCameraListener;
    }

    // 绑定生命周期 否者界面可能一片黑
    public void setBindToLifecycle(LifecycleOwner lifecycleOwner) {
        if (ActivityCompat.checkSelfPermission((Context) lifecycleOwner, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mVideoView.bindToLifecycle(lifecycleOwner);
        lifecycleOwner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            LogUtil.i("event---", event.toString());
        });
    }

    /**
     * 设置录制视频最大时长单位 s
     */
    public void setRecordVideoMaxTime(int maxDurationTime) {
        mCaptureLayout.setDuration(maxDurationTime * 1000);
    }

    /**
     * 设置拍摄模式分别是
     * 单独拍照 单独摄像 或者都支持
     *
     * @param state
     */
    public void setCaptureMode(int state) {
        if (mCaptureLayout != null) {
            mCaptureLayout.setButtonFeatures(state);
        }
    }

    /**
     * 关闭相机界面按钮
     *
     * @param clickListener
     */
    public void setLeftClickListener(ClickListener clickListener) {
        this.leftClickListener = clickListener;
    }

    private void setFlashRes() {
        switch (type_flash) {
            case TYPE_FLASH_AUTO:
                mFlashLamp.setImageResource(R.drawable.ic_flash_auto);
                mVideoView.setFlash(ImageCapture.FLASH_MODE_AUTO);
                break;
            case TYPE_FLASH_ON:
                mFlashLamp.setImageResource(R.drawable.ic_flash_on);
                mVideoView.setFlash(ImageCapture.FLASH_MODE_ON);
                break;
            case TYPE_FLASH_OFF:
                mFlashLamp.setImageResource(R.drawable.ic_flash_off);
                mVideoView.setFlash(ImageCapture.FLASH_MODE_OFF);
                break;
        }
    }

    /**
     * 重置状态
     */
    @SuppressLint("UnsafeExperimentalUsageError")
    private void resetState() {
        if (mVideoView.isRecording()) {
            mVideoView.stopRecording();
        }
        if (videoFile != null && videoFile.exists() && videoFile.delete()) {
            LogUtil.i("videoFile is clear");
        }

        if (photoFile != null && photoFile.exists() && photoFile.delete()) {
            LogUtil.i("photoFile is clear");
        }
        mPhoto.setVisibility(INVISIBLE);
        mSwitchCamera.setVisibility(VISIBLE);
        mFlashLamp.setVisibility(VISIBLE);
        mVideoView.setVisibility(View.VISIBLE);
        mCaptureLayout.resetCaptureLayout();
    }

    /**
     * 开始循环播放视频
     *
     * @param videoFile
     */
    private void startVideoPlay(File videoFile, OnVideoPlayPrepareListener onVideoPlayPrepareListener) {
        try {
            if (mMediaPlayer == null) {
                mMediaPlayer = new MediaPlayer();
            }
            mMediaPlayer.setDataSource(videoFile.getAbsolutePath());
            mMediaPlayer.setSurface(new Surface(mTextureView.getSurfaceTexture()));
            mMediaPlayer.setLooping(true);
            mMediaPlayer.setOnPreparedListener(mp -> {
                mp.start();

                float ratio = mp.getVideoWidth() * 1f / mp.getVideoHeight();
                int width1 = mTextureView.getWidth();
                ViewGroup.LayoutParams layoutParams = mTextureView.getLayoutParams();
                layoutParams.height = (int) (width1 / ratio);
                mTextureView.setLayoutParams(layoutParams);

                if (onVideoPlayPrepareListener != null) {
                    onVideoPlayPrepareListener.onPrepared();
                }
            });
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止视频播放
     */
    private void stopVideoPlay() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mTextureView.setVisibility(View.GONE);
    }
}

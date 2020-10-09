package com.hbzhou.open.flowcamera

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.hbzhou.open.flowcamera.listener.ClickListener
import com.hbzhou.open.flowcamera.listener.FlowCameraListener
import com.hbzhou.open.flowcamera.listener.OnVideoPlayPrepareListener
import com.hbzhou.open.flowcamera.listener.TypeListener
import com.hbzhou.open.flowcamera.util.LogUtil
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

/**
 * @author hbzhou
 * date 2020/8/3 10:26
 * email 1004695331@qq.com
 */
class FlowCameraView2 : FrameLayout {
    //闪关灯状态
    private val TYPE_FLASH_AUTO = 0x021
    private val TYPE_FLASH_ON = 0x022
    private val TYPE_FLASH_OFF = 0x023
    private var type_flash = TYPE_FLASH_OFF

    // 选择拍照 拍视频 或者都有
    val BUTTON_STATE_ONLY_CAPTURE = 0x101 //只能拍照

    val BUTTON_STATE_ONLY_RECORDER = 0x102 //只能录像

    val BUTTON_STATE_BOTH = 0x103

    //回调监听
    private var flowCameraListener: FlowCameraListener? = null
    private var leftClickListener: ClickListener? = null

    private var mContext: Context? = context

    //    private var mVideoView: PreviewView? = null
    private var mPhoto: ImageView? = null
    private var mSwitchCamera: ImageView? = null
    private var mFlashLamp: ImageView? = null
    private var mCaptureLayout: CaptureLayout? = null
    private var mMediaPlayer: MediaPlayer? = null
    private var mTextureView: TextureView? = null

    private var videoFile: File? = null
    private var photoFile: File? = null


    //切换摄像头按钮的参数
    private var iconSrc //图标资源
            = 0
    private var iconLeft //左图标
            = 0
    private var iconRight //右图标
            = 0
    private var duration //录制时间
            = 0
    private var recordTime: Long = 0


    private lateinit var container: FrameLayout
    private lateinit var viewFinder: PreviewView
    private lateinit var outputDirectory: File
//    private lateinit var broadcastManager: LocalBroadcastManager

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var displayManager: DisplayManager? = null
    private var lifecycleOwner: LifecycleOwner? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attributeSet,
        defStyleAttr
    ) {
        val a = context.theme
            .obtainStyledAttributes(attributeSet, R.styleable.FlowCameraView, defStyleAttr, 0)
        iconSrc = a.getResourceId(R.styleable.FlowCameraView_iconSrc, R.drawable.ic_camera)
        iconLeft = a.getResourceId(R.styleable.FlowCameraView_iconLeft, 0)
        iconRight = a.getResourceId(R.styleable.FlowCameraView_iconRight, 0)
        duration = a.getInteger(R.styleable.FlowCameraView_duration_max, 10 * 1000)
        a.recycle()
        initView()
    }

    companion object {
        private const val TAG = "FlowCameraView2"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.CHINA)
                    .format(System.currentTimeMillis()) + extension
            )
    }

    private fun initView() {
        val view: View =
            View.inflate(mContext, R.layout.flow_camera_view2, this)
        container = view as FrameLayout
        displayManager = mContext?.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        mCaptureLayout = view.findViewById(R.id.capture_layout)
        mCaptureLayout?.setDuration(duration)
        mCaptureLayout?.setIconSrc(iconLeft, iconRight)
        mTextureView = view.findViewById(R.id.mVideo)
        mPhoto = view.findViewById(R.id.image_photo)
        mSwitchCamera = view.findViewById(R.id.image_switch)
        mSwitchCamera?.setImageResource(iconSrc)
        mSwitchCamera?.let {
            // Disable the button until the camera is set up
            it.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            it.setOnClickListener {
                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            }
        }
        mFlashLamp = view.findViewById(R.id.image_flash)
        setFlashRes()
        mFlashLamp?.setOnClickListener {
            type_flash++
            if (type_flash > 0x023)
                type_flash = TYPE_FLASH_AUTO
            setFlashRes()
        }
        // Preview 初始化
        viewFinder = view.findViewById(R.id.video_preview)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Every time the orientation of device changes, update rotation for use cases
        displayManager?.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = getOutputDirectory(mContext!!)

        // Wait for the views to be properly laid out
        viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = viewFinder.display.displayId

            // Set up the camera and its use cases
            setUpCamera()
        }
        mCaptureLayout?.setCaptureLisenter(object : CaptureListener {
            override fun takePictures() {
                mSwitchCamera?.visibility = View.INVISIBLE
                mFlashLamp?.visibility = View.INVISIBLE

                // Get a stable reference of the modifiable image capture use case
                imageCapture?.let { imageCapture ->

                    // Create output file to hold the image
                    photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

                    // Setup image capture metadata
                    val metadata = ImageCapture.Metadata().apply {

                        // Mirror image when using the front camera
                        isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                    }

                    // Create output options object which contains file + metadata
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile!!)
                        .setMetadata(metadata)
                        .build()

                    // Setup image capture listener which is triggered after photo has been taken
                    imageCapture.takePicture(
                        outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                                flowCameraListener?.onError(0, exc.message.toString(), exc.cause)
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                                Log.d(TAG, "Photo capture succeeded: $savedUri")

                                if (!photoFile!!.exists()) {
                                    Toast.makeText(mContext, "图片保存出错!", Toast.LENGTH_LONG).show()
                                    return
                                }
                                mPhoto?.post {
                                    Glide.with(mContext!!)
                                        .load(photoFile)
                                        .into(mPhoto!!)
                                    mPhoto?.visibility = View.VISIBLE
                                    mCaptureLayout?.startTypeBtnAnimator()
                                }

                                // Implicit broadcasts will be ignored for devices running API level >= 24
                                // so if you only target API level 24+ you can remove this statement
//                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
//                                    mContext?.sendBroadcast(
//                                        Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
//                                    )
//                                }
//
//                                // If the folder selected is an external media directory, this is
//                                // unnecessary but otherwise other apps will not be able to access our
//                                // images unless we scan them using [MediaScannerConnection]
//                                val mimeType = MimeTypeMap.getSingleton()
//                                    .getMimeTypeFromExtension(savedUri.toFile().extension)
//                                MediaScannerConnection.scanFile(
//                                    context,
//                                    arrayOf(savedUri.toFile().absolutePath),
//                                    arrayOf(mimeType)
//                                ) { _, uri ->
//                                    Log.d(TAG, "Image capture scanned into media store: $uri")
//                                }
                            }
                        })
                }
            }

            @SuppressLint("RestrictedApi")
            override fun recordShort(time: Long) {
                recordTime = time
                mSwitchCamera?.visibility = View.VISIBLE
                mFlashLamp?.visibility = View.VISIBLE
                mCaptureLayout?.resetCaptureLayout()
                mCaptureLayout?.setTextWithAnimation("录制时间过短")
                videoCapture?.stopRecording()
            }

            @SuppressLint("RestrictedApi")
            override fun recordStart() {
                mSwitchCamera?.visibility = View.INVISIBLE
                mFlashLamp?.visibility = View.INVISIBLE

                videoCapture?.let {
                    // Create output file to hold the image
                    val videoFile1 = createFile(outputDirectory, FILENAME, VIDEO_EXTENSION)

                    // Setup image capture metadata
                    val metadata = VideoCapture.Metadata().apply {

                        // Mirror image when using the front camera
                        //rever = lensFacing == CameraSelector.LENS_FACING_FRONT
                    }

                    // Create output options object which contains file + metadata
                    val outputOptions = VideoCapture.OutputFileOptions.Builder(videoFile1)
                        .setMetadata(metadata)
                        .build()

                    videoCapture?.startRecording(
                        outputOptions,
                        cameraExecutor,
                        object : VideoCapture.OnVideoSavedCallback {
                            override fun onVideoSaved(@NonNull outputFileResults: VideoCapture.OutputFileResults) {
                                videoFile = videoFile1
                                if (recordTime < 1500 && videoFile!!.exists() && videoFile!!.delete()) {
                                    return
                                }

                                // 视频左右镜像处理
//                                val epVideo = EpVideo(file.absolutePath)
//                                epVideo.rotation(0, true)
//                                videoFile =
//                                    createFile(outputDirectory, FILENAME, VIDEO_EXTENSION)
//                                val outputOption =
//                                    EpEditor.OutputOption(videoFile?.absolutePath)
//                                EpEditor.exec(epVideo, outputOption, object : OnEditorListener {
//                                    override fun onSuccess() {

                                mTextureView?.post {
                                    mTextureView?.visibility = View.VISIBLE
                                    mCaptureLayout?.startTypeBtnAnimator()

                                    transformsTextureView(mTextureView!!)

                                    if (mTextureView!!.isAvailable) {
                                        startVideoPlay(
                                            videoFile!!,
                                            object : OnVideoPlayPrepareListener {
                                                override fun onPrepared() {
                                                    viewFinder.visibility = View.GONE
                                                }
                                            }
                                        )
                                    } else {
                                        mTextureView?.surfaceTextureListener = object :
                                            SurfaceTextureListener {
                                            override fun onSurfaceTextureAvailable(
                                                surface: SurfaceTexture,
                                                width: Int,
                                                height: Int
                                            ) {

                                                startVideoPlay(
                                                    videoFile!!,
                                                    object : OnVideoPlayPrepareListener {
                                                        override fun onPrepared() {
                                                            viewFinder.visibility =
                                                                View.GONE
                                                        }
                                                    }
                                                )
                                            }

                                            override fun onSurfaceTextureSizeChanged(
                                                surface: SurfaceTexture,
                                                width: Int,
                                                height: Int
                                            ) {
                                            }

                                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                                return false
                                            }

                                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                        }
                                    }
                                }
//                                    }
//
//                                    override fun onFailure() {
//
//                                    }
//
//                                    override fun onProgress(progress: Float) {
//
//                                    }
//                                })


                            }

                            override fun onError(
                                videoCaptureError: Int,
                                message: String,
                                cause: Throwable?
                            ) {
                                flowCameraListener?.onError(videoCaptureError, message, cause)
                            }
                        })
                }
            }

            @SuppressLint("RestrictedApi")
            override fun recordEnd(time: Long) {
                recordTime = time
                videoCapture?.stopRecording()
            }

            override fun recordZoom(zoom: Float) {

            }

            override fun recordError() {
                flowCameraListener?.onError(0, "未知原因!", null)
            }
        })
        //确认 取消
        mCaptureLayout?.setTypeLisenter(object : TypeListener {
            override fun cancel() {
                stopVideoPlay()
                resetState()
            }

            override fun confirm() {
                if (videoFile != null && videoFile!!.exists()) {
                    stopVideoPlay()
                    if (flowCameraListener != null) {
                        flowCameraListener!!.recordSuccess(videoFile!!)
                    }
                    scanPhotoAlbum(videoFile)
                } else if (photoFile != null && photoFile!!.exists()) {
                    mPhoto?.visibility = View.INVISIBLE
                    if (flowCameraListener != null) {
                        flowCameraListener!!.captureSuccess(photoFile!!)
                    }
                    scanPhotoAlbum(photoFile)
                }
            }
        })
        mCaptureLayout?.setLeftClickListener {
            leftClickListener?.onClick()
        }
    }

    // 自拍时 左右翻转预览视频
    private fun transformsTextureView(textureView: TextureView) {
        val transform = Matrix()
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            transform.postScale(
                -1f,
                1f,
                1f * metrics.widthPixels / 2,
                1f * metrics.heightPixels / 2
            )
        } else {
            transform.postScale(
                1f,
                1f,
                1f * metrics.widthPixels / 2,
                1f * metrics.heightPixels / 2
            )
        }
        textureView.setTransform(transform)
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        try {
            mSwitchCamera?.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            mSwitchCamera?.isEnabled = false
        }
    }

    /** Declare and bind preview, capture and analysis use cases */
    @SuppressLint("RestrictedApi")
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
//            .setCameraSelector(cameraSelector)
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()


        videoCapture = VideoCapture.Builder()
//            .setCameraSelector(cameraSelector)
//            .setDefaultCaptureConfig()
//            .setCameraSelector(cameraSelector)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            // 设置帧率
//            .setVideoFrameRate(25)
//            // 设置bit率 越大视频体积越大
//            .setBitRate(3 * 1024 * 1024)
            .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)

            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                    // Values returned from our analyzer are passed to the attached listener
                    // We log image analysis results here - you should do something useful
                    // instead!
                    Log.d(TAG, "Average luminosity: $luma")
                })
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner!!, cameraSelector, preview, imageCapture, videoCapture
            )
//            cameraProvider.bindToLifecycle()

//            cameraProvider.bindToLifecycle(
//                lifecycleOwner!!, cameraSelector, preview, videoCapture, imageAnalyzer
//            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    fun setBindToLifecycle(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(mContext!!)
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(mContext))
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        @SuppressLint("RestrictedApi")
        override fun onDisplayChanged(displayId: Int) = container.let { view ->
            if (displayId == this@FlowCameraView2.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
                videoCapture?.setTargetRotation(view.display.rotation)
            }
        }
    }

    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    private class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
         * call image.close() on received images when finished using them. Otherwise, new images
         * may not be received or the camera may stall, depending on back pressure setting.
         *
         */
        override fun analyze(image: ImageProxy) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) {
                image.close()
                return
            }

            // Keep track of frames analyzed
            val currentTime = System.currentTimeMillis()
            frameTimestamps.push(currentTime)

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
            val timestampLast = frameTimestamps.peekLast() ?: currentTime
            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

            // Analysis could take an arbitrarily long amount of time
            // Since we are running in a different thread, it won't stall other use cases

            lastAnalyzedTimestamp = frameTimestamps.first

            // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
            val buffer = image.planes[0].buffer

            // Extract image data from callback object
            val data = buffer.toByteArray()

            // Convert the data into an array of pixel values ranging 0-255
            val pixels = data.map { it.toInt() and 0xFF }

            // Compute average luminance for the image
            val luma = pixels.average()

            // Call all listeners with new value
            listeners.forEach { it(luma) }

            image.close()
        }
    }

    /**
     * 当确认保存此文件时才去扫描相册更新并显示视频和图片
     *
     * @param dataFile
     */
    private fun scanPhotoAlbum(dataFile: File?) {
        if (dataFile == null) {
            return
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            dataFile.absolutePath.substring(dataFile.absolutePath.lastIndexOf(".") + 1)
        )
        MediaScannerConnection.scanFile(
            mContext,
            arrayOf(dataFile.absolutePath),
            arrayOf(mimeType),
            null
        )
    }

    fun initTakePicPath(context: Context?): File {
        return File(
            context?.externalMediaDirs?.get(0),
            System.currentTimeMillis().toString() + ".jpeg"
        )
    }

    fun initStartRecordingPath(context: Context?): File? {
        return File(
            context?.externalMediaDirs?.get(0),
            System.currentTimeMillis().toString() + ".mp4"
        )
    }

    private fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, System.currentTimeMillis().toString()).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    /**************************************************
     * 对外提供的API                     *
     */
    fun setFlowCameraListener(flowCameraListener: FlowCameraListener?) {
        this.flowCameraListener = flowCameraListener
    }

    // 绑定生命周期 否者界面可能一片黑
//    fun setBindToLifecycle(lifecycleOwner: LifecycleOwner) {
//        if (ActivityCompat.checkSelfPermission(
//                (lifecycleOwner as Context),
//                Manifest.permission.CAMERA
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return
//        }
//        mVideoView!!.bindToLifecycle(lifecycleOwner)
//        lifecycleOwner.lifecycle
//            .addObserver(LifecycleEventObserver { source: LifecycleOwner?, event: Lifecycle.Event ->
//                LogUtil.i(
//                    "event---",
//                    event.toString()
//                )
//            })
//    }

    /**
     * 设置录制视频最大时长单位 s
     */
    fun setRecordVideoMaxTime(maxDurationTime: Int) {
        mCaptureLayout?.setDuration(maxDurationTime * 1000)
    }

    /**
     * 设置拍摄模式分别是
     * 单独拍照 单独摄像 或者都支持
     *
     * @param state
     */
    fun setCaptureMode(state: Int) {
        mCaptureLayout?.setButtonFeatures(state)
    }

    /**
     * 关闭相机界面按钮
     *
     * @param clickListener
     */
    fun setLeftClickListener(clickListener: ClickListener) {
        leftClickListener = clickListener
    }

    private fun setFlashRes() {
        when (type_flash) {
            TYPE_FLASH_AUTO -> {
                mFlashLamp!!.setImageResource(R.drawable.ic_flash_auto)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            TYPE_FLASH_ON -> {
                mFlashLamp!!.setImageResource(R.drawable.ic_flash_on)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
            }
            TYPE_FLASH_OFF -> {
                mFlashLamp!!.setImageResource(R.drawable.ic_flash_off)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
        }
    }

    /**
     * 重置状态
     */
    @SuppressLint("RestrictedApi")
    private fun resetState() {
        videoCapture?.stopRecording()
        if (videoFile != null && videoFile!!.exists() && videoFile!!.delete()) {
            LogUtil.i("videoFile is clear")
        }
        if (photoFile != null && photoFile!!.exists() && photoFile!!.delete()) {
            LogUtil.i("photoFile is clear")
        }
        mPhoto!!.visibility = View.INVISIBLE
        mSwitchCamera!!.visibility = View.VISIBLE
        mFlashLamp!!.visibility = View.VISIBLE
        viewFinder.visibility = View.VISIBLE
        mCaptureLayout?.resetCaptureLayout()
    }

    /**
     * 开始循环播放视频
     *
     * @param videoFile
     */
    private fun startVideoPlay(
        videoFile: File,
        onVideoPlayPrepareListener: OnVideoPlayPrepareListener?
    ) {
        try {
            if (mMediaPlayer == null) {
                mMediaPlayer = MediaPlayer()
            }
            mMediaPlayer!!.setDataSource(videoFile.absolutePath)
            mMediaPlayer!!.setSurface(Surface(mTextureView!!.surfaceTexture))
            mMediaPlayer!!.isLooping = true
            mMediaPlayer!!.setOnPreparedListener { mp: MediaPlayer ->
                mp.start()
                val ratio = mp.videoWidth * 1f / mp.videoHeight
                val width1 = mTextureView!!.width
                val layoutParams = mTextureView!!.layoutParams
                layoutParams.height = (width1 / ratio).toInt()
                mTextureView!!.layoutParams = layoutParams
                onVideoPlayPrepareListener?.onPrepared()
            }
            mMediaPlayer!!.prepareAsync()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 停止视频播放
     */
    private fun stopVideoPlay() {
        mMediaPlayer?.stop()
        mMediaPlayer?.release()
        mMediaPlayer = null
        mTextureView?.visibility = View.GONE
    }
}
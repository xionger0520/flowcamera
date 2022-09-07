package com.hbzhou.open.flowcamera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.hbzhou.open.flowcamera.listener.ClickListener
import com.hbzhou.open.flowcamera.listener.FlowCameraListener
import com.hbzhou.open.flowcamera.listener.OnVideoPlayPrepareListener
import com.hbzhou.open.flowcamera.listener.TypeListener
import com.hbzhou.open.flowcamera.util.LogUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * @author hb_zhou
 * @date 2022/9/7
 * @email 1004695331@qq.com
 */
class FlowCameraView3 : FrameLayout {
    //闪关灯状态
    private val TYPE_FLASH_AUTO = 0x021
    private val TYPE_FLASH_ON = 0x022
    private val TYPE_FLASH_OFF = 0x023
    private var type_flash = TYPE_FLASH_OFF

    //回调监听
    private var flowCameraListener: FlowCameraListener? = null
    private var leftClickListener: ClickListener? = null

    private var mContext: Context? = context

    //    private var mVideoView: PreviewView? = null
    private var mPhoto: ImageView? = null
    private var mSwitchCamera: ImageView? = null
    private var mFlashLamp: ImageView? = null
    private var mCaptureLayout: CaptureLayout? = null

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

    private var displayId: Int = -1
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var displayManager: DisplayManager? = null
    private var lifecycleOwner: LifecycleOwner? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

//---------------------------------------------------------------------------

    // 新参数
    private val cameraCapabilities = mutableListOf<CameraCapability>()
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var currentRecording: Recording? = null
    private lateinit var recordingState: VideoRecordEvent
    private var mMediaPlayer: MediaPlayer? = null

    private var cameraIndex = 0
    private var qualityIndex = DEFAULT_QUALITY_IDX
    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(mContext!!) }
    private var enumerationDeferred: Deferred<Unit>? = null

    companion object {
        // default Quality selection if no input from UI
        const val DEFAULT_QUALITY_IDX = 0
        val TAG: String = FlowCameraView3::class.java.simpleName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

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

    private fun getCameraSelector(idx: Int): CameraSelector {
        if (cameraCapabilities.size == 0) {
            Log.i(TAG, "Error: This device does not have any camera, bailing out")
            throw Exception("Error: This device does not have any camera, bailing out")
        }
        return (cameraCapabilities[idx % cameraCapabilities.size].camSelector)
    }

    data class CameraCapability(val camSelector: CameraSelector, val qualities: List<Quality>)

    private fun initCamera() {
        enumerationDeferred = lifecycleOwner?.lifecycleScope?.async {

            val provider = ProcessCameraProvider.getInstance(mContext!!).await()
            provider.unbindAll()
            for (camSelector in arrayOf(
                CameraSelector.DEFAULT_BACK_CAMERA,
                CameraSelector.DEFAULT_FRONT_CAMERA
            )) {
                try {
                    // just get the camera.cameraInfo to query capabilities
                    // we are not binding anything here.
                    if (provider.hasCamera(camSelector)) {
                        val camera = provider.bindToLifecycle(lifecycleOwner!!, camSelector)
                        QualitySelector
                            .getSupportedQualities(camera.cameraInfo)
                            .filter { quality ->
                                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                                    .contains(quality)
                            }.also {
                                cameraCapabilities.add(CameraCapability(camSelector, it))
                            }
                    }
                } catch (exc: java.lang.Exception) {
                    Log.e(TAG, "Camera Face $camSelector is not supported")
                }
            }

        }
    }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context,
        attributeSet,
        defStyleAttr) {
        val a = context.theme.obtainStyledAttributes(attributeSet,
            R.styleable.FlowCameraView,
            defStyleAttr,
            0)
        iconSrc = a.getResourceId(R.styleable.FlowCameraView_iconSrc, R.drawable.ic_camera)
        iconLeft = a.getResourceId(R.styleable.FlowCameraView_iconLeft, 0)
        iconRight = a.getResourceId(R.styleable.FlowCameraView_iconRight, 0)
        duration = a.getInteger(R.styleable.FlowCameraView_duration_max, 10 * 1000)
        a.recycle()
        initView()
    }

    private fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, System.currentTimeMillis().toString()).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    private fun initView() {
        val view: View =
            View.inflate(mContext, R.layout.flow_camera_view3, this)
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

                cameraIndex = (cameraIndex + 1) % cameraCapabilities.size
                // camera device change is in effect instantly:
                //   - reset quality selection
                //   - restart preview
                qualityIndex = DEFAULT_QUALITY_IDX

                lifecycleOwner?.lifecycleScope?.launch {
                    bindCameraUseCases()
                }

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

        }

        mCaptureLayout!!.setCaptureLisenter(object : CaptureListener {
            override fun takePictures() {
                mSwitchCamera?.visibility = View.INVISIBLE
                mFlashLamp?.visibility = View.INVISIBLE

                // Get a stable reference of the modifiable image capture use case
                imageCapture?.let { imageCapture ->

                    // Create output file to hold the image
                    photoFile = createFile(outputDirectory,
                        FILENAME,
                        PHOTO_EXTENSION)

                    // Setup image capture metadata
                    val metadata = ImageCapture.Metadata().apply {

                        // Mirror image when using the front camera
                        isReversedHorizontal = (cameraIndex % 2 == 1)

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
                            }
                        })
                }
            }

            override fun recordShort(time: Long) {
                recordTime = time
                mSwitchCamera?.visibility = View.VISIBLE
                mFlashLamp?.visibility = View.VISIBLE
                mCaptureLayout?.resetCaptureLayout()
                mCaptureLayout?.setTextWithAnimation("录制时间过短")
            }

            override fun recordStart() {
                mSwitchCamera?.visibility = View.INVISIBLE
                mFlashLamp?.visibility = View.INVISIBLE
                startRecording()
            }

            override fun recordEnd(time: Long) {
                recordTime = time
                currentRecording?.stop()

                viewFinder.visibility = View.GONE
                mTextureView?.visibility = View.VISIBLE
                mCaptureLayout?.startTypeBtnAnimator()
            }

            override fun recordZoom(zoom: Float) {

            }

            override fun recordError() {
                flowCameraListener?.onError(0, "未知原因!", null)
            }
        })

        mCaptureLayout!!.setTypeLisenter(object : TypeListener {
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

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private suspend fun updateCameraSwitchButton() {
        cameraProvider = ProcessCameraProvider.getInstance(mContext!!).await()
        try {
            mSwitchCamera?.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            mSwitchCamera?.isEnabled = false
        }
    }


    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
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

    private fun startVideoPlayInit() {
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
                TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
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
                    height: Int,
                ) {
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    return false
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    /**
     * 开始循环播放视频
     *
     * @param videoFile
     */
    private fun startVideoPlay(
        videoFile: File,
        onVideoPlayPrepareListener: OnVideoPlayPrepareListener?,
    ) {
        try {
            mMediaPlayer?.stop()
            mMediaPlayer?.release()
            mMediaPlayer = null
            mMediaPlayer = MediaPlayer()
            mMediaPlayer?.setDataSource(videoFile.absolutePath)
            mMediaPlayer?.setSurface(Surface(mTextureView?.surfaceTexture))
            mMediaPlayer?.isLooping = true
            mMediaPlayer?.setOnPreparedListener { mp: MediaPlayer ->
                mp.start()
                val ratio = mp.videoWidth * 1f / mp.videoHeight
                val width1 = mTextureView!!.width
                val layoutParams = mTextureView?.layoutParams
                layoutParams?.height = (width1 / ratio).toInt()
                mTextureView?.layoutParams = layoutParams
                onVideoPlayPrepareListener?.onPrepared()
            }
            mMediaPlayer?.prepareAsync()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 停止视频播放
     */
    private fun stopVideoPlay() {
        currentRecording?.stop()
        mTextureView?.visibility = View.GONE
    }

    /**
     * A helper function to get the captured file location.
     */
    private fun getAbsolutePathFromUri(contentUri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = mContext!!
                .contentResolver
                .query(contentUri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
            if (cursor == null) {
                return null
            }
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(columnIndex)
        } catch (e: RuntimeException) {
            Log.e("VideoViewerFragment", String.format(
                "Failed in getting absolute path for Uri %s with Exception %s",
                contentUri.toString(), e.toString()
            )
            )
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * A helper function to retrieve the captured file size.
     */
    private fun getFileSizeFromUri(contentUri: Uri): Long? {
        val cursor = mContext!!
            .contentResolver
            .query(contentUri, null, null, null, null)
            ?: return null

        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        cursor.moveToFirst()

        cursor.use {
            return it.getLong(sizeIndex)
        }
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
            if (displayId == this@FlowCameraView3.displayId) {

                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
                videoCapture.targetRotation = view.display.rotation
            }
        }
    }

    /**************************************************
     * 对外提供的API                     *
     */
    fun setFlowCameraListener(flowCameraListener: FlowCameraListener?) {
        this.flowCameraListener = flowCameraListener
    }

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


    fun setBindToLifecycle(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner
        initCamera()
        lifecycleOwner.lifecycleScope.launch {
            if (enumerationDeferred != null) {
                enumerationDeferred!!.await()
                enumerationDeferred = null
            }
            updateCameraSwitchButton()
            bindCameraUseCases()
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private suspend fun bindCameraUseCases() {
        val cameraProvider = ProcessCameraProvider.getInstance(mContext!!).await()

        val cameraSelector = getCameraSelector(cameraIndex)

        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val quality = cameraCapabilities[cameraIndex].qualities[qualityIndex]
        val qualitySelector = QualitySelector.from(quality)

        val preview = Preview.Builder()
            .setTargetAspectRatio(quality.getAspectRatio(quality))
            .build().apply {
                setSurfaceProvider(viewFinder.surfaceProvider)
            }

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(quality.getAspectRatio(quality))
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
//            .setTargetRotation()
            .build()

        // build a recorder, which can:
        //   - record video/audio to MediaStore(only shown here), File, ParcelFileDescriptor
        //   - be used create recording(s) (the recording performs recording)
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner!!,
                cameraSelector,
                videoCapture,
                imageCapture,
                preview
            )
        } catch (exc: Exception) {
            // we are on main thread, let's reset the controls on the UI.
            Log.e(TAG, "Use case binding failed", exc)
            //resetUIandState("bindToLifecycle failed: $exc")
            resetState()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = "CameraX-recording-" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA)
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            mContext!!.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // configure Recorder and Start recording to the mediaStoreOutput.
        currentRecording?.stop()
        currentRecording = videoCapture.output
            .prepareRecording(mContext!!, mediaStoreOutput)
            .start(mainThreadExecutor, captureListener)

        Log.i(TAG, "Recording started")
    }

    /**
     * CaptureEvent listener.
     */
    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status)
            recordingState = event

        if (event is VideoRecordEvent.Finalize) {
            // display the captured video
            videoFile = getAbsolutePathFromUri(event.outputResults.outputUri)?.let { File(it) }

            startVideoPlayInit()
        }
    }

    /**
     * 重置状态
     */
    @SuppressLint("RestrictedApi")
    private fun resetState() {
        currentRecording?.stop()
        if (videoFile != null && videoFile!!.exists() && videoFile!!.delete()) {
            LogUtil.e("videoFile is clear")
        }
        if (photoFile != null && photoFile!!.exists() && photoFile!!.delete()) {
            LogUtil.e("photoFile is clear")
        }
        mPhoto!!.visibility = View.INVISIBLE
        mSwitchCamera!!.visibility = View.VISIBLE
        mFlashLamp!!.visibility = View.VISIBLE
        viewFinder.visibility = View.VISIBLE
        mCaptureLayout?.resetCaptureLayout()
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

}
package com.hbzhou.open.CameraView

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ToastUtils
import com.hbzhou.open.flowcamera.FlowCameraView
import com.hbzhou.open.flowcamera.listener.FlowCameraListener
import java.io.File


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val flowCamera = findViewById<FlowCameraView>(R.id.flowCamera)
        flowCamera.setBindToLifecycle(this)
        flowCamera.setFlowCameraListener(object : FlowCameraListener {
            override fun recordSuccess(file: File) {
                Log.e("callBack000---", file.absolutePath)
                ToastUtils.showLong(file.absolutePath)
                finish()
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {

            }

            override fun captureSuccess(file: File) {
                Log.e("callBack123---", file.absolutePath)
                ToastUtils.showLong(file.absolutePath)
                finish()
            }
        })
        //左边按钮点击事件
        //左边按钮点击事件
        flowCamera.setLeftClickListener {
            ToastUtils.showLong("LeftClick")
            finish()
        }
    }
}

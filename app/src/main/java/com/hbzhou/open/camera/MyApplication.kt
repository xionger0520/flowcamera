package com.hbzhou.open.camera

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.blankj.utilcode.util.Utils

/**
 * author hbzhou
 * date 2019/12/16 13:54
 */
class MyApplication : Application()/*, CameraXConfig.Provider*/ {
    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
    }

//    override fun getCameraXConfig(): CameraXConfig {
//        return Camera2Config.defaultConfig()
//    }
}
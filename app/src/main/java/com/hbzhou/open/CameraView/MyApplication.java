package com.hbzhou.open.CameraView;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;

import com.blankj.utilcode.util.Utils;


/**
 * author hbzhou
 * date 2019/12/16 13:54
 */
public class MyApplication extends Application implements CameraXConfig.Provider {
    @Override
    public void onCreate() {
        super.onCreate();
        Utils.init(getApplicationContext());
    }

    @NonNull
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }
}

package com.hbzhou.open.flowcamera.listener;

import android.graphics.Bitmap;

/**
 * author hbzhou
 * date 2019/12/13 10:49
 */
public interface JCameraListener {

    void captureSuccess(Bitmap bitmap);

    void recordSuccess(String url, Bitmap firstFrame);

}

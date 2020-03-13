package com.hbzhou.open.flowcamera.listener;

/**
 * author hbzhou
 * date 2019/12/13 10:49
 */

public interface CaptureListener {
    void takePictures();

    void recordShort(long time);

    void recordStart();

    void recordEnd(long time);

    void recordZoom(float zoom);

    void recordError();
}

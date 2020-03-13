package com.hbzhou.open.flowcamera.view;

import android.graphics.Bitmap;

/**
 * author hbzhou
 * date 2019/12/13 10:49
 */
public interface CameraView {
    void resetState(int type);

    void confirmState(int type);

    void showPicture(Bitmap bitmap, boolean isVertical);

    void playVideo(Bitmap firstFrame, String url);

    void stopVideo();

    void setTip(String tip);

    void startPreviewCallback();

    boolean handlerFoucs(float x, float y);
}

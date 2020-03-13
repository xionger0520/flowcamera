package com.hbzhou.open.flowcamera.state;

import android.view.Surface;
import android.view.SurfaceHolder;

import com.hbzhou.open.flowcamera.CameraInterface;

/**
 * author hbzhou
 * date 2019/12/13 10:49
 */
public interface State {

    void start(SurfaceHolder holder, float screenProp);

    void stop();

    void foucs(float x, float y, CameraInterface.FocusCallback callback);

    void swtich(SurfaceHolder holder, float screenProp);

    void restart();

    void capture();

    void record(Surface surface, float screenProp);

    void stopRecord(boolean isShort, long time);

    void cancle(SurfaceHolder holder, float screenProp);

    void confirm();

    void zoom(float zoom, int type);

    void flash(String mode);
}

package com.hbzhou.open.flowcamera.listener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * author hbzhou
 * date 2019/12/16 12:06
 */
public interface FlowCameraListener {

    void captureSuccess(@NonNull File file);

    void recordSuccess(@NonNull File file);

    void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause);
}

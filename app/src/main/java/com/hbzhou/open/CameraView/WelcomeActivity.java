package com.hbzhou.open.CameraView;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.PermissionUtils;

import java.util.List;


/**
 * author hbzhou
 * date 2019/12/16 14:04
 */
public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_activity);
        findViewById(R.id.btn_start_camerax).setOnClickListener(view -> getPermission());
    }

    public void getPermission() {
        PermissionUtils.permission(PermissionConstants.STORAGE, PermissionConstants.CAMERA, PermissionConstants.MICROPHONE)
                .rationale(shouldRequest -> shouldRequest.again(true))
                .callback(new PermissionUtils.FullCallback() {
                    @Override
                    public void onGranted(List<String> permissionsGranted) {
                        startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                    }

                    @Override
                    public void onDenied(List<String> permissionsDeniedForever,
                                         List<String> permissionsDenied) {
                        if (!permissionsDeniedForever.isEmpty()) {
                            PermissionUtils.launchAppDetailsSettings();
                        }
                    }
                }).request();
    }
}

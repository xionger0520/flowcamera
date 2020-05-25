package com.hbzhou.open.camera

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.PermissionUtils.FullCallback
import com.blankj.utilcode.util.PermissionUtils.OnRationaleListener.ShouldRequest

/**
 * author hbzhou
 * date 2019/12/16 14:04
 */
class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.welcome_activity)
        findViewById<View>(R.id.btn_start_camerax).setOnClickListener { initPermission() }
    }

    private fun initPermission() {
        PermissionUtils.permission(
            PermissionConstants.STORAGE,
            PermissionConstants.CAMERA,
            PermissionConstants.MICROPHONE
        )
            .callback(object : FullCallback {
                override fun onGranted(permissionsGranted: List<String>) {
                    startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                }

                override fun onDenied(
                    permissionsDeniedForever: List<String>,
                    permissionsDenied: List<String>
                ) {
                    if (permissionsDeniedForever.isNotEmpty()) {
                        PermissionUtils.launchAppDetailsSettings()
                    }
                }
            }).request()
    }
}
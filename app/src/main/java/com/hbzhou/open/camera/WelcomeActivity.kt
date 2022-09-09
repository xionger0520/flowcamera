package com.hbzhou.open.camera

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

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
        XXPermissions.with(this)
            .permission(Permission.RECORD_AUDIO)
            .permission(Permission.WRITE_EXTERNAL_STORAGE)
            .permission(Permission.CAMERA)
            // 设置权限请求拦截器（局部设置）
            //.interceptor(new PermissionInterceptor())
            // 设置不触发错误检测机制（局部设置）
//            .unchecked()
            .request(object : OnPermissionCallback {

                override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                    if (!all) {
                        Toast.makeText(this@WelcomeActivity,
                            "获取部分权限成功，但部分权限未正常授予",
                            Toast.LENGTH_SHORT).show()
                        return
                    }
                    Toast.makeText(this@WelcomeActivity, "获取录音存储和相机权限成功!", Toast.LENGTH_SHORT)
                        .show()
                    startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                }

                override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                    if (never) {
                        Toast.makeText(this@WelcomeActivity,
                            "被永久拒绝授权，请手动授予录音存储和相机权限!",
                            Toast.LENGTH_SHORT).show()

                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(this@WelcomeActivity, permissions)
                    } else {
                        Toast.makeText(this@WelcomeActivity, "获取录音存储和相机权限失败!", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            })
    }
}
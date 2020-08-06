# FlowCamera

[![API 21+](https://img.shields.io/badge/API-21%2B-green.svg)](https://jitpack.io/#xionger0520/flowcamera)
[![](https://jitpack.io/v/xionger0520/flowcamera.svg)](https://jitpack.io/#xionger0520/flowcamera)


## 仿微信拍照和拍小视频界面 使用最新的CameraX相机库
## 适配Android Q存储权限 可以点击拍照长按拍摄小视频也可设置只拍照  只拍视频
## 可设置白平衡 HDR 视频拍摄最大时长 闪光灯 手势缩放等
## 用了本库的攻城狮们点个star啊 本是同根生 相煎何太急

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/mmp1.png" width="200"/>

## 使用方法 
### project的build.gradle中添加仓库地址
```xml
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
### module的build.gradle中添加依赖
```xml

dependencies {

	implementation 'com.github.xionger0520:flowcamera:V1.1.5'

}

CameraX需要java8环境

android {
    ...
    compileOptions {
        sourceCompatibility = 1.8
        targetCompatibility = 1.8
    }
}
	
```
### AndroidManifest.xml中添加权限
```xml
    <uses-feature android:name="android.hardware.camera" />
    <uses-feature android:name="android.hardware.camera.autofocus" />
    <uses-feature android:name="android.hardware.location.gps" />

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FLASHLIGHT" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```
  
### 布局文件中添加
```xml
 
基于Camera2 Api的Custom版本，拍照速度更快
<com.hbzhou.open.flowcamera.CustomCameraView
        android:id="@+id/customCamera"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        />
基于CameraX库flow版本,兼容性稳定性进一步增强
<com.hbzhou.open.flowcamera.FlowCameraView
        android:id="@+id/flowCamera"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        />
```
### 开始使用
```xml

在fragment或者activity调用 Android6.0以上系统需要自行动态申请 存储 相机和麦克风权限

使用CustomCameraView按照如下步骤配置 

val flowCamera = findViewById<CustomCameraView>(R.id.flowCamera)
	// 绑定生命周期 您就不用关心Camera的开启和关闭了 不绑定无法预览
        flowCamera.setBindToLifecycle(this)
        // 设置白平衡模式
        flowCamera.setWhiteBalance(WhiteBalance.AUTO)
	// 设置只支持单独拍照拍视频还是都支持
        // BUTTON_STATE_ONLY_CAPTURE  BUTTON_STATE_ONLY_RECORDER  BUTTON_STATE_BOTH
        flowCamera.setCaptureMode(BUTTON_STATE_BOTH)
        // 开启HDR
        flowCamera.setHdrEnable(Hdr.ON)
        // 设置最大可拍摄小视频时长
        flowCamera.setRecordVideoMaxTime(10)
        // 设置拍照或拍视频回调监听
        flowCamera.setFlowCameraListener(object : FlowCameraListener {
            // 录制完成视频文件返回
            override fun recordSuccess(file: File) {
                ToastUtils.showLong(file.absolutePath)
                finish()
            }
            // 操作拍照或录视频出错
            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {

            }
            // 拍照返回
            override fun captureSuccess(file: File) {
                ToastUtils.showLong(file.absolutePath)
                finish()
            }
        })
        //左边按钮点击事件
        flowCamera.setLeftClickListener {
            finish()
        }
```

## 示例截图

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/20191221222458.jpg" width="350"/><img src="https://github.com/xionger0520/flowcamera/blob/master/assets/20191221222518.jpg" width="350"/>



## 作者

如果有其他复杂需求可付费定制

wechat: zhouhaibin8357

1004695331@qq.com

## 如果本开源项目解决了你的小问题  大佬就打个赏呗

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/alipayicon.jpg" width="200"/>

### LICENSE
Copyright 2019 xionger0520

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0
   
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

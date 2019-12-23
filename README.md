# FlowCamera

[![](https://jitpack.io/v/xionger0520/flowcamera.svg)](https://jitpack.io/#xionger0520/flowcamera)

## 仿微信拍照和拍小视频界面 使用最新的CameraX 相机操作api 提升稳定性和兼容性
## 适配Android Q存储权限 可以点击拍照长按拍摄小视频
## 可切换闪光灯开启 自动和关闭模式 可自拍

### ---清晰的分割线---

### 之前项目有个拍照和拍小视频的需求 使用的是这位大佬的项目  非常感谢

https://github.com/CJT2325/CameraView

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/mmp2.jpg" width="200"/>

### 大佬由于未知原因已经很久没更新了 这个库因为使用的是系统相机操作的api 但是在国内大厂对相机的持续优化下... 
### 有不少兼容性和稳定性问题 在AndroidQ系统下就有不少bug 
### Google也意识到了这个问题 于是CameraX就诞生了 目的就是为了提升相机api的易用稳定和兼容性 
### 所以我就用CameraX重写了关于相机操作的代码
### 此项目我会定期维护更新

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

	implementation 'com.github.xionger0520:flowcamera:V1.0.3'

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
<uses-permission android:name="android.permission.FLASHLIGHT" />
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
```
  
### 布局文件中添加
```xml
<com.hbzhou.open.flowcamera.FlowCameraView
        android:id="@+id/flowCamera"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        />
```
### 开始使用
```xml
Application中实现此接口
public class App extends Application implements CameraXConfig.Provider {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @NonNull
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }
}
在fragment或者activity调用 Android6.0以上系统需要自行动态申请 存储 相机和麦克风权限
val flowCamera = findViewById<FlowCameraView>(R.id.flowCamera)
        // 绑定生命周期 您就不用关心Camera的开启和关闭了 不绑定无法预览
        flowCamera.setBindToLifecycle(this)
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

如果有其他的定制需求可以联系我

wechat: zhouhaibin8357

1004695331@qq.com

## 如果本开源项目解决了你的小问题  大佬就打个赏呗

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/alipayicon.jpg" width="200"/>

### LICENSE
Copyright 2019 xionger0520

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0
   
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

# flowcamera
之前项目有个拍照和拍小视频的需求 使用的是这位大佬的项目  非常感谢

https://github.com/CJT2325/CameraView

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/mmp2.jpg" width="200"/>

因为使用的是原生系统相机的api所以有一些兼容性的问题 
在Android Q系统下有不少bug 作者可能也由于太忙已经很久没更新了
鉴于官网宣传CameraX又那么好  于是我就用CameraX重写了底层关于相机操作的代码
此项目我会尽量抽时间修复问题 因为我自己也用

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/mmp1.png" width="200"/>

仿微信拍照和拍小视频界面 使用最新的CameraX 相机操作api 提升稳定性和兼容性
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

implementation 'com.github.xionger0520:flowcamera:V1.0.0'
	
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

xionger0520

1004695331@qq.com

## 如果本开源项目解决了你的小问题  大佬可以赏小的一点

<img src="https://github.com/xionger0520/flowcamera/blob/master/assets/alipayicon.jpg" width="200"/>

### LICENSE
Copyright 2019 xionger0520

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0
   
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

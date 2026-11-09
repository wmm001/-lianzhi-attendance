# Android 版本使用说明

## 安装现成调试包

将 `人脸考勤-Android-debug.apk` 发送到 Android 手机并允许“安装未知应用”后安装。

首次打开后，在登录页展开“服务器设置（首次使用）”：

- 手机和电脑连接同一个 Wi-Fi：填写 `http://电脑局域网IP:8080/api`
- 云端部署：填写 `https://你的域名/api`

不能填写 `127.0.0.1` 或 `localhost`，它们在手机中指向手机自身。

局域网联调时，电脑需要同时运行 Spring Boot 后端和 Flask 人脸服务，并允许防火墙放行对应端口。供同学长期使用时应将后端、数据库与人脸服务部署到云端并启用 HTTPS。

## 重新构建

```powershell
npm install
npm run android:apk
```

生成文件位于：

`android/app/build/outputs/apk/debug/app-debug.apk`

Android 原生工程可通过 `npm run android:open` 使用 Android Studio 打开。正式发布需要在 Android Studio 中配置签名，并生成 release APK 或 AAB。

# 千问微信聊天悬浮窗助手 (QwenWeChatHelper)

通过 GitHub Actions 云端自动编译 Android APK，无需在本地安装 Android Studio。

## 云端打包步骤

1. 在 GitHub 新建一个仓库（Public 或 Private 均可）。
2. 将本压缩包解压后的**所有文件**（注意包含隐藏文件夹 `.github`）上传到仓库根目录。
3. 点击仓库顶部的 **Actions** 标签页，等待 `Build Android APK` 任务运行完成（约 2~3 分钟）。
4. 点进该任务，在页面底部的 **Artifacts** 区域下载 `QwenWeChatHelper-debug-apk`，解压即可得到 `app-debug.apk`。
5. 将 APK 发送到安卓手机安装使用。

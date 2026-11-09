# 后续继续工作清单（2026-10-27）

当前交付目录：`lianzhi-attendance/`。默认后端使用本机 phpStudy MySQL 5.7.26；`scripts/start-demo.ps1` 可依次启动 MySQL、Python、Java 和 Vite。现用数据库是 phpStudy 列表可见的 `lianzhi_attendance`，数据保存在 `D:\phpstudy_pro\Extensions\MySQL5.7.26\data\lianzhi_attendance`，应用账号密码保存在被 Git 忽略的 `backend/config/mysql-local.properties`。

## 已验证

- 后端 24/24 个自动化测试通过，包括员工与场所分配、二维码签名校验、重复签到与签退。
- 前端生产构建通过；浏览器查看了登录、总览、办公场所和员工管理界面。
- OpenCV YuNet + SFace 能从示例人像注册特征，随后识别同一人像；空白图被拒绝。用户本人已通过电脑摄像头录入，并在默认拍照识别模式签到成功。当前演示库因此留有其本人脸部特征和当日考勤记录，不要未经同意清理。
- H2 中的 5 个用户、2 个场所及 2 笔真人验收考勤先迁移到 `attendance_system`，后完整复制到 phpStudy 创建的 `lianzhi_attendance`。14 张表逐表记录数一致，新库上的管理员登录和看板请求通过。原 MySQL 库、H2 文件及 `tmp/attendance-before-phpstudy-registration-20261027.sql` 保留用于回退。phpStudy MySQL 已配置只监听 `127.0.0.1`。

## 下次优先处理

1. 眨眼和左右转头采帧挑战及 MediaPipe FaceLandmarker 模型已接入为可选增强模式；真人验收未完成。默认模式只做真实人脸特征比对，不能防止照片冒用。
2. 清理原项目未接入企业界面的校园课程模块、旧 Android 品牌素材；把内部 `STUDENT` 角色及 `student_id` 字段迁移为企业员工语义。
3. 课件第 4–6、11–15 页的 AI Agent/知识库、Nacos/Redis/Spring Task、地图交互、预测热力图及容器部署等尚未实现；逐项状态见 `docs/需求对照.md`。正式部署前配置专用 JWT 密钥、HTTPS、数据库备份和人脸特征数据保护。

项目基础来源和模型许可保存在根目录 `LICENSE`、`README.md` 以及 `face-recognition/MODEL_LICENSES.md`。

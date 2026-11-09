# 联智 · 企业智能考勤

面向郑州轻工业大学 JavaEE 工程实践“轻工业”实训的企业考勤演示项目。以开源 [face-attendance-system](https://github.com/ratbro1214/face-attendance-system) 为基础，保留其 MIT 许可证，扩展办公场所、员工管理、动态二维码、真实人脸识别和考勤看板。按用户确认的“考勤功能优先”范围开发，未覆盖课件全部模块；差异见 [课程要求对照](docs/需求对照.md)。

## 当前功能

| 优先实现的考勤功能 | 实现位置 | 状态 |
| --- | --- | --- |
| 公司与办公场所、地址及地图坐标 | 管理端“办公场所” | 东一楼和学生第二生活园区 5 号楼已写入高德坐标；地图按钮优先按坐标打开标注 |
| 员工账号、工号、场所分配 | 管理端“员工管理” | 已实现 |
| 动态二维码及 H5 扫码签到 | 场所二维码、手机签到页 | 已完成手机实测，HMAC 签名并在约 2 分钟内过期 |
| 摄像头人脸录入与识别签到 | “人脸考勤”及 Python OpenCV 服务 | 已接入真实 YuNet + SFace 模型；真人摄像头录入及连续两次拍照签到已验收，每次独立留痕 |
| 考勤记录、签到/签退、统计分析 | “考勤记录”“数据概览” | 单笔工时、筛选及 CSV 导出；场所趋势、热力图及近三十天员工工时汇总 |
| 考勤 Agent 问答 | “考勤 Agent” | 考勤问题先按权限查询本地数据，其他主题直接由 `deepseek-flash`（当前 DeepSeek-V4.1-Flash-1017）回答；单次输出有 token 上限 |
| 管理员补签与缺勤预测 | “考勤记录”“数据概览” | 补签保留独立记录、原因和操作人；预测使用历史签到并标明样本不足 |
| Redis、Nacos 与云部署 | `compose.yaml`、`docs/云部署方案.md` | 本机 Redis 缓存预测、Nacos 动态配置已实测；六服务 Compose 在隔离环境构建并启动，HTTPS、缓存和服务注册通过联调；尚未购买云服务器 |

人脸签到默认只拍摄一张正脸，与已录入特征比对，识别结果必须属于当前登录员工。页面可选“增强活体检测”，只需眨眼或张嘴一次，帮助阻挡简单的静态照片。普通模式不能防止照片冒用；增强模式也不能保证识别所有视频回放或高质量翻拍。

## 本地启动（Windows PowerShell）

这台电脑已使用 phpStudy 自带的 MySQL 5.7.26，当前项目库在 `D:\phpstudy_pro\Extensions\MySQL5.7.26\data\lianzhi_attendance`，并显示在 phpStudy“数据库”列表中。一键启动本机演示服务：

```powershell
.\scripts\start-demo.ps1
```

脚本检查并启动 MySQL、Python 人脸服务、Java API 和 Vue 页面。MySQL、Python 和 Java 仅监听本机；Vue 页面监听局域网，手机与电脑在可互访的同一网络时可访问。电脑浏览器打开 `http://localhost:5173`。

若要演示 Redis 与 Nacos，先打开 Docker Desktop，再运行 `.\scripts\start-optional-infra.ps1`，然后启动上述本机服务。新电脑需把 `backend/config/redis-local.properties.example`、`backend/config/nacos-local.properties.example` 分别复制为去掉 `.example` 的本机配置文件；这台电脑已配置。Redis 只监听 `127.0.0.1:6379`，Nacos 控制台在 `http://127.0.0.1:8848/nacos/`。本机开发服务只读取 Nacos 配置；Compose 中的后端还会向 Nacos 注册服务。

如需分别启动或重新安装依赖，需要 JDK 17、Maven 3.9+、Node.js 18+、Python 3.11/3.12：

```powershell
cd face-recognition
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe app_opencv.py
```

```powershell
cd backend
mvn spring-boot:run
```

```powershell
cd frontend
npm ci
npm run dev
```

Python 健康检查地址为 `http://localhost:5000/health`，后端为 `http://localhost:8080/api`。演示账号：管理员 `admin / admin123`；员工 `student1`、`student2`、`student3` 均使用 `password123`。新增员工的初始密码预填为 `password123`，也允许其他 8–20 位、同时包含字母和数字的密码。以上均为公开演示密码，仅适用于本机演示；云部署使用单独的初始密码和 JWT 密钥。主场所与分场所使用用户提供的两条高德定位，详见 [场所定位](docs/场所定位.md)。

手机扫码演示时，可在前端目录用 `npm run dev -- --host 0.0.0.0` 启动服务，并用电脑的局域网 IP（例如 `http://192.168.1.10:5173`）打开管理员页面生成二维码；手机与电脑需处于可互访的网络。正式部署建议使用 HTTPS 域名。

真人摄像头、手机扫码及考勤 Agent 的逐步操作见 [验收步骤](docs/验收步骤.md)。
课件逐项完成情况见 [课程要求对照](docs/需求对照.md)，桌面材料的适用范围见 [老师材料核对](docs/老师材料核对-20261106.md)。

首次人脸签到需要员工在“人脸考勤”页开启摄像头、录入正脸，然后再识别签到。录入和签到照片在 Python 服务提取特征后删除；数值特征保存在 `face-recognition/face_features.json`。手机网页内实时扫一扫要求 HTTPS；局域网 HTTP 演示可直接用手机系统相机扫管理员展示的二维码。二维码链接使用管理员当前访问页面的主机名，本机可通过 `frontend/.env.local` 的 `VITE_QR_PUBLIC_ORIGIN` 指向手机可访问的局域网地址。

## 数据与部署

- 默认 `dev` 配置连接本机 phpStudy MySQL 的 `lianzhi_attendance` 库，保存账号、场所和考勤等业务数据。phpStudy“数据库”页显示该库和专用账号 `lianzhi_app`；密码保存在被 Git 忽略的 `backend/config/mysql-local.properties`。Navicat 若已能进入主界面，可新建 MySQL 连接：地址 `127.0.0.1`、端口 `3306`、用户名 `lianzhi_app`，密码从该本地配置文件读取。此电脑的 Navicat Premium Lite 17 首次打开会要求 Navicat ID 登录；这是 Navicat 自身的注册要求，MySQL 和项目运行不依赖它。Python 人脸服务提取的数值特征仍单独保存在 `face-recognition/face_features.json`，没有迁入 MySQL。
- phpStudy 列表显示的是它登记的数据库项；为避免重建已有业务数据，先在 phpStudy 新建 `lianzhi_attendance`，再从原 `attendance_system` 导入全部 14 张表和现有记录，并将后端切到新库。旧库和迁移前 SQL 备份仍保留。可在项目目录运行 `.\scripts\check-database.ps1` 核对当前库、表名及数量，输出中不会打印数据库密码。
- 项目原 H2 数据文件保留在 `backend/data/`，迁移前备份在工作区 `tmp/attendance-pre-mysql.mv.db`。需要回退时以 `--spring.profiles.active=local` 启动 Java 后端。当前 MySQL 已迁移 5 个用户、2 个场所和 2 笔真人验收考勤记录。
- 新建空 MySQL 库时使用 `database/schema.sql` 和 `database/migration-20261026-enterprise.sql`。`database/MigrateH2ToMySql.java` 是一次性迁移工具，目标表必须为空。老师提供的 `my.txt` 是 MySQL 服务配置，不含建表或业务数据。
- 前端请求默认走 Vite 的 `/api` 代理；独立部署时需要把 `/api` 反代到 Java 服务，或在登录页“服务器地址”中填写可访问的后端地址。
- 本地自动化检查：`cd backend; mvn test`，`cd frontend; npm run build`。

## 项目结构与来源

- `frontend/`：Vue 3、Element Plus、ECharts 页面。
- `backend/`：Spring Boot 2.7、Java 17 API 与 H2/MySQL 数据库。
- `face-recognition/`：Flask、OpenCV 人脸服务；模型文件位于 `models/`，来源及许可见 [MODEL_LICENSES.md](face-recognition/MODEL_LICENSES.md)。
- `database/`：MySQL 初始化及企业表迁移。
- [LICENSE](LICENSE)：基础项目 MIT 许可证。

企业页面已经替换原校园考勤入口。源码中仍有基础项目的课程/学生等旧模块，当前企业界面不使用这些模块；后续若要用于正式交付，可清理并将内部沿用的 `STUDENT` 角色名称改为 `EMPLOYEE`。

# NetworkToolkit Windows x64 v1.0.0

专业中文网络工程师工具箱，目标平台 Windows 10/11 x64，JDK 17，Kotlin + Compose Desktop。

## 首版功能

- 网卡信息读取；静态 IPv4 / 掩码 / 可选网关 / DNS 配置；本机配置方案保存和一键应用
- Ping、Tracert、ARP 扫描（/24 逐地址 ICMP 探测后读取 ARP 缓存）
- DNS 查询、TCP 端口测试、UDP 数据报探测
- Windows 路由表、IPv4 子网计算
- MAC OUI 厂商查询（内置常见 OUI 示例数据，可通过 `src/main/resources/oui.csv` 扩充 IEEE 数据）
- GitHub Releases 版本检查（填写自己的 Releases API 地址，发现新版后手动打开 Release 下载）
- Ed25519 离线设备绑定授权验证

## 构建环境

- Windows 10/11 x64
- JDK 17
- Gradle 8.10 或兼容版本
- 首次构建需要网络下载 Gradle / Maven 依赖

在 PowerShell 中进入项目目录，执行：

```powershell
.\build-windows.ps1
```

也可以执行：

```powershell
gradle packageReleaseExe packageReleaseMsi createDistributable
```

产物位于 `build/compose/binaries/`。

## GitHub Actions 自动构建

1. 将整个目录上传到 GitHub 仓库根目录。
2. 打开 Actions，运行 `NetworkToolkit Windows x64 Build`。
3. 构建完成后在该次 workflow 的 Artifacts 下载 Windows x64 构建产物。
4. 推送 `v1.0.0` 等 tag 可自动创建 GitHub Release。

## 离线授权

许可证格式为 `base64url(payload).base64url(signature)`。payload 为：

`DEVICE_ID|EXPIRY_UTC_MILLIS|NetworkToolkit`

永久授权 expiry 使用 `0`。授权签发工具位于 `tools/LicenseIssuer.java`。

### 重要安全说明

当前源码中的公钥是本项目开发阶段的测试公钥，正式发放前必须由你自己生成新的 Ed25519 密钥对，并将新公钥 DER Base64 写入 `Main.kt` 的 `PUBLIC_KEY_B64`。私钥只保存在你自己的离线授权电脑，绝不能上传 GitHub、放进客户端或发给用户。每次更换公钥后都需要重新构建客户端。

生成密钥（需要 OpenSSL）：

```powershell
openssl genpkey -algorithm ED25519 -out license-private.pem
openssl pkey -in license-private.pem -pubout -out license-public.pem
openssl pkey -pubin -in license-public.pem -outform DER | openssl base64 -A
```

将最后输出的 Base64 字符串替换 `PUBLIC_KEY_B64`。签发工具编译：

```powershell
javac .\tools\LicenseIssuer.java
```

签发许可证：

```powershell
java -cp .\tools LicenseIssuer DEVICE_ID 0 .\license-private.pem
```

期限授权请将第二个参数换成 UTC Unix 毫秒时间戳。将输出的许可证字符串交给对应用户。

## 本机数据

IP 配置方案和许可证保存在用户目录 `.networktoolkit`。卸载软件不会自动删除该目录。

## 重要说明 / 首版限制

- 网卡信息读取、系统诊断依赖 Windows PowerShell / CMD。
- 修改 IP 会触发 UAC 管理员授权提示。请先确认选中的网卡名称和参数；错误配置可能导致网络中断。
- ARP 扫描只扫描用户明确填写的 IPv4 /24 网段；只在自己拥有或获准管理的网络中使用。
- UDP 无连接握手，发送成功不能证明远端 UDP 端口开放。
- MAC 厂商识别当前仅提供 OUI 查询入口，尚未内置完整 IEEE OUI 数据库；需后续加入本地 oui.csv 数据文件。
- 版本更新页支持查询 GitHub Releases 最新 tag 和 Release 地址；首版不静默下载、不自动覆盖安装，用户确认后手动更新。
- Windows 安装包需在 Windows runner 上构建。本 ZIP 是完整源码项目，不是已编译 EXE；本执行环境没有 Windows 打包工具链，不能声称已产出并测试 Windows 安装包。

## 首次拿到项目后的建议顺序

1. 先把项目上传到自己的 GitHub 仓库，运行 Actions，下载 Windows x64 构建产物。
2. 首次测试建议使用虚拟机或备用电脑；不要直接在正在远程连接的电脑上测试 IP 修改功能。
3. 激活正式用户前，先生成自己的 Ed25519 密钥对、替换客户端公钥并重新构建。
4. 在 `src/main/resources/oui.csv` 中追加 `六位十六进制OUI,厂商名称`，重新构建即可更新本地厂商库。
5. 将版本更新页默认 API 地址中的 `OWNER/REPO` 改为自己的 GitHub 仓库路径，再重新构建。

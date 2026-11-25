# NetworkToolkit Windows x64 v1.3.0

专业中文网络工程师工具箱，目标平台 Windows 10/11 x64，JDK 17，Kotlin + Compose Desktop。

## 首版功能

- 仅筛选以太网 / Ethernet 网卡并通过下拉框选择；静态 IPv4 / 掩码 / 可选网关 / DNS 配置；IP 配置方案独立保存，不绑定网卡，应用时才选择当前网卡
- Ping、Tracert、ARP 扫描（/24 逐地址 ICMP 探测后读取 ARP 缓存）
- DNS 查询、TCP/UDP 合并测试页（左侧 TCP、右侧 UDP）
- TCP/UDP 服务端：TCP 客户端连接/断开状态及来源 IP:端口显示；UDP 来源活动状态显示
- Windows 路由表、IPv4 固定长度子网划分计算
- MAC OUI 厂商查询（内置常见 OUI 示例数据，可通过 `src/main/resources/oui.csv` 扩充 IEEE 数据）
- GitHub Releases 更新检查、便携版下载及 SHA-256 完整性校验；创建 v* 标签后由 GitHub Actions 自动构建并发布 Release
- Ed25519 离线设备绑定授权验证

## 界面与网络测试交互

- 窗口缩小时，左侧导航和右侧内容区域支持纵向滚动；TCP/UDP 测试左右分栏在窄窗口下可横向滚动。
- 网络适配器页仅显示名称以“以太网”或“Ethernet”开头的网卡，并使用下拉框选择，不显示 PowerShell 网卡读取执行结果。
- IPv4 子网页输入原始网段（例如 192.168.1.1/24）和目标前缀（例如 /26），计算可划分子网数量，并列出各子网的网络号、子网掩码、可用地址范围、广播地址和可用主机数。
- TCP 服务端持续跟踪每个客户端连接，显示来源 IP:端口及 `[已连接]` / `[断开连接]`。
- UDP 不存在 TCP 式连接状态；界面以最近 15 秒是否收到该来源的数据报显示活动/超时状态。

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
gradle packageReleaseExe createDistributable
```

产物位于 `build/compose/binaries/`。

## GitHub Actions 自动构建

1. 将整个目录上传到 GitHub 仓库根目录。
2. 手动测试构建：打开 Actions，运行 `NetworkToolkit Windows x64 Build and Release` 的 **Run workflow**，构建结果会出现在 Artifacts。
3. 正式发布：将源码版本号（`Main.kt` 的 `APP_VERSION`、`build.gradle.kts` 的 `version` 和 `packageVersion`）统一更新，然后推送对应标签，例如 `v1.3.0`。
4. 推送 `v*` 标签会自动触发 Windows x64 构建，并将 `NetworkToolkit-Setup.exe`、便携版 ZIP 和 `SHA256SUMS.txt` 上传到 GitHub Releases。
5. 软件「版本更新」页面会查询 `wongvince/nettoo1` 的最新 Release；进入该页面时自动检查，也可手动检查。用户确认后可下载便携版并校验 SHA-256。

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
- 版本更新页默认查询 `wongvince/nettoo1`；进入页面时自动检查，支持手动检查、下载便携版 ZIP 和 SHA-256 校验。下载后需退出软件并解压覆盖便携版目录；当前不静默更新，也不自动覆盖正在运行的程序。
- Windows 安装包需在 Windows runner 上构建。本 ZIP 是完整源码项目，不是已编译 EXE；本执行环境没有 Windows 打包工具链，不能声称已产出并测试 Windows 安装包。

## 首次拿到项目后的建议顺序

1. 先把项目上传到自己的 GitHub 仓库，运行 Actions，下载 Windows x64 构建产物。
2. 首次测试建议使用虚拟机或备用电脑；不要直接在正在远程连接的电脑上测试 IP 修改功能。
3. 激活正式用户前，先生成自己的 Ed25519 密钥对、替换客户端公钥并重新构建。
4. 在 `src/main/resources/oui.csv` 中追加 `六位十六进制OUI,厂商名称`，重新构建即可更新本地厂商库。
5. 当前更新地址已配置为 `wongvince/nettoo1`。若迁移仓库，需要同步修改 `DEFAULT_RELEASE_API` 和 `RELEASE_PAGE_URL`。


## Windows x64 产物说明

GitHub Actions 工作流会生成并上传两个独立 Artifact：

- `NetworkToolkit-Windows-Setup`：下载后文件名为 `NetworkToolkit-Setup.exe`。
- `NetworkToolkit-Windows-Portable`：下载后解压 ZIP，运行其中的 `NetworkToolkit.exe`（无需安装）。

安装版和便携版均由 Compose Desktop 的 `jpackage` 打包流程生成；便携版会在 app image 中检查 `runtime` 目录，确保包含随应用分发的 Java Runtime。推送 `v*` 标签时工作流会自动创建 GitHub Release 并上传三个发布资产；手动运行工作流则只生成 Actions Artifacts。


## 1.3.0 离线试用授权（增强本地防重置）

- 首次启动自动开始 24 小时免费试用。
- 试用期内可使用全部功能；到期后所有工具页面锁定，仅保留软件授权页面。
- 继续沿用原有设备码绑定 Ed25519 离线许可证验证，不需要联网。
- 试用状态同时保存在 `%ProgramData%\NetworkToolkit\trial.dat` 和当前 Windows 用户注册表 `HKCU\Software\NetworkToolkit\TrialState`；两份记录必须同时存在且逐字节一致。
- 状态使用 Windows DPAPI `LocalMachine` 保护，并以设备 ID 作为附加熵；客户端不能再通过简单编辑文本文件伪造试用起始时间。
- 首次启动仅在文件与注册表两处都不存在时初始化；升级自 v1.2.0 时会先校验旧版 `trial.dat` 并迁移原始开始时间，保留已消耗的试用时长，不因升级重新获得 24 小时。任一新记录缺失、内容不一致、DPAPI 解密失败、设备 ID 不匹配或时间倒退，均按试用到期处理，不自动补建新记录。
- 每次启动及运行期间定期刷新最后运行时间；24 小时到期后所有工具锁定。原 Ed25519 离线许可证格式、公钥验签与设备绑定授权逻辑保持不变。
- 普通卸载/重装软件不会清除 ProgramData 与注册表记录；删除其中一份不会重置试用，而会触发锁定。切换 Windows 用户时，由于当前用户注册表记录缺失，也会锁定而不是重新开始。
- 安全边界：完全离线客户端无法保证绝对防绕过。拥有本机管理员/系统控制权的人仍可能同时删除两份记录、恢复系统快照/重装 Windows、修改系统时钟并回滚状态，或补丁修改程序。因此这是提高重置成本的本地防护，不是不可破解 DRM。正式商业发行若要做到跨重装/跨用户可靠防重置，需要在线首次登记或硬件/企业级可信计时与服务端策略。

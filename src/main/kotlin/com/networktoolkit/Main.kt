package com.networktoolkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import java.io.File
import java.net.*
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.Executors

private val Navy = Color(0xFF102B49)
private val Panel = Color(0xFF173B5E)
private val Accent = Color(0xFF55A9F5)
private val Light = Color(0xFFEAF2FA)

private const val APP_VERSION = "1.0.0"
private const val DEFAULT_RELEASE_API =
    "https://api.github.com/repos/OWNER/REPO/releases/latest"

private const val PUBLIC_KEY_B64 =
    "MCowBQYDK2VwAyEABLHkBjACjcxTjOw2P6yQUuTOau4hTVzp6/1NgtH9he4="

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NetworkToolkit 网络工程师工具箱 v$APP_VERSION"
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                background = Navy,
                surface = Panel
            )
        ) {
            ToolkitApp()
        }
    }
}

@Composable
private fun ToolkitApp() {
    var activated by remember { mutableStateOf(LicenseManager.isActivated()) }
    var tab by remember {
        mutableStateOf(if (activated) "网络适配器" else "软件授权")
    }

    var licenseText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val tabs = listOf(
        "网络适配器",
        "Ping",
        "Tracert",
        "ARP 扫描",
        "DNS 查询",
        "TCP 测试",
        "UDP 测试",
        "TCP/UDP 服务端",
        "路由表",
        "IPv4 子网",
        "MAC 厂商",
        "版本更新",
        "软件授权"
    )

    Row(Modifier.fillMaxSize().background(Navy)) {
        Column(
            Modifier
                .width(218.dp)
                .fillMaxHeight()
                .background(Color(0xFF0B2036))
                .padding(14.dp)
        ) {
            Text("NETWORK", color = Accent)
            Text(
                "TOOLKIT",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Text("网络工程师工具箱", color = Light)

            Spacer(Modifier.height(22.dp))

            tabs.forEach { item ->
                if (activated || item == "软件授权") {
                    NavigationDrawerItem(
                        label = { Text(item) },
                        selected = tab == item,
                        onClick = { tab = item },
                        modifier = Modifier.padding(vertical = 2.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Panel,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Light
                        )
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Windows x64 · v$APP_VERSION",
                color = Color(0xFF9BB5CE)
            )
            Text(
                if (activated) "授权：已激活" else "授权：未激活",
                color = if (activated) Color(0xFF7FE0A7)
                else Color(0xFFFFC56B)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp)
        ) {
            Text(
                tab,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(14.dp))

            if (!activated && tab != "软件授权") {
                Text(
                    "软件尚未激活，请先完成离线设备绑定授权。",
                    color = Color(0xFFFFC56B)
                )
            } else {
                when (tab) {
                    "软件授权" -> LicensePage(
                        licenseText,
                        { licenseText = it },
                        message,
                        { code ->
                            message = LicenseManager.activate(code)
                            activated = LicenseManager.isActivated()
                            if (activated) tab = "网络适配器"
                        }
                    )

                    "网络适配器" -> AdapterPage()

                    "Ping" -> CommandPage(
                        "目标地址",
                        "192.168.1.1",
                        "执行 Ping"
                    ) {
                        "ping -n 4 -w 1000 $it"
                    }

                    "Tracert" -> CommandPage(
                        "目标地址",
                        "www.example.com",
                        "开始追踪"
                    ) {
                        "tracert -d -h 20 -w 1000 $it"
                    }

                    "ARP 扫描" -> ArpPage()
                    "DNS 查询" -> DnsPage()
                    "TCP 测试" -> TcpPage()
                    "UDP 测试" -> UdpPage()
                    "TCP/UDP 服务端" -> LocalServerPage()
                    "路由表" -> OutputPage("读取 Windows 路由表", "route print")
                    "IPv4 子网" -> SubnetPage()
                    "MAC 厂商" -> MacPage()
                    "版本更新" -> UpdatePage()
                }
            }
        }
    }
}

/* =========================
   授权
   ========================= */

@Composable
private fun LicensePage(
    code: String,
    onCode: (String) -> Unit,
    message: String,
    activate: (String) -> Unit
) {
    val device = remember { LicenseManager.deviceId() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            "离线设备绑定授权",
            "将以下设备识别码提供给授权签发方。授权方签发与本机设备码绑定的许可证。"
        )

        OutlinedTextField(
            device,
            {},
            readOnly = true,
            label = { Text("本机设备识别码") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                java.awt.datatransfer.StringSelection(device),
                null
            )
        }) {
            Text("复制设备识别码")
        }

        OutlinedTextField(
            code,
            onCode,
            label = { Text("许可证内容 / 授权码") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(onClick = { activate(code) }) {
            Text("验证并激活")
        }

        Text(message, color = Color(0xFFFFC56B))

        Text(
            "许可证验证采用 Ed25519 签名。授权私钥只保存在授权签发方。",
            color = Light
        )
    }
}

/* =========================
   网络适配器
   ========================= */

private data class AdapterItem(
    val name: String
)

private data class IpProfile(
    val name: String,
    val alias: String,
    val ip: String,
    val mask: String,
    val gateway: String,
    val dns: String
)

@Composable
private fun AdapterPage() {
    var adapters by remember { mutableStateOf<List<AdapterItem>>(emptyList()) }
    var selected by remember { mutableStateOf("") }

    var output by remember { mutableStateOf("正在读取本机网卡……") }
    var ip by remember { mutableStateOf("") }
    var mask by remember { mutableStateOf("255.255.255.0") }
    var gateway by remember { mutableStateOf("") }
    var dns by remember { mutableStateOf("") }

    var profileName by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf(ProfileStore.load()) }

    val scope = rememberCoroutineScope()

    fun refreshAdapters() {
        scope.launch {
            output = "正在读取网卡……"
            val names = getAdapterNames()

            adapters = names.map { AdapterItem(it) }

            if (selected !in names) {
                selected = names.firstOrNull().orEmpty()
            }

            output = if (selected.isNotBlank()) {
                getAdapterInfo(selected)
            } else {
                "没有读取到网卡。请检查 Windows 网络适配器。"
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAdapters()
    }

    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier
                .weight(1.45f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("自动识别本机网卡", color = Accent)

            Row(verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }

                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selected.ifBlank { "请选择网卡" },
                            color = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        adapters.forEach { adapter ->
                            DropdownMenuItem(
                                text = { Text(adapter.name) },
                                onClick = {
                                    selected = adapter.name
                                    expanded = false
                                    scope.launch {
                                        output = getAdapterInfo(selected)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Button(onClick = { refreshAdapters() }) {
                    Text("刷新")
                }
            }

            Button(
                onClick = {
                    if (selected.isNotBlank()) {
                        scope.launch {
                            output = getAdapterInfo(selected)
                        }
                    }
                }
            ) {
                Text("读取所选网卡信息")
            }

            OutputBox(output)

            Divider(color = Accent)

            Text(
                "静态 IPv4 配置",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )

            Field("IPv4 地址", ip) { ip = it }
            Field("子网掩码", mask) { mask = it }
            Field("默认网关（可留空）", gateway) { gateway = it }
            Field("首选 DNS（可留空）", dns) { dns = it }

            Button(
                onClick = {
                    scope.launch {
                        if (selected.isBlank()) {
                            output = "请先选择网卡。"
                        } else {
                            output = applyStaticIp(
                                selected,
                                ip,
                                mask,
                                gateway,
                                dns
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("应用静态 IP（需要管理员权限）")
            }

            Text(
                "提示：修改 IP 前请确认配置正确。错误的 IP 或网关可能导致网络中断。",
                color = Color(0xFFFFC56B)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "保存的配置方案",
                color = Accent,
                style = MaterialTheme.typography.titleLarge
            )

            Field("方案名称", profileName) {
                profileName = it
            }

            Button(
                onClick = {
                    if (
                        profileName.isNotBlank() &&
                        selected.isNotBlank() &&
                        validIpv4(ip) &&
                        validMask(mask)
                    ) {
                        val profile = IpProfile(
                            profileName,
                            selected,
                            ip,
                            mask,
                            gateway,
                            dns
                        )

                        profiles = profiles + profile
                        ProfileStore.save(profiles)
                        output = "配置方案已保存：${profile.name}"
                    } else {
                        output = "请填写方案名称、有效 IP 和子网掩码。"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存当前配置")
            }

            profiles.forEachIndexed { index, profile ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Panel
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(profile.name, color = Color.White)
                        Text(
                            "${profile.alias}\n${profile.ip} / ${profile.mask}",
                            color = Light
                        )

                        Row {
                            Button(
                                onClick = {
                                    selected = profile.alias
                                    ip = profile.ip
                                    mask = profile.mask
                                    gateway = profile.gateway
                                    dns = profile.dns
                                    scope.launch {
                                        output = applyStaticIp(
                                            profile.alias,
                                            profile.ip,
                                            profile.mask,
                                            profile.gateway,
                                            profile.dns
                                        )
                                    }
                                }
                            ) {
                                Text("一键应用")
                            }

                            Spacer(Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    profiles = profiles.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                    ProfileStore.save(profiles)
                                    output = "已删除配置方案。"
                                }
                            ) {
                                Text("删除")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun getAdapterNames(): List<String> =
    withContext(Dispatchers.IO) {
        val script = """
            Get-NetAdapter |
            Sort-Object ifIndex |
            ForEach-Object { ${'$'}_.Name }
        """.trimIndent()

        runPowerShell(script)
            .lineSequence()
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                !it.startsWith("WARNING", true)
            }
            .distinct()
            .toList()
    }

private suspend fun getAdapterInfo(alias: String): String {
    val safe = alias.replace("'", "''")

    return runPowerShell(
        """
        Get-NetIPConfiguration -InterfaceAlias '$safe' |
        Format-List InterfaceAlias,InterfaceDescription,IPv4Address,IPv4DefaultGateway,DNSServer

        Get-NetAdapter -Name '$safe' |
        Format-List Name,Status,LinkSpeed,MacAddress,InterfaceDescription
        """.trimIndent()
    )
}

private suspend fun applyStaticIp(
    alias: String,
    ip: String,
    mask: String,
    gateway: String,
    dns: String
): String = withContext(Dispatchers.IO) {
    if (
        alias.isBlank() ||
        !validIpv4(ip) ||
        !validMask(mask) ||
        (gateway.isNotBlank() && !validIpv4(gateway)) ||
        (dns.isNotBlank() && !validIpv4(dns))
    ) {
        return@withContext "配置失败：请检查网卡、IP、掩码、网关和 DNS。"
    }

    val prefix = maskToPrefix(mask) ?: return@withContext "子网掩码无效。"
    val safe = alias.replace("'", "''")

    val script = """
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}a = '$safe'

        Set-NetIPInterface -InterfaceAlias ${'$'}a -AddressFamily IPv4 -Dhcp Disabled

        Get-NetIPAddress -InterfaceAlias ${'$'}a -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { ${'$'}_.PrefixOrigin -eq 'Manual' } |
        Remove-NetIPAddress -Confirm:${'$'}false -ErrorAction SilentlyContinue

        New-NetIPAddress -InterfaceAlias ${'$'}a -IPAddress '$ip' -PrefixLength $prefix
    """.trimIndent() +
        if (gateway.isNotBlank()) {
            "\nSet-NetIPInterface -InterfaceAlias ${'$'}a -AddressFamily IPv4 -Dhcp Disabled\n" +
                "Set-NetRoute -InterfaceAlias ${'$'}a -DestinationPrefix '0.0.0.0/0' -NextHop '$gateway' -ErrorAction SilentlyContinue"
        } else {
            ""
        } +
        if (dns.isNotBlank()) {
            "\nSet-DnsClientServerAddress -InterfaceAlias ${'$'}a -ServerAddresses '$dns'"
        } else {
            ""
        }

    try {
        val encoded = Base64.getEncoder().encodeToString(
            script.toByteArray(Charsets.UTF_16LE)
        )

        val elevateScript = """
            ${'$'}p = Start-Process powershell.exe -Verb RunAs -Wait -PassThru -ArgumentList @(
                '-NoProfile',
                '-ExecutionPolicy',
                'Bypass',
                '-EncodedCommand',
                '$encoded'
            )
            if (${'$'}p.ExitCode -ne 0) {
                exit ${'$'}p.ExitCode
            }
        """.trimIndent()

        val result = runPowerShell(elevateScript)

        if (result.contains("Access is denied", true)) {
            "配置失败：权限不足，请使用管理员账户。"
        } else {
            "已提交管理员权限配置请求。\n" +
                "请确认 Windows 用户账户控制（UAC）提示。\n" +
                "完成后请点击刷新，确认 IP 是否已经生效。\n$result"
        }
    } catch (e: Exception) {
        "配置失败：${e.message}"
    }
}

@Composable
private fun OutputPage(title: String, command: String) {
    var output by remember { mutableStateOf("点击按钮读取结果。") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            enabled = !running,
            onClick = {
                scope.launch {
                    running = true
                    output = runCommand(command)
                    running = false
                }
            }
        ) {
            Text(if (running) "正在读取……" else title)
        }
        OutputBox(output)
    }
}

/* =========================
   Ping / Tracert
   ========================= */

@Composable
private fun CommandPage(
    label: String,
    initial: String,
    button: String,
    command: (String) -> String
) {
    var target by remember { mutableStateOf(initial) }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Field(label, target) { target = it }

    Button(
        onClick = {
            scope.launch {
                output = if (validHost(target)) {
                    runCommand(command(target))
                } else {
                    "目标地址格式无效。"
                }
            }
        }
    ) {
        Text(button)
    }

    OutputBox(output)
}

/* =========================
   ARP 扫描
   ========================= */

@Composable
private fun ArpPage() {
    var ip by remember { mutableStateOf("192.168.1.1") }
    var mask by remember { mutableStateOf("255.255.255.0") }
    var output by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Field("本机 / 目标网段 IPv4 地址", ip) { ip = it }
    Field("子网掩码", mask) { mask = it }

    Text(
        "根据 IP 和掩码计算扫描范围。扫描指定网段内的单播地址，不扫描广播地址。",
        color = Light
    )

    Button(
        enabled = !scanning,
        onClick = {
            scope.launch {
                if (!validIpv4(ip) || !validMask(mask)) {
                    output = "IP 地址或子网掩码无效。"
                } else {
                    scanning = true
                    output = scanArpSubnet(ip, mask)
                    scanning = false
                }
            }
        }
    ) {
        Text(if (scanning) "正在扫描……" else "开始 ARP 扫描")
    }

    OutputBox(output)
}

private suspend fun scanArpSubnet(
    ip: String,
    mask: String
): String = withContext(Dispatchers.IO) {
    val prefix = maskToPrefix(mask) ?: return@withContext "掩码无效。"

    val ipNum = ipv4ToLong(ip)
    val maskNum = ipv4ToLong(mask)
    val network = ipNum and maskNum
    val broadcast = network or (maskNum xor 0xFFFFFFFFL)

    val start = if (prefix >= 31) network else network + 1
    val end = if (prefix >= 31) broadcast else broadcast - 1

    val count = end - start + 1

    if (count <= 0) return@withContext "没有可扫描的地址。"
    if (count > 4096) {
        return@withContext "扫描范围过大：$count 个地址。\n请使用更小的子网掩码范围。"
    }

    val alive = java.util.Collections.synchronizedList(
        mutableListOf<String>()
    )

    val executor = Executors.newFixedThreadPool(32)

    try {
        val tasks = (start..end).map { address ->
            executor.submit {
                val target = longToIpv4(address)

                try {
                    val process = ProcessBuilder(
                        "ping.exe",
                        "-n", "1",
                        "-w", "350",
                        target
                    ).redirectErrorStream(true).start()

                    val response = process.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .readText()

                    val exit = process.waitFor()

                    if (
                        exit == 0 &&
                        response.contains("TTL=", true)
                    ) {
                        alive.add(target)
                    }
                } catch (_: Exception) {
                }
            }
        }

        tasks.forEach { it.get() }

        val arp = runCommand("arp -a")

        val result = buildString {
            appendLine("扫描网段：$ip / $mask")
            appendLine("CIDR：/$prefix")
            appendLine("扫描地址数量：$count")
            appendLine("发现响应设备：${alive.size}")
            appendLine()

            appendLine("响应设备：")
            alive.sorted().forEach {
                appendLine(it)
            }

            appendLine()
            appendLine("Windows ARP 表：")
            append(arp)
        }

        result
    } finally {
        executor.shutdownNow()
    }
}

/* =========================
   DNS
   ========================= */

@Composable
private fun DnsPage() {
    var host by remember { mutableStateOf("example.com") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Field("域名", host) { host = it }

    Button(
        onClick = {
            scope.launch {
                output = if (validHost(host)) {
                    runCommand("nslookup $host")
                } else {
                    "域名格式无效。"
                }
            }
        }
    ) {
        Text("查询 DNS")
    }

    OutputBox(output)
}

/* =========================
   TCP 连接测试
   ========================= */

@Composable
private fun TcpPage() {
    var host by remember { mutableStateOf("192.168.1.1") }
    var port by remember { mutableStateOf("80") }
    var output by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Field("目标 IP / 域名", host) { host = it }
    Field("TCP 端口", port) { port = it }

    Button(
        enabled = !testing,
        onClick = {
            scope.launch {
                val p = port.toIntOrNull()

                if (!validHost(host) || p == null || p !in 1..65535) {
                    output = "主机地址或端口格式无效。"
                } else {
                    testing = true
                    output = tcpConnectTest(host, p)
                    testing = false
                }
            }
        }
    ) {
        Text(if (testing) "正在连接……" else "立即测试 TCP")
    }

    OutputBox(output)
}

private suspend fun tcpConnectTest(
    host: String,
    port: Int
): String = withContext(Dispatchers.IO) {
    val start = System.nanoTime()

    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 1500)
        }

        val ms = (System.nanoTime() - start) / 1_000_000

        "TCP 连接成功\n目标：$host:$port\n耗时：${ms} ms"
    } catch (e: Exception) {
        val ms = (System.nanoTime() - start) / 1_000_000

        "TCP 连接失败\n目标：$host:$port\n耗时：${ms} ms\n原因：${e.message}"
    }
}

/* =========================
   UDP 探测
   ========================= */

@Composable
private fun UdpPage() {
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("53") }
    var output by remember {
        mutableStateOf("UDP 没有通用握手，发送成功不代表远端服务可用。")
    }

    val scope = rememberCoroutineScope()

    Field("目标主机", host) { host = it }
    Field("UDP 端口", port) { port = it }

    Button(
        onClick = {
            scope.launch {
                val p = port.toIntOrNull() ?: 0
                output = if (validHost(host)) {
                    udpProbe(host, p)
                } else {
                    "目标主机格式无效。"
                }
            }
        }
    ) {
        Text("发送 UDP 探测数据")
    }

    OutputBox(output)
}

private suspend fun udpProbe(host: String, port: Int): String =
    withContext(Dispatchers.IO) {
        if (port !in 1..65535) {
            return@withContext "端口范围必须是 1-65535。"
        }

        try {
            DatagramSocket().use { socket ->
                val data = "NetworkToolkit UDP probe".toByteArray()
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(host),
                    port
                )
                socket.send(packet)
            }

            "UDP 数据报已发送至 $host:$port。\n" +
                "注意：UDP 不建立 TCP 式连接，发送成功不能证明远端端口开放。"
        } catch (e: Exception) {
            "UDP 探测失败：${e.message}"
        }
    }

/* =========================
   TCP / UDP 模拟服务端
   ========================= */

@Composable
private fun LocalServerPage() {
    var protocol by remember { mutableStateOf("TCP") }
    var portText by remember { mutableStateOf("5566") }

    var status by remember { mutableStateOf("未启动") }
    var output by remember {
        mutableStateOf("输入端口并启动服务端。")
    }

    var tcpServer by remember { mutableStateOf<ServerSocket?>(null) }
    var udpServer by remember { mutableStateOf<DatagramSocket?>(null) }
    var serverJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()

    fun stopServer() {
        try {
            tcpServer?.close()
        } catch (_: Exception) {
        }

        try {
            udpServer?.close()
        } catch (_: Exception) {
        }

        serverJob?.cancel()

        tcpServer = null
        udpServer = null
        serverJob = null

        status = "已停止"
        output += "\n服务端已停止。"
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                tcpServer?.close()
                udpServer?.close()
                serverJob?.cancel()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "本机 TCP / UDP 模拟服务端",
            color = Accent,
            style = MaterialTheme.typography.titleLarge
        )

        Field("监听端口", portText) { portText = it }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = protocol == "TCP",
                onClick = { protocol = "TCP" },
                label = { Text("TCP") }
            )

            FilterChip(
                selected = protocol == "UDP",
                onClick = { protocol = "UDP" },
                label = { Text("UDP") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = status != "运行中",
                onClick = {
                    val listenPort = portText.toIntOrNull()

                    if (listenPort == null || listenPort !in 1..65535) {
                        output = "端口必须在 1-65535 之间。"
                    } else {
                        serverJob = scope.launch(Dispatchers.IO) {
                            try {
                                if (protocol == "TCP") {
                                    val server = ServerSocket()
                                    server.reuseAddress = true
                                    server.bind(
                                        InetSocketAddress("0.0.0.0", listenPort)
                                    )

                                    withContext(Dispatchers.Main) {
                                        tcpServer = server
                                        status = "运行中"
                                        output = "TCP 服务端启动成功。\n监听地址：0.0.0.0:$listenPort"
                                    }

                                    while (isActive) {
                                        val client = server.accept()

                                        launch(Dispatchers.IO) {
                                            client.use { s ->
                                                s.soTimeout = 1500

                                                val peer = s.inetAddress.hostAddress
                                                val data = try {
                                                    val buffer = ByteArray(1024)
                                                    val length = s.getInputStream()
                                                        .read(buffer)
                                                    if (length > 0) {
                                                        String(buffer, 0, length, Charsets.UTF_8)
                                                    } else {
                                                        "(客户端未发送数据)"
                                                    }
                                                } catch (_: Exception) {
                                                    "(客户端已连接，未读取到数据)"
                                                }

                                                withContext(Dispatchers.Main) {
                                                    output += "\n\nTCP 客户端：$peer"
                                                    output += "\n收到数据：$data"
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val server = DatagramSocket(
                                        InetSocketAddress("0.0.0.0", listenPort)
                                    )

                                    withContext(Dispatchers.Main) {
                                        udpServer = server
                                        status = "运行中"
                                        output = "UDP 服务端启动成功。\n监听地址：0.0.0.0:$listenPort"
                                    }

                                    while (isActive) {
                                        val buffer = ByteArray(4096)
                                        val packet = DatagramPacket(
                                            buffer,
                                            buffer.size
                                        )

                                        server.receive(packet)

                                        val source = "${packet.address.hostAddress}:${packet.port}"
                                        val data = String(
                                            packet.data,
                                            packet.offset,
                                            packet.length,
                                            Charsets.UTF_8
                                        )

                                        withContext(Dispatchers.Main) {
                                            output += "\n\nUDP 来源：$source"
                                            output += "\n收到数据：$data"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    status = "已停止"
                                    output += "\n服务端异常：${e.message}"
                                }
                            }
                        }
                    }
                }
            ) {
                Text("启动服务端")
            }

            Button(
                onClick = { stopServer() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9B3434)
                )
            ) {
                Text("停止服务端")
            }
        }

        Text("当前状态：$status", color = Color(0xFF7FE0A7))

        Text(
            "监听地址为 0.0.0.0，表示监听本机所有 IPv4 网卡。\n" +
                "TCP 会记录连接来源和接收的数据；UDP 会记录数据报来源和内容。\n" +
                "请勿在不可信网络中开放不必要的监听端口。",
            color = Light
        )

        OutputBox(output)
    }
}

/* =========================
   IPv4 子网计算
   ========================= */

@Composable
private fun SubnetPage() {
    var ip by remember { mutableStateOf("192.168.1.10") }
    var cidr by remember { mutableStateOf("24") }
    var output by remember { mutableStateOf("") }

    Field("IPv4 地址", ip) { ip = it }
    Field("前缀长度（0-32）", cidr) { cidr = it }

    Button(
        onClick = {
            output = subnetInfo(ip, cidr.toIntOrNull() ?: -1)
        }
    ) {
        Text("计算")
    }

    OutputBox(output)
}

private fun subnetInfo(ip: String, prefix: Int): String {
    if (!validIpv4(ip) || prefix !in 0..32) {
        return "IPv4 地址或前缀长度无效。"
    }

    val n = ipv4ToLong(ip)
    val mask = if (prefix == 0) {
        0L
    } else {
        (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    }

    val network = n and mask
    val broadcast = network or (mask xor 0xFFFFFFFFL)

    val usable = if (prefix >= 31) 0 else {
        (1L shl (32 - prefix)) - 2
    }

    return """
        网络地址：${longToIpv4(network)}
        广播地址：${longToIpv4(broadcast)}
        子网掩码：${longToIpv4(mask)}
        可用地址数量：$usable
        首个可用地址：${if (prefix >= 31) "无" else longToIpv4(network + 1)}
        最后可用地址：${if (prefix >= 31) "无" else longToIpv4(broadcast - 1)}
    """.trimIndent()
}

/* =========================
   MAC 厂商
   ========================= */

@Composable
private fun MacPage() {
    var mac by remember { mutableStateOf("") }
    var output by remember {
        mutableStateOf("内置常见厂商 OUI 示例库。")
    }

    Field("MAC 地址", mac) { mac = it }

    Button(
        onClick = { output = MacVendor.lookup(mac) }
    ) {
        Text("查询厂商")
    }

    OutputBox(output)
}

private object MacVendor {
    private val vendors = mapOf(
        "000C29" to "VMware, Inc.",
        "001C42" to "Apple, Inc.",
        "001B63" to "Apple, Inc.",
        "F4F5D8" to "Apple, Inc.",
        "3C5AB4" to "Apple, Inc.",
        "001A11" to "Google, Inc.",
        "001B21" to "Intel Corporate",
        "001D7E" to "Hewlett Packard",
        "001E68" to "Dell Inc.",
        "001C23" to "Cisco Systems, Inc.",
        "001B0C" to "Cisco Systems, Inc.",
        "001D09" to "Cisco Systems, Inc.",
        "001A2B" to "Huawei Technologies",
        "00259E" to "Huawei Technologies",
        "001E10" to "TP-Link Technologies",
        "001D0F" to "TP-Link Technologies",
        "C0A0BB" to "TP-Link Technologies",
        "001E58" to "ASUSTek Computer",
        "001F1F" to "ASUSTek Computer",
        "001D60" to "Samsung Electronics",
        "001E7D" to "Samsung Electronics"
    )

    fun lookup(mac: String): String {
        val hex = mac.filter { it.isLetterOrDigit() }.uppercase()

        if (
            hex.length != 12 ||
            !hex.all { it in "0123456789ABCDEF" }
        ) {
            return "MAC 地址格式无效。示例：00:1A:2B:3C:4D:5E"
        }

        val oui = hex.take(6)

        val external = try {
            MacVendor::class.java.getResourceAsStream("/oui.csv")
                ?.bufferedReader()
                ?.useLines { lines ->
                    lines.map { it.split(",", limit = 2) }
                        .firstOrNull {
                            it.size == 2 &&
                                it[0].trim().equals(oui, true)
                        }
                        ?.get(1)
                        ?.trim()
                }
        } catch (_: Exception) {
            null
        }

        val vendor = external ?: vendors[oui]
            ?: "未在本地 OUI 数据库中找到。"

        return """
            MAC 地址：${hex.chunked(2).joinToString(":")}
            OUI：${oui.chunked(2).joinToString(":")}
            厂商：$vendor
        """.trimIndent()
    }
}

/* =========================
   版本更新
   ========================= */

@Composable
private fun UpdatePage() {
    var api by remember { mutableStateOf(DEFAULT_RELEASE_API) }

    var result by remember {
        mutableStateOf(
            "当前版本：v$APP_VERSION\n请填写自己的 GitHub Releases API 地址。"
        )
    }

    val scope = rememberCoroutineScope()

    Field("GitHub Releases API 地址", api) { api = it }

    Button(
        onClick = {
            scope.launch {
                result = checkLatestRelease(api)
            }
        }
    ) {
        Text("检查更新")
    }

    OutputBox(result)

    Text(
        "只检查版本，不会静默下载或安装。",
        color = Light
    )
}

private suspend fun checkLatestRelease(api: String): String =
    withContext(Dispatchers.IO) {
        try {
            val connection = URL(api).openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
            )
            connection.setRequestProperty(
                "User-Agent",
                "NetworkToolkit/$APP_VERSION"
            )

            val body = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .readText()

            connection.disconnect()

            val tag = Regex(
                "\"tag_name\"\\s*:\\s*\"([^\"]+)\""
            ).find(body)?.groupValues?.get(1)
                ?: return@withContext "无法读取 tag_name。请检查 API 地址。"

            val html = Regex(
                "\"html_url\"\\s*:\\s*\"([^\"]+)\""
            ).find(body)?.groupValues?.get(1).orEmpty()

            if (tag.trimStart('v') == APP_VERSION) {
                "当前已是最新版本：$tag"
            } else {
                "发现新版本：$tag\n当前版本：v$APP_VERSION\nRelease：$html"
            }
        } catch (e: Exception) {
            "检查失败：${e.message}"
        }
    }

/* =========================
   通用命令执行
   ========================= */

private suspend fun runCommand(command: String): String =
    withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "cmd.exe",
                "/c",
                "chcp 65001>nul & $command"
            )
                .redirectErrorStream(true)
                .start()

            val text = process.inputStream
                .bufferedReader(Charsets.UTF_8)
                .readText()

            process.waitFor()

            text.ifBlank { "命令已执行，无输出。" }
        } catch (e: Exception) {
            "执行失败：${e.message}"
        }
    }

private suspend fun runPowerShell(script: String): String =
    withContext(Dispatchers.IO) {
        try {
            val fullScript = """
                [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding
                ${'$'}OutputEncoding = New-Object System.Text.UTF8Encoding
                $script
            """.trimIndent()

            val encoded = Base64.getEncoder().encodeToString(
                fullScript.toByteArray(Charsets.UTF_16LE)
            )

            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-EncodedCommand",
                encoded
            )
                .redirectErrorStream(true)
                .start()

            val text = process.inputStream
                .bufferedReader(Charsets.UTF_8)
                .readText()

            process.waitFor()

            text.ifBlank { "命令已执行，无输出。" }
        } catch (e: Exception) {
            "执行失败：${e.message}"
        }
    }

/* =========================
   输入组件
   ========================= */

@Composable
private fun Field(
    label: String,
    value: String,
    change: (String) -> Unit
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun OutputBox(text: String) {
    OutlinedTextField(
        value = text,
        onValueChange = {},
        readOnly = true,
        label = { Text("执行结果") },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp, max = 360.dp),
        minLines = 5
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Text(body, color = Light)
        }
    }
}

/* =========================
   IP / 掩码工具
   ========================= */

private fun validIpv4(value: String): Boolean {
    return try {
        val parts = value.split(".")
        parts.size == 4 && parts.all {
            it.isNotEmpty() &&
                it.toInt() in 0..255
        }
    } catch (_: Exception) {
        false
    }
}

private fun validMask(value: String): Boolean =
    maskToPrefix(value) != null

private fun maskToPrefix(value: String): Int? {
    if (!validIpv4(value)) return null

    val bits = value.split(".")
        .joinToString("") {
            it.toInt().toString(2).padStart(8, '0')
        }

    if (!Regex("1*0*").matches(bits)) return null

    return bits.count { it == '1' }
}

private fun ipv4ToLong(value: String): Long {
    return value.split(".").fold(0L) { result, part ->
        (result shl 8) or part.toLong()
    }
}

private fun longToIpv4(value: Long): String {
    return (0..3).joinToString(".") {
        ((value shr (24 - 8 * it)) and 255).toString()
    }
}

private fun validHost(value: String): Boolean {
    if (value.isBlank() || value.length > 253) return false

    if (validIpv4(value)) return true

    return value.matches(
        Regex(
            "(?i)^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$"
        )
    )
}

/* =========================
   配置方案存储
   ========================= */

private object ProfileStore {
    private val file = File(
        System.getProperty("user.home"),
        ".networktoolkit/profiles.tsv"
    )

    fun load(): List<IpProfile> {
        return try {
            file.readLines().mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size == 6) {
                    IpProfile(
                        parts[0],
                        parts[1],
                        parts[2],
                        parts[3],
                        parts[4],
                        parts[5]
                    )
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<IpProfile>) {
        file.parentFile.mkdirs()

        file.writeText(
            list.joinToString("\n") {
                listOf(
                    it.name,
                    it.alias,
                    it.ip,
                    it.mask,
                    it.gateway,
                    it.dns
                ).joinToString("\t")
            },
            Charsets.UTF_8
        )
    }
}

/* =========================
   授权管理
   ========================= */

private object LicenseManager {
    private val file = File(
        System.getProperty("user.home"),
        ".networktoolkit/license.txt"
    )

    fun deviceId(): String {
        val machineGuid = try {
            val process = ProcessBuilder(
                "reg.exe",
                "query",
                "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                "/v",
                "MachineGuid"
            ).redirectErrorStream(true).start()

            process.inputStream.bufferedReader().readText()
                .lines()
                .lastOrNull { it.contains("MachineGuid") }
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.lastOrNull().orEmpty()
        } catch (_: Exception) {
            ""
        }

        val biosUuid = try {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "(Get-CimInstance Win32_ComputerSystemProduct).UUID"
            ).redirectErrorStream(true).start()

            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) {
            ""
        }

        val raw = listOf(machineGuid, biosUuid)
            .filter { it.isNotBlank() }
            .joinToString("|")
            .ifBlank {
                System.getenv("COMPUTERNAME") ?: "UNKNOWN-PC"
            }

        return java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02X".format(it) }
            .chunked(4)
            .take(6)
            .joinToString("-")
    }

    fun isActivated(): Boolean =
        try {
            verify(file.readText().trim())
        } catch (_: Exception) {
            false
        }

    fun activate(value: String): String =
        try {
            if (!verify(value.trim())) {
                "授权无效：签名、设备码或许可证格式不正确。"
            } else {
                file.parentFile.mkdirs()
                file.writeText(value.trim())
                "激活成功。"
            }
        } catch (e: Exception) {
            "授权验证失败：${e.message}"
        }

    private fun verify(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 2) return false

        val payload = String(
            Base64.getUrlDecoder().decode(parts[0]),
            StandardCharsets.UTF_8
        )

        val fields = payload.split("|")
        if (fields.size != 3 || fields[0] != deviceId()) {
            return false
        }

        val expiry = fields[1].toLongOrNull() ?: return false

        if (expiry != 0L && System.currentTimeMillis() > expiry) {
            return false
        }

        val publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(
                X509EncodedKeySpec(
                    Base64.getDecoder().decode(PUBLIC_KEY_B64)
                )
            )

        val signature = Signature.getInstance("Ed25519")
        signature.initVerify(publicKey)
        signature.update(parts[0].toByteArray(Charsets.US_ASCII))

        return signature.verify(
            Base64.getUrlDecoder().decode(parts[1])
        )
    }
}

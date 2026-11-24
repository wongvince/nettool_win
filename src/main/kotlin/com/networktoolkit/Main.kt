package com.networktoolkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

private val Navy = Color(0xFF102B49)
private val Panel = Color(0xFF173B5E)
private val Accent = Color(0xFF55A9F5)
private val Light = Color(0xFFEAF2FA)
private const val APP_VERSION = "1.0.0"
private const val DEFAULT_RELEASE_API = "https://api.github.com/repos/OWNER/REPO/releases/latest"
private const val PUBLIC_KEY_B64 = "MCowBQYDK2VwAyEAmFNDAHIMRLXfW6emVdWQLmsHpLILXph/fZq1FC4pUyk="

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "NetworkToolkit 网络工程师工具箱 v1.0.0") {
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy, surface = Panel)) {
            ToolkitApp()
        }
    }
}

@Composable
private fun ToolkitApp() {
    var activated by remember { mutableStateOf(LicenseManager.isActivated()) }
    var tab by remember { mutableStateOf(if (activated) "网络适配器" else "软件授权") }
    var licenseText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val tabs = listOf("网络适配器", "Ping", "Tracert", "ARP 扫描", "DNS 查询", "TCP 测试", "UDP 测试", "路由表", "IPv4 子网", "MAC 厂商", "版本更新", "软件授权")
    Row(Modifier.fillMaxSize().background(Navy)) {
        Column(Modifier.width(218.dp).fillMaxHeight().background(Color(0xFF0B2036)).padding(14.dp)) {
            Text("NETWORK", color = Accent, style = MaterialTheme.typography.labelLarge)
            Text("TOOLKIT", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp)); Text("网络工程师工具箱", color = Light)
            Spacer(Modifier.height(22.dp))
            tabs.forEach { item ->
                if (activated || item == "软件授权") {
                    NavigationDrawerItem(label = { Text(item) }, selected = tab == item, onClick = { tab = item }, modifier = Modifier.padding(vertical = 2.dp), colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Panel, selectedTextColor = Color.White, unselectedTextColor = Light))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Windows x64 · v1.0.0", color = Color(0xFF9BB5CE), style = MaterialTheme.typography.labelSmall)
            Text(if (activated) "授权：已激活" else "授权：未激活", color = if (activated) Color(0xFF7FE0A7) else Color(0xFFFFC56B))
        }
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Text(tab, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(14.dp))
            if (!activated && tab != "软件授权") {
                Text("软件尚未激活，请先完成离线设备绑定授权。", color = Color(0xFFFFC56B))
            } else when (tab) {
                "软件授权" -> LicensePage(licenseText, { licenseText = it }, message, { code -> message = LicenseManager.activate(code); activated = LicenseManager.isActivated(); if (activated) tab = "网络适配器" })
                "网络适配器" -> AdapterPage()
                "Ping" -> CommandPage("目标地址", "192.168.1.1", "执行 Ping", { "ping -n 4 -w 1000 ${it}" })
                "Tracert" -> CommandPage("目标地址", "www.example.com", "开始追踪", { "tracert -d -h 20 -w 1000 ${it}" })
                "ARP 扫描" -> ArpPage()
                "DNS 查询" -> DnsPage()
                "TCP 测试" -> TcpPage()
                "UDP 测试" -> UdpPage()
                "路由表" -> OutputPage("读取 Windows 路由表", "route print")
                "IPv4 子网" -> SubnetPage()
                "MAC 厂商" -> MacPage()
                "版本更新" -> UpdatePage()
            }
        }
    }
}

@Composable
private fun LicensePage(code: String, onCode: (String) -> Unit, message: String, activate: (String) -> Unit) {
    val device = remember { LicenseManager.deviceId() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard("离线设备绑定授权", "将以下设备识别码提供给授权签发方。授权方签发与本机设备码绑定的许可证文件。")
        OutlinedTextField(device, {}, readOnly = true, label = { Text("本机设备识别码") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(device), null) }) { Text("复制设备识别码") }
        OutlinedTextField(code, onCode, label = { Text("许可证内容 / 授权码") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = { activate(code) }) { Text("验证并激活") }
        Text(message, color = Color(0xFFFFC56B))
        Text("许可证验证采用 Ed25519 签名。授权私钥必须只保存在授权签发方，不得放入客户端。", color = Light)
    }
}

@Composable
private fun AdapterPage() {
    var output by remember { mutableStateOf("点击刷新读取本机网卡。") }
    var alias by remember { mutableStateOf("") }; var ip by remember { mutableStateOf("") }; var mask by remember { mutableStateOf("255.255.255.0") }; var gateway by remember { mutableStateOf("") }; var dns by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf(ProfileStore.load()) }; var profileName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { scope.launch { output = runPowerShell("Get-NetAdapter | Format-Table -AutoSize; Get-NetIPConfiguration | Format-List InterfaceAlias,IPv4Address,IPv4DefaultGateway,DNSServer") } }) { Text("刷新网卡信息") }; Button(onClick = {
    scope.launch {
        output = runPowerShell("Get-NetAdapter | Format-List *")
    }
}) { Text("详细信息") }
        }
        OutlinedTextField(output, {}, readOnly = true, label = { Text("本机网卡信息") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
        Text("静态 IPv4 配置", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Field("网卡名称（InterfaceAlias）", alias) { alias = it }; Field("IPv4 地址", ip) { ip = it }; Field("子网掩码", mask) { mask = it }; Field("默认网关（可留空）", gateway) { gateway = it }; Field("首选 DNS（可留空）", dns) { dns = it }
        Button(onClick = { scope.launch { output = applyStaticIp(alias, ip, mask, gateway, dns) } }) { Text("应用静态 IP（将请求管理员权限）") }
        Divider(color = Accent)
        Text("保存配置方案", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Field("方案名称", profileName) { profileName = it }
        Button(onClick = { if (profileName.isNotBlank() && alias.isNotBlank() && ip.isNotBlank()) { profiles = profiles + IpProfile(profileName, alias, ip, mask, gateway, dns); ProfileStore.save(profiles) } }) { Text("保存当前配置") }
        profiles.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(p.name, color = Color.White); Text("${p.alias} · ${p.ip}/${p.mask}", color = Light) }; Button(onClick = { alias=p.alias; ip=p.ip; mask=p.mask; gateway=p.gateway; dns=p.dns; scope.launch { output = applyStaticIp(p.alias,p.ip,p.mask,p.gateway,p.dns) } }) { Text("一键应用") } } }
    }
}

@Composable private fun CommandPage(label: String, initial: String, button: String, command: (String) -> String) {
    var target by remember { mutableStateOf(initial) }; var output by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    Field(label, target) { target = it }; Button(onClick = { scope.launch { output = if(target.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}"))) runCommand(command(target)) else "目标地址格式无效。请使用 IPv4 或域名。" } }) { Text(button) }; OutputBox(output)
}
@Composable private fun OutputPage(title: String, cmd: String) { var out by remember { mutableStateOf("") }; val scope=rememberCoroutineScope(); Button(onClick={scope.launch{out=runCommand(cmd)}}){Text(title)}; OutputBox(out) }
@Composable private fun ArpPage() {
    var subnet by remember { mutableStateOf("192.168.1") }; var output by remember { mutableStateOf("") }; val scope=rememberCoroutineScope()
    Field("扫描网段前三段（示例 192.168.1）",subnet){subnet=it}
    Button(onClick={scope.launch { output = if(subnet.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) && subnet.split(".").all{it.toIntOrNull() in 0..255}) runCommand("for /L %i in (1,1,254) do @ping -n 1 -w 150 ${subnet}.%i >nul & arp -a") else "网段格式无效，请输入例如 192.168.1。" }}) { Text("先 Ping 扫描，再读取 ARP 表") }
    Text("说明：扫描会向指定 /24 网段逐地址发送 ICMP，不会扫描未授权网络。部分设备屏蔽 Ping 时仍可能无法发现。", color=Light)
    OutputBox(output)
}
@Composable private fun DnsPage() { var host by remember { mutableStateOf("example.com") }; var out by remember { mutableStateOf("") }; val scope=rememberCoroutineScope(); Field("域名",host){host=it}; Button(onClick={scope.launch{out=if(host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}"))) runCommand("nslookup ${host}") else "域名格式无效。"}}){Text("查询 DNS")}; OutputBox(out) }
@Composable private fun TcpPage() { var host by remember { mutableStateOf("example.com") }; var port by remember { mutableStateOf("443") }; var out by remember { mutableStateOf("") }; val scope=rememberCoroutineScope(); Field("目标主机",host){host=it}; Field("TCP 端口",port){port=it}; Button(onClick={scope.launch{out=if(host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")) && (port.toIntOrNull()?.let { it in 1..65535 } == true)) runPowerShell("Test-NetConnection -ComputerName '${host}' -Port ${port.toInt()} -InformationLevel Detailed") else "主机或端口格式无效。"}}){Text("测试 TCP")}; OutputBox(out) }
@Composable private fun UdpPage() { var host by remember { mutableStateOf("127.0.0.1") }; var port by remember { mutableStateOf("53") }; var out by remember { mutableStateOf("UDP 没有通用握手，发送成功不代表远端服务可用。") }; val scope=rememberCoroutineScope(); Field("目标主机",host){host=it}; Field("UDP 端口",port){port=it}; Button(onClick={scope.launch{out=udpProbe(host,port.toIntOrNull() ?: 0)}}){Text("发送 UDP 探测数据")}; OutputBox(out) }
@Composable private fun SubnetPage() { var ip by remember { mutableStateOf("192.168.1.10") }; var cidr by remember { mutableStateOf("24") }; var out by remember { mutableStateOf("") }; Field("IPv4 地址",ip){ip=it}; Field("前缀长度（0-32）",cidr){cidr=it}; Button(onClick={out=subnetInfo(ip,cidr.toIntOrNull() ?: -1)}){Text("计算")}; OutputBox(out) }
@Composable private fun MacPage() { var mac by remember { mutableStateOf("") }; var out by remember { mutableStateOf("内置常见厂商 OUI 示例库；可在 data/oui.csv 扩展完整数据库。") }; Field("MAC 地址",mac){mac=it}; Button(onClick={out=MacVendor.lookup(mac)}){Text("查询厂商")}; OutputBox(out) }
@Composable private fun UpdatePage() {
    var api by remember { mutableStateOf(DEFAULT_RELEASE_API) }; var result by remember { mutableStateOf("当前版本：v$APP_VERSION\n请填写你自己的 GitHub Releases API 地址后检查更新。") }; val scope=rememberCoroutineScope()
    Field("GitHub Releases API 地址",api){api=it}
    Button(onClick={scope.launch { result = checkLatestRelease(api) }}) { Text("检查更新") }
    OutputBox(result)
    Text("只检查版本并打开官方 Release 页面，不会静默下载或安装。", color=Light)
}

@Composable private fun Field(label: String, value: String, change: (String)->Unit) { OutlinedTextField(value, change, label={Text(label)}, modifier=Modifier.fillMaxWidth(), singleLine=true) }
@Composable private fun OutputBox(text: String) { OutlinedTextField(text, {}, readOnly=true, label={Text("执行结果")}, modifier=Modifier.fillMaxWidth().heightIn(min=130.dp,max=340.dp), minLines=5) }
@Composable private fun InfoCard(title: String, body: String) { Card(colors=CardDefaults.cardColors(containerColor=Panel), modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title,color=Color.White,style=MaterialTheme.typography.titleLarge); Text(body,color=Light) } } }

private suspend fun runCommand(command: String) = withContext(Dispatchers.IO) { try { val p=ProcessBuilder("cmd.exe","/c",command).redirectErrorStream(true).start(); val text=p.inputStream.bufferedReader(Charsets.UTF_8).readText(); p.waitFor(); text.ifBlank{"命令已执行，无输出。"} } catch(e:Exception){"执行失败：${e.message}"} }
private suspend fun runPowerShell(script: String) = withContext(Dispatchers.IO) { try { val p=ProcessBuilder("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-Command",script).redirectErrorStream(true).start(); val text=p.inputStream.bufferedReader(Charsets.UTF_8).readText(); p.waitFor(); text.ifBlank{"命令已执行，无输出。"} } catch(e:Exception){"执行失败：${e.message}"} }
private suspend fun applyStaticIp(alias:String,ip:String,mask:String,gateway:String,dns:String):String = withContext(Dispatchers.IO) {
    if(alias.isBlank() || !validIpv4(ip) || !validMask(mask) || (gateway.isNotBlank() && !validIpv4(gateway)) || (dns.isNotBlank() && !validIpv4(dns))) return@withContext "请检查网卡名称、IPv4、子网掩码、网关和 DNS。"
    val prefix=maskToPrefix(mask) ?: return@withContext "子网掩码无效。"
    val safeAlias=alias.replace("'","''")
    val script = "\$a='$safeAlias'; Set-NetIPInterface -InterfaceAlias \$a -AddressFamily IPv4 -Dhcp Disabled; Get-NetIPAddress -InterfaceAlias \$a -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object {\$_.IPAddress -notlike '169.254*'} | Remove-NetIPAddress -Confirm:\$false -ErrorAction SilentlyContinue; New-NetIPAddress -InterfaceAlias \$a -IPAddress '$ip' -PrefixLength $prefix"
    val fullScript = script + (if(gateway.isNotBlank()) " -DefaultGateway '$gateway'" else "") + ";" + (if(dns.isNotBlank()) " Set-DnsClientServerAddress -InterfaceAlias \$a -ServerAddresses '$dns';" else "")
    try {
        val encoded=Base64.getEncoder().encodeToString(fullScript.toByteArray(Charsets.UTF_16LE))
        val elevate="Start-Process powershell.exe -Verb RunAs -Wait -ArgumentList @('-NoProfile','-EncodedCommand','$encoded')"
        val p=ProcessBuilder("powershell.exe","-NoProfile","-Command",elevate).redirectErrorStream(true).start()
        val out=p.inputStream.bufferedReader().readText(); p.waitFor()
        "已提交管理员权限配置请求。请在 Windows UAC 提示中确认。\n$out"
    } catch(e:Exception){"配置失败：${e.message}"}
}
private fun udpProbe(host:String,port:Int):String { if(port !in 1..65535) return "端口范围必须是 1-65535"; return try { DatagramSocket().use { s -> s.soTimeout=1500; val b="NetworkToolkit UDP probe".toByteArray(); s.send(DatagramPacket(b,b.size,InetAddress.getByName(host),port)); "UDP 数据报已发送至 $host:$port。\nUDP 无连接确认，无法仅凭发送成功判断端口开放。" } } catch(e:Exception){"UDP 探测失败：${e.message}"} }
private fun validIpv4(s:String)=try { val a=s.split("."); a.size==4 && a.all{it.toInt() in 0..255} } catch(_:Exception){false}
private fun validMask(s:String)=maskToPrefix(s)!=null
private fun maskToPrefix(s:String):Int? { if(!validIpv4(s)) return null; val n=s.split(".").joinToString(""){it.toInt().toString(2).padStart(8,'0')}; if(!Regex("1*0*").matches(n)) return null; return n.count{it=='1'} }
private fun subnetInfo(ip:String,prefix:Int):String {
    if(!validIpv4(ip) || prefix !in 0..32) return "IPv4 地址或前缀长度无效。"
    val n=ip.split(".").fold(0L){a,v -> (a shl 8) or v.toLong()}
    val mask=if(prefix==0) 0L else (0xFFFFFFFFL shl (32-prefix)) and 0xFFFFFFFFL
    val network=n and mask
    val broadcast=network or (mask xor 0xFFFFFFFFL)
    fun fmt(v:Long)=(0..3).joinToString("."){((v shr (24-8*it)) and 255).toString()}
    val usable=if(prefix>=31) 0 else (1L shl (32-prefix))-2
    val first=if(prefix>=31) "无" else fmt(network+1)
    val last=if(prefix>=31) "无" else fmt(broadcast-1)
    return "网络地址：${fmt(network)}\n广播地址：${fmt(broadcast)}\n子网掩码：${fmt(mask)}\n可用地址数量：$usable\n首个可用地址：$first\n最后可用地址：$last"
}

private object MacVendor {
    private val vendors = mapOf(
        "000C29" to "VMware, Inc.", "001C42" to "Apple, Inc.", "001B63" to "Apple, Inc.",
        "F4F5D8" to "Apple, Inc.", "3C5AB4" to "Apple, Inc.", "001A11" to "Google, Inc.",
        "001B21" to "Intel Corporate", "001D7E" to "Hewlett Packard", "001E68" to "Dell Inc.",
        "001C23" to "Cisco Systems, Inc.", "001B0C" to "Cisco Systems, Inc.", "001D09" to "Cisco Systems, Inc.",
        "001A2B" to "Huawei Technologies", "00259E" to "Huawei Technologies", "001E10" to "TP-Link Technologies",
        "001D0F" to "TP-Link Technologies", "C0A0BB" to "TP-Link Technologies", "001E58" to "ASUSTek Computer",
        "001F1F" to "ASUSTek Computer", "001D60" to "Samsung Electronics", "001E7D" to "Samsung Electronics"
    )
    fun lookup(mac:String):String {
        val hex=mac.filter{it.isLetterOrDigit()}.uppercase()
        if(hex.length!=12 || !hex.all{it in "0123456789ABCDEF"}) return "MAC 地址格式无效。示例：00:1A:2B:3C:4D:5E"
        val oui=hex.take(6)
        val external = try {
            MacVendor::class.java.getResourceAsStream("/oui.csv")?.bufferedReader()?.useLines { lines ->
                lines.map { it.split(",", limit=2) }.firstOrNull { it.size==2 && it[0].trim().equals(oui, ignoreCase=true) }?.get(1)?.trim()
            }
        } catch(_:Exception) { null }
        val vendor=external ?: vendors[oui] ?: "未在本地 OUI 数据库中找到。可更新 src/main/resources/oui.csv。"
        return "MAC 地址：${hex.chunked(2).joinToString(":")}\nOUI：${oui.chunked(2).joinToString(":")}\n厂商：$vendor"
    }
}
private suspend fun checkLatestRelease(api:String):String = withContext(Dispatchers.IO) {
    try {
        val url=URL(api); val c=url.openConnection() as HttpURLConnection; c.connectTimeout=6000; c.readTimeout=6000; c.setRequestProperty("Accept","application/vnd.github+json"); c.setRequestProperty("User-Agent","NetworkToolkit/$APP_VERSION")
        val body=c.inputStream.bufferedReader().readText(); c.disconnect()
        val tag=Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: return@withContext "无法从 GitHub 响应中读取 tag_name。请检查 API 地址。"
        val html=Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1).orEmpty()
        if(tag.trimStart('v')==APP_VERSION) "当前已是最新版本：$tag" else "发现新版本：$tag\n当前版本：v$APP_VERSION\nRelease：$html\n请在浏览器打开 Release 页面手动下载。"
    } catch(e:Exception){ "检查失败：${e.message}" }
}

private data class IpProfile(val name:String,val alias:String,val ip:String,val mask:String,val gateway:String,val dns:String)
private object ProfileStore { private val file=File(System.getProperty("user.home"),".networktoolkit/profiles.tsv"); fun load():List<IpProfile> = try { file.readLines().mapNotNull{val a=it.split("\t"); if(a.size==6) IpProfile(a[0],a[1],a[2],a[3],a[4],a[5]) else null} } catch(_:Exception){emptyList()}; fun save(list:List<IpProfile>){file.parentFile.mkdirs();file.writeText(list.joinToString("\n"){listOf(it.name,it.alias,it.ip,it.mask,it.gateway,it.dns).joinToString("\t")})} }
private object LicenseManager {
    private val file=File(System.getProperty("user.home"),".networktoolkit/license.txt")
    fun deviceId():String {
        val machineGuid = try { val p=ProcessBuilder("reg.exe","query","HKLM\\SOFTWARE\\Microsoft\\Cryptography","/v","MachineGuid").redirectErrorStream(true).start(); p.inputStream.bufferedReader().readText().lines().lastOrNull{it.contains("MachineGuid")}?.trim()?.split(Regex("\\s+"))?.lastOrNull().orEmpty() } catch(_:Exception){""}
        val biosUuid = try { val p=ProcessBuilder("powershell.exe","-NoProfile","-Command","(Get-CimInstance Win32_ComputerSystemProduct).UUID").redirectErrorStream(true).start(); p.inputStream.bufferedReader().readText().trim() } catch(_:Exception){""}
        val raw=listOf(machineGuid,biosUuid).filter{it.isNotBlank()}.joinToString("|").ifBlank { System.getenv("COMPUTERNAME") ?: "UNKNOWN-PC" }
        return java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02X".format(it)}.chunked(4).take(6).joinToString("-")
    }
    fun isActivated():Boolean = try { verify(file.readText().trim()) } catch(_:Exception){false}
    fun activate(value:String):String = try { if(!verify(value.trim())) "授权无效：签名、设备码或许可证格式不正确。" else { file.parentFile.mkdirs(); file.writeText(value.trim()); "激活成功。" } } catch(e:Exception){"授权验证失败：${e.message}"}
    private fun verify(value:String):Boolean { val parts=value.split("."); if(parts.size!=2) return false; val payload=String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8); val fields=payload.split("|"); if(fields.size!=3 || fields[0]!=deviceId()) return false; val exp=fields[1].toLongOrNull() ?: return false; if(exp!=0L && System.currentTimeMillis()>exp) return false; val pub=KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_B64))); val sig=Signature.getInstance("Ed25519"); sig.initVerify(pub); sig.update(parts[0].toByteArray()); return sig.verify(Base64.getUrlDecoder().decode(parts[1])) }
}

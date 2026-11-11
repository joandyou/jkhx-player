package com.jkhx.player.ui.screens.local.remote

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.RemoteServerEntity
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerDialog(
    initialServer: RemoteServerEntity? = null,
    onDismiss: () -> Unit,
    onSave: (RemoteServerEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var protocol by remember { mutableStateOf(initialServer?.protocol ?: "webdav") }
    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var host by remember { mutableStateOf(initialServer?.host ?: "") }
    var portText by remember {
        mutableStateOf(
            initialServer?.port?.toString() ?: when (protocol) {
                "smb" -> "445"
                "sftp" -> "22"
                else -> "5005"
            }
        )
    }
    var useSsl by remember { mutableStateOf(initialServer?.useSsl ?: false) }
    var basePath by remember { mutableStateOf(initialServer?.basePath ?: if (protocol == "smb") "Share" else "/") }
    var username by remember { mutableStateOf(initialServer?.username ?: "") }
    var password by remember { mutableStateOf(initialServer?.password ?: "") }
    var isAnonymous by remember { mutableStateOf(initialServer?.isAnonymous ?: false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun updateProtocol(newProto: String) {
        protocol = newProto
        portText = when (newProto) {
            "smb" -> "445"
            "sftp" -> "22"
            else -> if (useSsl) "443" else "80"
        }
        if (newProto == "smb" && (basePath == "/" || basePath.isBlank())) {
            basePath = "Share"
        } else if (newProto != "smb" && basePath == "Share") {
            basePath = "/"
        }
    }

    fun autoParseInput(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val isHttps = trimmed.startsWith("https://", ignoreCase = true)
            protocol = "webdav"
            useSsl = isHttps
            val noScheme = if (isHttps) trimmed.substring(8) else trimmed.substring(7)
            val slashIndex = noScheme.indexOf('/')
            val hostAndPort = if (slashIndex != -1) noScheme.substring(0, slashIndex) else noScheme
            val path = if (slashIndex != -1) noScheme.substring(slashIndex) else "/"

            if (hostAndPort.contains(':')) {
                val parts = hostAndPort.split(':')
                host = parts[0]
                portText = parts.getOrNull(1) ?: (if (isHttps) "443" else "80")
            } else {
                host = hostAndPort
                portText = if (isHttps) "443" else "80"
            }
            val cleanPath = if (path.endsWith("/")) path else "$path/"
            basePath = cleanPath
            if (name.isBlank()) {
                name = host
            }
        } else if (trimmed.startsWith("ssh ", ignoreCase = true) || trimmed.startsWith("sftp://", ignoreCase = true)) {
            protocol = "sftp"
            var clean = if (trimmed.startsWith("ssh ", ignoreCase = true)) trimmed.substring(4).trim() else trimmed.substring(7).trim()
            val userInHost = if (clean.contains('@')) clean.substringBefore('@') else null
            if (userInHost != null) {
                clean = clean.substringAfter('@')
                username = userInHost
            }
            val slashIndex = clean.indexOf('/')
            val hostAndPort = if (slashIndex != -1) clean.substring(0, slashIndex) else clean
            val path = if (slashIndex != -1) clean.substring(slashIndex) else "/"

            if (hostAndPort.contains(':')) {
                val parts = hostAndPort.split(':')
                host = parts[0]
                portText = parts.getOrNull(1) ?: "22"
            } else {
                host = hostAndPort
                portText = "22"
            }
            basePath = path
            if (name.isBlank()) {
                name = host
            }
        } else if (trimmed.startsWith("smb://", ignoreCase = true) || trimmed.startsWith("\\\\")) {
            protocol = "smb"
            var clean = if (trimmed.startsWith("smb://", ignoreCase = true)) trimmed.substring(6) else trimmed.removePrefix("\\\\")
            clean = clean.replace('\\', '/')
            val slashIndex = clean.indexOf('/')
            val hostAndPort = if (slashIndex != -1) clean.substring(0, slashIndex) else clean
            val share = if (slashIndex != -1) clean.substring(slashIndex + 1).trim('/') else "share"

            if (hostAndPort.contains(':')) {
                val parts = hostAndPort.split(':')
                host = parts[0]
                portText = parts.getOrNull(1) ?: "445"
            } else {
                host = hostAndPort
                portText = "445"
            }
            basePath = share
            if (name.isBlank()) {
                name = host
            }
        } else {
            host = trimmed
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ShapeTokens.Card,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (initialServer == null) stringResource(R.string.remote_server_add) else stringResource(R.string.remote_server_edit),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Chọn giao thức (Protocol)
                Text(
                    text = stringResource(R.string.remote_server_protocol),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "webdav" to "WebDAV",
                        "smb" to "SMB (NAS/Win)",
                        "sftp" to "SFTP (SSH)"
                    ).forEach { (pKey, pLabel) ->
                        val isSelected = protocol == pKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .tvFocusableItem(cornerRadius = 12.dp)
                                .clickable { updateProtocol(pKey) }
                        ) {
                            Text(
                                text = pLabel,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                // Tên gợi nhớ
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.remote_server_name)) },
                    placeholder = { Text(stringResource(R.string.remote_server_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = ShapeTokens.Card
                )

                // Host và Port
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { autoParseInput(it) },
                        label = { Text(stringResource(R.string.remote_server_host)) },
                        placeholder = { Text(stringResource(R.string.remote_server_host_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(2.5f),
                        shape = ShapeTokens.Card
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text(stringResource(R.string.remote_server_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = ShapeTokens.Card
                    )
                }

                // Đường dẫn gốc / Share
                OutlinedTextField(
                    value = basePath,
                    onValueChange = { basePath = it },
                    label = { Text(stringResource(R.string.remote_server_path)) },
                    placeholder = { Text(stringResource(R.string.remote_server_path_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = ShapeTokens.Card
                )

                // SSL toggle cho WebDAV
                if (protocol == "webdav") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable {
                                useSsl = !useSsl
                                if (portText == "5005" || portText == "80") portText = if (useSsl) "443" else "5005"
                            }
                    ) {
                        Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.remote_server_use_ssl), fontSize = 13.sp)
                    }
                }

                // Đăng nhập ẩn danh cho SMB
                if (protocol == "smb") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable { isAnonymous = !isAnonymous }
                    ) {
                        Checkbox(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.remote_server_anonymous), fontSize = 13.sp)
                    }
                }

                // Username & Password (nếu không ẩn danh)
                if (!isAnonymous) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.remote_server_username)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = ShapeTokens.Card
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.remote_server_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = ShapeTokens.Card
                    )
                }

                // Hiển thị kết quả kiểm tra kết nối
                if (testResult != null) {
                    val (success, msg) = testResult!!
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (success) Color(0xFF1B5E20).copy(alpha = 0.5f) else Color(0xFFB71C1C).copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Nút hành động
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Kiểm tra kết nối
                    OutlinedButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: when (protocol) {
                                "smb" -> 445
                                "sftp" -> 22
                                else -> if (useSsl) 443 else 80
                            }
                            val tempServer = RemoteServerEntity(
                                id = "test_server",
                                name = name.ifBlank { "Test" },
                                protocol = protocol,
                                host = host.trim(),
                                port = port,
                                useSsl = useSsl,
                                basePath = basePath.trim(),
                                username = username.trim().ifBlank { null },
                                password = password.ifBlank { null },
                                isAnonymous = isAnonymous
                            )
                            isTesting = true
                            testResult = null
                            scope.launch(Dispatchers.IO) {
                                val client = RemoteClientFactory.createClient(tempServer)
                                try {
                                    val result = client.testConnection()
                                    withContext(Dispatchers.Main) {
                                        isTesting = false
                                        if (result.isSuccess) {
                                            testResult = true to context.getString(R.string.remote_server_test_success)
                                        } else {
                                            val err = result.exceptionOrNull()?.message ?: "Unknown error"
                                            testResult = false to context.getString(R.string.remote_server_test_failed, err)
                                        }
                                    }
                                } finally {
                                    client.close()
                                }
                            }
                        },
                        enabled = !isTesting && host.isNotBlank(),
                        modifier = Modifier
                            .weight(1.2f)
                            .tvFocusableItem()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.remote_server_test_btn), fontSize = 12.sp)
                        }
                    }

                    // Nút Hủy
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.tvFocusableItem()
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    // Nút Lưu
                    Button(
                        onClick = {
                            if (host.isBlank()) {
                                Toast.makeText(context, "Vui lòng nhập địa chỉ máy chủ", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val port = portText.toIntOrNull() ?: when (protocol) {
                                "smb" -> 445
                                "sftp" -> 22
                                else -> if (useSsl) 443 else 80
                            }
                            val resolvedName = if (name.isNotBlank()) name.trim() else "${protocol.uppercase()} ($host)"
                            val server = RemoteServerEntity(
                                id = initialServer?.id ?: UUID.randomUUID().toString(),
                                name = resolvedName,
                                protocol = protocol,
                                host = host.trim(),
                                port = port,
                                useSsl = useSsl,
                                basePath = basePath.trim().ifBlank { if (protocol == "smb") "Share" else "/" },
                                username = username.trim().ifBlank { null },
                                password = password.ifBlank { null },
                                isAnonymous = isAnonymous,
                                addedAt = initialServer?.addedAt ?: System.currentTimeMillis()
                            )
                            RemoteClientFactory.removeClient(server.id)
                            onSave(server)
                        },
                        enabled = host.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableItem()
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }
}

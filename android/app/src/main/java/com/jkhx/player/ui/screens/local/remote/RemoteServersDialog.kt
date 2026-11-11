package com.jkhx.player.ui.screens.local.remote

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jkhx.player.R
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.core.database.entity.LocalLibraryEntity
import com.jkhx.player.core.database.entity.RemoteServerEntity
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServersDialog(
    onDismiss: () -> Unit,
    onPlayEpisode: (providerId: String, movieSlug: String) -> Unit,
    onSaveEntity: (LocalLibraryEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getInstance(context) }
    val serverDao = remember { database.remoteServerDao() }

    val servers by serverDao.getAll().collectAsState(initial = emptyList())

    var showEditDialog by remember { mutableStateOf(false) }
    var selectedServerForEdit by remember { mutableStateOf<RemoteServerEntity?>(null) }
    var browsingServer by remember { mutableStateOf<RemoteServerEntity?>(null) }
    var serverToDelete by remember { mutableStateOf<RemoteServerEntity?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.remote_servers_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.remote_servers_subtitle),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.tvFocusableItem()) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Content List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (servers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = stringResource(R.string.remote_server_empty),
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(servers, key = { it.id }) { server ->
                                val protoBadgeColor = when (server.protocol) {
                                    "smb" -> Color(0xFF1976D2)
                                    "sftp" -> Color(0xFFE65100)
                                    else -> Color(0xFF388E3C)
                                }

                                Surface(
                                    shape = ShapeTokens.Card,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableItem()
                                        .clickable {
                                            browsingServer = server
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = protoBadgeColor,
                                            modifier = Modifier.padding(end = 12.dp)
                                        ) {
                                            Text(
                                                text = server.protocol.uppercase(),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = server.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${server.host}:${server.port}${if (server.basePath != "/") " (${server.basePath})" else ""}",
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Nút Duyệt file
                                        IconButton(
                                            onClick = { browsingServer = server },
                                            modifier = Modifier.tvFocusableItem()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = "Duyệt thư mục",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Nút Sửa
                                        IconButton(
                                            onClick = {
                                                selectedServerForEdit = server
                                                showEditDialog = true
                                            },
                                            modifier = Modifier.tvFocusableItem()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Sửa",
                                                tint = Color.White.copy(alpha = 0.7f)
                                            )
                                        }

                                        // Nút Xóa
                                        IconButton(
                                            onClick = { serverToDelete = server },
                                            modifier = Modifier.tvFocusableItem()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Xóa",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Bottom Add Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            selectedServerForEdit = null
                            showEditDialog = true
                        },
                        modifier = Modifier.tvFocusableItem()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.remote_server_add), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialog Thêm / Chỉnh sửa
    if (showEditDialog) {
        RemoteServerDialog(
            initialServer = selectedServerForEdit,
            onDismiss = { showEditDialog = false },
            onSave = { savedServer ->
                scope.launch(Dispatchers.IO) {
                    serverDao.insert(savedServer)
                    RemoteClientFactory.removeClient(savedServer.id)
                }
                showEditDialog = false
                Toast.makeText(context, "Đã lưu máy chủ: ${savedServer.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog Duyệt tệp tin
    if (browsingServer != null) {
        RemoteFileBrowserDialog(
            server = browsingServer!!,
            onDismiss = { browsingServer = null },
            onPlayEpisode = onPlayEpisode,
            onSaveEntity = onSaveEntity
        )
    }

    // Dialog xác nhận xóa
    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text(stringResource(R.string.remote_server_delete_confirm)) },
            text = { Text("Bạn có chắc chắn muốn xóa máy chủ '${serverToDelete?.name}' khỏi danh sách?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = serverToDelete ?: return@TextButton
                        scope.launch(Dispatchers.IO) {
                            serverDao.delete(toDelete)
                            RemoteClientFactory.removeClient(toDelete.id)
                        }
                        serverToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

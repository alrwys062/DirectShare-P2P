package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.AppLanguage
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.ui.components.TransferProgressCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TechGreen
import com.example.ui.theme.TechPurple
import com.example.viewmodel.TransferViewModel

@Composable
fun ReceiveScreen(
    viewModel: TransferViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings by viewModel.strings.collectAsState()
    val language by viewModel.appLanguage.collectAsState()

    val connectionState by viewModel.connectionState.collectAsState()
    val isServerListening by viewModel.isServerListening.collectAsState()
    val activeTransfer by viewModel.activeTransfer.collectAsState()
    val transferHistory by viewModel.transferHistory.collectAsState()
    val targetPort by viewModel.targetPort.collectAsState()

    val receivedItems = transferHistory.filter { it.direction == TransferDirection.RECEIVING }

    // Pulsing animation for radar effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarPulse"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Transfer Card
        activeTransfer?.let { transfer ->
            item {
                TransferProgressCard(
                    transfer = transfer,
                    strings = strings,
                    language = language,
                    onCancel = { viewModel.cancelActiveTransfer() },
                    onDismiss = { viewModel.clearActiveTransfer() }
                )
            }
        }

        // Radar / Listening Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receive_status_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulsing Radar Icon
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .scale(if (isServerListening) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(
                                    if (isServerListening) NeonCyan.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isServerListening) NeonCyan.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = strings.waitingForSender,
                                tint = if (isServerListening) NeonCyan else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isServerListening) strings.waitingForSender else strings.receiverStopped,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = strings.receiveInstructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // IP & Port info capsule
                    val displayIp = connectionState.localIpAddress
                        ?: connectionState.groupOwnerAddress
                        ?: "192.168.49.1"

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CellTower,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.ipAndPortFormat(displayIp, targetPort),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons: Show QR Code & Restart/Toggle Server
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setShowQrDialog(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("show_receiver_qr_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.btnShowQr, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (isServerListening) {
                                    viewModel.stopReceivingServer()
                                } else {
                                    viewModel.startReceivingServer()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_server_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isServerListening) strings.btnRestartServer else strings.btnStartServer)
                        }
                    }
                }
            }
        }

        // Wi-Fi P2P Group Control
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("p2p_group_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.p2pGroupTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (connectionState.isConnected) {
                                if (connectionState.isGroupOwner) strings.groupOwnerHosting
                                else strings.connectedToGroup
                            } else strings.noActiveGroup,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionState.isConnected) TechGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    if (connectionState.isConnected) {
                        OutlinedButton(
                            onClick = { viewModel.removeP2pGroup() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("leave_p2p_group_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupRemove,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.btnLeaveGroup)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.createP2pGroup() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TechPurple
                            ),
                            modifier = Modifier.testTag("create_p2p_group_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.btnCreateGroup)
                        }
                    }
                }
            }
        }

        // Recently Received Files Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.recentReceivedTitle(receivedItems.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (receivedItems.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.noReceivedFiles,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            items(receivedItems, key = { it.id }) { item ->
                ReceivedFileCard(
                    item = item,
                    language = language,
                    receivedSuccessText = strings.receivedSuccess,
                    openFileText = strings.openFile,
                    shareFileText = strings.shareFile,
                    onOpen = {
                        item.localFilePath?.let { path ->
                            viewModel.openFile(context, path, item.mimeType)
                        }
                    },
                    onShare = {
                        item.localFilePath?.let { path ->
                            viewModel.shareFile(context, path, item.mimeType)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ReceivedFileCard(
    item: TransferItem,
    language: AppLanguage,
    receivedSuccessText: String,
    openFileText: String,
    shareFileText: String,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("received_file_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TechGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = TechGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = item.getFormattedSize(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = receivedSuccessText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TechGreen
                    )
                }
            }

            IconButton(
                onClick = onOpen,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("open_file_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = openFileText,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("share_file_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = shareFileText,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

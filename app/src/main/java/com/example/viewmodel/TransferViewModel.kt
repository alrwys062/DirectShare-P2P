package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localization.AppLanguage
import com.example.localization.AppStrings
import com.example.localization.AppThemeMode
import com.example.localization.ArabicStrings
import com.example.localization.EnglishStrings
import com.example.model.FileCategory
import com.example.model.P2PConnectionState
import com.example.model.PeerDevice
import com.example.model.SelectedFile
import com.example.model.TransferItem
import com.example.p2p.WifiP2pHelper
import com.example.transfer.SocketFileTransferEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppTab {
    SEND,
    RECEIVE,
    HISTORY
}

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val p2pHelper = WifiP2pHelper(application)
    private val transferEngine = SocketFileTransferEngine(application)

    // Language & Theme State
    private val _appLanguage = MutableStateFlow(AppLanguage.ARABIC)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    val strings: StateFlow<AppStrings> = _appLanguage.map { lang ->
        if (lang == AppLanguage.ARABIC) ArabicStrings else EnglishStrings
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ArabicStrings)

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    val connectionState: StateFlow<P2PConnectionState> = p2pHelper.connectionState
    val peers: StateFlow<List<PeerDevice>> = p2pHelper.peers
    val isDiscovering: StateFlow<Boolean> = p2pHelper.isDiscovering

    val activeTransfer: StateFlow<TransferItem?> = transferEngine.activeTransfer
    val transferHistory: StateFlow<List<TransferItem>> = transferEngine.transferHistory
    val isServerListening: StateFlow<Boolean> = transferEngine.isServerListening

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _currentTab = MutableStateFlow(AppTab.SEND)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _targetIp = MutableStateFlow("")
    val targetIp: StateFlow<String> = _targetIp.asStateFlow()

    private val _targetPort = MutableStateFlow(SocketFileTransferEngine.DEFAULT_PORT)
    val targetPort: StateFlow<Int> = _targetPort.asStateFlow()

    private val _showQrDialog = MutableStateFlow(false)
    val showQrDialog: StateFlow<Boolean> = _showQrDialog.asStateFlow()

    private val _showManualConnectDialog = MutableStateFlow(false)
    val showManualConnectDialog: StateFlow<Boolean> = _showManualConnectDialog.asStateFlow()

    private val _snackBarMessage = MutableStateFlow<String?>(null)
    val snackBarMessage: StateFlow<String?> = _snackBarMessage.asStateFlow()

    init {
        // Automatically sync target IP when connected as P2P client
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state.isConnected && state.groupOwnerAddress != null) {
                    _targetIp.value = state.groupOwnerAddress
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
    }

    fun toggleLanguage() {
        _appLanguage.value = if (_appLanguage.value == AppLanguage.ARABIC) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.ARABIC
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun cycleThemeMode() {
        _themeMode.value = when (_themeMode.value) {
            AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.DARK
            AppThemeMode.DARK -> AppThemeMode.SYSTEM
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
        if (tab == AppTab.RECEIVE && !isServerListening.value) {
            startReceivingServer()
        }
    }

    fun registerP2p() {
        p2pHelper.register()
    }

    fun unregisterP2p() {
        p2pHelper.unregister()
    }

    fun discoverPeers() {
        val s = strings.value
        p2pHelper.discoverPeers(
            onSuccess = { showMessage(s.msgSearchingPeers) },
            onFailure = { code -> showMessage(s.msgPeerDiscoveryFailed(code)) }
        )
    }

    fun stopPeerDiscovery() {
        p2pHelper.stopPeerDiscovery()
    }

    fun connectToPeer(deviceAddress: String) {
        val s = strings.value
        p2pHelper.connectToPeer(
            deviceAddress = deviceAddress,
            onSuccess = { showMessage(s.msgConnectingToPeer) },
            onFailure = { showMessage(s.msgConnectionFailed) }
        )
    }

    fun createP2pGroup() {
        val s = strings.value
        p2pHelper.createGroup(
            onSuccess = { showMessage(s.msgGroupCreated) },
            onFailure = { showMessage(s.msgGroupFailed) }
        )
    }

    fun removeP2pGroup() {
        val s = strings.value
        p2pHelper.removeGroup(
            onSuccess = { showMessage(s.msgGroupLeft) },
            onFailure = { showMessage(s.msgGroupFailed) }
        )
    }

    fun startReceivingServer(port: Int = SocketFileTransferEngine.DEFAULT_PORT) {
        viewModelScope.launch {
            transferEngine.startReceivingServer(port) { receivedItem ->
                showMessage(strings.value.msgFileReceived(receivedItem.fileName))
            }
        }
    }

    fun stopReceivingServer() {
        transferEngine.stopReceivingServer()
    }

    fun setTargetIp(ip: String) {
        _targetIp.value = ip.trim()
    }

    fun setTargetPort(port: Int) {
        _targetPort.value = port
    }

    fun setShowQrDialog(show: Boolean) {
        _showQrDialog.value = show
    }

    fun setShowManualConnectDialog(show: Boolean) {
        _showManualConnectDialog.value = show
    }

    fun clearSnackBarMessage() {
        _snackBarMessage.value = null
    }

    private fun showMessage(msg: String) {
        _snackBarMessage.value = msg
    }

    fun addSelectedUris(uris: List<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        val newFiles = mutableListOf<SelectedFile>()

        for (uri in uris) {
            try {
                var fileName = "file_${System.currentTimeMillis()}"
                var fileSize = 0L
                val mimeType = resolver.getType(uri) ?: "*/*"

                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                    }
                }

                val category = determineCategory(fileName, mimeType)
                newFiles.add(
                    SelectedFile(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        name = fileName,
                        size = fileSize,
                        mimeType = mimeType,
                        category = category
                    )
                )
            } catch (e: Exception) {
                Log.e("TransferViewModel", "Error reading file metadata: ${e.message}")
            }
        }

        _selectedFiles.value = _selectedFiles.value + newFiles
        if (newFiles.isNotEmpty()) {
            showMessage(strings.value.msgFilesAdded(newFiles.size))
        }
    }

    fun removeSelectedFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.filterNot { it.id == id }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun sendFiles(overrideIp: String? = null) {
        val s = strings.value
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            showMessage(s.msgPickAtLeastOneFile)
            return
        }

        val ip = overrideIp?.ifBlank { null }
            ?: _targetIp.value.ifBlank { null }
            ?: connectionState.value.groupOwnerAddress

        if (ip.isNullOrBlank()) {
            showMessage(s.msgNoReceiverIp)
            _showManualConnectDialog.value = true
            return
        }

        viewModelScope.launch {
            showMessage(s.msgSendingFilesTo(files.size, ip))
            transferEngine.sendFiles(
                serverIp = ip,
                port = _targetPort.value,
                filesToSend = files,
                contentResolver = getApplication<Application>().contentResolver
            )
        }
    }

    fun cancelActiveTransfer() {
        transferEngine.cancelActiveTransfer()
        showMessage(strings.value.msgTransferCancelled)
    }

    fun clearActiveTransfer() {
        transferEngine.clearActiveTransfer()
    }

    fun openFile(context: Context, localPath: String, mimeType: String) {
        val s = strings.value
        try {
            val file = File(localPath)
            if (!file.exists()) {
                showMessage(s.msgFileNotFound)
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, s.msgChooserOpen))
        } catch (e: Exception) {
            Log.e("TransferViewModel", "Cannot open file: ${e.message}")
            showMessage(s.msgNoAppToOpen)
        }
    }

    fun shareFile(context: Context, localPath: String, mimeType: String) {
        val s = strings.value
        try {
            val file = File(localPath)
            if (!file.exists()) {
                showMessage(s.msgFileNotFound)
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, s.msgChooserShare))
        } catch (e: Exception) {
            Log.e("TransferViewModel", "Cannot share file: ${e.message}")
            showMessage(s.msgShareError)
        }
    }

    private fun determineCategory(name: String, mimeType: String): FileCategory {
        val lowerName = name.lowercase()
        val lowerMime = mimeType.lowercase()

        return when {
            lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") -> FileCategory.IMAGE
            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") || lowerName.endsWith(".mov") -> FileCategory.VIDEO
            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") || lowerName.endsWith(".flac") -> FileCategory.AUDIO
            lowerName.endsWith(".apk") || lowerMime.contains("android.package-archive") -> FileCategory.APK
            lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") || lowerName.endsWith(".gz") -> FileCategory.ARCHIVE
            lowerMime.startsWith("text/") || lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx") -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    override fun onCleared() {
        super.onCleared()
        p2pHelper.unregister()
        transferEngine.stopReceivingServer()
    }
}

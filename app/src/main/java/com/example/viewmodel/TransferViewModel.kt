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
import com.example.model.FileCategory
import com.example.model.P2PConnectionState
import com.example.model.PeerDevice
import com.example.model.SelectedFile
import com.example.model.TransferItem
import com.example.p2p.WifiP2pHelper
import com.example.transfer.SocketFileTransferEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppTab(val title: String) {
    SEND("إرسال"),
    RECEIVE("استقبال"),
    HISTORY("السجل")
}

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val p2pHelper = WifiP2pHelper(application)
    private val transferEngine = SocketFileTransferEngine(application)

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
        // Automatically start server when in receive tab or when connected
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state.isConnected && state.groupOwnerAddress != null) {
                    _targetIp.value = state.groupOwnerAddress
                }
            }
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
        p2pHelper.discoverPeers(
            onSuccess = { showMessage("جارٍ البحث عن الأجهزة القريبة...") },
            onFailure = { code -> showMessage("تعذر بدء البحث عن أجهزة Wi-Fi المباشرة (رمز: $code)") }
        )
    }

    fun stopPeerDiscovery() {
        p2pHelper.stopPeerDiscovery()
    }

    fun connectToPeer(deviceAddress: String) {
        p2pHelper.connectToPeer(
            deviceAddress = deviceAddress,
            onSuccess = { showMessage("جارٍ الاتصال بالجهاز...") },
            onFailure = { showMessage("فشل بدء الاتصال بالجهاز") }
        )
    }

    fun createP2pGroup() {
        p2pHelper.createGroup(
            onSuccess = { showMessage("تم إنشاء مجموعة Wi-Fi Direct بنجاح!") },
            onFailure = { showMessage("تعذر إنشاء مجموعة Wi-Fi Direct") }
        )
    }

    fun removeP2pGroup() {
        p2pHelper.removeGroup(
            onSuccess = { showMessage("تمت مغادرة مجموعة Wi-Fi Direct") },
            onFailure = { showMessage("تعذر مغادرة المجموعة") }
        )
    }

    fun startReceivingServer(port: Int = SocketFileTransferEngine.DEFAULT_PORT) {
        viewModelScope.launch {
            transferEngine.startReceivingServer(port) { receivedItem ->
                showMessage("تم استلام الملف: ${receivedItem.fileName}")
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
                var fileName = "ملف_${System.currentTimeMillis()}"
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
                Log.e("TransferViewModel", "Error reading metadata: ${e.message}")
            }
        }

        _selectedFiles.value = _selectedFiles.value + newFiles
        if (newFiles.isNotEmpty()) {
            showMessage("تمت إضافة ${newFiles.size} ملف بنجاح")
        }
    }

    fun removeSelectedFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.filterNot { it.id == id }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun sendFiles(overrideIp: String? = null) {
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            showMessage("يرجى اختيار ملف واحد على الأقل للإرسال")
            return
        }

        val ip = overrideIp?.ifBlank { null }
            ?: _targetIp.value.ifBlank { null }
            ?: connectionState.value.groupOwnerAddress

        if (ip.isNullOrBlank()) {
            showMessage("لم يتم العثور على عنوان IP للمستلم. اتصل بجهاز أو أدخل الـ IP.")
            _showManualConnectDialog.value = true
            return
        }

        viewModelScope.launch {
            showMessage("جارٍ إرسال ${files.size} ملف إلى $ip...")
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
        showMessage("تم إلغاء عملية النقل")
    }

    fun clearActiveTransfer() {
        transferEngine.clearActiveTransfer()
    }

    fun openFile(context: Context, localPath: String, mimeType: String) {
        try {
            val file = File(localPath)
            if (!file.exists()) {
                showMessage("الملف غير موجود في ذاكرة التخزين")
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
            context.startActivity(Intent.createChooser(intent, "فتح الملف باستخدام"))
        } catch (e: Exception) {
            Log.e("TransferViewModel", "Cannot open file: ${e.message}")
            showMessage("لا يوجد تطبيق مثبت قادر على فتح هذا النوع من الملفات")
        }
    }

    fun shareFile(context: Context, localPath: String, mimeType: String) {
        try {
            val file = File(localPath)
            if (!file.exists()) {
                showMessage("الملف غير موجود في ذاكرة التخزين")
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
            context.startActivity(Intent.createChooser(intent, "مشاركة الملف"))
        } catch (e: Exception) {
            Log.e("TransferViewModel", "Cannot share file: ${e.message}")
            showMessage("تعذرت مشاركة الملف")
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

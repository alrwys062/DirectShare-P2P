package com.example.model

import android.net.Uri

enum class FileCategory(val displayName: String) {
    IMAGE("الصور"),
    VIDEO("الفيديوهات"),
    AUDIO("الصوتيات"),
    DOCUMENT("المستندات"),
    APK("تطبيقات (APK)"),
    ARCHIVE("ملفات مضغوطة"),
    OTHER("ملفات متنوعة")
}

data class SelectedFile(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: FileCategory
) {
    val formattedSize: String
        get() = formatBytes(size)
}

enum class TransferDirection(val label: String) {
    SENDING("إرسال"),
    RECEIVING("استقبال")
}

enum class TransferStatus(val label: String) {
    IDLE("جاهز"),
    CONNECTING("جارٍ الاتصال..."),
    TRANSFERRING("جارٍ النقل..."),
    COMPLETED("اكتمل بنجاح"),
    FAILED("فشل النقل"),
    CANCELLED("تم الإلغاء")
}

data class TransferItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val progress: Float = 0f,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.IDLE,
    val errorMessage: String? = null,
    val localFilePath: String? = null,
    val mimeType: String = "*/*",
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() = formatBytes(fileSize)

    val formattedSpeed: String
        get() = formatSpeed(speedBytesPerSec)

    val formattedTransferred: String
        get() = formatBytes(bytesTransferred)
}

data class PeerDevice(
    val deviceAddress: String,
    val deviceName: String,
    val status: Int,
    val isGroupOwner: Boolean = false
) {
    val statusText: String
        get() = when (status) {
            0 -> "متصل"
            1 -> "تمت الدعوة"
            2 -> "فشل"
            3 -> "متاح للاتصال"
            4 -> "غير متاح"
            else -> "غير معروف"
        }
}

data class P2PConnectionState(
    val isWifiP2pEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerAddress: String? = null,
    val localIpAddress: String? = null,
    val groupNetworkName: String? = null,
    val groupPassphrase: String? = null,
    val thisDeviceName: String = "جهاز أندرويد",
    val thisDeviceAddress: String = ""
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 بايت"
    val units = arrayOf("بايت", "ك.ب", "م.ب", "ج.ب", "ت.ب")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 ك.ب/ث"
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1024.0) {
        val mb = kb / 1024.0
        String.format(java.util.Locale.US, "%.2f م.ب/ث", mb)
    } else {
        String.format(java.util.Locale.US, "%.1f ك.ب/ث", kb)
    }
}

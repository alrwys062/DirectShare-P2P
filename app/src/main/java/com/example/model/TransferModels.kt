package com.example.model

import android.net.Uri
import com.example.localization.AppLanguage

enum class FileCategory(val arabicName: String, val englishName: String) {
    IMAGE("الصور", "Photos"),
    VIDEO("الفيديوهات", "Videos"),
    AUDIO("الصوتيات", "Audio"),
    DOCUMENT("المستندات", "Documents"),
    APK("تطبيقات (APK)", "APKs"),
    ARCHIVE("ملفات مضغوطة", "Archives"),
    OTHER("ملفات متنوعة", "Other Files");

    fun getDisplayName(language: AppLanguage): String =
        if (language == AppLanguage.ARABIC) arabicName else englishName
}

data class SelectedFile(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: FileCategory
) {
    fun getFormattedSize(language: AppLanguage = AppLanguage.ARABIC): String =
        formatBytes(size, language)
}

enum class TransferDirection(val arabicLabel: String, val englishLabel: String) {
    SENDING("إرسال", "Sending"),
    RECEIVING("استقبال", "Receiving");

    fun getLabel(language: AppLanguage): String =
        if (language == AppLanguage.ARABIC) arabicLabel else englishLabel
}

enum class TransferStatus(val arabicLabel: String, val englishLabel: String) {
    IDLE("جاهز", "Idle"),
    CONNECTING("جارٍ الاتصال...", "Connecting..."),
    TRANSFERRING("جارٍ النقل...", "Transferring..."),
    COMPLETED("اكتمل بنجاح", "Completed"),
    FAILED("فشل النقل", "Failed"),
    CANCELLED("تم الإلغاء", "Cancelled");

    fun getLabel(language: AppLanguage): String =
        if (language == AppLanguage.ARABIC) arabicLabel else englishLabel
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
    fun getFormattedSize(language: AppLanguage = AppLanguage.ARABIC): String =
        formatBytes(fileSize, language)

    fun getFormattedSpeed(language: AppLanguage = AppLanguage.ARABIC): String =
        formatSpeed(speedBytesPerSec, language)

    fun getFormattedTransferred(language: AppLanguage = AppLanguage.ARABIC): String =
        formatBytes(bytesTransferred, language)
}

data class PeerDevice(
    val deviceAddress: String,
    val deviceName: String,
    val status: Int,
    val isGroupOwner: Boolean = false
) {
    fun getStatusText(language: AppLanguage): String {
        return if (language == AppLanguage.ARABIC) {
            when (status) {
                0 -> "متصل"
                1 -> "تمت الدعوة"
                2 -> "فشل"
                3 -> "متاح للاتصال"
                4 -> "غير متاح"
                else -> "غير معروف"
            }
        } else {
            when (status) {
                0 -> "Connected"
                1 -> "Invited"
                2 -> "Failed"
                3 -> "Available"
                4 -> "Unavailable"
                else -> "Unknown"
            }
        }
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
    val thisDeviceName: String = "My Android Device",
    val thisDeviceAddress: String = ""
)

fun formatBytes(bytes: Long, language: AppLanguage = AppLanguage.ARABIC): String {
    if (bytes <= 0) return if (language == AppLanguage.ARABIC) "0 بايت" else "0 B"
    val units = if (language == AppLanguage.ARABIC) {
        arrayOf("بايت", "ك.ب", "م.ب", "ج.ب", "ت.ب")
    } else {
        arrayOf("B", "KB", "MB", "GB", "TB")
    }
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
}

fun formatSpeed(bytesPerSec: Long, language: AppLanguage = AppLanguage.ARABIC): String {
    if (bytesPerSec <= 0) return if (language == AppLanguage.ARABIC) "0 ك.ب/ث" else "0 KB/s"
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1024.0) {
        val mb = kb / 1024.0
        val unit = if (language == AppLanguage.ARABIC) "م.ب/ث" else "MB/s"
        String.format(java.util.Locale.US, "%.2f %s", mb, unit)
    } else {
        val unit = if (language == AppLanguage.ARABIC) "ك.ب/ث" else "KB/s"
        String.format(java.util.Locale.US, "%.1f %s", kb, unit)
    }
}

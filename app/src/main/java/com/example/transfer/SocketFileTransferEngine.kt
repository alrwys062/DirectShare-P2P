package com.example.transfer

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.example.model.SelectedFile
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class SocketFileTransferEngine(private val context: Context) {

    private val tag = "SocketTransferEngine"

    companion object {
        const val DEFAULT_PORT = 8888
        private const val MAGIC_HEADER = 0x53484152 // "SHAR"
        private const val PROTOCOL_VERSION = 1
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunks
        private const val SOCKET_TIMEOUT_MS = 30000 // 30 seconds
    }

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    @Volatile private var isTransferCancelled = false

    // State flow of current transfer
    private val _activeTransfer = MutableStateFlow<TransferItem?>(null)
    val activeTransfer: StateFlow<TransferItem?> = _activeTransfer.asStateFlow()

    // State flow of transfer history
    private val _transferHistory = MutableStateFlow<List<TransferItem>>(emptyList())
    val transferHistory: StateFlow<List<TransferItem>> = _transferHistory.asStateFlow()

    // Server listening state
    private val _isServerListening = MutableStateFlow(false)
    val isServerListening: StateFlow<Boolean> = _isServerListening.asStateFlow()

    /**
     * Starts TCP ServerSocket on specified port (default 8888) and accepts incoming connections.
     */
    suspend fun startReceivingServer(
        port: Int = DEFAULT_PORT,
        onFileReceived: (TransferItem) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (_isServerListening.value && serverSocket != null && !serverSocket!!.isClosed) {
            return@withContext
        }

        stopReceivingServer()
        isTransferCancelled = false

        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            _isServerListening.value = true
            Log.d(tag, "Server listening on port $port")

            while (_isServerListening.value && serverSocket?.isClosed == false) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    activeSocket = clientSocket
                    Log.d(tag, "Client connected: ${clientSocket.inetAddress.hostAddress}")
                    handleIncomingClient(clientSocket, onFileReceived)
                } catch (e: Exception) {
                    if (isTransferCancelled || !_isServerListening.value) {
                        Log.d(tag, "Server socket closed gracefully")
                        break
                    }
                    Log.e(tag, "Error accepting client: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start server: ${e.message}")
        } finally {
            _isServerListening.value = false
            stopReceivingServer()
        }
    }

    /**
     * Reads files sent by client according to protocol.
     */
    private suspend fun handleIncomingClient(
        socket: Socket,
        onFileReceived: (TransferItem) -> Unit
    ) = withContext(Dispatchers.IO) {
        var dataIn: DataInputStream? = null
        var dataOut: DataOutputStream? = null

        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            dataIn = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))
            dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))

            // 1. Verify Magic Header and Protocol Version
            val magic = dataIn.readInt()
            if (magic != MAGIC_HEADER) {
                Log.e(tag, "Invalid magic header: $magic")
                return@withContext
            }
            val version = dataIn.readInt()
            val totalFiles = dataIn.readInt()
            Log.d(tag, "Incoming batch: $totalFiles files (protocol v$version)")

            val storageDir = getReceiveDirectory()

            // 2. Process each file in the batch
            for (i in 0 until totalFiles) {
                if (isTransferCancelled) break

                val originalName = dataIn.readUTF()
                val fileSize = dataIn.readLong()
                val mimeType = dataIn.readUTF()

                val safeFileName = sanitizeFileName(originalName)
                val destFile = getUniqueFile(storageDir, safeFileName)

                val transferId = UUID.randomUUID().toString()
                var currentItem = TransferItem(
                    id = transferId,
                    fileName = originalName,
                    fileSize = fileSize,
                    bytesTransferred = 0L,
                    direction = TransferDirection.RECEIVING,
                    status = TransferStatus.TRANSFERRING,
                    mimeType = mimeType,
                    localFilePath = destFile.absolutePath
                )
                _activeTransfer.value = currentItem

                // Stream file payload
                val fileOut = BufferedOutputStream(FileOutputStream(destFile), BUFFER_SIZE)
                var bytesReadTotal = 0L
                val buffer = ByteArray(BUFFER_SIZE)
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L

                try {
                    while (bytesReadTotal < fileSize) {
                        if (isTransferCancelled) throw CancellationException("تم إلغاء عملية النقل")
                        val bytesToRead = Math.min(buffer.size.toLong(), fileSize - bytesReadTotal).toInt()
                        val count = dataIn.read(buffer, 0, bytesToRead)
                        if (count == -1) throw java.io.EOFException("انقطع الاتصال فجأة أثناء استلام الملف")

                        fileOut.write(buffer, 0, count)
                        bytesReadTotal += count
                        bytesSinceLastCalc += count

                        val now = System.currentTimeMillis()
                        val diffTime = now - lastSpeedCalcTime
                        if (diffTime >= 250 || bytesReadTotal == fileSize) {
                            val speed = if (diffTime > 0) (bytesSinceLastCalc * 1000) / diffTime else 0L
                            val progress = if (fileSize > 0) bytesReadTotal.toFloat() / fileSize else 0f
                            currentItem = currentItem.copy(
                                bytesTransferred = bytesReadTotal,
                                speedBytesPerSec = speed,
                                progress = progress
                            )
                            _activeTransfer.value = currentItem
                            lastSpeedCalcTime = now
                            bytesSinceLastCalc = 0L
                        }
                    }
                    fileOut.flush()
                } finally {
                    fileOut.close()
                }

                // Notify media scanner so images/videos appear in Gallery
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                } catch (_: Exception) {}

                // Send ACK to sender
                dataOut.writeByte(1)
                dataOut.flush()

                // Mark completed
                currentItem = currentItem.copy(
                    bytesTransferred = fileSize,
                    progress = 1f,
                    status = TransferStatus.COMPLETED
                )
                _activeTransfer.value = currentItem
                _transferHistory.value = listOf(currentItem) + _transferHistory.value
                onFileReceived(currentItem)
                Log.d(tag, "Successfully received ${destFile.name} (${destFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during receive: ${e.message}")
            _activeTransfer.value?.let { current ->
                val failedItem = current.copy(
                    status = if (isTransferCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED,
                    errorMessage = e.message ?: "فشل استلام الملف"
                )
                _activeTransfer.value = failedItem
                _transferHistory.value = listOf(failedItem) + _transferHistory.value
            }
        } finally {
            try {
                dataIn?.close()
                dataOut?.close()
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Connects as a client to a server IP on port and sends the selected files.
     */
    suspend fun sendFiles(
        serverIp: String,
        port: Int = DEFAULT_PORT,
        filesToSend: List<SelectedFile>,
        contentResolver: ContentResolver
    ) = withContext(Dispatchers.IO) {
        if (filesToSend.isEmpty()) return@withContext
        isTransferCancelled = false

        var socket: Socket? = null
        var dataOut: DataOutputStream? = null
        var dataIn: DataInputStream? = null

        try {
            _activeTransfer.value = TransferItem(
                id = UUID.randomUUID().toString(),
                fileName = "جارٍ الاتصال بـ $serverIp...",
                fileSize = filesToSend.sumOf { it.size },
                direction = TransferDirection.SENDING,
                status = TransferStatus.CONNECTING
            )

            socket = Socket()
            socket.connect(InetSocketAddress(serverIp, port), SOCKET_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS
            activeSocket = socket

            dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))
            dataIn = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))

            // 1. Send header: Magic, Version, Files Count
            dataOut.writeInt(MAGIC_HEADER)
            dataOut.writeInt(PROTOCOL_VERSION)
            dataOut.writeInt(filesToSend.size)
            dataOut.flush()

            // 2. Send each file
            for (file in filesToSend) {
                if (isTransferCancelled) break

                val transferId = UUID.randomUUID().toString()
                var currentItem = TransferItem(
                    id = transferId,
                    fileName = file.name,
                    fileSize = file.size,
                    bytesTransferred = 0L,
                    direction = TransferDirection.SENDING,
                    status = TransferStatus.TRANSFERRING,
                    mimeType = file.mimeType
                )
                _activeTransfer.value = currentItem

                // Send metadata
                dataOut.writeUTF(file.name)
                dataOut.writeLong(file.size)
                dataOut.writeUTF(file.mimeType)
                dataOut.flush()

                // Stream file contents
                val inputStream = contentResolver.openInputStream(file.uri)
                    ?: throw java.io.FileNotFoundException("تعذر الوصول إلى الملف: ${file.name}")
                val bufferedIn = BufferedInputStream(inputStream, BUFFER_SIZE)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesSentTotal = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L

                try {
                    var readCount: Int
                    while (bufferedIn.read(buffer).also { readCount = it } != -1) {
                        if (isTransferCancelled) throw CancellationException("تم إلغاء النقل من قِبل المستخدم")

                        dataOut.write(buffer, 0, readCount)
                        bytesSentTotal += readCount
                        bytesSinceLastCalc += readCount

                        val now = System.currentTimeMillis()
                        val diffTime = now - lastSpeedCalcTime
                        if (diffTime >= 250 || bytesSentTotal == file.size) {
                            val speed = if (diffTime > 0) (bytesSinceLastCalc * 1000) / diffTime else 0L
                            val progress = if (file.size > 0) bytesSentTotal.toFloat() / file.size else 0f
                            currentItem = currentItem.copy(
                                bytesTransferred = bytesSentTotal,
                                speedBytesPerSec = speed,
                                progress = progress
                            )
                            _activeTransfer.value = currentItem
                            lastSpeedCalcTime = now
                            bytesSinceLastCalc = 0L
                        }
                    }
                    dataOut.flush()
                } finally {
                    bufferedIn.close()
                }

                // Wait for ACK from receiver
                val ack = dataIn.readByte()
                if (ack.toInt() != 1) {
                    throw java.io.IOException("رفض المستلم استلام الملف: ${file.name}")
                }

                currentItem = currentItem.copy(
                    bytesTransferred = file.size,
                    progress = 1f,
                    status = TransferStatus.COMPLETED
                )
                _activeTransfer.value = currentItem
                _transferHistory.value = listOf(currentItem) + _transferHistory.value
                Log.d(tag, "Successfully sent ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Send failed: ${e.message}")
            _activeTransfer.value?.let { current ->
                val failedItem = current.copy(
                    status = if (isTransferCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED,
                    errorMessage = e.message ?: "فشلت عملية الإرسال"
                )
                _activeTransfer.value = failedItem
                _transferHistory.value = listOf(failedItem) + _transferHistory.value
            }
        } finally {
            try {
                dataOut?.close()
                dataIn?.close()
                socket?.close()
            } catch (_: Exception) {}
            activeSocket = null
        }
    }

    fun cancelActiveTransfer() {
        isTransferCancelled = true
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        _activeTransfer.value = _activeTransfer.value?.copy(
            status = TransferStatus.CANCELLED,
            errorMessage = "تم إلغاء عملية النقل"
        )
    }

    fun clearActiveTransfer() {
        _activeTransfer.value = null
    }

    fun stopReceivingServer() {
        _isServerListening.value = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun getReceiveDirectory(): File {
        val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "ReceivedFiles")
        if (!downloads.exists()) {
            downloads.mkdirs()
        }
        return downloads
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- \u0600-\u06FF]"), "_")
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var count = 1

        while (file.exists()) {
            file = File(dir, "${nameWithoutExt}_$count$ext")
            count++
        }
        return file
    }
}

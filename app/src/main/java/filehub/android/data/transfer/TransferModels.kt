package filehub.android.data.transfer

import kotlinx.serialization.Serializable

@Serializable
enum class TransferKind {
    UPLOAD,
    DOWNLOAD,
}

@Serializable
enum class TransferStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class TransferItem(
    val id: String,
    val kind: TransferKind,
    val profileId: String,
    val baseUrl: String,
    val rootId: Long,
    val remoteDir: String,
    val remotePath: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val localUri: String,
    val mtime: Long,
    val bytesDone: Long = 0,
    val status: TransferStatus = TransferStatus.QUEUED,
    val error: String? = null,
    val uploadId: String? = null,
    val chunkSize: Long = 0,
    val localFile: String? = null,
    val createdAt: Long = System.currentTimeMillis() / 1000,
    val updatedAt: Long = System.currentTimeMillis() / 1000,
) {
    val progress: Float
        get() = if (size <= 0) 0f else (bytesDone.toFloat() / size).coerceIn(0f, 1f)
}

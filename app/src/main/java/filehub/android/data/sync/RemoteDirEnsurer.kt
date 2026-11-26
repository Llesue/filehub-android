package filehub.android.data.sync

import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException

/**
 * Makes sure a remote directory exists without failing on directories that were
 * created earlier. Older FileHub servers return 500 for mkdir on an existing
 * directory, so every segment is probed first.
 */
internal class RemoteDirEnsurer(
    private val api: FileHubApi,
    private val rootId: Long,
) {
    private val known = mutableSetOf<String>()

    suspend fun ensure(directory: String) {
        val path = directory.trim('/').replace(Regex("/+"), "/")
        if (path.isEmpty() || path in known) return

        // Always walk from the storage root. Probing the complete deep path
        // first fails on servers that return 500 ("parent directory does not
        // exist") instead of 404 when an intermediate directory is missing.
        var parent = ""
        for (segment in path.split('/')) {
            val current = if (parent.isEmpty()) segment else "$parent/$segment"
            if (current in known) {
                parent = current
                continue
            }
            if (!exists(current)) {
                mkdir(parent, segment)
            }
            known += current
            parent = current
        }
    }

    private suspend fun exists(path: String): Boolean =
        try {
            api.properties(rootId, path).isDir
        } catch (error: FileHubApiException) {
            when {
                error.statusCode == 404 -> false
                error.statusCode == 500 &&
                    error.message.contains("parent directory does not exist", ignoreCase = true) ->
                    false
                else -> throw error
            }
        }

    private suspend fun mkdir(parent: String, name: String) {
        try {
            api.mkdir(rootId, parent, name)
        } catch (error: FileHubApiException) {
            // FileHub >= 0.2.2 reports an existing directory as 409; the race is harmless.
            val existing = error.statusCode == 409 ||
                (error.statusCode == 500 &&
                    error.message.contains("already exists", ignoreCase = true))
            if (!existing) throw error
        }
    }
}

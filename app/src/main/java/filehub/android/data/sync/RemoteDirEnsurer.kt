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

        if (exists(path)) {
            known += path
            return
        }

        var parent = ""
        for (segment in path.split('/')) {
            val current = if (parent.isEmpty()) segment else "$parent/$segment"
            if (current in known) {
                parent = current
                continue
            }
            if (current == path) {
                mkdir(parent, segment)
            } else if (!exists(current)) {
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
            if (error.statusCode == 404) false else throw error
        }

    private suspend fun mkdir(parent: String, name: String) {
        try {
            api.mkdir(rootId, parent, name)
        } catch (error: FileHubApiException) {
            if (error.statusCode !in listOf(409, 403)) throw error
        }
    }
}

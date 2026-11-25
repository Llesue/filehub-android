package filehub.android.ui

import filehub.android.core.model.FileRoot

internal data class RootGroups(
    val online: List<FileRoot>,
    val offline: List<FileRoot>,
)

internal fun rootGroups(roots: List<FileRoot>): RootGroups = RootGroups(
    online = roots.filter { it.online != false },
    offline = roots.filter { it.online == false },
)

internal fun selectableRoots(roots: List<FileRoot>): List<FileRoot> =
    roots.filter { it.online != false }

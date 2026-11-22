package filehub.android.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal object NetworkStatus {
    /** True when the active network is metered or unavailable, i.e. not safe for video uploads. */
    fun isMetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

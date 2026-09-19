package dev.cernoh.quotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/** Reports the outcome of the install the user approved. */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> R.string.install_waiting
            PackageInstaller.STATUS_SUCCESS -> R.string.install_ok
            PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.install_cancelled
            PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.install_blocked
            PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.install_conflict
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.install_incompatible
            PackageInstaller.STATUS_FAILURE_INVALID -> R.string.install_invalid
            PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.install_storage
            else -> R.string.install_unknown
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

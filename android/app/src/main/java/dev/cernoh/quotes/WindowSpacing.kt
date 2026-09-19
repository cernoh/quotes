package dev.cernoh.quotes

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Adds the window insets to the base padding of the root view.
 *
 * The app targets API 36, so Android draws it edge to edge: without this, the
 * card starts under the status bar and near the camera cutout, and the last
 * control of a form can sit under the navigation bar.
 *
 * On API 30 and later the insets come from the system bar and display cutout
 * types. On older releases the deprecated system window insets carry the same
 * numbers, and the window is inset by the system anyway, which leaves them zero.
 */
object WindowSpacing {
    fun apply(activity: Activity, root: View) {
        val base = activity.resources.getDimensionPixelSize(R.dimen.screen_padding)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Insets.of(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            view.setPadding(
                base + bars.left,
                base + bars.top,
                base + bars.right,
                base + bars.bottom,
            )
            insets
        }
        root.requestApplyInsets()
    }
}

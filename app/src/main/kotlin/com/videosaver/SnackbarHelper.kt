package com.videosaver

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {

    fun showSuccess(view: View, message: String) {
        show(view, message, "#00D9A3", Snackbar.LENGTH_LONG)
    }

    fun showError(view: View, message: String) {
        show(view, message, "#FF4757", Snackbar.LENGTH_LONG)
    }

    fun showInfo(view: View, message: String) {
        show(view, message, "#1C1C27", Snackbar.LENGTH_SHORT)
    }

    fun showUndo(view: View, message: String, actionLabel: String, onUndo: () -> Unit) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setAction(actionLabel) { onUndo() }
        snackbar.setActionTextColor(Color.parseColor("#6C63FF"))
        val snackbarView = snackbar.view
        val radius = 12f * view.context.resources.displayMetrics.density
        snackbarView.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1C1C27"))
            cornerRadius = radius
        }
        val params = snackbarView.layoutParams as? ViewGroup.MarginLayoutParams
        val margin = (16 * view.context.resources.displayMetrics.density).toInt()
        params?.setMargins(margin, 0, margin, margin)
        snackbarView.layoutParams = params
        snackbar.show()
    }

    private fun show(view: View, message: String, colorHex: String, duration: Int) {
        val snackbar = Snackbar.make(view, message, duration)
        val snackbarView = snackbar.view

        val radius = 12f * view.context.resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = radius
        }
        snackbarView.background = bg

        val params = snackbarView.layoutParams as? ViewGroup.MarginLayoutParams
        val margin = (16 * view.context.resources.displayMetrics.density).toInt()
        params?.setMargins(margin, 0, margin, margin)
        snackbarView.layoutParams = params

        val tv = snackbar.view.findViewById<android.widget.TextView>(
            com.google.android.material.R.id.snackbar_text
        )
        tv?.setTextColor(Color.WHITE)
        tv?.textSize = 14f

        snackbar.show()
    }
}

package com.videosaver

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.videosaver.prefs.AppPrefs

class PrivacyDisclaimerDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.privacy_disclaimer_title)
            .setMessage(R.string.privacy_disclaimer_message)
            .setPositiveButton(R.string.privacy_accept) { _, _ ->
                AppPrefs.setDisclaimerAccepted(requireContext())
            }
            .setNeutralButton(R.string.privacy_view_policy) { _, _ ->
                startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
                AppPrefs.setDisclaimerAccepted(requireContext())
            }
            .setCancelable(false)
            .create()

    companion object {
        const val TAG = "privacy_disclaimer"

        fun showIfNeeded(
            fragmentManager: androidx.fragment.app.FragmentManager,
            context: android.content.Context,
        ) {
            if (!AppPrefs.isDisclaimerAccepted(context)) {
                PrivacyDisclaimerDialog().show(fragmentManager, TAG)
            }
        }
    }
}

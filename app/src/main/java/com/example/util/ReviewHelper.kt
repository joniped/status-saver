package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.example.R
import com.google.android.play.core.review.ReviewManagerFactory

object ReviewHelper {
    private const val TAG = "ReviewHelper"
    private const val PREFS_NAME = "review_prefs"
    private const val KEY_SAVED_COUNT = "key_saved_count"
    private const val KEY_HAS_REVIEWED = "key_has_reviewed"
    private const val REVIEW_TRIGGER_THRESHOLD = 2 // Prompt after saving 2 statuses

    fun notifyStatusSaved(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_REVIEWED, false)) {
            return
        }

        val currentCount = prefs.getInt(KEY_SAVED_COUNT, 0) + 1
        prefs.edit().putInt(KEY_SAVED_COUNT, currentCount).apply()

        Log.d(TAG, "Status saved! Total saves: $currentCount")

        if (currentCount >= REVIEW_TRIGGER_THRESHOLD) {
            launchInAppReview(activity)
        }
    }

    fun launchInAppReview(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        Log.d(TAG, "In-App Review flow finished")
                        prefs.edit().putBoolean(KEY_HAS_REVIEWED, true).apply()
                    }
                } else {
                    Log.w(TAG, "In-App Review request failed, showing fallback rate dialog", task.exception)
                    showFallbackRateDialog(activity)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching In-App Review", e)
            showFallbackRateDialog(activity)
        }
    }

    fun showFallbackRateDialog(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AlertDialog.Builder(activity)
            .setTitle(R.string.review_prompt_title)
            .setMessage(R.string.review_prompt_message)
            .setPositiveButton(R.string.rate_now) { _, _ ->
                prefs.edit().putBoolean(KEY_HAS_REVIEWED, true).apply()
                openPlayStore(activity)
            }
            .setNegativeButton(R.string.rate_later, null)
            .setNeutralButton(R.string.rate_no_thanks) { _, _ ->
                prefs.edit().putBoolean(KEY_HAS_REVIEWED, true).apply()
            }
            .show()
    }

    fun openPlayStore(context: Context) {
        val pkg = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    fun directShareApp(context: Context) {
        try {
            val shareText = context.getString(R.string.share_app_text, context.packageName)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_app)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share app", e)
        }
    }
}

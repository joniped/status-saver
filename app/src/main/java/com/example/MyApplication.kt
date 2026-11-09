package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.ads.UnityAdsManager

class MyApplication : Application(), DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "MyApplication"
    }

    private var currentActivity: Activity? = null
    private var isColdBoot: Boolean = true
    private var wasInBackground: Boolean = false

    override fun onCreate() {
        super<Application>.onCreate()
        Log.d(TAG, "MyApplication onCreate")

        // 1. Initialize Unity Ads
        UnityAdsManager.init(this)

        // 2. Register ProcessLifecycleOwner observer for app foreground/background detection
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // 3. Register ActivityLifecycleCallbacks to keep track of current active Activity
        registerActivityLifecycleCallbacks(this)
    }

    // DefaultLifecycleObserver methods
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "App entered FOREGROUND (onStart). wasInBackground=$wasInBackground, isColdBoot=$isColdBoot")

        if (isColdBoot) {
            // Ignore cold boot launch
            isColdBoot = false
            wasInBackground = false
            return
        }

        if (wasInBackground) {
            wasInBackground = false
            val activity = currentActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                if (UnityAdsManager.canShowResumeAd()) {
                    Log.d(TAG, "Showing App Resume Interstitial Ad on $activity")
                    UnityAdsManager.showInterstitial(activity)
                } else {
                    Log.d(TAG, "Cannot show resume ad (cooldown active or ad not ready)")
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "App entered BACKGROUND (onStop)")
        wasInBackground = true
    }

    // ActivityLifecycleCallbacks methods
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}

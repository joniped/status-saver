package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

object UnityAdsManager {
    private const val TAG = "UnityAdsManager"

    // Configuration
    const val UNITY_GAME_ID = "5270635"
    const val DEFAULT_TEST_MODE = false // Try real ads first, with automatic test fallback on NO_FILL

    var currentTestMode = DEFAULT_TEST_MODE
        private set

    const val BANNER_PLACEMENT_ID = "Banner_Android"
    const val INTERSTITIAL_PLACEMENT_ID = "Interstitial_Android"

    private const val AD_COOLDOWN_MS = 30_000L // 30 seconds cooldown
    private var lastAdShowTimeMs = 0L

    var isInitialized = false
        private set
    var isInterstitialLoaded = false
        private set
    var isAdShowing = false
        private set

    private val initCallbacks = mutableListOf<() -> Unit>()

    fun init(context: Context) {
        if (isInitialized) {
            triggerInitCallbacks()
            return
        }
        Log.d(TAG, "Initializing Unity Ads with Game ID: $UNITY_GAME_ID, testMode: $currentTestMode")
        UnityAds.initialize(
            context.applicationContext,
            UNITY_GAME_ID,
            currentTestMode,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.d(TAG, "Unity Ads initialized successfully (testMode=$currentTestMode)")
                    isInitialized = true
                    triggerInitCallbacks()
                    loadInterstitial(context)
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    Log.e(TAG, "Unity Ads initialization failed: $error, msg: $message")
                    isInitialized = false
                }
            }
        )
    }

    private fun triggerInitCallbacks() {
        val callbacks = ArrayList(initCallbacks)
        initCallbacks.clear()
        callbacks.forEach { it.invoke() }
    }

    fun doWhenInitialized(action: () -> Unit) {
        if (isInitialized) {
            action.invoke()
        } else {
            initCallbacks.add(action)
        }
    }

    fun loadInterstitial(context: Context? = null) {
        if (!isInitialized) {
            Log.w(TAG, "Unity Ads not initialized yet. Skipping interstitial load.")
            return
        }
        Log.d(TAG, "Loading Interstitial Ad: $INTERSTITIAL_PLACEMENT_ID (testMode=$currentTestMode)")
        UnityAds.load(
            INTERSTITIAL_PLACEMENT_ID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    Log.d(TAG, "Interstitial ad loaded: $placementId")
                    if (placementId == INTERSTITIAL_PLACEMENT_ID) {
                        isInterstitialLoaded = true
                    }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    Log.w(TAG, "Interstitial ad failed to load: $placementId, err: $error, msg: $message")
                    if (placementId == INTERSTITIAL_PLACEMENT_ID) {
                        isInterstitialLoaded = false
                    }

                    // Fallback to test mode if production returns NO_FILL
                    if ((message?.contains("NO_FILL", ignoreCase = true) == true ||
                                error?.toString()?.contains("NO_FILL", ignoreCase = true) == true) &&
                        !currentTestMode && context != null
                    ) {
                        Log.i(TAG, "Interstitial NO_FILL received. Switching to test fill fallback...")
                        currentTestMode = true
                        isInitialized = false
                        init(context)
                    }
                }
            }
        )
    }

    fun canShowResumeAd(): Boolean {
        if (!isInitialized || !isInterstitialLoaded || isAdShowing) {
            return false
        }
        val elapsed = SystemClock.elapsedRealtime() - lastAdShowTimeMs
        return elapsed >= AD_COOLDOWN_MS
    }

    fun showInterstitial(activity: Activity, onComplete: (() -> Unit)? = null): Boolean {
        if (!isInitialized || !isInterstitialLoaded || isAdShowing) {
            Log.d(TAG, "Cannot show ad: init=$isInitialized, loaded=$isInterstitialLoaded, showing=$isAdShowing")
            onComplete?.invoke()
            return false
        }

        isAdShowing = true
        lastAdShowTimeMs = SystemClock.elapsedRealtime()

        UnityAds.show(
            activity,
            INTERSTITIAL_PLACEMENT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    Log.e(TAG, "Interstitial show failed: $placementId, err: $error, msg: $message")
                    isAdShowing = false
                    isInterstitialLoaded = false
                    onComplete?.invoke()
                    loadInterstitial(activity) // Reload on failure
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Interstitial show started: $placementId")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Interstitial ad clicked: $placementId")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState?
                ) {
                    Log.d(TAG, "Interstitial show complete: $placementId, state: $state")
                    isAdShowing = false
                    isInterstitialLoaded = false
                    onComplete?.invoke()
                    loadInterstitial(activity) // Automatically reload a new interstitial
                }
            }
        )
        return true
    }

    /**
     * Creates and attaches a centered 320x50 Unity BannerView to the container.
     * Automatically handles delayed initialization and NO_FILL fallback.
     */
    fun setupBanner(
        activity: Activity,
        container: ViewGroup,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String?) -> Unit)? = null
    ): BannerView {
        val bannerView = BannerView(activity, BANNER_PLACEMENT_ID, UnityBannerSize(320, 50))
        
        container.removeAllViews()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        container.addView(bannerView, params)

        bannerView.listener = object : BannerView.IListener {
            override fun onBannerLoaded(bannerAdView: BannerView) {
                Log.d(TAG, "Banner ad loaded successfully")
                activity.runOnUiThread {
                    container.visibility = View.VISIBLE
                    onLoaded?.invoke()
                }
            }

            override fun onBannerShown(bannerAdView: BannerView) {
                Log.d(TAG, "Banner ad shown")
                activity.runOnUiThread {
                    container.visibility = View.VISIBLE
                }
            }

            override fun onBannerFailedToLoad(
                bannerAdView: BannerView,
                errorInfo: BannerErrorInfo?
            ) {
                val errorMsg = errorInfo?.errorMessage ?: "Unknown error"
                Log.w(TAG, "Banner ad failed to load: $errorMsg (testMode=$currentTestMode)")

                // If real ads return NO_FILL on test/dev devices, fall back to test mode so ads reliably render
                if (errorMsg.contains("NO_FILL", ignoreCase = true) && !currentTestMode) {
                    Log.i(TAG, "Banner NO_FILL on production. Falling back to test mode for guaranteed fill...")
                    currentTestMode = true
                    isInitialized = false
                    UnityAds.initialize(
                        activity.applicationContext,
                        UNITY_GAME_ID,
                        true,
                        object : IUnityAdsInitializationListener {
                            override fun onInitializationComplete() {
                                isInitialized = true
                                activity.runOnUiThread {
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        try {
                                            bannerAdView.destroy()
                                        } catch (e: Exception) {
                                            // ignore
                                        }
                                        setupBanner(activity, container, onLoaded, onFailed)
                                    }
                                }
                            }

                            override fun onInitializationFailed(
                                error: UnityAds.UnityAdsInitializationError?,
                                message: String?
                            ) {
                                activity.runOnUiThread {
                                    container.visibility = View.GONE
                                    onFailed?.invoke(message)
                                }
                            }
                        }
                    )
                    return
                }

                activity.runOnUiThread {
                    container.visibility = View.GONE
                    onFailed?.invoke(errorMsg)
                }
            }

            override fun onBannerClick(bannerAdView: BannerView) {
                Log.d(TAG, "Banner clicked")
            }

            override fun onBannerLeftApplication(bannerAdView: BannerView) {
                Log.d(TAG, "Banner left application")
            }
        }

        // Wait for SDK initialization before calling load()
        doWhenInitialized {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    bannerView.load()
                }
            }
        }

        return bannerView
    }
}

package com.example.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.MainActivity
import com.example.R
import com.example.databinding.ActivityOnboardingBinding
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    companion object {
        const val PREFS_NAME = "status_saver_prefs"
        const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"

        fun isOnboardingCompleted(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }

        fun markOnboardingCompleted(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle WindowInsets for Edge-to-Edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOnboarding) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutTopOnboard.updatePadding(top = systemBars.top)
            binding.layoutBottomOnboard.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setupViewPager()
        setupListeners()
    }

    private fun setupViewPager() {
        val slides = listOf(
            OnboardingSlide(
                imageRes = R.drawable.onboard_save_1794238805082,
                titleRes = R.string.onboard_title_1,
                descRes = R.string.onboard_desc_1
            ),
            OnboardingSlide(
                imageRes = R.drawable.onboard_dual_1794238823994,
                titleRes = R.string.onboard_title_2,
                descRes = R.string.onboard_desc_2
            ),
            OnboardingSlide(
                imageRes = R.drawable.onboard_play_1794238841574,
                titleRes = R.string.onboard_title_3,
                descRes = R.string.onboard_desc_3
            )
        )

        binding.viewPagerOnboard.adapter = OnboardingAdapter(slides)

        // Attach TabLayout as dot indicators
        TabLayoutMediator(binding.tabIndicator, binding.viewPagerOnboard) { _, _ -> }.attach()

        binding.viewPagerOnboard.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val isLastPage = (position == slides.size - 1)
                if (isLastPage) {
                    binding.btnNext.setText(R.string.get_started)
                    binding.btnSkip.visibility = View.INVISIBLE
                } else {
                    binding.btnNext.setText(R.string.next)
                    binding.btnSkip.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun setupListeners() {
        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }

        binding.btnNext.setOnClickListener {
            val currentPos = binding.viewPagerOnboard.currentItem
            val adapter = binding.viewPagerOnboard.adapter ?: return@setOnClickListener
            if (currentPos < adapter.itemCount - 1) {
                binding.viewPagerOnboard.currentItem = currentPos + 1
            } else {
                finishOnboarding()
            }
        }
    }

    private fun finishOnboarding() {
        markOnboardingCompleted(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

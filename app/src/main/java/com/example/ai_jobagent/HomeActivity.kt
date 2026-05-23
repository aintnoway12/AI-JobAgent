package com.example.ai_jobagent

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.ai_jobagent.databinding.ActivityHomeBinding
import com.example.ai_jobagent.databinding.NavHeaderUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth

    private val resumeFragment = ResumeFragment()
    private val newsFragment = NewsFragment()
    private val aiChatFragment = AiChatFragment()
    private var activeFragment: Fragment = newsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        if (auth.currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupFragments()
        setupBottomNavigation()
        loadUserInfoIntoDrawer()
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, newsFragment, "news")
            add(R.id.fragmentContainer, aiChatFragment, "chat").hide(aiChatFragment)
            add(R.id.fragmentContainer, resumeFragment, "resume").hide(resumeFragment)
        }.commitNow()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_news
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_resume -> {
                    showFragment(resumeFragment)
                    supportActionBar?.title = "이력서 작성"
                }
                R.id.nav_news -> {
                    showFragment(newsFragment)
                    supportActionBar?.title = "취업 뉴스"
                }
                R.id.nav_chat -> {
                    showFragment(aiChatFragment)
                    supportActionBar?.title = "AI 모의 면접"
                }
            }
            true
        }
        supportActionBar?.title = "취업 뉴스"
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(fragment)
            .commit()
        activeFragment = fragment
    }

    private fun loadUserInfoIntoDrawer() {
        val user = auth.currentUser ?: return
        val headerView = binding.navView.getHeaderView(0)
        val headerBinding = NavHeaderUserBinding.bind(headerView)

        headerBinding.tvEmail.text = user.email ?: ""

        Firebase.firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val nickname = doc.getString("nickname")
                    ?: user.email?.split("@")?.get(0)
                    ?: "User"
                val photoUrl = doc.getString("photoUrl")

                headerBinding.tvNickname.text = nickname

                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_placeholder)
                        .into(headerBinding.ivUserPhoto)
                }
            }
    }

    fun refreshDrawerPhoto() {
        val user = auth.currentUser ?: return
        val headerView = binding.navView.getHeaderView(0)
        val headerBinding = NavHeaderUserBinding.bind(headerView)
        Firebase.firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val photoUrl = doc.getString("photoUrl")
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_placeholder)
                        .into(headerBinding.ivUserPhoto)
                }
            }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

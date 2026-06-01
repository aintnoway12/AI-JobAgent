package com.example.ai_jobagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ai_jobagent.databinding.ActivityHomeBinding
import com.example.ai_jobagent.databinding.NavHeaderUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

        // 로그인되지 않은 상태면 메인(로그인) 화면으로 이동
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

        // 사이드 메뉴 설정 버튼들(변경, 로그아웃, 탈퇴) 클릭 이벤트 등록
        setupHeaderButtons()
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
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: window.decorView
        imm.hideSoftInputFromWindow(view.windowToken, 0)

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
                // nickname 대신 name 필드를 가져오도록 수정
                val userName = doc.getString("name")
                    ?: user.email?.split("@")?.get(0)
                    ?: "User"
                val photoUrl = doc.getString("photoUrl")

                headerBinding.tvNickname.text = userName // 변수명 맞춰서 세팅

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

    // ==========================================
    // 설정 관련 로직 (아이디, 비밀번호, 로그아웃, 탈퇴)
    // ==========================================

    private fun setupHeaderButtons() {
        val headerView = binding.navView.getHeaderView(0)
        val headerBinding = NavHeaderUserBinding.bind(headerView)

        headerBinding.btnChangeId.setOnClickListener { showChangeIdDialog() }
        headerBinding.btnChangePw.setOnClickListener { showChangePwDialog() }
        headerBinding.btnLogout.setOnClickListener { showLogoutDialog() }
        headerBinding.btnDeleteAccount.setOnClickListener { showDeleteAccountDialog() }
    }

    private fun showChangeIdDialog() {
        val editText = EditText(this)
        editText.hint = "새로운 이름 입력"

        AlertDialog.Builder(this)
            .setTitle("이름 변경")
            .setView(editText)
            .setPositiveButton("변경") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    changeName(newName) // 이름 변경 함수 호출
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 💡 Firestore의 'name' 필드를 업데이트하도록 변경
    private fun changeName(newName: String) {
        val uid = auth.currentUser?.uid
        val db = Firebase.firestore

        if (uid != null) {
            lifecycleScope.launch {
                try {
                    // "nickname" 대신 "name" 필드 업데이트
                    db.collection("users").document(uid).update("name", newName).await()
                    Toast.makeText(this@HomeActivity, "이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()

                    val headerView = binding.navView.getHeaderView(0)
                    val headerBinding = NavHeaderUserBinding.bind(headerView)
                    headerBinding.tvNickname.text = newName
                } catch (e: Exception) {
                    Toast.makeText(this@HomeActivity, "오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 2. 비밀번호 변경
    private fun showChangePwDialog() {
        val editText = EditText(this)
        editText.hint = "새로운 비밀번호 입력"

        AlertDialog.Builder(this)
            .setTitle("비밀번호 변경")
            .setView(editText)
            .setPositiveButton("변경") { _, _ ->
                val newPassword = editText.text.toString().trim()
                if (newPassword.isNotEmpty()) {
                    changePassword(newPassword)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun changePassword(newPassword: String) {
        val user = auth.currentUser
        if (user != null) {
            lifecycleScope.launch {
                try {
                    user.updatePassword(newPassword).await()
                    Toast.makeText(this@HomeActivity, "비밀번호가 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@HomeActivity, "오류: ${e.message} (최근 로그인 상태가 필요할 수 있습니다)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 3. 로그아웃
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("로그아웃")
            .setMessage("정말로 로그아웃 하시겠습니까?")
            .setPositiveButton("로그아웃") { _, _ ->
                logout()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun logout() {
        auth.signOut()
        Toast.makeText(this@HomeActivity, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()

        val intent = Intent(this@HomeActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // 4. 회원 탈퇴
    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("회원 탈퇴")
            .setMessage("정말로 탈퇴하시겠습니까? 모든 데이터가 삭제되며 복구할 수 없습니다.")
            .setPositiveButton("탈퇴") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser
        val uid = user?.uid
        val db = Firebase.firestore

        if (user != null && uid != null) {
            lifecycleScope.launch {
                try {
                    // Firestore 데이터 삭제
                    db.collection("users").document(uid).delete().await()

                    // Auth 계정 삭제
                    user.delete().await()

                    Toast.makeText(this@HomeActivity, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()

                    // 로그인 화면으로 이동
                    val intent = Intent(this@HomeActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@HomeActivity, "탈퇴 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
package com.example.ai_jobagent

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ai_jobagent.databinding.ActivityMainBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FirebaseApp.initializeApp(this)

        auth = Firebase.auth
        db = Firebase.firestore

        auth.signOut()

        if (auth.currentUser != null) {
            goToResumeWrite()
        }

        binding.btnGoToLogin.setOnClickListener { showLayout(View.GONE, View.VISIBLE, View.GONE) }
        binding.btnGoToRegister.setOnClickListener { showLayout(View.GONE, View.GONE, View.VISIBLE) }
        binding.btnBackFromLogin.setOnClickListener { showLayout(View.VISIBLE, View.GONE, View.GONE) }
        binding.btnBackFromReg.setOnClickListener { showLayout(View.VISIBLE, View.GONE, View.GONE) }

        binding.btnLoginDone.setOnClickListener {
            val email = binding.etLoginEmail.text.toString().trim()
            val pw = binding.etLoginPw.text.toString().trim()

            if (email.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "항목을 기입해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener {
                    Toast.makeText(this, "로그인 인증 통과!", Toast.LENGTH_SHORT).show()
                    goToResumeWrite()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "인증 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        binding.btnRegDone.setOnClickListener {
            val email = binding.etRegEmail.text.toString().trim()
            val name = binding.etRegName.text.toString().trim() // Added name variable
            val nickname = binding.etRegId.text.toString().trim()
            val pw = binding.etRegPw.text.toString().trim()

            // Added name validation
            if (email.isEmpty() || name.isEmpty() || nickname.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val result = auth.createUserWithEmailAndPassword(email, pw).await()
                    val uid = result.user?.uid ?: throw Exception("유저 UID 확인 불가")

                    val userProfile = hashMapOf(
                        "email" to email,
                        "name" to name, // Added name to Firestore map
                        "nickname" to nickname,
                        "photoUrl" to null,
                        "createdAt" to Timestamp.now()
                    )

                    withContext(Dispatchers.IO) {
                        db.collection("users").document(uid).set(userProfile).await()
                    }

                    Toast.makeText(this@MainActivity, "계정 생성 및 데이터베이스 등록 완료!", Toast.LENGTH_SHORT).show()
                    goToResumeWrite()

                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "가입 실패 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLayout(landing: Int, login: Int, reg: Int) {
        binding.layoutLanding.visibility = landing
        binding.layoutLogin.visibility = login
        binding.layoutRegister.visibility = reg
    }

    private fun goToResumeWrite() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
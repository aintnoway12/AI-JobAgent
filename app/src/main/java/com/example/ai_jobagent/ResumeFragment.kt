package com.example.ai_jobagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ai_jobagent.databinding.FragmentResumeBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ResumeFragment : Fragment() {

    private var _binding: FragmentResumeBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var classifier: JobClassifier

    private var computedKeywords: List<String> = emptyList()

    // 💡 새로 추가: 현재 불러온 이력서의 문서 ID를 저장
    private var currentResumeDocId: String? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            binding.ivProfile.imageTintList = null
            binding.ivProfile.setImageURI(it)
            uploadProfilePhoto(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResumeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        db = Firebase.firestore
        storage = Firebase.storage
        classifier = JobClassifier(requireContext())

        // 화면이 열릴 때 기존 이력서와 사진이 있는지 확인해서 불러옴
        loadExistingResumeAndPhoto()

        binding.btnPickPhoto.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnCreateResume.setOnClickListener {
            handleResumeCreationFlow()
        }

        // 💡 수정하기 버튼 클릭 시 모드 변경
        binding.btnEditResume.setOnClickListener {
            setEditMode(true)
        }

        // 💡 삭제하기 버튼 클릭 로직
        binding.btnDeleteResume.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ==========================================
    // 1. 기존 데이터 불러오기 및 UI 모드 제어
    // ==========================================

    private fun loadExistingResumeAndPhoto() = lifecycleScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch

        // 1. 사용자 사진 불러오기
        try {
            val userDoc = db.collection("users").document(uid).get().await()
            val photoUrl = userDoc.getString("photoUrl")
            if (!photoUrl.isNullOrEmpty()) {
                loadImageFromUrlIntoView(photoUrl)
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 2. 가장 최근에 저장한 이력서 데이터 불러오기
        try {
            val snapshot = db.collection("users").document(uid).collection("resumes")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get().await()

            if (!snapshot.isEmpty) {
                // 저장된 이력서가 있으면 데이터를 채우고 보기(View) 모드로 설정
                val doc = snapshot.documents[0]
                currentResumeDocId = doc.id

                binding.etSkills.setText(doc.get("skills")?.let { (it as List<*>).joinToString(", ") })
                binding.etProjects.setText(doc.getString("projects"))
                binding.etAwards.setText(doc.getString("awards"))
                binding.etGpa.setText(doc.getString("gpa"))
                binding.etCert.setText(doc.getString("certificates"))
                binding.etHighlight.setText(doc.getString("highlight"))

                setEditMode(false)
            } else {
                // 저장된 이력서가 없으면 작성(Edit) 모드로 설정
                setEditMode(true)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "이력서를 불러오는 중 오류 발생", Toast.LENGTH_SHORT).show()
        }
    }

    // 입력 모드(true)와 보기 모드(false)를 전환하는 함수
    private fun setEditMode(isEdit: Boolean) {
        // 모든 입력칸 활성/비활성화
        binding.etSkills.isEnabled = isEdit
        binding.etProjects.isEnabled = isEdit
        binding.etAwards.isEnabled = isEdit
        binding.etGpa.isEnabled = isEdit
        binding.etCert.isEnabled = isEdit
        binding.etHighlight.isEnabled = isEdit

        // 버튼들 보이기/숨기기 처리
        binding.btnPickPhoto.visibility = if (isEdit) View.VISIBLE else View.GONE
        binding.btnCreateResume.visibility = if (isEdit) View.VISIBLE else View.GONE
        binding.layoutViewButtons.visibility = if (isEdit) View.GONE else View.VISIBLE

        if (isEdit && currentResumeDocId != null) {
            binding.btnCreateResume.text = "이력서 수정 완료하기"
        }
    }

    // ==========================================
    // 2. 사진 관련 로직 (기존과 동일)
    // ==========================================

    private fun uploadProfilePhoto(uri: Uri) = lifecycleScope.launch {
        val safeContext = context ?: return@launch
        val uid = auth.currentUser?.uid ?: return@launch
        try {
            binding.btnPickPhoto.isEnabled = false
            val ref = storage.reference.child("profile/$uid.jpg")
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            db.collection("users").document(uid).update("photoUrl", url).await()
            Toast.makeText(safeContext, "증명사진 업로드 성공!", Toast.LENGTH_SHORT).show()
            (activity as? HomeActivity)?.refreshDrawerPhoto()
        } catch (e: Exception) {
            Toast.makeText(safeContext, "사진 오류: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            binding.btnPickPhoto.isEnabled = true
        }
    }

    private suspend fun loadImageFromUrlIntoView(url: String) {
        withContext(Dispatchers.IO) {
            try {
                val input = java.net.URL(url).openStream()
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                withContext(Dispatchers.Main) {
                    binding.ivProfile.setImageBitmap(bitmap)
                    binding.ivProfile.imageTintList = null
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ==========================================
    // 3. 이력서 저장 및 삭제 로직
    // ==========================================

    private fun handleResumeCreationFlow() = lifecycleScope.launch {
        val safeContext = context ?: return@launch
        val uid = auth.currentUser?.uid ?: return@launch

        val skillsText = binding.etSkills.text.toString().trim()
        val projectsText = binding.etProjects.text.toString().trim()
        val awardsText = binding.etAwards.text.toString().trim()
        val gpaText = binding.etGpa.text.toString().trim()
        val certText = binding.etCert.text.toString().trim()
        val highlightText = binding.etHighlight.text.toString().trim()

        if (skillsText.isEmpty() || projectsText.isEmpty()) {
            Toast.makeText(safeContext, "필수 항목(기술 스택, 프로젝트)을 채워주세요.", Toast.LENGTH_SHORT).show()
            return@launch
        }

        val combinedText = "$skillsText $projectsText $awardsText $gpaText $certText $highlightText"

        try {
            withContext(Dispatchers.Default) {
                computedKeywords = classifier.getTop5Keywords(combinedText)
            }
        } catch (e: Exception) {
            computedKeywords = emptyList()
        }

        AlertDialog.Builder(safeContext)
            .setTitle("이력서 저장 및 생성")
            .setMessage("입력하신 이력서 데이터를 파이어베이스 서버에 저장하고 PDF 문서를 출력하시겠습니까?")
            .setPositiveButton("동의") { _, _ ->
                saveDataAndGeneratePdf(uid, skillsText, projectsText, awardsText, gpaText, certText, highlightText)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveDataAndGeneratePdf(
        uid: String, skills: String, projects: String,
        awards: String, gpa: String, cert: String, highlight: String
    ) = lifecycleScope.launch {
        val safeContext = context ?: return@launch

        val resumeMap = hashMapOf(
            "skills" to skills.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            "projects" to projects,
            "awards" to awards,
            "gpa" to gpa,
            "certificates" to cert,
            "highlight" to highlight,
            "recommendedKeywords" to computedKeywords,
            "updatedAt" to Timestamp.now()
        )

        try {
            withContext(Dispatchers.IO) {
                val collectionRef = db.collection("users").document(uid).collection("resumes")
                if (currentResumeDocId != null) {
                    // 이미 문서 ID가 있다면 '수정(업데이트)'
                    collectionRef.document(currentResumeDocId!!).set(resumeMap).await()
                } else {
                    // 새로 작성하는 것이라면 '새 문서 추가'
                    val newDoc = collectionRef.add(resumeMap).await()
                    currentResumeDocId = newDoc.id
                }
            }
            Toast.makeText(safeContext, "이력서 저장 완료!", Toast.LENGTH_SHORT).show()

            // 저장 성공 후 다시 '보기 모드'로 전환
            setEditMode(false)

            // PDF 생성 로직 수행
            val userName = auth.currentUser?.email?.split("@")?.get(0) ?: "User"
            val photoUrl = withContext(Dispatchers.IO) {
                db.collection("users").document(uid).get().await().getString("photoUrl")
            }

            val fileUri = withContext(Dispatchers.IO) {
                // Resume(Data class) 대신 Map을 썼으므로 Resume 객체로 변환하여 넘기거나,
                // Generator를 상황에 맞게 유동적으로 쓰시면 됩니다.
                // 기존 코드를 유지하기 위해 객체를 재구성합니다.
                val resumeObj = Resume(
                    skills = resumeMap["skills"] as List<String>,
                    projects = projects, awards = awards, gpa = gpa,
                    certificates = cert, highlight = highlight,
                    recommendedKeywords = computedKeywords, updatedAt = Timestamp.now()
                )
                ResumePdfGenerator(safeContext).generateResumePdf(resumeObj, userName, photoUrl)
            }

            val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            startActivity(Intent.createChooser(pdfIntent, "PDF 이력서 확인하기"))

        } catch (e: Exception) {
            Toast.makeText(safeContext, "이력서 생성 중 오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("이력서 삭제")
            .setMessage("정말로 이력서를 삭제하시겠습니까? 삭제된 데이터는 복구할 수 없습니다.")
            .setPositiveButton("삭제") { _, _ -> deleteResume() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteResume() = lifecycleScope.launch {
        val safeContext = context ?: return@launch
        val uid = auth.currentUser?.uid ?: return@launch
        val docId = currentResumeDocId ?: return@launch

        try {
            db.collection("users").document(uid).collection("resumes").document(docId).delete().await()
            Toast.makeText(safeContext, "이력서가 삭제되었습니다.", Toast.LENGTH_SHORT).show()

            // 데이터 초기화 및 입력 모드로 전환
            currentResumeDocId = null
            binding.etSkills.text.clear()
            binding.etProjects.text.clear()
            binding.etAwards.text.clear()
            binding.etGpa.text.clear()
            binding.etCert.text.clear()
            binding.etHighlight.text.clear()
            binding.btnCreateResume.text = "이력서 만들기"

            setEditMode(true)

        } catch (e: Exception) {
            Toast.makeText(safeContext, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
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

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
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

        binding.btnPickPhoto.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnCreateResume.setOnClickListener {
            handleResumeCreationFlow()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun uploadProfilePhoto(uri: Uri) = lifecycleScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        try {
            binding.btnPickPhoto.isEnabled = false
            val ref = storage.reference.child("profile/$uid.jpg")
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            db.collection("users").document(uid).update("photoUrl", url).await()
            Toast.makeText(requireContext(), "증명사진 업로드 성공!", Toast.LENGTH_SHORT).show()
            (activity as? HomeActivity)?.refreshDrawerPhoto()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "사진 오류: ${e.message}", Toast.LENGTH_SHORT).show()
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleResumeCreationFlow() = lifecycleScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch

        val skillsText = binding.etSkills.text.toString().trim()
        val projectsText = binding.etProjects.text.toString().trim()
        val awardsText = binding.etAwards.text.toString().trim()
        val gpaText = binding.etGpa.text.toString().trim()
        val certText = binding.etCert.text.toString().trim()
        val highlightText = binding.etHighlight.text.toString().trim()

        if (skillsText.isEmpty() || projectsText.isEmpty()) {
            Toast.makeText(requireContext(), "필수 항목(기술 스택, 프로젝트)을 채워주세요.", Toast.LENGTH_SHORT).show()
            return@launch
        }

        val combinedText = "$skillsText $projectsText $awardsText $gpaText $certText $highlightText"
        withContext(Dispatchers.Default) {
            computedKeywords = classifier.getTop5Keywords(combinedText)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("이력서 저장 및 생성")
            .setMessage("입력하신 이력서 데이터를 파이어베이스 서버에 저장하고 PDF 문서를 출력하시겠습니까?")
            .setPositiveButton("동의") { _, _ ->
                saveDataAndGeneratePdf(uid, skillsText, projectsText, awardsText, gpaText, certText, highlightText)
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(requireContext(), "동의하지 않으시면 이력서를 만들 수 없습니다.", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveDataAndGeneratePdf(
        uid: String, skills: String, projects: String,
        awards: String, gpa: String, cert: String, highlight: String
    ) = lifecycleScope.launch {
        val resume = Resume(
            skills = skills.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            projects = projects,
            awards = awards,
            gpa = gpa,
            certificates = cert,
            highlight = highlight,
            recommendedKeywords = computedKeywords,
            updatedAt = Timestamp.now()
        )

        try {
            withContext(Dispatchers.IO) {
                db.collection("users").document(uid)
                    .collection("resumes").add(resume).await()
            }
            Toast.makeText(requireContext(), "데이터베이스 서버 적재 완료!", Toast.LENGTH_SHORT).show()

            val userName = auth.currentUser?.email?.split("@")?.get(0) ?: "User"
            val photoUrl = withContext(Dispatchers.IO) {
                db.collection("users").document(uid).get().await().getString("photoUrl")
            }

            if (!photoUrl.isNullOrEmpty()) {
                loadImageFromUrlIntoView(photoUrl)
                (activity as? HomeActivity)?.refreshDrawerPhoto()
            }

            val fileUri = withContext(Dispatchers.IO) {
                ResumePdfGenerator(requireContext()).generateResumePdf(resume, userName, photoUrl)
            }
            Toast.makeText(requireContext(), "공용 폴더 PDF 보관 완료!", Toast.LENGTH_SHORT).show()
            val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            startActivity(Intent.createChooser(pdfIntent, "PDF 이력서 확인하기"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "이력서 생성 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

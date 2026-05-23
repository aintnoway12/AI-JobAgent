package com.example.ai_jobagent

//NOTICE : 수정 - 표시 필드 및 카드 레이아웃은 API 응답 구조 확정 후 조정 필요
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_jobagent.databinding.ItemRecruitCardBinding

class RecruitAdapter : ListAdapter<RecruitItem, RecruitAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecruitItem>() {
            override fun areItemsTheSame(old: RecruitItem, new: RecruitItem) =
                old.recruitId == new.recruitId
            override fun areContentsTheSame(old: RecruitItem, new: RecruitItem) = old == new
        }
    }

    inner class ViewHolder(private val binding: ItemRecruitCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecruitItem) {
            binding.tvInstName.text = item.instName.ifEmpty { "기관명 없음" }
            binding.tvTitle.text = item.title.ifEmpty { "공고 제목 없음" }
            binding.tvWorkRegion.text = item.workRegion.ifEmpty { "지역 미상" }
            binding.tvRecruitType.text = item.recruitType.ifEmpty { "-" }
            binding.tvStartDate.text = if (item.startDate.isNotEmpty()) "접수 시작: ${item.startDate}" else ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecruitCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

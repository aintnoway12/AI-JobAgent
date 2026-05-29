package com.example.ai_jobagent

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_jobagent.databinding.ItemTechTrendBinding

class TechTrendAdapter : ListAdapter<StackOverflowItem, TechTrendAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemTechTrendBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StackOverflowItem) {
            binding.tvTitle.text = item.title
            binding.tvTags.text = item.tags.take(3).joinToString("  ·  ") { "#$it" }
            binding.tvStats.text = "조회 ${formatCount(item.viewCount)}  |  답변 ${item.answerCount}  |  점수 ${item.score}"
            binding.root.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                binding.root.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTechTrendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private fun formatCount(count: Int): String = when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<StackOverflowItem>() {
            override fun areItemsTheSame(a: StackOverflowItem, b: StackOverflowItem) = a.link == b.link
            override fun areContentsTheSame(a: StackOverflowItem, b: StackOverflowItem) = a == b
        }
    }
}

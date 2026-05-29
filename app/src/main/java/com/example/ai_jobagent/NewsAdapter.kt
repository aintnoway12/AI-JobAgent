package com.example.ai_jobagent

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_jobagent.databinding.ItemNewsCardBinding

class NewsAdapter : ListAdapter<NaverNewsItem, NewsAdapter.NewsViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NaverNewsItem>() {
            override fun areItemsTheSame(old: NaverNewsItem, new: NaverNewsItem) =
                old.link == new.link
            override fun areContentsTheSame(old: NaverNewsItem, new: NaverNewsItem) =
                old == new
        }
    }

    inner class NewsViewHolder(private val binding: ItemNewsCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NaverNewsItem) {
            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description
            binding.tvDate.text = item.pubDate.take(16)
            binding.root.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                it.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

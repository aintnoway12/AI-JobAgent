package com.example.ai_jobagent

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_jobagent.databinding.ItemChatMessageBinding

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
        }
    }

    inner class MessageViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: ChatMessage) {
            binding.tvMessage.text = msg.content

            val params = binding.cardMessage.layoutParams as FrameLayout.LayoutParams
            if (msg.isUser) {
                params.gravity = Gravity.END
                binding.cardMessage.setCardBackgroundColor(Color.parseColor("#5D5FEF"))
                binding.tvMessage.setTextColor(Color.WHITE)
            } else {
                params.gravity = Gravity.START
                binding.cardMessage.setCardBackgroundColor(Color.parseColor("#F0F4F8"))
                binding.tvMessage.setTextColor(Color.parseColor("#1E293B"))
            }
            binding.cardMessage.layoutParams = params
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

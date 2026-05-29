package com.meshhood

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConversationAdapter(
    private var items: List<MeshService.DmConversation>,
    private val onClick: (MeshService.DmConversation) -> Unit,
) : RecyclerView.Adapter<ConversationAdapter.Holder>() {

    fun submitList(next: List<MeshService.DmConversation>) {
        items = next
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarText: TextView = itemView.findViewById(R.id.avatarText)
        private val peerNameText: TextView = itemView.findViewById(R.id.peerNameText)
        private val previewText: TextView = itemView.findViewById(R.id.previewText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(item: MeshService.DmConversation, onClick: (MeshService.DmConversation) -> Unit) {
            avatarText.text = item.peer.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            peerNameText.text = item.peer
            previewText.text = item.preview
            timeText.text = item.time
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

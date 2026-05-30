package com.meshhood

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConversationAdapter(
    private val service: MeshService,
    private var items: List<MeshService.DmConversation>,
    private val onClick: (MeshService.DmConversation) -> Unit,
    private val onAvatarClick: (String) -> Unit,
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
        holder.bind(service, items[position], onClick, onAvatarClick)
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarButton: FrameLayout = itemView.findViewById(R.id.avatarButton)
        private val avatarImage: ImageView = itemView.findViewById(R.id.avatarImage)
        private val avatarInitial: TextView = itemView.findViewById(R.id.avatarInitial)
        private val verifiedBadge: ImageView = itemView.findViewById(R.id.avatarVerifiedBadge)
        private val peerNameText: TextView = itemView.findViewById(R.id.peerNameText)
        private val previewText: TextView = itemView.findViewById(R.id.previewText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(
            service: MeshService,
            item: MeshService.DmConversation,
            onClick: (MeshService.DmConversation) -> Unit,
            onAvatarClick: (String) -> Unit,
        ) {
            AvatarBinder.bind(
                itemView.context,
                service,
                item.peer,
                avatarImage,
                avatarInitial,
                verifiedBadge,
            )
            peerNameText.text = item.peer
            previewText.text = item.preview
            timeText.text = item.time
            avatarButton.setOnClickListener { onAvatarClick(item.peer) }
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

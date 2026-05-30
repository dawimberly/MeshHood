package com.meshhood

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DirectoryAdapter(
    private val service: MeshService,
    private var names: List<String>,
    private val onClick: (String) -> Unit,
) : RecyclerView.Adapter<DirectoryAdapter.Holder>() {

    fun submitList(next: List<String>) {
        names = next
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_directory, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(service, names[position], onClick)
    }

    override fun getItemCount(): Int = names.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarButton: FrameLayout = itemView.findViewById(R.id.directoryAvatarButton)
        private val avatarImage: ImageView = itemView.findViewById(R.id.directoryAvatarImage)
        private val avatarInitial: TextView = itemView.findViewById(R.id.directoryAvatarInitial)
        private val verifiedBadge: ImageView = itemView.findViewById(R.id.directoryVerifiedBadge)
        private val nameText: TextView = itemView.findViewById(R.id.directoryNameText)
        private val subtitleText: TextView = itemView.findViewById(R.id.directorySubtitleText)

        fun bind(service: MeshService, name: String, onClick: (String) -> Unit) {
            val ctx = itemView.context
            AvatarBinder.bind(ctx, service, name, avatarImage, avatarInitial, verifiedBadge)
            val self = if (name == service.myName) " (you)" else ""
            nameText.text = "$name$self"
            val p = service.profileOf(name)
            val skillCount = p?.skills?.size ?: 0
            val cap = service.capacityOf(name)
            val capGlyph = when {
                service.isExempt(name) -> " 💛"
                cap == MeshService.CAP_LIMITED || cap == MeshService.CAP_HOMEBOUND -> " 🟡"
                else -> ""
            }
            val skills = if (skillCount > 0) "$skillCount skill(s)" else "no skills listed"
            subtitleText.text = "$skills$capGlyph"
            avatarButton.setOnClickListener { onClick(name) }
            itemView.setOnClickListener { onClick(name) }
        }
    }
}

package com.meshhood

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class FeedAdapter : ListAdapter<FeedLine, FeedAdapter.Holder>(DIFF) {

    var onLineLongClick: ((FeedLine) -> Boolean)? = null
    var onOpenMapsClick: ((FeedLine) -> Unit)? = null
    var onLinkClick: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val line = getItem(position)
        holder.bind(
            line,
            onOpenMapsClick = { onOpenMapsClick?.invoke(line) },
            onLinkClick = { url -> onLinkClick?.invoke(url) },
        )
        holder.itemView.setOnLongClickListener {
            onLineLongClick?.invoke(line) ?: false
        }
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.feedCardRoot)
        private val senderText: TextView = itemView.findViewById(R.id.feedSenderText)
        private val timeText: TextView = itemView.findViewById(R.id.feedTimeText)
        private val bodyText: TextView = itemView.findViewById(R.id.feedBodyText)
        private val badgeText: TextView = itemView.findViewById(R.id.feedBadgeText)
        private val openMapsButton: MaterialButton = itemView.findViewById(R.id.feedOpenMapsButton)

        fun bind(
            line: FeedLine,
            onOpenMapsClick: (() -> Unit)?,
            onLinkClick: ((String) -> Unit)?,
        ) {
            FeedStyler.bindCard(
                itemView.context,
                cardRoot,
                senderText,
                timeText,
                bodyText,
                badgeText,
                openMapsButton,
                line,
                onOpenMapsClick,
                onLinkClick,
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FeedLine>() {
            override fun areItemsTheSame(oldItem: FeedLine, newItem: FeedLine): Boolean =
                oldItem.time == newItem.time &&
                    oldItem.sender == newItem.sender &&
                    oldItem.kind == newItem.kind

            override fun areContentsTheSame(oldItem: FeedLine, newItem: FeedLine): Boolean =
                oldItem == newItem
        }
    }
}

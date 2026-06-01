package com.meshhood

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FeedAdapter : RecyclerView.Adapter<FeedAdapter.Holder>() {

    private var lines: List<FeedLine> = emptyList()
    var onLineLongClick: ((FeedLine) -> Boolean)? = null

    fun submitList(next: List<FeedLine>) {
        lines = next
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val line = lines[position]
        holder.bind(line)
        holder.itemView.setOnLongClickListener {
            onLineLongClick?.invoke(line) ?: false
        }
    }

    override fun getItemCount(): Int = lines.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.feedCardRoot)
        private val senderText: TextView = itemView.findViewById(R.id.feedSenderText)
        private val timeText: TextView = itemView.findViewById(R.id.feedTimeText)
        private val bodyText: TextView = itemView.findViewById(R.id.feedBodyText)
        private val badgeText: TextView = itemView.findViewById(R.id.feedBadgeText)

        fun bind(line: FeedLine) {
            FeedStyler.bindCard(
                itemView.context,
                cardRoot,
                senderText,
                timeText,
                bodyText,
                badgeText,
                line,
            )
        }
    }
}

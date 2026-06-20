package com.trading.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuotesAdapter(
    private val quotes: List<Quote>
) : RecyclerView.Adapter<QuotesAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvChange: TextView = itemView.findViewById(R.id.tvChange)
        val tvMinMax: TextView = itemView.findViewById(R.id.tvMinMax)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.quote_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = quotes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val quote = quotes[position]
        holder.tvName.text = quote.name
        holder.tvPrice.text = String.format("%.2f", quote.price)

        val changeText = String.format("%+.2f%%", quote.percentageChange)
        holder.tvChange.text = changeText
        holder.tvChange.setTextColor(
            if (quote.percentageChange >= 0)
                holder.itemView.context.getColor(R.color.green)
            else
                holder.itemView.context.getColor(R.color.red)
        )

        holder.tvMinMax.text = "Min: ${quote.minCost}  Max: ${quote.maxCost}"
    }
}
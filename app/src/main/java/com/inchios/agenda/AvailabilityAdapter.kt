package com.inchios.agenda

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inchios.agendapadel.R
import java.time.format.DateTimeFormatter

class AvailabilityAdapter(
    private val items: MutableList<Availability>,
    private val onItemLongClick: (Availability) -> Unit
) : RecyclerView.Adapter<AvailabilityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_availability, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.personName
        val javaDate = java.time.LocalDate.of(item.date.year, item.date.monthNumber, item.date.dayOfMonth)
        holder.tvDate.text = javaDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        holder.tvTime.text = "${item.startTime} - ${item.endTime}"

        holder.itemView.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
    }

    override fun getItemCount() = items.size
}

package com.example.Taskly.ui.projects

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import com.example.Taskly.R

class IconSpinnerAdapter(
    context: Context,
    private val icons: List<Pair<String, String>>
) : ArrayAdapter<Pair<String, String>>(context, 0, icons) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.icon_spinner, parent, false)

        val icon = view.findViewById<ImageView>(R.id.spinnerIcon)

        val (_, iconResName) = icons[position]


        val iconResId = context.resources.getIdentifier(
            iconResName,
            "drawable",
            context.packageName
        )

        if (iconResId != 0) {
            icon.setImageResource(iconResId)
        } else {
            icon.setImageResource(R.drawable.ic_home_project)
        }

        return view
    }
}
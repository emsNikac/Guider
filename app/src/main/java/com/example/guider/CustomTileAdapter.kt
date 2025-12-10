package com.example.guider

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.guider.models.Tile

class CustomTileAdapter(
    context: Context,
    private val items: List<Tile>,
): ArrayAdapter<Tile>(context, R.layout.grid_item_view, items)  {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.grid_item_view, parent, false)

        val tile = items[position]

        val icon = view.findViewById<ImageView>(R.id.iconImage)
        val number = view.findViewById<TextView>(R.id.numberText)
        val title = view.findViewById<TextView>(R.id.titleText)

        icon.setImageResource(tile.iconId)
        number.text = tile.taskCount.toString()
        title.text = tile.taskCategory.toPrettyCase()

        val bg = view.background as GradientDrawable
        bg.setColor(tile.color)

        bg.alpha = (255 * tile.opacity).toInt()

        return view
    }

    fun String.toPrettyCase(): String{
        return this
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }

}
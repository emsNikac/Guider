package com.example.guider

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.guider.models.DailyTask

class CustomDailyTaskAdapter(
    private val tasks: List<DailyTask>
): RecyclerView.Adapter<CustomDailyTaskAdapter.TaskViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.daily_task_view,parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: TaskViewHolder,
        position: Int
    ) {
        val task = tasks[position]

        holder.dailyTaskTitleView.text = task.title
        holder.dailyTaskCategoryFiled.text = task.taskCategory.displayName

        val colorInt = ContextCompat.getColor(
            holder.itemView.context,
            task.taskCategory.colorRes
        )
        val bg = holder.dailyTaskCategoryFiled.background as GradientDrawable
        bg.setColor(colorInt)

        val fontColor = ContextCompat.getColor(
            holder.itemView.context,
            task.taskCategory.textColor
        )
        holder.dailyTaskCategoryFiled.setTextColor(fontColor)

        holder.checkBox.isChecked = task.isFinished
    }

    override fun getItemCount(): Int {
        return tasks.size
    }

    class TaskViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        var checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        val dailyTaskTitleView: TextView = itemView.findViewById(R.id.dailyTaskTitleField)
        val dailyTaskCategoryFiled: TextView = itemView.findViewById(R.id.dailyTaskCategoryField)
    }

}
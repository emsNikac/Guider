package com.example.guider

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.guider.databinding.ActivityMainBinding
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import com.example.guider.models.Tile
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navButtons: List<ImageButton>
    private lateinit var navIndicator: View


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        val tiles = listOf<Tile>(
            Tile(0, TaskCategory.HEALTH.displayName, "#9E86B9".toColorInt(), 0.58,  R.drawable.health_icon),
            Tile(0, TaskCategory.WORK.displayName, "#85AC81".toColorInt(), 0.58, R.drawable.work_icon),
            Tile(0, TaskCategory.MENTAL_HEALTH.displayName, "#CD62A6".toColorInt(), 0.43, R.drawable.mental_health_icon),
            Tile(0, TaskCategory.OTHER.displayName, "#3A3A34".toColorInt(), 0.36, R.drawable.other_icon)
        )
        binding.gridItems.adapter = CustomTileAdapter(this, tiles)
        navIndicator = binding.navIndicator

        val tasks = listOf<DailyTask>(
            DailyTask(TaskCategory.HEALTH, "Drink water", false),
            DailyTask(TaskCategory.WORK, "Write essay", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes testing longer text to see how it fits", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.MENTAL_HEALTH, "Meditate 30 minutes", false),
            DailyTask(TaskCategory.OTHER, "Help mom", false)
        )

        val recyclerView = binding.dailyTaskListRec
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = CustomDailyTaskAdapter(tasks)
        recyclerView.adapter = adapter

        navButtons = listOf(
            binding.navTasks,
            binding.navSleep,
            binding.navStats,
            binding.navCalendar,
            binding.navMoney
        )

        navButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
            moveIndicatorTo(index)
        } }

        navIndicator.post {
            moveIndicatorTo(0, animate = false)
        }

    }

    private fun moveIndicatorTo(index: Int, animate: Boolean = true) {
        val target = navButtons[index]

        val targetCenterX =
            target.x + target.width / 2f - navIndicator.width / 2f

        if (animate) {
            navIndicator.animate()
                .translationX(targetCenterX)
                .setDuration(260)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            navIndicator.translationX = targetCenterX
        }

        navButtons.forEachIndexed { i, button ->
            button.isSelected = i == index
        }
    }
}
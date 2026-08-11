package com.upsi.smartbus.feature.admin.seeder

import android.view.View
import androidx.recyclerview.widget.RecyclerView

object AdminUiHelper {

    /** RecyclerView inside NestedScrollView only shows ~1 screen unless height is expanded. */
    fun expandRecyclerView(recyclerView: RecyclerView) {
        recyclerView.post {
            val adapter = recyclerView.adapter ?: return@post
            if (adapter.itemCount == 0) {
                recyclerView.layoutParams = recyclerView.layoutParams.apply { height = 0 }
                return@post
            }
            var totalHeight = 0
            val width = recyclerView.width.takeIf { it > 0 }
                ?: recyclerView.measuredWidth.takeIf { it > 0 }
                ?: recyclerView.context.resources.displayMetrics.widthPixels
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            for (i in 0 until adapter.itemCount) {
                val type = adapter.getItemViewType(i)
                val holder = adapter.createViewHolder(recyclerView, type)
                adapter.onBindViewHolder(holder, i)
                holder.itemView.measure(widthSpec, heightSpec)
                totalHeight += holder.itemView.measuredHeight
            }
            recyclerView.layoutParams = recyclerView.layoutParams.apply {
                height = totalHeight + recyclerView.paddingTop + recyclerView.paddingBottom
            }
        }
    }
}

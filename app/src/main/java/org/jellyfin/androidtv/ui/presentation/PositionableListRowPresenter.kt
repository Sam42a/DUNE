package org.jellyfin.androidtv.ui.presentation

import android.content.Context
import android.view.View
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import timber.log.Timber

class PositionableListRowPresenter : CustomListRowPresenter {
    private var viewHolder: ViewHolder? = null
    private var shouldCenterContent = false

    // Backward compatible constructor
    @JvmOverloads
    constructor(padding: Int = 0) : super(padding) {
        init()
    }

    // New constructor with context and spacing preference
    constructor(context: Context, useLargeSpacing: Boolean = false, centerContent: Boolean = false) : this(
        context.resources.getDimensionPixelSize(
            if (useLargeSpacing) R.dimen.home_row_spacing_large
            else R.dimen.home_row_spacing
        )
    ) {
        shouldCenterContent = centerContent
    }


    private fun init() {
        shadowEnabled = false
		selectEffectEnabled = true

        // Configure header to always be visible
        headerPresenter = object : RowHeaderPresenter() {
            override fun onSelectLevelChanged(holder: ViewHolder) {
                super.onSelectLevelChanged(holder)
                // Keep header always visible
                holder.view.alpha = 1f
            }
        }
    }

    override fun isUsingDefaultListSelectEffect() = true

    override fun isUsingDefaultShadow() = false

    override fun onRowViewExpanded(viewHolder: RowPresenter.ViewHolder, expanded: Boolean) {
        super.onRowViewExpanded(viewHolder, expanded)
        // Ensure header is always visible when row is expanded
        viewHolder.headerViewHolder?.view?.visibility = View.VISIBLE
        viewHolder.headerViewHolder?.view?.alpha = 1f
    }

    @Suppress("UNUSED_PARAMETER")
    override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) {
        super.onSelectLevelChanged(holder)
        // Keep header visible when row is selected
        holder.headerViewHolder?.view?.visibility = View.VISIBLE
        viewHolder?.headerViewHolder?.view?.alpha = 1f
    }

    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(holder, item)
        if (holder is ViewHolder) {
            viewHolder = holder
            if (shouldCenterContent) {
                applyCenteringToRow(holder)
            }
        }
    }

    private fun applyCenteringToRow(holder: ViewHolder) {
        try {
            val gridView = holder.gridView

            val screenWidth = gridView.context.resources.displayMetrics.widthPixels
            val paddingStart = (screenWidth * 0.05).toInt() // Further reduced to 5% from left

            gridView.setPadding(paddingStart, gridView.paddingTop, gridView.paddingRight, gridView.paddingBottom)
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply centering to row")
        }
    }

    var position: Int
        get() = viewHolder?.gridView?.selectedPosition ?: -1
        set(value) {
            Timber.d("Setting position to $value")
            viewHolder?.gridView?.selectedPosition = value
        }
}

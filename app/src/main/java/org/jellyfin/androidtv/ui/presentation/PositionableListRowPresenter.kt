package org.jellyfin.androidtv.ui.presentation

import android.content.Context
import android.view.View
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import timber.log.Timber

class PositionableListRowPresenter : CustomListRowPresenter {
    private var viewHolder: ViewHolder? = null

    // Backward compatible constructor
    @JvmOverloads
    constructor(padding: Int = 0) : super(padding) {
        init()
    }

    // New constructor with context and spacing preference
    constructor(context: Context, useLargeSpacing: Boolean = false) : this(
        context.resources.getDimensionPixelSize(
            if (useLargeSpacing) R.dimen.home_row_spacing_large
            else R.dimen.home_row_spacing
        )
    )


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

    override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder?) {
        super.onSelectLevelChanged(holder)
        // Keep header visible when row is selected
        holder?.headerViewHolder?.view?.visibility = View.VISIBLE
        holder?.headerViewHolder?.view?.alpha = 1f
    }

    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder?, item: Any?) {
        super.onBindRowViewHolder(holder, item)
        if (holder !is ViewHolder) return

        viewHolder = holder
    }

    var position: Int
        get() = viewHolder?.gridView?.selectedPosition ?: -1
        set(value) {
            Timber.d("Setting position to $value")
            viewHolder?.gridView?.selectedPosition = value
        }
}

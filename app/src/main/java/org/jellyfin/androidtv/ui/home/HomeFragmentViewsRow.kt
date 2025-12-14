package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.querying.GetUserViewsRequest
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.UserViewCardPresenter
import org.koin.java.KoinJavaComponent

/**
 * ButtonViewPresenter creates compact button-style views for media folders
 * instead of the standard card with poster image.
 */
class ButtonViewPresenter : Presenter() {
    class ExtraSmallTextView(context: Context) : AppCompatTextView(context) {
        private var focusedBackground: GradientDrawable? = null
        private var unfocusedBackground: GradientDrawable? = null
        private var textColor: Int = Color.WHITE
        private var focusedTextColor: Int = Color.WHITE

        init {
            // Get theme attributes
            val attrs = context.theme.obtainStyledAttributes(
                intArrayOf(
                    R.attr.mediaFolderButtonBackground,
                    R.attr.mediaFolderButtonFocusedBackground,
                    R.attr.mediaFolderButtonTextColor,
                    R.attr.mediaFolderButtonFocusedTextColor
                )
            )

            // Create focused background with theme color
            focusedBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(attrs.getColor(1, Color.argb(179, 48, 48, 48))) // Default to 70% opacity grey if not set
            }


            // Create unfocused background with theme color
            unfocusedBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(attrs.getColor(0, Color.TRANSPARENT)) // Default to transparent if not set
            }

            // Set text colors from theme
            textColor = attrs.getColor(2, Color.WHITE)
            focusedTextColor = attrs.getColor(3, Color.WHITE)

            attrs.recycle()

            // Basic styling
            gravity = Gravity.CENTER
            setPadding(25, 15, 25, 15)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
        }

        // Handle the focus change directly in the view
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

            // Set background and elevation based on focus state
            if (gainFocus) {
                background = focusedBackground
                setTextColor(focusedTextColor)
                elevation = 8f
            } else {
                background = unfocusedBackground
                setTextColor(textColor)
                elevation = 0f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val textView = ExtraSmallTextView(parent.context)
        textView.isFocusable = true
        textView.isFocusableInTouchMode = true
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        val marginStart = (screenWidth * 0.02).toInt()

        val marginParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        marginParams.marginStart = marginStart
        textView.layoutParams = marginParams

        return ViewHolder(textView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is BaseRowItem) return

        val textView = viewHolder.view as ExtraSmallTextView
        textView.text = item.getName(textView.context)

        // Set a tag to identify this as a Media Folders item
        textView.tag = "media_folders_item"
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Nothing to clean up
    }
}

class HomeFragmentViewsRow(
	val small: Boolean,
) : HomeFragmentRow {
	private companion object {
		val smallCardPresenter = UserViewCardPresenter(true)
		val largeCardPresenter = UserViewCardPresenter(false)
		val buttonPresenter = ButtonViewPresenter()
	}

    fun isMediaFoldersItem(item: Any?): Boolean {
        return item is BaseRowItem && item.baseItem?.type == org.jellyfin.sdk.model.api.BaseItemKind.USER_VIEW
    }

    fun shouldUseCenteredLayout(): Boolean {
        return small
    }

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Get user preferences to check if extra small option is enabled
		val userPrefs = KoinJavaComponent.get<UserSettingPreferences>(UserSettingPreferences::class.java)
		val useExtraSmall = userPrefs.get(userPrefs.useExtraSmallMediaFolders)

		// Choose the appropriate presenter based on preference
		val presenter = when {
			useExtraSmall -> buttonPresenter  // Use button-style for extra small
			small -> smallCardPresenter      // Use small cards if small is enabled
			else -> largeCardPresenter       // Use large cards by default
		}

		// Create the adapter with the selected presenter
		val rowAdapter = ItemRowAdapter(context, GetUserViewsRequest, presenter, rowsAdapter)

		// Set empty header text to hide the title
		val headerText = ""
		val header = HeaderItem(headerText)
		val row = ListRow(header, rowAdapter)
		rowAdapter.setRow(row)
		rowAdapter.Retrieve()
		rowsAdapter.add(row)
	}
}

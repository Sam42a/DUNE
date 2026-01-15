package org.jellyfin.androidtv.ui.startup.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.shared.toolbar.StartupToolbar

class StartupToolbarFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				StartupToolbar(
					openHelp = {
						parentFragmentManager.commit {
							addToBackStack(null)
							replace<ConnectHelpAlertFragment>(R.id.content_view)
						}
					},
					openSettings = {
						val intent = Intent(requireContext(), org.jellyfin.androidtv.ui.preference.PreferencesComposeActivity::class.java)
						intent.putExtra("initialScreen", "auth")
						intent.putExtra("showAbout", true)
						intent.putExtra("standalone", true)
						startActivity(intent)
					},
				)
			}
		}
	}
}

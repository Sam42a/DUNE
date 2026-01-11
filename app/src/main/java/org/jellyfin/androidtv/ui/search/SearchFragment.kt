package org.jellyfin.androidtv.ui.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class SearchFragment : Fragment() {
	companion object {
		const val EXTRA_QUERY = "query"
	}

	private val viewModel: SearchViewModel by viewModel()
	private val backgroundService: BackgroundService by inject()
	private val searchFragmentDelegate: SearchFragmentDelegate by inject {
		parametersOf(requireContext())
	}

	private val voiceSearchLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
			if (!matches.isNullOrEmpty()) {
				voiceSearchResult = matches[0]
				viewModel.searchImmediately(matches[0])
			}
		} else {
			Toast.makeText(requireContext(), "No voice input recognized", Toast.LENGTH_SHORT).show()
		}
	}

	private fun launchVoiceSearch() {
		try {
			val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
				putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
				putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.lbl_voice_search))
			}
			voiceSearchLauncher.launch(intent)
		} catch (e: Exception) {
			Toast.makeText(requireContext(), "Voice search not supported", Toast.LENGTH_SHORT).show()
		}
	}

	private var voiceSearchResult: String? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

			setContent {
				SearchScreen(
					viewModel = viewModel,
					onNavigateToItem = { baseRowItem ->
						val itemLauncher: ItemLauncher by inject()
						itemLauncher.launch(baseRowItem, null, requireContext())
					},
					onVoiceSearch = {
						launchVoiceSearch()
					}
				)
			}
		}
	}

	override fun onResume() {
		super.onResume()
		org.jellyfin.androidtv.ui.itemdetail.ThemeSongs.setAnyFragmentActive(true)

	}
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		try {
			backgroundService.clearBackgrounds()
		} catch (e: Exception) {
			Timber.e(e, "Error clearing backdrops in SearchFragment")
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
	}

}

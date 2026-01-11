package org.jellyfin.androidtv.ui.search

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.ImagePreloader
import org.koin.android.ext.android.inject
import timber.log.Timber

@Composable
fun SearchScreen(
	viewModel: SearchViewModel,
	onNavigateToItem: (BaseRowItem) -> Unit,
	onVoiceSearch: () -> Unit,
	modifier: Modifier = Modifier,
	initialQuery: String? = null
) {
	val context = LocalContext.current
	val searchResults by viewModel.searchResultsFlow.collectAsStateWithLifecycle()

	var query by remember { mutableStateOf(initialQuery ?: "") }
	var isSearchFocused by remember { mutableStateOf(false) }
	var isVoiceButtonFocused by remember { mutableStateOf(false) }
	var isClearButtonFocused by remember { mutableStateOf(false) }

	val searchFieldFocusRequester = remember { FocusRequester() }
	val voiceButtonFocusRequester = remember { FocusRequester() }
	val clearButtonFocusRequester = remember { FocusRequester() }
	val launchVoiceSearch: () -> Unit = {
		onVoiceSearch()
	}

	val showKeyboard: () -> Unit = {
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
	}

	val hideKeyboard: () -> Unit = {
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		imm.hideSoftInputFromWindow(null, 0)
	}

	LaunchedEffect(Unit) {
		try {
			val backgroundService: BackgroundService by (context as FragmentActivity).inject()
			backgroundService.clearBackgrounds()
		} catch (e: Exception) {
			Timber.e(e, "Error clearing backdrops in SearchScreen")
		}
	}

	LaunchedEffect(initialQuery) {
		if (!initialQuery.isNullOrBlank()) {
			query = initialQuery
			viewModel.searchImmediately(initialQuery)
			delay(200)
		} else {
			delay(100)
			searchFieldFocusRequester.requestFocus()
		}
	}

	LaunchedEffect(searchResults) {
		if (searchResults.isNotEmpty()) {
			val imageHelper: ImageHelper by (context as FragmentActivity).inject()
			val width = 300
			val height = 450

			searchResults.forEach { group ->
				group.items.take(5).forEach { item ->
					val imageUrl = imageHelper.getPrimaryImageUrl(item, width, height)
					if (imageUrl != null) {
						ImagePreloader.preloadImages(context, listOf(imageUrl))
					}
				}
			}
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(
				color = with(LocalContext.current) {
					val attrs = intArrayOf(R.attr.defaultBackground)
					val typedArray = obtainStyledAttributes(attrs)
					val colorInt = typedArray.getColor(0, Color.Black.toArgb())
					typedArray.recycle()
					Color(colorInt)
				}
			)
			.padding(vertical = 16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			TVButton(
				onClick = launchVoiceSearch,
				onFocusChanged = { isVoiceButtonFocused = it },
				focusRequester = voiceButtonFocusRequester,
				isFocused = isVoiceButtonFocused
			) {
				Icon(
					Icons.Default.Mic,
					contentDescription = stringResource(R.string.lbl_voice_search),
					tint = if (isVoiceButtonFocused) Color.Red else Color.White,
					modifier = Modifier.size(24.dp)
				)
			}

			TVSearchFieldWithKeyboard(
				value = query,
				onValueChange = { newQuery ->
					query = newQuery
					viewModel.searchDebounced(newQuery)
					if (newQuery.isEmpty()) {
						hideKeyboard()
					}
				},
				onSearch = {
					if (query.isNotBlank()) {
						viewModel.searchImmediately(query)
						hideKeyboard()
					}
				},
				onFocusChanged = {
					isSearchFocused = it
					if (it) {
						showKeyboard()
					}
				},
				focusRequester = searchFieldFocusRequester,
				modifier = Modifier.weight(1f)
			)

			if (query.isNotEmpty()) {
				TVButton(
					onClick = {
						query = ""
						viewModel.searchDebounced("")
						searchFieldFocusRequester.requestFocus()
					},
					onFocusChanged = { isClearButtonFocused = it },
					focusRequester = clearButtonFocusRequester,
					isFocused = isClearButtonFocused
				) {
					Icon(
						Icons.Default.Close,
						contentDescription = stringResource(R.string.lbl_cancel),
						tint = if (isClearButtonFocused) Color.Black else Color.White,
						modifier = Modifier.size(24.dp)
					)
				}
			}
		}

		AnimatedVisibility(
			visible = searchResults.isNotEmpty(),
			enter = fadeIn(),
			exit = fadeOut()
		) {
			SearchResultsList(
				results = searchResults.toList(),
				onItemClicked = onNavigateToItem,
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f)
			)
		}

		if (searchResults.isEmpty() && query.isNotBlank()) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				contentAlignment = Alignment.Center
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Icon(
						Icons.Default.Search,
						contentDescription = null,
						tint = Color.Gray,
						modifier = Modifier.size(64.dp)
					)
					Text(
						text = stringResource(R.string.no_results_found),
						color = Color.White,
						style = MaterialTheme.typography.headlineSmall
					)
					Text(
						text = stringResource(R.string.lbl_search_try),
						color = Color.Gray,
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}
		}

		if (query.isEmpty()) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				contentAlignment = Alignment.Center
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Icon(
						Icons.Default.Search,
						contentDescription = null,
						tint = Color.Gray,
						modifier = Modifier.size(64.dp)
					)
					Text(
						text = stringResource(R.string.start_typing),
						color = Color.White,
						style = MaterialTheme.typography.headlineSmall
					)
					Text(
						text = stringResource(R.string.use_keyboard_or),
						color = Color.Gray,
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}
		}
	}
}

@Composable
private fun TVSearchFieldWithKeyboard(
	value: String,
	onValueChange: (String) -> Unit,
	onSearch: () -> Unit,
	onFocusChanged: (Boolean) -> Unit,
	focusRequester: FocusRequester,
	modifier: Modifier = Modifier
) {
	var isFocused by remember { mutableStateOf(false) }

	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		modifier = modifier
			.height(56.dp)
			.focusRequester(focusRequester)
			.onFocusChanged { focusState ->
				isFocused = focusState.isFocused
				onFocusChanged(focusState.isFocused)
			},
		placeholder = {
			Text(
				stringResource(R.string.lbl_search_hint),
				color = Color.Gray
			)
		},
		leadingIcon = {
			Icon(
				Icons.Default.Search,
				contentDescription = stringResource(R.string.lbl_search_hint),
				tint = if (isFocused) Color.White else Color.Gray
			)
		},
		colors = OutlinedTextFieldDefaults.colors(
			focusedTextColor = Color.White,
			unfocusedTextColor = Color.White,
			cursorColor = Color.White,
			focusedBorderColor = Color.White,
			unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
			focusedContainerColor = Color.White.copy(alpha = 0.15f),
			unfocusedContainerColor = Color.White.copy(alpha = 0.08f)
		),
		keyboardOptions = KeyboardOptions(
			imeAction = ImeAction.Search
		),
		keyboardActions = KeyboardActions(
			onSearch = { onSearch() }
		),
		singleLine = true,
		shape = RoundedCornerShape(8.dp),
		textStyle = TextStyle(
			fontSize = 18.sp,
			color = Color.White
		)
	)
}

@Composable
private fun TVButton(
	onClick: () -> Unit,
	onFocusChanged: (Boolean) -> Unit,
	focusRequester: FocusRequester,
	isFocused: Boolean,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	Box(
		modifier = modifier
			.size(50.dp)
			.background(
				color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
				shape = CircleShape
			)
			.border(
				width = if (isFocused) 2.dp else 1.dp,
				color = if (isFocused) Color.White else Color.Gray.copy(alpha = 0.5f),
				shape = CircleShape
			)
			.focusRequester(focusRequester)
			.onFocusChanged { onFocusChanged(it.isFocused) }
			.focusable()
			.onKeyEvent { keyEvent ->
				if (keyEvent.type == KeyEventType.KeyDown &&
					(keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
				) {
					onClick()
					true
				} else {
					false
				}
			},
		contentAlignment = Alignment.Center
	) {
		content()
	}
}

@Composable
private fun SearchResultsList(
	results: List<SearchResultGroup>,
	onItemClicked: (BaseRowItem) -> Unit,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current

	LazyColumn(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(34.dp)
	) {
		items(results, key = { it.labelRes }) { group ->
			SearchResultGroup(
				title = stringResource(group.labelRes),
				items = group.items.toList(),
				onItemClick = { baseItemDto ->
					val baseRowItem = BaseItemDtoBaseRowItem(baseItemDto)
					onItemClicked(baseRowItem)


				}
			)
		}
	}
}

@Composable
private fun SearchResultGroup(
	title: String,
	items: List<org.jellyfin.sdk.model.api.BaseItemDto>,
	onItemClick: (org.jellyfin.sdk.model.api.BaseItemDto) -> Unit
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text(
			text = title.uppercase(),
			color = Color.White,
			style = MaterialTheme.typography.titleMedium,
			modifier = Modifier.padding(horizontal = 20.dp)
		)

		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(horizontal = 20.dp)
		) {
			items(items, key = { it.id }) { item ->
				SearchItemCard(
					item = item,
					onClick = { onItemClick(item) }
				)
			}
		}
	}
}

@Composable
private fun SearchItemCard(
	item: org.jellyfin.sdk.model.api.BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val imageHelper: ImageHelper by (context as FragmentActivity).inject()
	var isCardFocused by remember { mutableStateOf(false) }

	val (cardWidth, cardHeight) = when (item.type) {
		org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> Pair(220, 110)
		org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_VIDEO -> Pair(220, 110)
		org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET -> Pair(220, 110)
		else -> Pair(120, 180)
	}

	Column(
		modifier = modifier.width(cardWidth.dp)
	) {
		Box(
			modifier = Modifier
				.height(cardHeight.dp)
				.onFocusChanged { isCardFocused = it.isFocused }
				.scale(if (isCardFocused) 1.10f else 1.0f)
				.border(
					width = 2.dp,
					color = if (isCardFocused) {
						with(LocalContext.current) {
							val attrs = intArrayOf(R.attr.focusBorderColor)
							val typedArray = obtainStyledAttributes(attrs)
							val colorInt = typedArray.getColor(0, Color.White.toArgb())
							typedArray.recycle()
							Color(colorInt)
						}
					} else {
						Color.White.copy(alpha = 0.2f)
					},
					shape = RoundedCornerShape(3.dp)
				)
				.clip(RoundedCornerShape(2.dp))
				.clickable { onClick() }
		) {
			AsyncImage(
				model = when (item.type) {
					org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET ->
						imageHelper.getThumbImageUrl(item, cardWidth * 2, cardHeight * 2)
					else ->
						imageHelper.getPrimaryImageUrl(item, cardWidth * 2, cardHeight * 2)
				},
				contentDescription = item.name,
				modifier = Modifier.fillMaxSize(),
				contentScale = androidx.compose.ui.layout.ContentScale.Crop
			)
		}

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 1.dp, vertical = 15.dp)
		) {
			val displayText = when (item.type) {
				org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
					val seriesName = item.seriesName ?: ""
					val episodeName = item.name ?: ""
					val seasonNumber = item.parentIndexNumber ?: 0
					val episodeNumber = item.indexNumber ?: 0

					if (seriesName.isNotBlank() && episodeName.isNotBlank()) {
						"$seriesName\nS${seasonNumber}E${episodeNumber}- $episodeName"
					} else {
						item.name ?: ""
					}
				}
				else -> item.name ?: ""
			}

			Text(
				text = displayText,
				color = Color.White,
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 2
			)
		}
	}
}

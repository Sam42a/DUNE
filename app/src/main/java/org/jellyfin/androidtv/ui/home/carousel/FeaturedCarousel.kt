package org.jellyfin.androidtv.ui.home.carousel

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.CarouselState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ShapeDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.composable.modifier.getBackdropFadingColor
import org.koin.compose.koinInject

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeaturedCarousel(
	items: List<CarouselItem>,
	onItemSelected: (CarouselItem) -> Unit,
	modifier: Modifier = Modifier
) {
	if (items.isEmpty()) {
		Box(
			modifier = modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.surface)
		) {
			Text(
				text = "No featured items available",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.align(Alignment.Center)
			)
		}
		return
	}

	var isCarouselFocused by remember { mutableStateOf(false) }
	val borderAlpha = if (isCarouselFocused) 1f else 0.1f
	var actualCarouselIndex by remember { mutableIntStateOf(0) }
	val carouselState = remember { CarouselState() }

	var currentIndex by remember { mutableIntStateOf(0) }
	val context = LocalContext.current
	var isManualNavigation by remember { mutableStateOf(false) } // Flag to prevent auto-scroll during manual navigation
	var lastManualNavigationTime by remember { mutableLongStateOf(0L) } // Track last manual navigation time
    val isAndroid12OrLower = remember { Build.VERSION.SDK_INT <= Build.VERSION_CODES.S }
    var autoScrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    val startAutoScroll = {
        autoScrollJob?.cancel()

        autoScrollJob = kotlinx.coroutines.GlobalScope.launch {
            while (true) {
                kotlinx.coroutines.delay(8000L) // 8 seconds delay
                if (autoScrollEnabled && !isCarouselFocused) {
                    currentIndex = (currentIndex + 1) % items.size
                    timber.log.Timber.d("Carousel auto-scrolled to index: $currentIndex")

                    // For Android 12 and bellow, we rely on the visual index change
                    if (isAndroid12OrLower) {
                        timber.log.Timber.d("Android 12 compatibility mode - visual index updated to $currentIndex")
                    }
                }
            }
        }
    }

    val disableAutoScrollTemporarily = {
        autoScrollEnabled = false
        timber.log.Timber.d("Auto-scroll disabled temporarily")

        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(5000L) // 5 second delay
            autoScrollEnabled = true
            timber.log.Timber.d("Auto-scroll re-enabled after delay")
        }
    }
    LaunchedEffect(Unit) {
        if (items.size > 1) {
            timber.log.Timber.d("Starting initial auto-scroll timer")
            startAutoScroll()
        }
    }

    // Don't steal focus pls
Android12CompatibleCarousel(
    items = items,
    currentIndex = currentIndex,
    onItemSelected = onItemSelected,
    onNavigate = { newIndex ->
        currentIndex = newIndex
        timber.log.Timber.d("Manual navigation updated currentIndex to: $newIndex")
    },
    onManualNavigation = { isManual ->
        isManualNavigation = isManual
        if (isManual) {
            lastManualNavigationTime = System.currentTimeMillis()
            timber.log.Timber.d("Manual navigation flag set to: $isManual, disabling auto-scroll")
            disableAutoScrollTemporarily()
        } else {
            timber.log.Timber.d("Manual navigation flag set to: $isManual")
        }
    },
    isCarouselFocused = isCarouselFocused,
    borderAlpha = borderAlpha,
    modifier = modifier
        .onFocusChanged { focusState ->
            isCarouselFocused = focusState.isFocused
            timber.log.Timber.d("Main carousel focus updated from Android12CompatibleCarousel: ${focusState.isFocused}")
        }
)
}

@Composable
private fun Android12CompatibleCarousel(
	items: List<CarouselItem>,
	currentIndex: Int,
	onItemSelected: (CarouselItem) -> Unit,
	onNavigate: (Int) -> Unit,
	onManualNavigation: (Boolean) -> Unit,
	isCarouselFocused: Boolean,
	borderAlpha: Float,
	modifier: Modifier = Modifier
) {
    val carouselFocusRequester = remember { FocusRequester() }
    var carouselHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        carouselFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(carouselFocusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                carouselHasFocus = focusState.isFocused
                timber.log.Timber.d("Android12CompatibleCarousel focus changed: ${focusState.isFocused}")
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }

                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onItemSelected(items[currentIndex])
                        true
                    }
                    Key.DirectionLeft -> {
                        if (items.isNotEmpty()) {
                            onManualNavigation(true) // Disable auto-scroll temporarily
                            val newIndex = if (currentIndex == 0) items.size - 1 else currentIndex - 1
                            timber.log.Timber.d("Manual navigation: previous item $newIndex")
                            onNavigate(newIndex)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (items.isNotEmpty()) {
                            onManualNavigation(true) // Disable auto-scroll temporarily
                            val newIndex = (currentIndex + 1) % items.size
                            timber.log.Timber.d("Manual navigation: next item $newIndex")
                            onNavigate(newIndex)
                        }
                        true
                    }
                    else -> false
                }
            }
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = "Featured items carousel - Android 12 compatibility mode"
            }
    ) {
        if (items.isNotEmpty()) {
            val currentItem = items[currentIndex]

            CarouselItemBackground(item = currentItem, modifier = Modifier.fillMaxSize())
            CarouselItemForeground(
                item = currentItem,
                isCarouselFocused = carouselHasFocus,
                onItemSelected = { onItemSelected(currentItem) },
                modifier = Modifier.fillMaxSize()
            )

            // Indicator
            CarouselIndicator(
                itemCount = items.size,
                activeItemIndex = currentIndex,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScope.CarouselIndicator(
	itemCount: Int,
	activeItemIndex: Int,
	modifier: Modifier = Modifier
) {
	Box(
		modifier = modifier
			.padding(16.dp)
			.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
			.graphicsLayer {
				clip = true
				shape = ShapeDefaults.ExtraSmall
			}
			.align(Alignment.BottomEnd)
	) {
		CarouselDefaults.IndicatorRow(
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(8.dp),
			itemCount = itemCount,
			activeItemIndex = activeItemIndex,
		)
	}
}

@Composable
private fun CarouselItemForeground(
	item: CarouselItem,
	isCarouselFocused: Boolean = false,
	onItemSelected: () -> Unit,
	modifier: Modifier = Modifier
) {
	Box(
		modifier = modifier,
		contentAlignment = Alignment.BottomStart
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = 28.dp, top = 28.dp, bottom = 28.dp, end = 0.dp),
			verticalArrangement = Arrangement.Bottom,
			horizontalAlignment = Alignment.Start
		) {
			Text(
				text = item.title,
				style = MaterialTheme.typography.displayMedium.copy(
					fontSize = 24.sp,
					fontWeight = FontWeight.Bold,
					shadow = Shadow(
						color = Color.Black.copy(alpha = 0.5f),
						offset = Offset(x = 2f, y = 4f),
						blurRadius = 2f
					)
				),
				maxLines = 1,
				color = Color.White
			)

			val yearAndRuntime = listOfNotNull(
				item.getYear().takeIf { it.isNotEmpty() },
				item.getRuntime().takeIf { it.isNotEmpty() }
			).joinToString(" • ")

			val userPreferences = koinInject<UserPreferences>()
			val ratingType = userPreferences[UserPreferences.defaultRatingType]

			val infoLineParts = mutableListOf<String>()

			if (yearAndRuntime.isNotEmpty()) {
				infoLineParts.add(yearAndRuntime)
			}

			if (ratingType != RatingType.RATING_HIDDEN) {
				item.communityRating?.let { communityRating ->
					if (communityRating > 0) {
						infoLineParts.add("⭐ ${String.format("%.1f", communityRating)}")
					}
				}
				item.criticRating?.let { criticRating ->
					if (criticRating > 0) {
						val tomatoIcon = if (criticRating >= 60f) "🍅" else "🍊"
						infoLineParts.add("$tomatoIcon ${String.format("%.0f", criticRating)}%")
					}
				}
			}

			if (infoLineParts.isNotEmpty()) {
				Text(
					text = infoLineParts.joinToString(" • "),
					style = MaterialTheme.typography.titleMedium.copy(
						fontSize = 16.sp,
						color = Color.White.copy(alpha = 0.8f)
					),
					modifier = Modifier.padding(top = 4.dp)
				)
			}
			if (item.description.isNotBlank()) {
				Text(
					text = item.description,
					style = MaterialTheme.typography.titleMedium.copy(
						fontSize = 14.sp,
						color = Color.White.copy(alpha = 0.9f),
						shadow = Shadow(
							color = Color.Black.copy(alpha = 0.7f),
							offset = Offset(x = 2f, y = 4f),
							blurRadius = 4f
						)
					),
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier
						.padding(top = 15.dp)
						.fillMaxWidth(0.595f)
				)
			} else {
				// Debug logging for missing description
				timber.log.Timber.w("No description available for item: ${item.title}")
			}

			WatchNowButton(onItemSelected = onItemSelected)
		}
	}
}

@Composable
private fun CarouselItemBackground(item: CarouselItem, modifier: Modifier = Modifier) {
	val imageUrl = item.backdropUrl ?: item.imageUrl

	val backgroundService: org.jellyfin.androidtv.data.service.BackgroundService = koinInject()
	val dimmingIntensity by backgroundService.backdropDimmingIntensity.collectAsState()
	val backdropFadingIntensity by backgroundService.backdropFadingIntensity.collectAsState()
	val localContext = androidx.compose.ui.platform.LocalContext.current

	val typedArray = localContext.theme.obtainStyledAttributes(
		intArrayOf(org.jellyfin.androidtv.R.attr.backdrop_fading_color)
	)
	val backgroundColor = androidx.compose.ui.graphics.Color(typedArray.getColor(0, 0x000000)).copy(alpha = dimmingIntensity * 0.3f)
	typedArray.recycle()

	val fadingColor = getBackdropFadingColor()

	Box(modifier = modifier.fillMaxSize()) {
		AsyncImage(
			modifier = androidx.compose.ui.Modifier
				.width(600.dp)
				.aspectRatio(16f / 9f)
				.align(androidx.compose.ui.Alignment.TopEnd),
			url = imageUrl,
			scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
		)

		// Dimmingg effect
		if (dimmingIntensity > 0f) {
			Box(
				modifier = androidx.compose.ui.Modifier
					.fillMaxSize()
					.background(
						brush = Brush.horizontalGradient(
							0f to backgroundColor,
							0.42f to backgroundColor,
							0.49f to Color.Transparent,
							1f to Color.Transparent
						)
					)
			)
		}

		//  This fading effect shit breaks if you change even a bit, keep the sweet spot as is
		Box(
			modifier = androidx.compose.ui.Modifier
				.fillMaxSize()
				.background(
					brush = Brush.horizontalGradient(
						0f to fadingColor,
						0.42f to fadingColor,
						0.49f to Color.Transparent,
						1f to Color.Transparent
					)
				)
		)
	}
}

@Composable
private fun WatchNowButton(onItemSelected: () -> Unit) {
	val buttonFocusRequester = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		buttonFocusRequester.requestFocus()
	}

	Button(
		onClick = onItemSelected,
		modifier = Modifier
			.padding(top = 15.dp)
			.focusRequester(buttonFocusRequester),
		contentPadding = androidx.compose.foundation.layout.PaddingValues(
			start = 1.dp,
			end = 17.dp,
			bottom = 0.dp
		),
		shape = ButtonDefaults.shape(shape = RoundedCornerShape(16.dp)),
		colors = ButtonDefaults.colors(
			containerColor = Color(0xFFFFFFFF),
			contentColor = Color.Black,
			focusedContentColor = Color.White.copy(alpha = 0.7f),
		),
		scale = ButtonDefaults.scale(scale = 0.95f),
		glow = ButtonDefaults.glow()
	) {
		Icon(
			imageVector = Icons.Outlined.PlayArrow,
			contentDescription = null,
			modifier = Modifier.size(21.dp)

		)
		Spacer(Modifier.size(4.dp))
		Text(
			text = "Play",
			style = MaterialTheme.typography.titleMedium.copy(
				fontSize = 12.sp,
				fontWeight = FontWeight.Medium
			)
		)
	}
}

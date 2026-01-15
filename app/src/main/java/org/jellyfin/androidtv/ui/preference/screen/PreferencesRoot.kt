package org.jellyfin.androidtv.ui.preference.screen

import android.R
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.preference.Preference
import org.jellyfin.preference.store.PreferenceStore

object Colors {
	val Background = Color(0xFF1F1F1F)
	val Surface = Color(0x25737373)
	val SurfaceVariant = Color(0x076000FF)
	val Primary = Color(0xFF5000C7)
	val PrimaryVariant = Color(0xBC6000FF)
	val OnSurface = Color(0xFFE5E5E5)
	val OnSurfaceVariant = Color(0xFFFFFFFF)
	val Divider = Color(0xDF3D3D3D)
	val FocusedOverlay = Color(0x19FFFFFF)
	val FocusedBorder = Color(0xFFFFFFFF)
	val DisabledContent = Color(0xFF666666)
	val ScrimBackground = Color(0x00000000)
	val PanelBackground = Color(0xFF171616)
}

@Composable
fun PreferencesRoot(content: @Composable () -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Colors.ScrimBackground),
		contentAlignment = Alignment.CenterEnd
	) {
		Surface(
			modifier = Modifier
				.padding(end = 16.dp)
				.width(420.dp)
				.height(520.dp),
			shape = RoundedCornerShape(12.dp),
			tonalElevation = 2.dp,
			shadowElevation = 16.dp,
			color = Colors.PanelBackground
		) {
			content()
		}
	}
}


@Composable
private fun PreferenceContainer(
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
	onKeyEvent: (KeyEvent) -> Boolean = { false },
	onClick: (() -> Unit)? = null,
	content: @Composable RowScope.() -> Unit
) {
	val isFocused by interactionSource.collectIsFocusedAsState()

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(6.dp))
			.then(
				if (onClick != null) {
					Modifier
						.focusable(interactionSource = interactionSource, enabled = enabled)
						.onKeyEvent(onKeyEvent)
						.clickable(
							interactionSource = interactionSource,
							indication = null,
							enabled = enabled
						) { onClick() }
				} else {
					Modifier.focusable(interactionSource = interactionSource, enabled = enabled)
				}
			)
			.border(
				width = if (isFocused && enabled) 1.5.dp else 0.dp,
				color = if (isFocused && enabled) Colors.FocusedBorder else Color.Transparent,
				shape = RoundedCornerShape(6.dp)
			)
			.background(
				when {
					isFocused && enabled -> Colors.FocusedOverlay
					!enabled -> Colors.Surface.copy(alpha = 0.5f)
					else -> Colors.Surface
				},
				RoundedCornerShape(6.dp)
			)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		content()
	}
}


@Composable
fun <T : Any, P> rememberPreferenceState(
	preference: Preference<T>,
	preferences: P
): Pair<T, (T) -> Unit> where P : PreferenceStore<*, *> {
	val initialValue = remember { preferences[preference] }
	val (state, setState) = remember { mutableStateOf(initialValue) }

	LaunchedEffect(state) {
		preferences[preference] = state
	}

	return state to { newValue: T -> setState(newValue) }
}


@Composable
fun <T : Enum<T>, P> rememberEnumPreferenceState(
	preference: Preference<T>,
	preferences: P
): Pair<T, (T) -> Unit> where P : PreferenceStore<*, *> {
	val initialValue = remember { preferences.getEnum(preference) }
	val (state, setState) = remember { mutableStateOf(initialValue) }

	LaunchedEffect(state) {
		preferences[preference] = state
	}

	return state to { newValue: T -> setState(newValue) }
}

@Composable
fun <T : Any, P> rememberListPreferenceState(
	preference: Preference<T>,
	preferences: P,
	onValueChanged: (T) -> Unit = {}
): Pair<T, (T) -> Unit> where P : PreferenceStore<*, *> {
	val initialValue = remember { preferences[preference] }
	val (state, setState) = remember { mutableStateOf(initialValue) }
	val isFirstComposition = remember { mutableStateOf(true) }

	LaunchedEffect(state) {
		if (!isFirstComposition.value) {
			preferences[preference] = state
			onValueChanged(state)
		} else {
			isFirstComposition.value = false
		}
	}

	return state to { newValue: T -> setState(newValue) }
}

@Composable
fun SwitchPreference(
	title: String,
	checked: Boolean,
	preference: Preference<Boolean>,
	onCheckedChange: (Boolean) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	description: String? = null
) {
	val interactionSource = remember { MutableInteractionSource() }

	PreferenceContainer(
		modifier = modifier,
		enabled = enabled,
		interactionSource = interactionSource,
		onKeyEvent = { keyEvent ->
			if (enabled && (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
				keyEvent.type == KeyEventType.KeyUp) {
				onCheckedChange(!checked)
				true
			} else {
				false
			}
		},
		onClick = if (enabled) {{ onCheckedChange(!checked) }} else null
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge,
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium,
				color = if (enabled) Colors.OnSurface else Colors.DisabledContent
			)
			if (description != null) {
				Spacer(modifier = Modifier.height(2.dp))
				Text(
					text = description,
					style = MaterialTheme.typography.bodyMedium,
					fontSize = 12.sp,
					color = if (enabled) Colors.OnSurfaceVariant else Colors.DisabledContent,
					lineHeight = 15.sp
				)
			}
		}

		Spacer(modifier = Modifier.width(12.dp))

		Switch(
			checked = checked,
			onCheckedChange = null,
			enabled = enabled,
			modifier = Modifier.scale(0.85f),
			colors = SwitchDefaults.colors(
				checkedThumbColor = Color.White,
				checkedTrackColor = Colors.Primary,
				uncheckedThumbColor = Color.White,
				uncheckedTrackColor = Colors.Divider,
				disabledCheckedThumbColor = Colors.DisabledContent,
				disabledCheckedTrackColor = Colors.Divider,
				disabledUncheckedThumbColor = Colors.DisabledContent,
				disabledUncheckedTrackColor = Colors.Divider
			)
		)
	}
}

@Composable
fun SeekBarPreference(
	title: String,
	value: Int,
	range: IntRange,
	onValueChange: (Int) -> Unit,
	modifier: Modifier = Modifier,
	step: Int = 1,
	description: String? = null,
	valueFormatter: (Int) -> String = { it.toString() }
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	PreferenceContainer(
		modifier = modifier,
		enabled = true,
		interactionSource = interactionSource,
		onKeyEvent = { keyEvent ->
			if (isFocused && keyEvent.type == KeyEventType.KeyUp) {
				when (keyEvent.key) {
					Key.DirectionRight, Key.Plus -> {
						val newValue = (value + step).coerceIn(range)
						onValueChange(newValue)
						true
					}
					Key.DirectionLeft, Key.Minus -> {
						val newValue = (value - step).coerceIn(range)
						onValueChange(newValue)
						true
					}
					else -> false
				}
			} else {
				false
			}
		},
		onClick = { }
	) {
		Column(modifier = Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = title,
						style = MaterialTheme.typography.bodyLarge,
						fontSize = 14.sp,
						fontWeight = FontWeight.Medium,
						color = Colors.OnSurface
					)
					if (description != null) {
						Spacer(modifier = Modifier.height(2.dp))
						Text(
							text = description,
							style = MaterialTheme.typography.bodyMedium,
							fontSize = 12.sp,
							color = Colors.OnSurfaceVariant,
							lineHeight = 15.sp
						)
					}
				}

				Text(
					text = valueFormatter(value),
					style = MaterialTheme.typography.bodyLarge,
					fontSize = 13.sp,
					fontWeight = FontWeight.SemiBold,
					color = Colors.OnSurface
				)
			}

			Spacer(modifier = Modifier.height(4.dp))

			Slider(
				value = value.toFloat(),
				onValueChange = { onValueChange(it.toInt()) },
				valueRange = range.first.toFloat()..range.last.toFloat(),
				modifier = Modifier
					.fillMaxWidth()
					.height(28.dp),
				enabled = isFocused,
				colors = SliderDefaults.colors(
					thumbColor = Colors.Primary,
					activeTrackColor = Colors.Primary,
					inactiveTrackColor = Colors.DisabledContent
				)
			)
		}
	}
}

@Composable
fun PreferenceHeader(
	title: String,
	modifier: Modifier = Modifier
) {
	Text(
		text = title.uppercase(),
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 14.dp)
			.padding(top = 8.dp),
		style = MaterialTheme.typography.titleMedium,
		fontSize = 20.sp,
		fontWeight = FontWeight.ExtraBold,
		letterSpacing = 1.2.sp,
		color = Colors.OnSurface
	)
}

@Composable
fun PreferenceDivider(modifier: Modifier = Modifier) {
	Spacer(
		modifier = modifier
			.fillMaxWidth()
			.height(1.dp)
			.padding(horizontal = 16.dp)
			.background(Colors.Divider)
	)
}

@Composable
fun ListPreference(
	title: String,
	value: String,
	onValueChange: (String) -> Unit,
	options: Map<String, String>,
	defaultValue: String,
	modifier: Modifier = Modifier,
	description: String? = null,
	context: Context = LocalContext.current
) {
	val interactionSource = remember { MutableInteractionSource() }
	val showDialog = remember { mutableStateOf(false) }

	PreferenceContainer(
		modifier = modifier,
		interactionSource = interactionSource,
		onKeyEvent = { keyEvent ->
			if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
				keyEvent.type == KeyEventType.KeyUp) {
				showDialog.value = true
				true
			} else {
				false
			}
		},
		onClick = { showDialog.value = true }
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge,
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium,
				color = Colors.OnSurface
			)
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				text = description ?: (options[value] ?: defaultValue),
				style = MaterialTheme.typography.bodyMedium,
				fontSize = 12.sp,
				color = Colors.OnSurfaceVariant,
				lineHeight = 15.sp
			)
		}

		Icon(
			painter = painterResource(R.drawable.arrow_down_float),
			contentDescription = null,
			modifier = Modifier.size(10.dp),
			tint = Colors.Primary
		)
	}

	if (showDialog.value) {
		SelectionDialog(
			title = title,
			options = options.keys.toList(),
			selectedOption = value,
			onOptionSelected = { option ->
				onValueChange(option)
				showDialog.value = false
			},
			onDismiss = { showDialog.value = false },
			optionLabel = { options[it] ?: it },
			context = context
		)
	}
}

@Composable
fun <T : Enum<T>> EnumPreference(
	title: String,
	value: T,
	onValueChange: (T) -> Unit,
	options: List<T>,
	modifier: Modifier = Modifier,
	description: String? = null,
	optionLabel: (T) -> String = { it.name },
	context: Context = LocalContext.current
) {
	val interactionSource = remember { MutableInteractionSource() }
	val showDialog = remember { mutableStateOf(false) }

	PreferenceContainer(
		modifier = modifier,
		interactionSource = interactionSource,
		onKeyEvent = { keyEvent ->
			if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
				keyEvent.type == KeyEventType.KeyUp) {
				showDialog.value = true
				true
			} else {
				false
			}
		},
		onClick = { showDialog.value = true }
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyMedium,
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium,
				color = Colors.OnSurface
			)
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				text = description ?: optionLabel(value),
				style = MaterialTheme.typography.bodyMedium,
				fontSize = 12.sp,
				color = Colors.OnSurfaceVariant,
				lineHeight = 15.sp
			)
		}

		Icon(
			painter = painterResource(R.drawable.arrow_down_float),
			contentDescription = null,
			modifier = Modifier.size(10.dp),
			tint = Colors.Primary
		)
	}

	if (showDialog.value) {
		SelectionDialog(
			title = title,
			options = options,
			selectedOption = value,
			onOptionSelected = { option ->
				onValueChange(option)
				showDialog.value = false
			},
			onDismiss = { showDialog.value = false },
			optionLabel = optionLabel,
			context = context
		)
	}
}

@Composable
private fun <T> SelectionDialog(
	title: String,
	options: List<T>,
	selectedOption: T,
	onOptionSelected: (T) -> Unit,
	onDismiss: () -> Unit,
	optionLabel: (T) -> String,
	context: Context = LocalContext.current
) {
	val firstOptionFocusRequester = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		kotlinx.coroutines.delay(100)
		firstOptionFocusRequester.requestFocus()
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		modifier = Modifier
			.width(380.dp)
			.height(440.dp),
		title = {
			Text(
				text = title,
				color = Colors.OnSurface,
				fontSize = 16.sp,
				fontWeight = FontWeight.SemiBold
			)
		},
		containerColor = Colors.PanelBackground,
		shape = RoundedCornerShape(10.dp),
		text = {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(12.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				LazyColumn(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(3.dp)
				) {
					itemsIndexed(options) { index, option ->
						val isSelected = option == selectedOption
						val isFirstOption = index == 0
						val optionInteractionSource = remember { MutableInteractionSource() }
						val isOptionFocused by optionInteractionSource.collectIsFocusedAsState()

						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(4.dp))
								.then(if (isFirstOption) Modifier.focusRequester(firstOptionFocusRequester) else Modifier)
								.focusable(interactionSource = optionInteractionSource)
								.onKeyEvent { keyEvent ->
									if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
										keyEvent.type == KeyEventType.KeyUp) {
										onOptionSelected(option)
										true
									} else {
										false
									}
								}
								.clickable(
									interactionSource = optionInteractionSource,
									indication = null
								) { onOptionSelected(option) }
								.border(
									width = if (isOptionFocused) 1.5.dp else 0.dp,
									color = if (isOptionFocused) Colors.FocusedBorder else Color.Transparent,
									shape = RoundedCornerShape(4.dp)
								)
								.background(
									when {
										isOptionFocused -> Colors.FocusedOverlay
										isSelected -> Colors.Surface
										else -> Color.Transparent
									},
									RoundedCornerShape(4.dp)
								)
								.padding(horizontal = 12.dp, vertical = 9.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							RadioButton(
								selected = isSelected,
								onClick = null,
								modifier = Modifier.scale(0.85f),
								colors = RadioButtonDefaults.colors(
									selectedColor = Colors.Primary,
									unselectedColor = Colors.OnSurfaceVariant
								)
							)
							Spacer(modifier = Modifier.width(8.dp))
							Text(
								text = optionLabel(option),
								color = Colors.OnSurface,
								fontSize = 13.sp,
								modifier = Modifier.weight(1f)
							)
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(
					text = context.getString(org.jellyfin.androidtv.R.string.btn_cancel),
					color = Colors.Primary,
					fontSize = 13.sp,
					fontWeight = FontWeight.SemiBold
				)
			}
		}
	)
}

@Composable
fun ActionPreference(
	title: String,
	description: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	icon: Int? = null,
	enabled: Boolean = true
) {
	val interactionSource = remember { MutableInteractionSource() }

	PreferenceContainer(
		modifier = modifier,
		enabled = enabled,
		interactionSource = interactionSource,
		onKeyEvent = { keyEvent ->
			if (enabled && (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
				keyEvent.type == KeyEventType.KeyUp) {
				onClick()
				true
			} else {
				false
			}
		},
		onClick = if (enabled) onClick else null
	) {
		if (icon != null) {
			Icon(
				painter = painterResource(icon),
				contentDescription = null,
				modifier = Modifier.size(18.dp),
				tint = if (enabled) Colors.Primary else Colors.DisabledContent
			)
			Spacer(modifier = Modifier.width(12.dp))
		}

		Column(modifier = Modifier.weight(1f, fill = true)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge,
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium,
				color = if (enabled) Colors.OnSurface else Colors.DisabledContent
			)
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				text = description,
				style = MaterialTheme.typography.bodyMedium,
				fontSize = 12.sp,
				color = if (enabled) Colors.OnSurfaceVariant else Colors.DisabledContent,
				lineHeight = 15.sp
			)
		}

		Icon(
			painter = painterResource(R.drawable.ic_menu_more),
			contentDescription = null,
			modifier = Modifier.size(16.dp),
			tint = if (enabled) Colors.OnSurfaceVariant else Colors.DisabledContent
		)
	}
}

@Composable
fun PreferenceCard(
	title: String,
	description: String,
	icon: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	Column(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.focusable(interactionSource = interactionSource, enabled = enabled)
			.onKeyEvent { keyEvent ->
				if (enabled && (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
					keyEvent.type == KeyEventType.KeyUp) {
					onClick()
					true
				} else {
					false
				}
			}
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				enabled = enabled
			) { if (enabled) onClick() }
			.border(
				width = if (isFocused && enabled) 1.5.dp else 0.dp,
				color = if (isFocused && enabled) Colors.FocusedBorder else Color.Transparent,
				shape = RoundedCornerShape(8.dp)
			)
			.background(
				brush = if (isFocused && enabled) {
					Brush.verticalGradient(
						colors = listOf(
							Colors.SurfaceVariant,
							Colors.Surface
						)
					)
				} else {
					Brush.verticalGradient(
						colors = listOf(
							Colors.Surface,
							Colors.Surface
						)
					)
				},
				shape = RoundedCornerShape(8.dp)
			)
			.padding(12.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				painter = painterResource(icon),
				contentDescription = null,
				modifier = Modifier.size(18.dp),
				tint = if (enabled) Colors.Primary else Colors.DisabledContent
			)
			Spacer(modifier = Modifier.width(10.dp))
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				fontSize = 15.sp,
				fontWeight = FontWeight.SemiBold,
				color = if (enabled) Colors.OnSurface else Colors.DisabledContent
			)
		}

		Text(
			text = description,
			style = MaterialTheme.typography.bodyMedium,
			fontSize = 12.sp,
			color = if (enabled) Colors.OnSurfaceVariant else Colors.DisabledContent,
			lineHeight = 16.sp
		)
	}
}
@Composable
fun ButtonRemapPreference(
	title: String,
	value: Int,
	defaultValue: Int,
	onValueChange: (Int) -> Unit,
	modifier: Modifier = Modifier,
	description: String? = null,
	context: Context = LocalContext.current
) {
	val interactionSource = remember { MutableInteractionSource() }
	val showDialog = remember { mutableStateOf(false) }
	val currentKeyCode = remember { mutableStateOf(value) }

	fun getKeyCodeName(keyCode: Int): String {
		val keyCodeString = android.view.KeyEvent.keyCodeToString(keyCode)
		return if (keyCodeString.startsWith("KEYCODE")) {
			keyCodeString
				.removePrefix("KEYCODE_")
				.lowercase()
				.split("_")
				.joinToString(" ") {
					it.replaceFirstChar { char ->
						if (char.isLowerCase()) char.titlecase() else char.toString()
					}
				}
		} else {
			keyCodeString
		}
	}

	PreferenceContainer(
		modifier = modifier,
		interactionSource = interactionSource,
		onKeyEvent = { keyEvent ->
			if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) &&
				keyEvent.type == KeyEventType.KeyUp) {
				showDialog.value = true
				true
			} else {
				false
			}
		},
		onClick = { showDialog.value = true }
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge,
				fontSize = 15.sp,
				fontWeight = FontWeight.Medium,
				color = Colors.OnSurface
			)
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				text = description ?: getKeyCodeName(value),
				style = MaterialTheme.typography.bodyMedium,
				fontSize = 12.sp,
				color = Colors.OnSurfaceVariant,
				lineHeight = 16.sp
			)
		}
	}

	if (showDialog.value) {
		AlertDialog(
			onDismissRequest = {
				showDialog.value = false
				currentKeyCode.value = value
			},
			title = {
				Text(
					text = title,
					color = Colors.OnSurface,
					fontSize = 16.sp,
					fontWeight = FontWeight.SemiBold
				)
			},
			containerColor = Colors.PanelBackground,
			text = {
				Column(
					modifier = Modifier.fillMaxWidth(),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Text(
						text = context.getString(org.jellyfin.androidtv.R.string.pref_button_remapping_description),
						color = Colors.OnSurfaceVariant,
						fontSize = 14.sp
					)

					Text(
						text = "Current: ${getKeyCodeName(currentKeyCode.value)}",
						color = Colors.Primary,
						fontSize = 16.sp,
						fontWeight = FontWeight.SemiBold
					)

					Text(
						text = "Default: ${getKeyCodeName(defaultValue)}",
						color = Colors.OnSurfaceVariant,
						fontSize = 14.sp
					)
				}
			},
			confirmButton = {
				TextButton(onClick = {
					onValueChange(currentKeyCode.value)
					showDialog.value = false
				}) {
					Text(
						text = context.getString(org.jellyfin.androidtv.R.string.lbl_ok),
						color = Colors.Primary,
						fontSize = 15.sp,
						fontWeight = FontWeight.SemiBold
					)
				}
			},
			dismissButton = {
				TextButton(onClick = {
					showDialog.value = false
					currentKeyCode.value = value
				}) {
					Text(
						text = context.getString(org.jellyfin.androidtv.R.string.btn_cancel),
						color = Colors.OnSurfaceVariant,
						fontSize = 15.sp,
						fontWeight = FontWeight.SemiBold
					)
				}
			}
		)
	}
}

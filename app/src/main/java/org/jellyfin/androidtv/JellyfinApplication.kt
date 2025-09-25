package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.ACRA
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppLanguage
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.util.LocaleHelper
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

private val appModule = module {
	// Define your modules here
}

@Suppress("unused")
class JellyfinApplication : Application() {
	private lateinit var userPreferences: UserPreferences
	private lateinit var notificationsRepository: NotificationsRepository
	private var appContext: Context? = null

	override fun onCreate() {
		super.onCreate()

		// Store application context
		appContext = applicationContext

		// Don't run in ACRA service
		if (ACRA.isACRASenderServiceProcess()) return

		// Initialize Timber for logging in debug builds
		if (BuildConfig.DEBUG) {
			Timber.plant(Timber.DebugTree())
		}

		// Initialize Koin
		try {
			startKoin {
				androidContext(this@JellyfinApplication)
				modules(appModule)
			}

			// Initialize dependencies
			userPreferences = get()
			notificationsRepository = get()

			// Apply language configuration
			applyLanguage()

			// Add default notifications
			notificationsRepository.addDefaultNotifications()
		} catch (e: Exception) {
			if (BuildConfig.DEBUG) Timber.e(e, "Error during application setup")
		}
	}


	/**
	 * Apply the selected language to the app context
	 */
	private fun applyLanguage() {
		if (!::userPreferences.isInitialized) return

		try {
			val language = userPreferences[UserPreferences.appLanguage]
			val locale = when (language) {
				AppLanguage.SYSTEM_DEFAULT -> {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
						LocaleList.getDefault().get(0)
					} else {
						@Suppress("DEPRECATION")
						Locale.getDefault()
					}
				}
				else -> when (language.code) {
					"zh-rCN" -> Locale("zh", "CN")
					"zh-rTW" -> Locale("zh", "TW")
					else -> Locale(language.code)
				}
			}

			Locale.setDefault(locale)
			val config = Configuration(resources.configuration)

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				val localeList = LocaleList(locale)
				LocaleList.setDefault(localeList)
				config.setLocales(localeList)
				config.setLocale(locale)

				createConfigurationContext(config)
				resources.updateConfiguration(config, resources.displayMetrics)
				applicationContext.createConfigurationContext(config)
			} else {
				@Suppress("DEPRECATION")
				val config = Configuration(resources.configuration)
				@Suppress("DEPRECATION")
				config.locale = locale

				val context = createConfigurationContext(config)
				val resources = context.resources
				val configuration = Configuration(config)

				super.getResources().updateConfiguration(configuration, resources.displayMetrics)
			}

			Locale.setDefault(locale)
		} catch (e: Exception) {
			if (BuildConfig.DEBUG) Timber.e(e, "Failed to apply language settings")
		}
	}

	/**
	 * Called from the StartupActivity when the user session is started.
	 */
	suspend fun onSessionStart() = withContext(Dispatchers.IO) {
		val workManager by inject<WorkManager>()
		val socketListener by inject<SocketHandler>()

		// Update background worker
		launch {
			// Cancel all current workers
			workManager.cancelAllWork().await()

			// Recreate periodic workers
			workManager.enqueueUniquePeriodicWork(
				LeanbackChannelWorker.PERIODIC_UPDATE_REQUEST_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
				PeriodicWorkRequestBuilder<LeanbackChannelWorker>(1, TimeUnit.HOURS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
					.build()
			).await()
		}

		// Update WebSockets
		launch { socketListener.updateSession() }
	}

	override fun attachBaseContext(base: Context) {
		// Get the saved language preference directly from SharedPreferences
		val prefs = base.getSharedPreferences("org.jellyfin.androidtv.preferences", Context.MODE_PRIVATE)
		val savedLanguageCode = prefs.getString("app_language", null)

		// Create locale based on saved preference or fall back to system default
		val locale = when {
			!savedLanguageCode.isNullOrEmpty() && savedLanguageCode != "system" -> when (savedLanguageCode) {
				"zh-rCN" -> Locale("zh", "CN")
				"zh-rTW" -> Locale("zh", "TW")
				else -> Locale(savedLanguageCode)
			}
			else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				LocaleList.getDefault().get(0)
			} else {
				@Suppress("DEPRECATION")
				Locale.getDefault()
			}
		}

		// Set the default locale
		Locale.setDefault(locale)

		// Create configuration with the selected locale
		val config = Configuration()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			// For Android 7.0+ (API 24+)
			config.setLocale(locale)
			config.setLocales(LocaleList(locale))

			// Apply the configuration to the base context
			val context = base.createConfigurationContext(config)
			super.attachBaseContext(context)

			// Update the resources configuration
			val resources = context.resources
			resources.updateConfiguration(config, resources.displayMetrics)

			Timber.d("Applied locale configuration (API 24+): $locale")
		} else {
			// For older Android versions
			@Suppress("DEPRECATION")
			config.locale = locale

			// Apply the configuration to the base context
			val context = base.createConfigurationContext(config)
			super.attachBaseContext(context)

			// Update the resources configuration
			val resources = context.resources
			val metrics = resources.displayMetrics
			resources.configuration.setTo(config)
			resources.updateConfiguration(resources.configuration, metrics)

			Timber.d("Applied locale configuration (legacy): $locale")
		}

		// Initialize telemetry (don't access Koin here)
		TelemetryService.init(this)

		Timber.d("attachBaseContext completed. Current locale: ${Locale.getDefault()}")
	}
}

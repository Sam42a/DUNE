package org.jellyfin.androidtv.di

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import java.io.File
import coil3.annotation.ExperimentalCoilApi
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import coil3.util.Logger
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.auth.repository.UserRepositoryImpl
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.CustomMessageRepositoryImpl
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemMutationRepositoryImpl
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepositoryImpl
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepositoryImpl
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.integration.dream.DreamViewModel
import org.jellyfin.androidtv.ui.ScreensaverViewModel
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.NavigationRepositoryImpl
import org.jellyfin.androidtv.ui.picture.PictureViewerViewModel
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer
import org.jellyfin.androidtv.ui.playback.nextup.NextUpViewModel
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepositoryImpl
import org.jellyfin.androidtv.ui.search.SearchFragmentDelegate
import org.jellyfin.androidtv.ui.search.SearchRepository
import org.jellyfin.androidtv.ui.search.SearchRepositoryImpl
import org.jellyfin.androidtv.ui.search.SearchViewModel
import org.jellyfin.androidtv.ui.startup.ServerAddViewModel
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.ui.startup.UserLoginViewModel
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.ReportingHelper
import org.jellyfin.androidtv.util.coil.CoilTimberLogger
import org.jellyfin.androidtv.util.coil.createCoilConnectivityChecker
import org.jellyfin.androidtv.util.sdk.SdkPlaybackHelper
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.jellyfin.sdk.Jellyfin as JellyfinSdk
import android.content.SharedPreferences
// Removed allowHardware import as it's not available in Coil 3.x

val defaultDeviceInfo = named("defaultDeviceInfo")

val appModule = module {
	// SharedPreferences
	single<SharedPreferences> {
		androidx.preference.PreferenceManager.getDefaultSharedPreferences(androidContext())
	}

	// Online Subtitles dependencies







	// New SDK
	single(defaultDeviceInfo) { androidDevice(get()) }
	single { HttpClientOptions() }
	single {
		createJellyfin {
			context = androidContext()

			// Add client info
			clientInfo = ClientInfo("Dune Android TV", BuildConfig.VERSION_NAME)
			deviceInfo = get(defaultDeviceInfo)

			// Change server version
			minimumServerVersion = ServerRepository.minimumServerVersion
		}
	}

	single {
		// Create an empty API instance, the actual values are set by the SessionRepository
		get<JellyfinSdk>().createApi(httpClientOptions = get<HttpClientOptions>())
	}

	single { SocketHandler(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

	// Coil (images)
	single {
		val context = androidContext()
		// Memory cache size (in bytes)
		val memoryCacheSize = 700L * 1024 * 1024
		// Set disk cache size to 1GB (1024 * 1024 * 1024 bytes)
		val diskCacheSize = 1024L * 1024 * 1024

		// Configure disk cache
		val diskCacheDir = File(context.cacheDir, "image_cache")
		diskCacheDir.mkdirs()
		val diskCache = coil3.disk.DiskCache.Builder()
			.directory(diskCacheDir.toOkioPath())
			.maxSizeBytes(1024L * 1024 * 1024) // 1GB
			.build()

		ImageLoader.Builder(context).apply {
			serviceLoaderEnabled(false)
			logger(CoilTimberLogger(if (BuildConfig.DEBUG) Logger.Level.Warn else Logger.Level.Error))

			// Configure memory cache
			memoryCache {
				coil3.memory.MemoryCache.Builder()
					.maxSizeBytes(memoryCacheSize)
					.build()
			}

			// Set disk cache
			diskCache(diskCache)

			// Coil 3.x configuration
			components {
				@OptIn(ExperimentalCoilApi::class)
				add(OkHttpNetworkFetcherFactory(connectivityChecker = ::createCoilConnectivityChecker))

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory())
				else add(GifDecoder.Factory())
				add(SvgDecoder.Factory())
			}
		}.build()
	}

	// Non API related
	single { DataRefreshService() }
	single { PlaybackControllerContainer() }

	single<UserRepository> { UserRepositoryImpl() }
	single<UserViewsRepository> { UserViewsRepositoryImpl(get()) }
	single<NotificationsRepository> { NotificationsRepositoryImpl(get(), get()) }
	single<ItemMutationRepository> { ItemMutationRepositoryImpl(get(), get()) }
	single<CustomMessageRepository> { CustomMessageRepositoryImpl() }
	single<NavigationRepository> { NavigationRepositoryImpl(Destinations.home) }
	single<SearchRepository> { SearchRepositoryImpl(get()) }
	single<MediaSegmentRepository> { MediaSegmentRepositoryImpl(get(), get()) }

	viewModel { StartupViewModel(get(), get(), get(), get()) }
	viewModel { UserLoginViewModel(get(), get(), get(), get(defaultDeviceInfo)) }
	viewModel { ServerAddViewModel(get()) }
	viewModel { NextUpViewModel(get(), get(), get()) }
	viewModel { PictureViewerViewModel(get()) }
	viewModel { ScreensaverViewModel(get()) }
	viewModel { SearchViewModel(get()) }
	viewModel { DreamViewModel(get(), get(), get(), get(), get()) }

	single {
        BackgroundService(
            context = get(),
            jellyfin = get(),
            api = get(),
            userPreferences = get(),
            imageLoader = get(),
            imageHelper = get()
        )
    }

	single { MarkdownRenderer(get()) }
	single { ItemLauncher() }
	single { KeyProcessor() }
	single { ReportingHelper(get(), get()) }
	single<PlaybackHelper> { SdkPlaybackHelper(get(), get(), get(), get()) }

	factory { (context: Context) -> SearchFragmentDelegate(context, get(), get()) }
}

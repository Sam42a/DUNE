package org.jellyfin.androidtv.di

import android.content.Context
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.ImageHelper
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val utilsModule = module {
	single { ImageHelper(get(), get(), androidContext()) }
}

package org.jellyfin.androidtv.util

/**
 * Represents the result of an update check.
 */
sealed class UpdateResult {
    /**
     * An update is available.
     * @property version The version string of the available update.
     * @property downloadUrl The URL to download the update from.
     */
    data class UpdateAvailable(val version: String, val downloadUrl: String) : UpdateResult()

    /**
     * No update is available - the current version is up to date.
     */
    object NoUpdateAvailable : UpdateResult()

    /**
     * An error occurred while checking for updates.
     * @property message A description of the error that occurred.
     */
    data class Error(val message: String) : UpdateResult()
}

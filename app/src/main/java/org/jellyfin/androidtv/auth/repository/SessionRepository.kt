package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.DISABLED
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.LAST_USER
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.SPECIFIC_USER
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

data class Session(
	val userId: UUID,
	val serverId: UUID,
	val accessToken: String,
)

enum class SessionRepositoryState {
	READY,
	RESTORING_SESSION,
	SWITCHING_SESSION,
}

interface SessionRepository {
	val currentSession: StateFlow<Session?>
	val state: StateFlow<SessionRepositoryState>

	suspend fun restoreSession(destroyOnly: Boolean)
	suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean
	fun destroyCurrentSession()
}

class SessionRepositoryImpl(
	private val authenticationPreferences: AuthenticationPreferences,
	private val authenticationStore: AuthenticationStore,
	private val userApiClient: ApiClient,
	private val preferencesRepository: PreferencesRepository,
	private val defaultDeviceInfo: DeviceInfo,
	private val userRepository: UserRepository,
	private val serverRepository: ServerRepository,
	private val telemetryPreferences: TelemetryPreferences,
) : SessionRepository {
	private val currentSessionMutex = Mutex()
	private val _currentSession = MutableStateFlow<Session?>(null)
	override val currentSession = _currentSession.asStateFlow()
	private val _state = MutableStateFlow(SessionRepositoryState.READY)
	override val state = _state.asStateFlow()

	override suspend fun restoreSession(destroyOnly: Boolean): Unit = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			Timber.i("Restoring session (destroyOnly: $destroyOnly)")

			_state.value = SessionRepositoryState.RESTORING_SESSION

			val alwaysAuthenticate = authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate]
			val autoLoginBehavior = authenticationPreferences[AuthenticationPreferences.autoLoginUserBehavior]

			Timber.d("Auto-login behavior: $autoLoginBehavior, alwaysAuthenticate: $alwaysAuthenticate")

			try {
				when {
					alwaysAuthenticate || autoLoginBehavior == DISABLED -> {
						Timber.i("Auto-login disabled or always authenticate is enabled - clearing session")
						destroyCurrentSession()
						authenticationPreferences[AuthenticationPreferences.lastServerId] = ""
						authenticationPreferences[AuthenticationPreferences.lastUserId] = ""
					}
					autoLoginBehavior == LAST_USER && !destroyOnly -> {
						Timber.i("Attempting to restore last user session")
						val session = createLastUserSession()
						if (session != null) {
							Timber.d("Found last user session for user ${session.userId}")
							setCurrentSession(session)
						} else {
							Timber.d("No last user session found")
						}
					}
					autoLoginBehavior == SPECIFIC_USER && !destroyOnly -> {
						val serverId = authenticationPreferences[AuthenticationPreferences.autoLoginServerId].toUUIDOrNull()
						val userId = authenticationPreferences[AuthenticationPreferences.autoLoginUserId].toUUIDOrNull()
						if (serverId != null && userId != null) {
							Timber.d("Attempting to restore specific user session for user $userId")
							val session = createUserSession(serverId, userId)
							if (session != null) {
								Timber.d("Found specific user session for user $userId")
								setCurrentSession(session)
							} else {
								Timber.d("No specific user session found for user $userId")
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "Error during session restoration")
			} finally {
				_state.value = SessionRepositoryState.READY
				Timber.d("Session restoration complete. Current user: ${currentSession.value?.userId}")
			}
		}
	}

	override suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean {
		// No change in user - don't switch
		if (currentSession.value?.userId == userId) {
			Timber.d("Current session user is the same as the requested user")
			return false
		}

		_state.value = SessionRepositoryState.SWITCHING_SESSION
		Timber.i("Switching current session to user $userId")

		val session = createUserSession(serverId, userId)
		if (session == null) {
			Timber.w("Could not switch to non-existing session for user $userId")
			_state.value = SessionRepositoryState.READY
			return false
		}

		val switched = setCurrentSession(session)
		_state.value = SessionRepositoryState.READY
		return switched
	}

	override fun destroyCurrentSession() {
		Timber.i("Destroying current session")

		userRepository.updateCurrentUser(null)
		_currentSession.value = null
		_state.value = SessionRepositoryState.READY
	}

	private suspend fun setCurrentSession(session: Session?): Boolean {
		Timber.d("Setting current session: ${session?.userId} (current: ${currentSession.value?.userId})")

		if (session != null) {
			// No change in session - don't switch
			if (currentSession.value?.userId == session.userId) {
				Timber.d("Session already active for user ${session.userId}")
				return true
			}

			// Update last active user
			Timber.d("Updating last active user to ${session.userId}")
			authenticationPreferences[AuthenticationPreferences.lastServerId] = session.serverId.toString()
			authenticationPreferences[AuthenticationPreferences.lastUserId] = session.userId.toString()

			// Check if server version is supported
			val server = serverRepository.getServer(session.serverId)
			if (server == null || !server.versionSupported) {
				Timber.w("Server ${session.serverId} not found or version not supported")
				return false
			}
		}

		// Update session after binding the apiclient settings
		val deviceInfo = session?.let { defaultDeviceInfo.forUser(it.userId) } ?: defaultDeviceInfo
		Timber.i("Updating current session. userId=${session?.userId}")

		val applied = userApiClient.applySession(session, deviceInfo)
		if (applied && session != null) {
			try {
				val user = withContext(Dispatchers.IO) {
					userApiClient.userApi.getCurrentUser().content
				}
				Timber.d("Successfully authenticated user ${user.id}")
				userRepository.updateCurrentUser(user)

				// Update crash reporting URL
				val crashReportUrl = userApiClient.clientLogApi.logFileUrl()
				telemetryPreferences[TelemetryPreferences.crashReportUrl] = crashReportUrl
				telemetryPreferences[TelemetryPreferences.crashReportToken] = session.accessToken

				// Important: Update the current session value after successful authentication
				_currentSession.value = session
				Timber.d("Session updated successfully for user ${user.id}")

				// Notify preferences after session is fully established
				preferencesRepository.onSessionChanged()
				return true
			} catch (err: ApiClientException) {
				Timber.e(err, "Unable to authenticate: bad response when getting user info")
				destroyCurrentSession()
				return false
			}
		} else {
			Timber.w("Failed to apply session or session is null")
			userRepository.updateCurrentUser(null)
			_currentSession.value = null
			preferencesRepository.onSessionChanged()
			return false
		}

		return true
	}

	private fun createLastUserSession(): Session? {
		val lastUserId = authenticationPreferences[AuthenticationPreferences.lastUserId].toUUIDOrNull()
		val lastServerId = authenticationPreferences[AuthenticationPreferences.lastServerId].toUUIDOrNull()

		return if (lastUserId != null && lastServerId != null) createUserSession(lastServerId, lastUserId)
		else null
	}

	private fun createUserSession(serverId: UUID, userId: UUID): Session? {
		val account = authenticationStore.getUser(serverId, userId)
		if (account?.accessToken == null) return null

		return Session(
			userId = userId,
			serverId = serverId,
			accessToken = account.accessToken
		)
	}

	private fun ApiClient.applySession(session: Session?, newDeviceInfo: DeviceInfo = defaultDeviceInfo): Boolean {
		if (session == null) {
			update(
				baseUrl = null,
				accessToken = null,
				deviceInfo = newDeviceInfo,
			)
		} else {
			val server = authenticationStore.getServer(session.serverId)
				?: return false

			update(
				baseUrl = server.address,
				accessToken = session.accessToken,
				deviceInfo = newDeviceInfo,
			)
		}

		return true
	}
}

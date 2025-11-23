package org.jellyfin.androidtv.ui.startup.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.FragmentServerBinding
import org.jellyfin.androidtv.ui.ServerButtonView
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber

class ServerFragment : Fragment() {
	private var initialFocusSet = false
	companion object {
		const val ARG_SERVER_ID = "server_id"
	}

	private val startupViewModel: StartupViewModel by activityViewModel()
	private val markdownRenderer: MarkdownRenderer by inject()
	private val authenticationRepository: AuthenticationRepository by inject()
	private val serverUserRepository: ServerUserRepository by inject()
	private val backgroundService: BackgroundService by inject()
	private var _binding: FragmentServerBinding? = null
	private val binding get() = _binding!!

	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }?.toUUIDOrNull()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val server = serverIdArgument?.let(startupViewModel::getServer)

		if (server == null) {
			navigateFragment<SelectServerFragment>(keepToolbar = true, keepHistory = false)
			return null
		}

		_binding = FragmentServerBinding.inflate(inflater, container, false)

		val userAdapter = UserAdapter(requireContext(), server, startupViewModel, authenticationRepository, serverUserRepository)
		userAdapter.onItemPressed = { user ->
			startupViewModel.authenticate(server, user).onEach { state ->
				when (state) {
					// Ignored states
					AuthenticatingState -> Unit
					AuthenticatedState -> Unit
					// Actions
					RequireSignInState -> navigateFragment<UserLoginFragment>(bundleOf(
						UserLoginFragment.ARG_SERVER_ID to server.id.toString(),
						UserLoginFragment.ARG_USERNAME to user.name,
					))
					// Errors
					ServerUnavailableState,
					is ApiClientErrorLoginState -> Toast.makeText(context, R.string.server_connection_failed, Toast.LENGTH_LONG).show()

					is ServerVersionNotSupported -> Toast.makeText(
						context,
						getString(
							R.string.server_issue_outdated_version,
							state.server.version,
							ServerRepository.recommendedServerVersion.toString()
						),
						Toast.LENGTH_LONG
					).show()
				}
			}.launchIn(lifecycleScope)
		}
		binding.users.adapter = userAdapter

		startupViewModel.users
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.onEach { users ->
				userAdapter.updateItems(users)

				binding.users.isFocusable = users.any()
				binding.noUsersWarning.isVisible = users.isEmpty()

				// Set initial focus on first user if not already set
				if (users.isNotEmpty() && !initialFocusSet) {
					initialFocusSet = true
					view?.post {
						try {
							if (isAdded && view != null) {
								val firstChild = binding.users.getChildAt(0)
								firstChild?.requestFocus()
							}
						} catch (e: Exception) {
							Timber.e(e, "Error setting initial focus")
						}
					}
				}
			}.launchIn(viewLifecycleOwner.lifecycleScope)

		startupViewModel.loadUsers(server)

		onServerChange(server)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				val updated = startupViewModel.updateServer(server)
				if (updated) startupViewModel.getServer(server.id)?.let(::onServerChange)
			}
		}

		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()

		_binding = null
	}

	private fun onServerChange(server: Server) {
		binding.loginDisclaimer.text = server.loginDisclaimer?.let { markdownRenderer.toMarkdownSpanned(it) }

		binding.serverButton.apply {
			state = ServerButtonView.State.EDIT
			name = server.name
			address = server.address
			version = server.version
		}

		binding.addUserButton.setOnClickListener {
			navigateFragment<UserLoginFragment>(
				args = bundleOf(
					UserLoginFragment.ARG_SERVER_ID to server.id.toString(),
					UserLoginFragment.ARG_USERNAME to null
				)
			)
		}

		binding.serverButton.setOnClickListener {
			navigateFragment<SelectServerFragment>(keepToolbar = true)
		}

		if (!server.versionSupported) {
			binding.notification.isVisible = true
			binding.notification.text = getString(
				R.string.server_unsupported_notification,
				server.version,
				ServerRepository.recommendedServerVersion.toString(),
			)
		} else if (!server.setupCompleted) {
			binding.notification.isVisible = true
			binding.notification.text = getString(R.string.server_setup_incomplete)
		} else {
			binding.notification.isGone = true
		}
	}

	private inline fun <reified F : Fragment> navigateFragment(
		args: Bundle = bundleOf(),
		keepToolbar: Boolean = false,
		keepHistory: Boolean = true,
	) {
		requireActivity()
			.supportFragmentManager
			.commit {
				if (keepToolbar) {
					replace<StartupToolbarFragment>(R.id.content_view)
					add<F>(R.id.content_view, null, args)
				} else {
					replace<F>(R.id.content_view, null, args)
				}

				if (keepHistory) addToBackStack(null)
			}
	}

	override fun onResume() {
		super.onResume()

		startupViewModel.reloadStoredServers()
		backgroundService.clearBackgrounds()

		val server = serverIdArgument?.let(startupViewModel::getServer)
		if (server != null) startupViewModel.loadUsers(server)
		else navigateFragment<SelectServerFragment>(keepToolbar = true)
	}

	private inner class UserAdapter(
		private val context: Context,
		private val server: Server,
		private val startupViewModel: StartupViewModel,
		private val authenticationRepository: AuthenticationRepository,
		private val serverUserRepository: ServerUserRepository,
	) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {
		private var items: List<User> = emptyList()
		var onItemPressed: (User) -> Unit = {}

		fun updateItems(newItems: List<User>) {
			items = newItems
			notifyDataSetChanged()
		}

		override fun getItemCount(): Int = items.size

		private fun getItem(position: Int): User = items[position]

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val view = LayoutInflater.from(parent.context).inflate(R.layout.view_circular_user_profile, parent, false)
			return ViewHolder(view)
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val user = getItem(position)

			// Load the profile image
			val placeholder = ContextCompat.getDrawable(holder.itemView.context, R.drawable.tile_user)
			holder.profileImage.load(
				url = startupViewModel.getUserImage(server, user),
				blurHash = null,
				placeholder = placeholder,
				aspectRatio = 1.0,
				blurHashResolution = 32
			)

			holder.itemView.findViewById<TextView>(R.id.name).text = user.name

			// Set focus change listener for animations
			holder.container.setOnFocusChangeListener { _, hasFocus ->
				holder.itemView.animate()
					.scaleX(if (hasFocus) 1.05f else 1.0f)
					.scaleY(if (hasFocus) 1.05f else 1.0f)
					.translationZ(if (hasFocus) 8.8f else 0f)
					.setDuration(200)
					.start()

				if (hasFocus) {
					// Scroll to the focused position
					holder.itemView.post {
						val recyclerView = holder.itemView.parent as? RecyclerView
						recyclerView?.smoothScrollToPosition(holder.bindingAdapterPosition)
					}
				}
			}
		}

		private fun showUserMenu(view: View, user: User) {
			val popup = PopupMenu(view.context, view)
			val menu = popup.menu

			// Logout button
			if (user is PrivateUser && user.accessToken != null) {
				menu.add(0, View.generateViewId(), 0, R.string.lbl_sign_out).setOnMenuItemClickListener {
					authenticationRepository.logout(user)
					true
				}
			}

			// Remove button
			if (user is PrivateUser) {
				menu.add(0, View.generateViewId(), 0, R.string.lbl_remove).setOnMenuItemClickListener {
					serverUserRepository.deleteStoredUser(user)
					startupViewModel.loadUsers(server)
					true
				}
			}
		}

		inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
			val profileImage: AsyncImageView = itemView.findViewById(R.id.profile_image)
			val container: ViewGroup = itemView.findViewById(R.id.profile_container)

			init {
				// Set click listener on the container
				container.setOnClickListener {
					val position = bindingAdapterPosition
					if (position != RecyclerView.NO_POSITION) {
						onItemPressed(getItem(position))
					}
				}

				// Set long click listener on the container
				container.setOnLongClickListener {
					val position = bindingAdapterPosition
					if (position != RecyclerView.NO_POSITION) {
						showUserMenu(it, getItem(position))
					}
					true
				}
			}
		}
	}
}

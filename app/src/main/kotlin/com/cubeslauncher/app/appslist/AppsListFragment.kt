package com.cubeslauncher.app.appslist

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.FragmentAppsListBinding
import com.cubeslauncher.app.model.AppInfo
import com.cubeslauncher.app.settings.SettingsActivity
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.AppLoader
import com.cubeslauncher.app.util.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppsListFragment : Fragment() {

    companion object {
        private const val PREFERENCES_PACKAGE = "com.cubeslauncher.PREFERENCES"
        private const val PHONE_PACKAGE = "com.cubeslauncher.PHONE"
        private const val CONTACTS_PACKAGE = "com.cubeslauncher.CONTACTS"
        private const val MESSAGES_PACKAGE = "com.cubeslauncher.MESSAGES"
    }

    private var _binding: FragmentAppsListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AppsListAdapter
    private lateinit var preferencesManager: PreferencesManager

    /** All apps, ungrouped, for search filtering. */
    private var allApps: List<AppInfo> = emptyList()

    /** Full grouped list (headers + apps), used when not searching. */
    private var fullItemsList: List<AppItem> = emptyList()

    /** Set of letters that actually have apps. */
    private var activeLetters: Set<String> = emptySet()

    private var isLetterGridVisible = false
    private lateinit var stickyHeaderDecoration: StickyHeaderDecoration

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferencesManager = PreferencesManager(requireContext())

        adapter = AppsListAdapter(
            onAppClick = { app -> launchApp(app.packageName) },
            onAppLongClick = { app, anchor -> showContextMenu(app, anchor) },
            onLetterClick = { _, anchor -> showLetterGrid(anchor) },
            onLetterFastScroll = { letter -> scrollToLetter(letter) }
        )

        stickyHeaderDecoration = StickyHeaderDecoration()
        binding.appsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AppsListFragment.adapter
            addItemDecoration(stickyHeaderDecoration)
        }

        setupSearch()
        setupSearchFab()
        setupLetterGridOverlay()
        loadApps()
    }

    // ---- Search ----------------------------------------------------------------

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString().orEmpty())
            }
        })
        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            val firstApp = adapter.getFirstApp()
            if (firstApp != null) {
                launchApp(firstApp.packageName)
            }
            true
        }
    }

    private var searchAnimating = false
    private var searchBarHeight = 0

    private fun setupSearchFab() {
        val accentColor = AccentColorManager.getAccentColor()
        binding.searchFab.setBackgroundColor(accentColor)
        binding.searchFab.setOnClickListener { showSearch() }
    }

    private fun showSearch() {
        if (searchAnimating) return
        val b = _binding ?: return
        searchAnimating = true

        b.searchFab.visibility = View.GONE

        // Make container visible but 0-height to measure
        b.searchBarContainer.visibility = View.VISIBLE
        b.searchBarContainer.alpha = 0f
        b.searchBarContainer.measure(
            View.MeasureSpec.makeMeasureSpec(b.root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        searchBarHeight = b.searchBarContainer.measuredHeight
        b.searchBarContainer.layoutParams.height = 0
        b.searchBarContainer.requestLayout()

        // Phase 1: Slide list down by expanding container height
        ValueAnimator.ofInt(0, searchBarHeight).apply {
            duration = 200
            addUpdateListener {
                b.searchBarContainer.layoutParams.height = it.animatedValue as Int
                b.searchBarContainer.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    b.searchBarContainer.layoutParams.height =
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    b.searchBarContainer.requestLayout()

                    // Phase 2: Fade in search bar content
                    b.searchBarContainer.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                searchAnimating = false
                                b.searchEditText.requestFocus()
                                val imm = requireContext().getSystemService(
                                    Context.INPUT_METHOD_SERVICE
                                ) as InputMethodManager
                                imm.showSoftInput(
                                    b.searchEditText,
                                    InputMethodManager.SHOW_IMPLICIT
                                )
                            }
                        })
                        .start()
                }
            })
            start()
        }
    }

    fun hideSearch() {
        val b = _binding ?: return
        if (b.searchBarContainer.visibility != View.VISIBLE) return
        if (searchAnimating) return
        searchAnimating = true

        // Dismiss keyboard immediately
        val imm = requireContext().getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        imm.hideSoftInputFromWindow(b.searchEditText.windowToken, 0)
        b.searchEditText.text?.clear()

        // Phase 1: Fade out search bar content
        b.searchBarContainer.animate()
            .alpha(0f)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Phase 2: Collapse container height (list slides up)
                    val startHeight = b.searchBarContainer.height
                    ValueAnimator.ofInt(startHeight, 0).apply {
                        duration = 200
                        addUpdateListener {
                            b.searchBarContainer.layoutParams.height = it.animatedValue as Int
                            b.searchBarContainer.requestLayout()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                b.searchBarContainer.visibility = View.GONE
                                b.searchBarContainer.layoutParams.height =
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                b.searchFab.visibility = View.VISIBLE
                                searchAnimating = false
                            }
                        })
                        start()
                    }
                }
            })
            .start()
    }

    fun isSearchOpen(): Boolean {
        val b = _binding ?: return false
        return b.searchBarContainer.visibility == View.VISIBLE && !searchAnimating
    }

    /** Instantly reset search state without animation (for lifecycle events). */
    private fun resetSearch() {
        _binding?.let { b ->
            b.searchEditText.text?.clear()
            b.searchBarContainer.alpha = 1f
            b.searchBarContainer.visibility = View.GONE
            b.searchBarContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            b.searchFab.visibility = View.VISIBLE
            searchAnimating = false
        }
    }

    private fun filterApps(query: String) {
        if (query.isBlank()) {
            adapter.submitList(fullItemsList)
            return
        }

        val lower = query.lowercase()
        val filtered = allApps.filter { it.label.lowercase().contains(lower) }

        // Flat list — no headers during search (matches WP10 behavior)
        val items = filtered.map { AppItem.App(it) }
        adapter.submitList(items)
    }

    // ---- Letter Jump Grid ------------------------------------------------------

    private fun setupLetterGridOverlay() {
        // Only dismiss when tapping the dark background, not the grid itself
        binding.letterGridOverlay.setOnClickListener {
            hideLetterGrid()
        }
        // Stop grid clicks from propagating to overlay
        binding.letterGrid.setOnClickListener { /* consume */ }
    }

    private fun showLetterGrid(anchor: View) {
        if (isLetterGridVisible) return
        isLetterGridVisible = true

        buildLetterGrid()

        // Position the grid at the top of the RecyclerView (first letter position)
        val recyclerLoc = IntArray(2)
        binding.appsRecycler.getLocationOnScreen(recyclerLoc)

        val rootLoc = IntArray(2)
        binding.root.getLocationOnScreen(rootLoc)

        val topMargin = (recyclerLoc[1] - rootLoc[1]).coerceAtLeast(0)
        val params = binding.letterGrid.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = topMargin
        binding.letterGrid.layoutParams = params

        // Hide the app list behind the overlay
        binding.appsRecycler.visibility = View.INVISIBLE

        binding.letterGridOverlay.clearAnimation()
        binding.letterGridOverlay.alpha = 0f
        binding.letterGridOverlay.visibility = View.VISIBLE
        binding.letterGridOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .setListener(null)
            .start()
    }

    private fun hideLetterGrid() {
        if (!isLetterGridVisible) return
        isLetterGridVisible = false

        binding.letterGridOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.letterGridOverlay.visibility = View.GONE
                    binding.appsRecycler.visibility = View.VISIBLE
                }
            })
            .start()
    }

    fun refreshAccentColors() {
        stickyHeaderDecoration.invalidateColors()
        adapter.notifyDataSetChanged()
        binding.appsRecycler.invalidate()
        binding.searchFab.setBackgroundColor(AccentColorManager.getAccentColor())
    }

    fun isLetterGridOpen(): Boolean = isLetterGridVisible

    fun closeLetterGrid() = hideLetterGrid()

    private fun buildLetterGrid() {
        val grid = binding.letterGrid
        grid.removeAllViews()

        val letters = listOf("#") + ('A'..'Z').map { it.toString() }
        val cellSize = resources.getDimensionPixelSize(R.dimen.letter_grid_cell_size)
        val spacing = resources.getDimensionPixelSize(R.dimen.letter_grid_spacing)
        val textSize = resources.getDimension(R.dimen.letter_grid_text_size)

        val accentColor = AccentColorManager.getAccentColor()
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.wp_white_10)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.wp_white)
        val dimColor = ContextCompat.getColor(requireContext(), R.color.wp_white_30)

        for ((index, letter) in letters.withIndex()) {
            val isActive = activeLetters.contains(letter)

            val cell = TextView(requireContext()).apply {
                text = letter
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                typeface = resources.getFont(R.font.selawik_semibold)
                setTextColor(if (isActive) whiteColor else dimColor)
                setBackgroundColor(if (isActive) accentColor else inactiveColor)

                if (isActive) {
                    setOnClickListener {
                        scrollToLetter(letter)
                        hideLetterGrid()
                    }
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                columnSpec = GridLayout.spec(index % 5)
                rowSpec = GridLayout.spec(index / 5)
                setMargins(spacing, spacing, spacing, spacing)
            }
            grid.addView(cell, params)
        }
    }

    private fun scrollToLetter(letter: String) {
        val position = fullItemsList.indexOfFirst {
            it is AppItem.Header && it.letter == letter
        }
        if (position >= 0) {
            (binding.appsRecycler.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, 0)
        }
    }

    // ---- Data loading ----------------------------------------------------------

    fun loadApps() {
        val preferencesEntry = AppInfo(
            label = getString(R.string.preferences),
            packageName = PREFERENCES_PACKAGE,
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_settings_gear)!!
        )
        val phoneEntry = AppInfo(
            label = getString(R.string.phone_title),
            packageName = PHONE_PACKAGE,
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_phone_call)!!
        )
        val contactsEntry = AppInfo(
            label = getString(R.string.contacts_title),
            packageName = CONTACTS_PACKAGE,
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_contacts_person)!!
        )
        val messagesEntry = AppInfo(
            label = getString(R.string.messages_title),
            packageName = MESSAGES_PACKAGE,
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_messages_app)!!
        )
        allApps = (AppLoader.loadApps(requireContext()) + preferencesEntry + phoneEntry + contactsEntry + messagesEntry)
            .sortedBy { it.label.lowercase() }
        fullItemsList = buildAppItemsList(allApps)
        activeLetters = fullItemsList
            .filterIsInstance<AppItem.Header>()
            .map { it.letter }
            .toSet()

        val query = _binding?.searchEditText?.text?.toString().orEmpty()
        if (query.isBlank()) {
            adapter.submitList(fullItemsList)
        } else {
            filterApps(query)
        }
    }

    private fun buildAppItemsList(apps: List<AppInfo>): List<AppItem> {
        val items = mutableListOf<AppItem>()
        var currentLetter = ""

        for (app in apps) {
            val firstChar = app.label.first().uppercase()
            val letter = if (firstChar.first().isLetter()) firstChar else "#"

            if (letter != currentLetter) {
                currentLetter = letter
                items.add(AppItem.Header(letter))
            }
            items.add(AppItem.App(app))
        }
        return items
    }

    // ---- App actions -----------------------------------------------------------

    private fun launchApp(packageName: String) {
        if (packageName == PREFERENCES_PACKAGE) {
            clearSearch()
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
            return
        }
        if (packageName == PHONE_PACKAGE) {
            clearSearch()
            startActivity(Intent(requireContext(), com.cubeslauncher.app.phone.PhoneActivity::class.java))
            return
        }
        if (packageName == CONTACTS_PACKAGE) {
            clearSearch()
            startActivity(Intent(requireContext(), com.cubeslauncher.app.contacts.ContactsActivity::class.java))
            return
        }
        if (packageName == MESSAGES_PACKAGE) {
            clearSearch()
            startActivity(Intent(requireContext(), com.cubeslauncher.app.messages.MessagesActivity::class.java))
            return
        }
        val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            clearSearch()
            viewLifecycleOwner.lifecycleScope.launch {
                preferencesManager.recordAppLaunch(packageName)
            }
            startActivity(intent)
        }
    }

    fun clearSearch() {
        resetSearch()
    }

    private fun showContextMenu(app: AppInfo, anchor: View) {
        if (app.packageName == PREFERENCES_PACKAGE || app.packageName == PHONE_PACKAGE || app.packageName == CONTACTS_PACKAGE || app.packageName == MESSAGES_PACKAGE) return
        viewLifecycleOwner.lifecycleScope.launch {
            val pinnedList = preferencesManager.pinnedApps.first()
            val isPinned = pinnedList.contains(app.packageName)

            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add(
                0, 1, 0,
                if (isPinned) R.string.unpin_from_home else R.string.pin_to_home
            )
            popup.menu.add(0, 2, 1, R.string.app_info)
            popup.menu.add(0, 3, 2, R.string.uninstall)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { pinApp(app); true }
                    2 -> { openAppInfo(app.packageName); true }
                    3 -> { uninstallApp(app.packageName); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun pinApp(app: AppInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            val pinnedList = preferencesManager.pinnedApps.first()
            if (pinnedList.contains(app.packageName)) {
                preferencesManager.unpinApp(app.packageName)
                Toast.makeText(requireContext(), R.string.app_unpinned, Toast.LENGTH_SHORT).show()
            } else if (pinnedList.size >= PreferencesManager.MAX_PINNED_APPS) {
                Toast.makeText(requireContext(), R.string.max_pins_reached, Toast.LENGTH_SHORT)
                    .show()
            } else {
                preferencesManager.pinApp(app.packageName)
                Toast.makeText(requireContext(), R.string.app_pinned, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAppInfo(packageName: String) {
        startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun uninstallApp(packageName: String) {
        startActivity(
            Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    // ---- Lifecycle -------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        resetSearch()
        loadApps()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

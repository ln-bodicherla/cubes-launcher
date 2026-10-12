package com.cubeslauncher.launcher.home

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cubeslauncher.launcher.R
import com.cubeslauncher.launcher.databinding.FragmentHomeBinding
import com.cubeslauncher.launcher.model.AppInfo
import com.cubeslauncher.launcher.model.TileSize
import com.cubeslauncher.launcher.statusbar.NotificationService
import com.cubeslauncher.launcher.util.AppLoader
import com.cubeslauncher.launcher.util.LauncherPreferencesManager
import com.cubeslauncher.launcher.util.WallpaperHelper
import androidx.core.content.ContextCompat
import com.cubeslauncher.common.intents.CubesIntents
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferencesManager: LauncherPreferencesManager
    private lateinit var pinnedAppsAdapter: PinnedAppsAdapter
    private var isTileMode = false
    private var isMinimalTiles = false
    private var currentTileSizes: Map<String, TileSize> = emptyMap()
    private var actionBarApp: AppInfo? = null

    // Wiggle mode state
    private var isWiggleMode = false
    private var isDragging = false
    private var dragStartPosition = -1
    private var dragCurrentPosition = -1
    private var dragBitmap: Bitmap? = null
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f
    private var longPressRunnable: Runnable? = null
    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f
    private var touchDownRawX: Float = 0f
    private var touchDownRawY: Float = 0f
    private var dragItemTouchListener: RecyclerView.OnItemTouchListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private val clockUpdater = object : Runnable {
        override fun run() {
            updateClock()
            val now = Calendar.getInstance()
            val delay = (60 - now.get(Calendar.SECOND)) * 1000L - now.get(Calendar.MILLISECOND)
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferencesManager = LauncherPreferencesManager(requireContext())

        setupWallpaper()
        setupPinnedApps()
        setupActionBar()
        observePinnedApps()
        observeSettings()
        setupNotificationListener()
    }

    private fun setupWallpaper() {
        val wallpaper = WallpaperHelper.getWallpaper(requireContext())
        if (wallpaper != null) {
            binding.wallpaperImage.setImageDrawable(wallpaper)
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Background mode + image
                launch {
                    preferencesManager.bgMode.combine(preferencesManager.bgImageUri) { mode, uri ->
                        Pair(mode, uri)
                    }.collect { (mode, uri) ->
                        if (mode == "full_screen" && uri != null) {
                            val file = File(uri)
                            if (file.exists()) {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                binding.wallpaperImage.setImageBitmap(bitmap)
                                binding.wallpaperImage.visibility = View.VISIBLE
                            }
                        } else if (mode == "none") {
                            // Use system wallpaper
                            val wallpaper = WallpaperHelper.getWallpaper(requireContext())
                            if (wallpaper != null) {
                                binding.wallpaperImage.setImageDrawable(wallpaper)
                                binding.wallpaperImage.visibility = View.VISIBLE
                            } else {
                                binding.wallpaperImage.visibility = View.GONE
                            }
                        }
                    }
                }

                // Tile transparency
                launch {
                    preferencesManager.tileTransparency.collect { transparency ->
                        val alpha = 1f - (transparency / 100f)
                        pinnedAppsAdapter.setTileAlpha(alpha)
                    }
                }

                // Show more tiles
                launch {
                    preferencesManager.showMoreTiles.collect { showMore ->
                        val spanCount = if (showMore) 6 else 4
                        (binding.pinnedAppsRecycler.layoutManager as? TileGridLayoutManager)
                            ?.setSpanCount(spanCount)
                    }
                }

                // Tile mode
                launch {
                    preferencesManager.tileMode.collect { enabled ->
                        isTileMode = enabled
                        applyTileMode(enabled)
                    }
                }

                // Minimal tiles
                launch {
                    preferencesManager.minimalTiles.collect { enabled ->
                        isMinimalTiles = enabled
                        if (enabled && !isTileMode) {
                            rebuildMinimalTilesList()
                        } else if (!enabled && !isTileMode) {
                            // Restore default layout: recycler wrap_content, no weight
                            val recyclerParams = binding.pinnedAppsRecycler.layoutParams as android.widget.LinearLayout.LayoutParams
                            recyclerParams.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            recyclerParams.weight = 0f
                            binding.pinnedAppsRecycler.layoutParams = recyclerParams
                        }
                    }
                }

                // Tile sizes
                launch {
                    preferencesManager.tileSizes.collect { sizes ->
                        currentTileSizes = sizes
                        pinnedAppsAdapter.setTileSizes(sizes)
                        refreshAppsWithTileSizes()
                    }
                }
            }
        }
    }

    private fun applyTileMode(enabled: Boolean) {
        if (enabled) {
            binding.clockDateSection.visibility = View.GONE
            val params = binding.pinnedAppsRecycler.layoutParams as android.widget.LinearLayout.LayoutParams
            params.height = 0
            params.weight = 1f
            binding.pinnedAppsRecycler.layoutParams = params

            // Use system window insets to start below the notch/status bar
            ViewCompat.setOnApplyWindowInsetsListener(binding.pinnedAppsRecycler) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                val topInset = maxOf(systemBars.top, cutout.top)
                v.setPadding(
                    v.paddingLeft,
                    topInset,
                    v.paddingRight,
                    v.paddingBottom
                )
                insets
            }
            binding.pinnedAppsRecycler.requestApplyInsets()

            binding.pinnedAppsRecycler.visibility = View.VISIBLE

            // Merge saved tileModeOrder with AppLoader.loadApps()
            viewLifecycleOwner.lifecycleScope.launch {
                val savedOrder = preferencesManager.tileModeOrder.first()
                val allApps = AppLoader.loadApps(requireContext()).map { app ->
                    app.copy(tileSize = currentTileSizes[app.packageName] ?: TileSize.SMALL)
                }
                if (savedOrder.isNotEmpty()) {
                    val appMap = allApps.associateBy { it.packageName }
                    val ordered = mutableListOf<AppInfo>()
                    for (pkg in savedOrder) {
                        appMap[pkg]?.let { ordered.add(it) }
                    }
                    // Append any apps not in the saved order
                    val remaining = allApps.filter { it.packageName !in savedOrder }
                    ordered.addAll(remaining)
                    pinnedAppsAdapter.submitList(ordered)
                } else {
                    pinnedAppsAdapter.submitList(allApps)
                }
            }
        } else {
            binding.clockDateSection.visibility = View.VISIBLE
            val params = binding.pinnedAppsRecycler.layoutParams as android.widget.LinearLayout.LayoutParams
            params.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            params.weight = 0f
            binding.pinnedAppsRecycler.layoutParams = params

            ViewCompat.setOnApplyWindowInsetsListener(binding.pinnedAppsRecycler, null)
            binding.pinnedAppsRecycler.setPadding(
                binding.pinnedAppsRecycler.paddingLeft,
                0,
                binding.pinnedAppsRecycler.paddingRight,
                binding.pinnedAppsRecycler.paddingBottom
            )
            // Pinned apps will be reloaded by observePinnedApps()
        }
    }

    private fun setupPinnedApps() {
        pinnedAppsAdapter = PinnedAppsAdapter(
            onAppClick = { app ->
                if (isWiggleMode) {
                    // Select tile in wiggle mode
                    val pos = pinnedAppsAdapter.currentList.indexOfFirst { it.packageName == app.packageName }
                    if (pos >= 0) {
                        pinnedAppsAdapter.setSelectedTile(pos)
                        actionBarApp = app
                    }
                } else {
                    launchApp(app.packageName)
                }
            },
            onAppLongClick = { app, _ ->
                if (!isWiggleMode) {
                    enterWiggleMode(app)
                }
            },
            onResizeClick = { app ->
                cycleResize(app)
            }
        )
        val spacing = resources.getDimensionPixelSize(R.dimen.tile_grid_spacing)
        val tileLayoutManager = TileGridLayoutManager(4, spacing)
        tileLayoutManager.tileSizeProvider = { position ->
            pinnedAppsAdapter.currentList.getOrNull(position)?.tileSize ?: TileSize.SMALL
        }
        binding.pinnedAppsRecycler.apply {
            layoutManager = tileLayoutManager
            adapter = pinnedAppsAdapter
        }
    }

    private fun cycleResize(app: AppInfo) {
        val currentSize = currentTileSizes[app.packageName] ?: TileSize.SMALL
        val nextSize = when (currentSize) {
            TileSize.SMALL -> TileSize.MEDIUM
            TileSize.MEDIUM -> TileSize.WIDE
            TileSize.WIDE -> TileSize.SMALL
        }
        viewLifecycleOwner.lifecycleScope.launch {
            preferencesManager.setTileSize(app.packageName, nextSize)
        }
    }

    private fun enterWiggleMode(selectedApp: AppInfo) {
        isWiggleMode = true
        pinnedAppsAdapter.setWiggleMode(true)

        // Select the initial tile
        val pos = pinnedAppsAdapter.currentList.indexOfFirst { it.packageName == selectedApp.packageName }
        if (pos >= 0) {
            pinnedAppsAdapter.setSelectedTile(pos)
        }
        actionBarApp = selectedApp

        // Show wiggle-mode bottom bar: hide Resize, show Done
        binding.actionResize.visibility = View.GONE
        binding.actionDone.visibility = View.VISIBLE
        showTileActionBar(selectedApp)

        // Attach drag touch listener
        setupDragTouchListener()

        // Haptic feedback
        binding.pinnedAppsRecycler.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun exitWiggleMode() {
        if (!isWiggleMode) return
        isWiggleMode = false
        isDragging = false
        pinnedAppsAdapter.setWiggleMode(false)

        // Remove drag item touch listener
        dragItemTouchListener?.let {
            binding.pinnedAppsRecycler.removeOnItemTouchListener(it)
        }
        dragItemTouchListener = null

        // Cancel any pending long press
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null

        // Hide action bar
        hideTileActionBar()

        // Save current order
        val order = pinnedAppsAdapter.currentList.map { it.packageName }
        viewLifecycleOwner.lifecycleScope.launch {
            if (isTileMode) {
                preferencesManager.saveTileModeOrder(order)
            } else if (isMinimalTiles) {
                // Only save the pinned apps portion of the order
                val pinnedSet = preferencesManager.pinnedAppsOrdered.first().toSet()
                val pinnedOrder = order.filter { it in pinnedSet }
                preferencesManager.savePinnedAppsOrder(pinnedOrder)
            } else {
                preferencesManager.savePinnedAppsOrder(order)
            }
        }
    }

    fun handleBackPress(): Boolean {
        if (isWiggleMode) {
            exitWiggleMode()
            return true
        }
        return false
    }

    private fun setupDragTouchListener() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        val listener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                val layoutManager = rv.layoutManager as? TileGridLayoutManager ?: return false

                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = e.x
                        touchDownY = e.y
                        touchDownRawX = e.rawX
                        touchDownRawY = e.rawY

                        val pos = layoutManager.getPositionForPoint(e.x, e.y)
                        if (pos >= 0) {
                            dragStartPosition = pos
                            // Capture raw coords for the runnable since the event object is recycled
                            val downRawX = e.rawX
                            val downRawY = e.rawY
                            longPressRunnable = Runnable {
                                startDrag(pos, downRawX, downRawY)
                            }
                            handler.postDelayed(longPressRunnable!!, longPressTimeout)
                        }
                        return false // Don't intercept yet — allow taps
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            return true // Intercept: we're dragging
                        }
                        // If moved past slop before long press fired, cancel it (user is scrolling)
                        val dx = e.x - touchDownX
                        val dy = e.y - touchDownY
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                            longPressRunnable = null
                        }
                        return false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                        if (isDragging) {
                            endDrag()
                            return true
                        }
                        return false
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                val layoutManager = rv.layoutManager as? TileGridLayoutManager ?: return

                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            updateDragOverlay(e.rawX, e.rawY)

                            // Hit-test under finger (use local coordinates)
                            val hoverPos = layoutManager.getPositionForPoint(e.x, e.y)
                            if (hoverPos >= 0 && hoverPos != dragCurrentPosition) {
                                pinnedAppsAdapter.moveItem(dragCurrentPosition, hoverPos)
                                dragCurrentPosition = hoverPos
                            }

                            // Prevent ViewPager2 from intercepting
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            endDrag()
                        }
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // No-op
            }
        }

        dragItemTouchListener = listener
        binding.pinnedAppsRecycler.addOnItemTouchListener(listener)
    }

    private fun startDrag(position: Int, rawX: Float, rawY: Float) {
        val recycler = binding.pinnedAppsRecycler
        val childView = recycler.findViewHolderForAdapterPosition(position)?.itemView ?: return

        isDragging = true
        dragCurrentPosition = position

        // Create bitmap snapshot
        dragBitmap = childView.drawToBitmap()

        // Calculate offset: where on the tile the finger touched
        val viewLocation = IntArray(2)
        childView.getLocationOnScreen(viewLocation)
        dragOffsetX = rawX - viewLocation[0]
        dragOffsetY = rawY - viewLocation[1]

        // Show drag overlay
        val overlay = binding.dragOverlay
        overlay.setImageBitmap(dragBitmap)
        overlay.visibility = View.VISIBLE

        val params = overlay.layoutParams as FrameLayout.LayoutParams
        params.width = childView.width
        params.height = childView.height
        overlay.layoutParams = params

        updateDragOverlay(rawX, rawY)

        // Make original tile semi-transparent
        childView.alpha = 0.3f

        // Haptic feedback
        recycler.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        // Prevent ViewPager2 from intercepting
        recycler.parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun updateDragOverlay(rawX: Float, rawY: Float) {
        val overlay = binding.dragOverlay
        val rootLocation = IntArray(2)
        binding.root.getLocationOnScreen(rootLocation)
        overlay.translationX = rawX - rootLocation[0] - dragOffsetX
        overlay.translationY = rawY - rootLocation[1] - dragOffsetY
    }

    private fun endDrag() {
        isDragging = false

        // Hide overlay
        binding.dragOverlay.visibility = View.GONE
        binding.dragOverlay.setImageBitmap(null)

        // Recycle bitmap
        dragBitmap?.recycle()
        dragBitmap = null

        // Restore alpha on all visible items
        val recycler = binding.pinnedAppsRecycler
        for (i in 0 until recycler.childCount) {
            recycler.getChildAt(i).alpha = 1f
        }

        pinnedAppsAdapter.notifyDataSetChanged()
        dragStartPosition = -1
        dragCurrentPosition = -1
    }

    private fun setupActionBar() {
        binding.actionUnpin.setOnClickListener {
            val app = getActionBarTarget() ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                preferencesManager.unpinApp(app.packageName)
            }
            if (isWiggleMode) {
                pinnedAppsAdapter.setSelectedTile(-1)
                actionBarApp = null
            } else {
                hideTileActionBar()
            }
        }

        binding.actionResize.setOnClickListener {
            val app = getActionBarTarget() ?: return@setOnClickListener
            cycleResize(app)
            if (!isWiggleMode) {
                hideTileActionBar()
            }
        }

        binding.actionInfo.setOnClickListener {
            val app = getActionBarTarget() ?: return@setOnClickListener
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
            )
            if (!isWiggleMode) {
                hideTileActionBar()
            }
        }

        binding.actionUninstall.setOnClickListener {
            val app = getActionBarTarget() ?: return@setOnClickListener
            startActivity(
                Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
            )
            if (!isWiggleMode) {
                hideTileActionBar()
            }
        }

        binding.actionDone.setOnClickListener {
            exitWiggleMode()
        }

        // Dismiss on tap outside (on the root FrameLayout)
        binding.root.setOnClickListener {
            if (isWiggleMode) {
                exitWiggleMode()
            } else if (binding.tileActionBar.visibility == View.VISIBLE) {
                hideTileActionBar()
            }
        }
    }

    private fun getActionBarTarget(): AppInfo? {
        return if (isWiggleMode) {
            val pos = pinnedAppsAdapter.getSelectedTile()
            if (pos >= 0 && pos < pinnedAppsAdapter.currentList.size) {
                pinnedAppsAdapter.currentList[pos]
            } else null
        } else {
            actionBarApp
        }
    }

    private fun showTileActionBar(app: AppInfo) {
        actionBarApp = app
        val bar = binding.tileActionBar
        bar.visibility = View.VISIBLE
        bar.translationY = bar.height.toFloat().let { if (it == 0f) 200f else it }
        bar.alpha = 0f
        bar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideTileActionBar() {
        val bar = binding.tileActionBar
        bar.animate()
            .translationY(bar.height.toFloat())
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                bar.visibility = View.GONE
                // Reset visibility: show Resize, hide Done
                binding.actionResize.visibility = View.VISIBLE
                binding.actionDone.visibility = View.GONE
            }
            .start()
        actionBarApp = null
    }

    private fun refreshAppsWithTileSizes() {
        val currentList = pinnedAppsAdapter.currentList
        if (currentList.isEmpty()) return
        val updated = currentList.map { app ->
            app.copy(tileSize = currentTileSizes[app.packageName] ?: TileSize.SMALL)
        }
        pinnedAppsAdapter.submitList(updated)
    }

    private fun observePinnedApps() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferencesManager.pinnedAppsOrdered.collect { packageNames ->
                    // First launch: pin built-in apps by default
                    if (packageNames.isEmpty() && !isTileMode) {
                        launch {
                            preferencesManager.pinApp(CubesIntents.PHONE_APP_ID)
                            preferencesManager.pinApp(CubesIntents.CONTACTS_APP_ID)
                            preferencesManager.pinApp(CubesIntents.MESSAGES_APP_ID)
                        }
                        return@collect
                    }
                    if (isTileMode) return@collect
                    if (isWiggleMode) return@collect
                    if (isMinimalTiles) {
                        rebuildMinimalTilesList()
                        return@collect
                    }
                    val apps = packageNames.mapNotNull { pkg -> resolveAppInfo(pkg) }
                    pinnedAppsAdapter.submitList(apps)
                    binding.pinnedAppsRecycler.visibility =
                        if (apps.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun rebuildMinimalTilesList() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Get pinned apps
            val pinnedPackages = preferencesManager.pinnedAppsOrdered.first()
            val pinnedApps = pinnedPackages.mapNotNull { pkg -> resolveAppInfo(pkg) }

            // 2. Get packages with active notifications that are NOT pinned
            val pinnedSet = pinnedPackages.toSet()
            val notificationInfos = NotificationService.instance?.getActiveNotificationInfos() ?: emptyList()
            val notifPackages = notificationInfos.map { it.packageName }.distinct()
                .filter { it !in pinnedSet }

            val notifApps = notifPackages.mapNotNull { pkg -> resolveAppInfo(pkg) }
                .sortedBy { it.label.lowercase() }

            // 3. Combine: pinned first, then notification apps
            val combined = pinnedApps + notifApps

            if (combined.isEmpty()) {
                binding.pinnedAppsRecycler.visibility = View.GONE
                binding.clockDateSection.visibility = View.VISIBLE
                // Restore clock to fill screen
                val clockParams = binding.clockDateSection.layoutParams as android.widget.LinearLayout.LayoutParams
                clockParams.weight = 1f
                binding.clockDateSection.layoutParams = clockParams
            } else {
                binding.pinnedAppsRecycler.visibility = View.VISIBLE
                binding.clockDateSection.visibility = View.VISIBLE
                pinnedAppsAdapter.submitList(combined)

                // Clock gets 1/4, tiles get 3/4
                val clockParams = binding.clockDateSection.layoutParams as android.widget.LinearLayout.LayoutParams
                clockParams.weight = 1f
                binding.clockDateSection.layoutParams = clockParams

                val recyclerParams = binding.pinnedAppsRecycler.layoutParams as android.widget.LinearLayout.LayoutParams
                recyclerParams.height = 0
                recyclerParams.weight = 3f
                binding.pinnedAppsRecycler.layoutParams = recyclerParams
            }
        }
    }

    private fun setupNotificationListener() {
        updateNotificationCounts()
        NotificationService.onNotificationsChanged = {
            handler.post { updateNotificationCounts() }
        }
    }

    private fun updateNotificationCounts() {
        val notifications = NotificationService.instance?.getActiveNotificationInfos() ?: return
        val counts = notifications.groupBy { it.packageName }.mapValues { it.value.size }
        pinnedAppsAdapter.setNotificationCounts(counts)
        if (isMinimalTiles && !isTileMode) {
            rebuildMinimalTilesList()
        }
    }

    private fun updateClock() {
        val now = Date()
        binding.clockText.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
        binding.dateText.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
    }

    /**
     * Resolves a package name to an AppInfo. Built-in apps are now real installed APKs,
     * so we resolve via PackageManager like any other app. Legacy pseudo-packages are
     * mapped to real app IDs as a fallback.
     */
    private fun resolveAppInfo(pkg: String): AppInfo? {
        val ctx = requireContext()
        val pm = ctx.packageManager

        // Map legacy pseudo-packages to real app IDs
        val realPkg = when (pkg) {
            CubesIntents.LEGACY_PHONE_PACKAGE -> CubesIntents.PHONE_APP_ID
            CubesIntents.LEGACY_CONTACTS_PACKAGE -> CubesIntents.CONTACTS_APP_ID
            CubesIntents.LEGACY_MESSAGES_PACKAGE -> CubesIntents.MESSAGES_APP_ID
            else -> pkg
        }

        // Try PackageManager first (works for all installed apps including our built-in APKs)
        try {
            val appInfo = pm.getApplicationInfo(realPkg, 0)
            return AppInfo(
                label = pm.getApplicationLabel(appInfo).toString(),
                packageName = realPkg,
                icon = pm.getApplicationIcon(appInfo),
                tileSize = currentTileSizes[realPkg] ?: currentTileSizes[pkg] ?: TileSize.SMALL
            )
        } catch (_: PackageManager.NameNotFoundException) {
            // Not installed — fall back to built-in icons for our apps
        }

        // Fallback for built-in apps not yet installed
        return when (realPkg) {
            CubesIntents.PHONE_APP_ID -> AppInfo(
                label = getString(R.string.phone_title),
                packageName = realPkg,
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_phone_call)!!,
                tileSize = currentTileSizes[realPkg] ?: TileSize.SMALL
            )
            CubesIntents.CONTACTS_APP_ID -> AppInfo(
                label = getString(R.string.contacts_title),
                packageName = realPkg,
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_contacts_person)!!,
                tileSize = currentTileSizes[realPkg] ?: TileSize.SMALL
            )
            CubesIntents.MESSAGES_APP_ID -> AppInfo(
                label = getString(R.string.messages_title),
                packageName = realPkg,
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_messages_app)!!,
                tileSize = currentTileSizes[realPkg] ?: TileSize.SMALL
            )
            CubesIntents.CALCULATOR_APP_ID -> AppInfo(
                label = getString(R.string.calculator_title),
                packageName = realPkg,
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_calculator)!!,
                tileSize = currentTileSizes[realPkg] ?: TileSize.SMALL
            )
            CubesIntents.CLOCK_APP_ID -> AppInfo(
                label = getString(R.string.clock_title),
                packageName = realPkg,
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_clock)!!,
                tileSize = currentTileSizes[realPkg] ?: TileSize.SMALL
            )
            CubesIntents.WEATHER_APP_ID -> AppInfo(
                label = getString(R.string.weather_title),
                packageName = realPkg,
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_weather)!!,
                tileSize = currentTileSizes[realPkg] ?: TileSize.SMALL
            )
            else -> null
        }
    }

    private fun launchApp(packageName: String) {
        // Map legacy pseudo-packages to real app IDs
        val realPkg = when (packageName) {
            CubesIntents.LEGACY_PHONE_PACKAGE -> CubesIntents.PHONE_APP_ID
            CubesIntents.LEGACY_CONTACTS_PACKAGE -> CubesIntents.CONTACTS_APP_ID
            CubesIntents.LEGACY_MESSAGES_PACKAGE -> CubesIntents.MESSAGES_APP_ID
            else -> packageName
        }

        when (realPkg) {
            CubesIntents.PHONE_APP_ID -> {
                startActivity(Intent(CubesIntents.ACTION_LAUNCH_PHONE).apply {
                    setPackage(CubesIntents.PHONE_APP_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            CubesIntents.CONTACTS_APP_ID -> {
                startActivity(Intent(CubesIntents.ACTION_LAUNCH_CONTACTS).apply {
                    setPackage(CubesIntents.CONTACTS_APP_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            CubesIntents.MESSAGES_APP_ID -> {
                startActivity(Intent(CubesIntents.ACTION_LAUNCH_MESSAGES).apply {
                    setPackage(CubesIntents.MESSAGES_APP_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            CubesIntents.CALCULATOR_APP_ID -> {
                startActivity(Intent(CubesIntents.ACTION_LAUNCH_CALCULATOR).apply {
                    setPackage(CubesIntents.CALCULATOR_APP_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            CubesIntents.CLOCK_APP_ID -> {
                startActivity(Intent(CubesIntents.ACTION_LAUNCH_CLOCK).apply {
                    setPackage(CubesIntents.CLOCK_APP_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            CubesIntents.WEATHER_APP_ID -> {
                startActivity(Intent(CubesIntents.ACTION_LAUNCH_WEATHER).apply {
                    setPackage(CubesIntents.WEATHER_APP_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            else -> {
                val intent = requireContext().packageManager.getLaunchIntentForPackage(realPkg)
                if (intent != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferencesManager.recordAppLaunch(realPkg)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isTileMode) {
            binding.clockDateSection.visibility = View.GONE
            handler.removeCallbacks(clockUpdater)
        } else {
            clockUpdater.run()
        }
        updateNotificationCounts()
        if (isTileMode) {
            viewLifecycleOwner.lifecycleScope.launch {
                val savedOrder = preferencesManager.tileModeOrder.first()
                val allApps = AppLoader.loadApps(requireContext()).map { app ->
                    app.copy(tileSize = currentTileSizes[app.packageName] ?: TileSize.SMALL)
                }
                if (savedOrder.isNotEmpty()) {
                    val appMap = allApps.associateBy { it.packageName }
                    val ordered = mutableListOf<AppInfo>()
                    for (pkg in savedOrder) {
                        appMap[pkg]?.let { ordered.add(it) }
                    }
                    val remaining = allApps.filter { it.packageName !in savedOrder }
                    ordered.addAll(remaining)
                    pinnedAppsAdapter.submitList(ordered)
                } else {
                    pinnedAppsAdapter.submitList(allApps)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockUpdater)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up wiggle mode state
        if (isWiggleMode) {
            isWiggleMode = false
            isDragging = false
            longPressRunnable?.let { handler.removeCallbacks(it) }
            longPressRunnable = null
            dragBitmap?.recycle()
            dragBitmap = null
        }
        _binding = null
    }
}

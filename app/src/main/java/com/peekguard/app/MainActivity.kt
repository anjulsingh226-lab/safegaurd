package com.peekguard.app

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var toggle: Button
    private lateinit var status: TextView
    private lateinit var friendText: TextView
    private lateinit var friendBtn: Button
    private lateinit var setupSummary: TextView
    private lateinit var setupBtn: Button
    private val selected = HashSet<String>()
    private val ui = Handler(Looper.getMainLooper())
    private var waitingForSetting = false
    private var openingSettings = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("peek", Context.MODE_PRIVATE)
        selected.addAll(prefs.getStringSet("apps", emptySet()) ?: emptySet())

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0B0F1A"))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(30))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("PeekGuard", 28f, "#00E5A8", true))
        root.addView(text(
            "Chune hue apps use karte waqt front camera face-count check karta hai. Extra face detect hone par alert dikhta hai.",
            14f, "#9AA3B8", false
        ))

        status = text("", 15f, "#FFFFFF", true).also { it.setPadding(0, dp(12), 0, dp(8)) }
        root.addView(status)
        root.addView(text("Permissions setup", 18f, "#FFFFFF", true).also {
            it.setPadding(0, dp(8), 0, dp(6))
        })
        setupSummary = text("", 14f, "#D5DAE5", false).also { it.setPadding(dp(14), dp(12), dp(14), dp(12)) }
        setupSummary.background = rounded("#161B2B", 12)
        root.addView(setupSummary)
        setupBtn = Button(this).apply {
            text = "Complete setup"
            setOnClickListener { continueSetup() }
        }
        root.addView(setupBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(8) })
        root.addView(text(
            "Android security ke kaaran Usage Access aur Display over other apps ko unki Settings screen par manually Allow karna hota hai. Battery Unrestricted optional hai.",
            12f, "#9AA3B8", false
        ).also { it.setPadding(0, dp(4), 0, dp(8)) })

        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        root.addView(toggle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(10) })

        friendText = text("", 14f, "#FFFFFF", false).also { it.setPadding(0, dp(14), 0, dp(4)) }
        root.addView(friendText)
        friendBtn = Button(this).apply {
            text = "Friend mode band karo"
            setOnClickListener { prefs.edit().putLong("friendUntil", 0L).apply(); refresh() }
        }
        root.addView(friendBtn)

        root.addView(text("Protected apps chuno", 18f, "#FFFFFF", true).also {
            it.setPadding(0, dp(18), 0, dp(6))
        })
        loadApps(root)

        // On first launch, request normal runtime permissions immediately.
        if (!runtimePermissionsOk()) requestRuntimePermissions() else refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (waitingForSetting) {
            waitingForSetting = false
            val next = nextMissingSpecialPermission()
            if (next == null) {
                openingSettings = false
                setupSummary.text = "✅ Required permissions complete. Ab protected apps select karke START Protection dabao."
                setupBtn.text = "Check permissions"
            } else {
                setupSummary.text = "Abhi ye permission baaki hai: $next. Continue setup dabao; Android Settings mein Allow karke wapas aao."
                setupBtn.text = "Continue setup"
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        if (runtimePermissionsOk()) {
            continueSetup()
        } else {
            setupSummary.text = "Camera aur notifications permission chahiye. Permission prompt mein Allow karo, phir Continue setup dabao."
            setupBtn.text = "Continue setup"
        }
    }

    private fun text(t: String, size: Float, color: String, bold: Boolean): TextView = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(color: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun loadApps(root: LinearLayout) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.loadLabel(pm).toString().lowercase() }
        for ((pkg, info) in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val icon = ImageView(this).apply { setImageDrawable(info.loadIcon(pm)) }
            row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            val cb = CheckBox(this).apply {
                text = info.loadLabel(pm).toString()
                setTextColor(Color.WHITE)
                isChecked = selected.contains(pkg)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(pkg) else selected.remove(pkg)
                    prefs.edit().putStringSet("apps", HashSet(selected)).apply()
                }
            }
            row.addView(cb)
            root.addView(row)
        }
    }

    private fun runtimePermissionsOk(): Boolean {
        val camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return camera && notifications
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(permissions.toTypedArray(), 100)
    }

    @Suppress("DEPRECATION")
    private fun usageOk(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = android.os.Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun overlayOk() = Settings.canDrawOverlays(this)

    private fun nextMissingSpecialPermission(): String? = when {
        !usageOk() -> "Usage Access"
        !overlayOk() -> "Display over other apps"
        else -> null
    }

    private fun continueSetup() {
        if (!runtimePermissionsOk()) {
            requestRuntimePermissions()
            return
        }
        when {
            !usageOk() -> {
                setupSummary.text = "Usage Access Settings khul rahi hai. List mein PeekGuard dhoondo aur Allow karo, phir app par wapas aao."
                waitingForSetting = true
                openingSettings = true
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            !overlayOk() -> {
                setupSummary.text = "Display over other apps Settings khul rahi hai. PeekGuard ko Allow karo, phir app par wapas aao."
                waitingForSetting = true
                openingSettings = true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            else -> {
                waitingForSetting = false
                openingSettings = false
                setupSummary.text = "✅ Required permissions complete. Battery Unrestricted optional hai."
                setupBtn.text = "Check permissions"
                Toast.makeText(this, "Required permissions complete", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun refresh() {
        if (!::status.isInitialized) return
        val running = prefs.getBoolean("running", false)
        status.text = if (running) "● Protection ON" else "○ Protection OFF"
        status.setTextColor(Color.parseColor(if (running) "#00E5A8" else "#FF6B81"))
        toggle.text = if (running) "STOP Protection" else "START Protection"
        val missing = when {
            !runtimePermissionsOk() -> "Camera aur Notifications permission pending hai."
            !usageOk() -> "Usage Access pending hai. Continue setup dabao."
            !overlayOk() -> "Display over other apps pending hai. Continue setup dabao."
            else -> "✅ Required permissions ready."
        }
        if (!waitingForSetting) setupSummary.text = missing
        if (!::friendText.isInitialized) return
        val left = prefs.getLong("friendUntil", 0L) - System.currentTimeMillis()
        if (left > 0) {
            friendText.text = "Friend mode ON: ${left / 60000 + 1} minute baaki (is dauran popup nahi aayega)"
            friendBtn.visibility = View.VISIBLE
        } else {
            friendText.text = "Friend mode OFF"
            friendBtn.visibility = View.GONE
        }
    }

    private fun requiredOk() = runtimePermissionsOk() && usageOk() && overlayOk()

    private fun onToggle() {
        val running = prefs.getBoolean("running", false)
        if (running) {
            stopService(Intent(this, GuardService::class.java))
            prefs.edit().putBoolean("running", false).apply()
            refresh()
            return
        }
        if (!requiredOk()) {
            Toast.makeText(this, "Pehle Complete setup karo", Toast.LENGTH_LONG).show()
            continueSetup()
            return
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Kam se kam ek app chuno", Toast.LENGTH_LONG).show()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, GuardService::class.java))
        ui.postDelayed({ refresh() }, 800)
    }
}

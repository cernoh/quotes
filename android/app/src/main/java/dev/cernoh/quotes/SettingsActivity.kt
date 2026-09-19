package dev.cernoh.quotes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

/**
 * The settings, one section each: rotation, sources, card, order, and updates.
 *
 * Every change is written to [Prefs] as it is made and applied to the widget at
 * once, so there is no Apply button to forget.
 */
class SettingsActivity : android.app.Activity() {
    private val intervals = intArrayOf(Prefs.NEVER, 15, 30, 60, 120, 240)

    private lateinit var intervalSpinner: Spinner
    private lateinit var authorsRow: Button
    private lateinit var worksRow: Button
    private lateinit var scaleBar: SeekBar
    private lateinit var scaleLabel: TextView
    private lateinit var showWorkSwitch: Switch
    private lateinit var lightCardSwitch: Switch
    private lateinit var shuffleSwitch: Switch
    private lateinit var installedLabel: TextView
    private lateinit var updateStatus: TextView
    private lateinit var checkButton: Button
    private lateinit var installButton: Button
    private lateinit var tokenField: EditText
    private lateinit var shizukuState: TextView
    private lateinit var shizukuPermission: Button
    private lateinit var shizukuSwitch: Switch
    private lateinit var installCachedButton: Button

    private var pending: Updates.Release? = null

    /** Shizuku answers the permission request through this listener. */
    private val shizukuPermissionResult =
        rikka.shizuku.Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != SHIZUKU_REQUEST) return@OnRequestPermissionResultListener
            val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            updateStatus.text = getString(
                if (granted) R.string.shizuku_granted else R.string.shizuku_refused,
            )
            showShizuku()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        WindowSpacing.apply(this, findViewById(R.id.root))

        intervalSpinner = findViewById(R.id.interval_spinner)
        authorsRow = findViewById(R.id.row_authors)
        worksRow = findViewById(R.id.row_works)
        scaleBar = findViewById(R.id.scale_bar)
        scaleLabel = findViewById(R.id.scale_label)
        showWorkSwitch = findViewById(R.id.switch_work)
        lightCardSwitch = findViewById(R.id.switch_light)
        shuffleSwitch = findViewById(R.id.switch_shuffle)
        installedLabel = findViewById(R.id.installed_label)
        updateStatus = findViewById(R.id.update_status)
        checkButton = findViewById(R.id.button_check)
        installButton = findViewById(R.id.button_install)
        tokenField = findViewById(R.id.field_token)
        shizukuState = findViewById(R.id.shizuku_state)
        shizukuPermission = findViewById(R.id.button_shizuku_permission)
        shizukuSwitch = findViewById(R.id.switch_shizuku)
        installCachedButton = findViewById(R.id.button_install_cached)

        setUpRotation()
        setUpSources()
        setUpCard()
        setUpOrder()
        setUpUpdates()
        setUpShizuku()
    }

    override fun onResume() {
        super.onResume()
        showShizuku()
    }

    override fun onDestroy() {
        rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermissionResult)
        super.onDestroy()
    }

    // Rotation -------------------------------------------------------------

    private fun setUpRotation() {
        intervalSpinner.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervals.map { intervalLabel(it) },
        )
        intervalSpinner.setSelection(
            intervals.indexOf(Prefs.intervalMinutes(this)).coerceAtLeast(0),
        )
        intervalSpinner.onItemSelectedListener = SimpleItemListener { position ->
            Prefs.setIntervalMinutes(this, intervals[position])
            AlarmScheduler.schedule(this)
            WidgetRenderer.updateAll(this)
        }
    }

    private fun intervalLabel(minutes: Int): String = when {
        minutes == Prefs.NEVER -> getString(R.string.interval_never)
        minutes < 60 -> getString(R.string.interval_minutes, minutes)
        minutes == 60 -> getString(R.string.interval_hour)
        else -> getString(R.string.interval_hours, minutes / 60)
    }

    // Sources -------------------------------------------------------------

    private fun setUpSources() {
        showSources()
        authorsRow.setOnClickListener {
            chooseSources(
                title = getString(R.string.author_label),
                options = Corpus.authors(this),
                selected = Prefs.authors(this),
            ) { picked -> Prefs.setAuthors(this, picked); showSources(); afterChange() }
        }
        worksRow.setOnClickListener {
            chooseSources(
                title = getString(R.string.work_label),
                options = Corpus.works(this),
                selected = Prefs.works(this),
            ) { picked -> Prefs.setWorks(this, picked); showSources(); afterChange() }
        }
    }

    private fun showSources() {
        authorsRow.text = sourceSummary(
            label = getString(R.string.author_label),
            selected = Prefs.authors(this),
            total = Corpus.authors(this).size,
        )
        worksRow.text = sourceSummary(
            label = getString(R.string.work_label),
            selected = Prefs.works(this),
            total = Corpus.works(this).size,
        )
    }

    private fun sourceSummary(label: String, selected: Set<String>, total: Int): String = when {
        selected.isEmpty() -> getString(R.string.source_all, label, total)
        selected.size == 1 -> getString(R.string.source_one, label, selected.first())
        else -> getString(R.string.source_some, label, selected.size)
    }

    private fun chooseSources(
        title: String,
        options: List<String>,
        selected: Set<String>,
        onPicked: (Set<String>) -> Unit,
    ) {
        val checked = BooleanArray(options.size) { selected.contains(options[it]) }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(options.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.done) { _, _ ->
                onPicked(options.filterIndexed { index, _ -> checked[index] }.toSet())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // Card -----------------------------------------------------------------

    private fun setUpCard() {
        scaleBar.min = Prefs.MIN_TEXT_SCALE
        scaleBar.max = Prefs.MAX_TEXT_SCALE
        scaleBar.progress = Prefs.textScale(this)
        showScale()
        scaleBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                Prefs.setTextScale(this@SettingsActivity, progress)
                showScale()
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit

            override fun onStopTrackingTouch(bar: SeekBar) = afterChange()
        })

        showWorkSwitch.isChecked = Prefs.showWork(this)
        showWorkSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowWork(this, checked)
            afterChange()
        }

        lightCardSwitch.isChecked = Prefs.lightCard(this)
        lightCardSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setLightCard(this, checked)
            afterChange()
        }
    }

    private fun showScale() {
        scaleLabel.text = getString(R.string.scale_value, Prefs.textScale(this))
    }

    // Order ----------------------------------------------------------------

    private fun setUpOrder() {
        shuffleSwitch.isChecked = Prefs.shuffle(this)
        shuffleSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setShuffle(this, checked)
            afterChange()
        }
    }

    // Updates --------------------------------------------------------------

    private fun setUpUpdates() {
        installedLabel.text = getString(R.string.update_installed, Updates.installedVersion(this))
        tokenField.setText(Prefs.githubToken(this))

        checkButton.setOnClickListener { checkForUpdates() }
        installButton.visibility = View.GONE
        installButton.setOnClickListener { downloadAndInstall() }
        findViewById<Button>(R.id.button_save_token).setOnClickListener {
            Prefs.setGithubToken(this, tokenField.text.toString())
            updateStatus.text = getString(R.string.token_saved)
        }
    }

    private fun checkForUpdates() {
        updateStatus.text = getString(R.string.update_checking)
        checkButton.isEnabled = false
        installButton.visibility = View.GONE
        Thread {
            val result = Updates.check(this)
            runOnUiThread { showUpdateResult(result) }
        }.start()
    }

    private fun showUpdateResult(result: Updates.Result) {
        if (isFinishing) return
        checkButton.isEnabled = true
        when (result) {
            is Updates.Result.UpToDate -> {
                updateStatus.text = getString(R.string.update_latest, result.version)
            }

            is Updates.Result.Available -> {
                pending = result.release
                updateStatus.text = getString(
                    R.string.update_available,
                    result.release.tag,
                    result.installed,
                )
                installButton.visibility = View.VISIBLE
                installButton.isEnabled = true
            }

            is Updates.Result.Failed -> {
                pending = null
                updateStatus.text = result.message
            }
        }
    }

    private fun downloadAndInstall() {
        val release = pending ?: return
        updateStatus.text = getString(R.string.update_downloading)
        installButton.isEnabled = false
        Thread {
            try {
                val apk = Updates.download(this, release)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    updateStatus.text = getString(R.string.update_installing)
                    installFile(apk)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    updateStatus.text = getString(
                        R.string.update_download_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                    installButton.isEnabled = true
                }
            }
        }.start()
    }

    /**
     * Install a downloaded APK. With Shizuku ready and the switch on, the
     * install runs as the shell user and shows no prompt. Otherwise the system
     * installer takes over and asks the user.
     */
    private fun installFile(apk: java.io.File) {
        val silent = Prefs.shizukuInstall(this) &&
            ShizukuInstaller.state() == ShizukuInstaller.State.READY
        updateStatus.text = getString(R.string.install_running)
        Thread {
            try {
                if (silent) {
                    val answer = ShizukuInstaller.install(apk)
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        updateStatus.text = getString(R.string.install_done, answer)
                    }
                } else {
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        Updates.install(this, apk)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    updateStatus.text = getString(
                        R.string.install_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                    installButton.isEnabled = true
                }
            }
        }.start()
    }

    // Automatic install ----------------------------------------------------

    private fun setUpShizuku() {
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermissionResult)

        shizukuSwitch.isChecked = Prefs.shizukuInstall(this)
        shizukuSwitch.setOnCheckedChangeListener { _, checked ->
            val ready = ShizukuInstaller.state() == ShizukuInstaller.State.READY
            if (checked && !ready) {
                shizukuSwitch.isChecked = false
                updateStatus.text = getString(R.string.shizuku_switch_needs_ready)
                return@setOnCheckedChangeListener
            }
            Prefs.setShizukuInstall(this, checked)
        }

        shizukuPermission.setOnClickListener {
            if (!ShizukuInstaller.requestPermission(SHIZUKU_REQUEST)) {
                updateStatus.text = getString(R.string.shizuku_off)
            }
        }

        installCachedButton.setOnClickListener {
            Updates.cachedUpdate(this)?.let { apk -> installFile(apk) }
        }
        showShizuku()
    }

    /** The state line, the permission button, and the switch follow Shizuku. */
    private fun showShizuku() {
        val state = ShizukuInstaller.state()
        shizukuState.text = when (state) {
            ShizukuInstaller.State.NOT_INSTALLED -> getString(R.string.shizuku_off)
            ShizukuInstaller.State.NOT_RUNNING -> getString(R.string.shizuku_not_running)
            ShizukuInstaller.State.DENIED -> getString(R.string.shizuku_needs_permission)
            ShizukuInstaller.State.READY -> getString(R.string.shizuku_ready)
        }
        shizukuPermission.visibility =
            if (state == ShizukuInstaller.State.DENIED) View.VISIBLE else View.GONE
        shizukuSwitch.isEnabled = state == ShizukuInstaller.State.READY
        installCachedButton.visibility =
            if (Updates.cachedUpdate(this) != null) View.VISIBLE else View.GONE
    }

    // Shared ---------------------------------------------------------------

    /** Redraw every placed widget after a setting changes. */
    private fun afterChange() {
        WidgetRenderer.updateAll(this)
    }

    /** A spinner listener that reports the chosen position. */
    private class SimpleItemListener(private val onSelected: (Int) -> Unit) :
        android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) = onSelected(position)

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    companion object {
        private const val SHIZUKU_REQUEST = 9001

        fun intent(context: android.content.Context): Intent =
            Intent(context, SettingsActivity::class.java)
    }
}

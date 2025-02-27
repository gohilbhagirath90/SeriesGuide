package com.battlelancer.seriesguide.dataliberation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.battlelancer.seriesguide.R
import com.battlelancer.seriesguide.databinding.FragmentDataLiberationBinding
import com.battlelancer.seriesguide.dataliberation.JsonExportTask.OnTaskProgressListener
import com.battlelancer.seriesguide.util.Utils
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

/**
 * One button export or import of the show database using a JSON file on external storage.
 * Uses Storage Access Framework so no permissions are required.
 */
class DataLiberationFragment : Fragment(), OnTaskProgressListener {

    private var binding: FragmentDataLiberationBinding? = null
    private val model: DataLiberationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDataLiberationBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding ?: return
        binding.progressBarDataLib.visibility = View.GONE

        // setup listeners
        binding.checkBoxDataLibShows.setOnCheckedChangeListener { _, _ -> updateImportButtonEnabledState() }
        binding.checkBoxDataLibLists.setOnCheckedChangeListener { _, _ -> updateImportButtonEnabledState() }
        binding.checkBoxDataLibMovies.setOnCheckedChangeListener { _, _ -> updateImportButtonEnabledState() }
        binding.buttonDataLibImport.setOnClickListener {
            doDataImport()
        }

        // note: selecting custom backup files is only supported on KitKat and up
        // as we use Storage Access Framework in this case
        binding.buttonDataLibShowsExportFile.setOnClickListener {
            createShowExportFileResult.launch(JsonExportTask.EXPORT_JSON_FILE_SHOWS)
        }
        binding.buttonDataLibShowsImportFile.setOnClickListener {
            selectShowsImportFileResult.launch(null)
        }

        binding.buttonDataLibListsExportFile.setOnClickListener {
            createListsExportFileResult.launch(JsonExportTask.EXPORT_JSON_FILE_LISTS)
        }
        binding.buttonDataLibListsImportFile.setOnClickListener {
            selectListsImportFileResult.launch(null)
        }

        binding.buttonDataLibMoviesExportFile.setOnClickListener {
            createMovieExportFileResult.launch(JsonExportTask.EXPORT_JSON_FILE_MOVIES)
        }
        binding.buttonDataLibMoviesImportFile.setOnClickListener {
            selectMoviesImportFileResult.launch(null)
        }
        updateFileViews()

        // restore UI state
        if (model.isDataLibTaskNotCompleted) {
            setProgressLock(true)
        }
    }

    private fun updateImportButtonEnabledState() {
        val binding = binding ?: return
        binding.buttonDataLibImport.isEnabled = (binding.checkBoxDataLibShows.isChecked
                || binding.checkBoxDataLibLists.isChecked
                || binding.checkBoxDataLibMovies.isChecked)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onProgressUpdate(total: Int, completed: Int) {
        val binding = binding ?: return
        binding.progressBarDataLib.isIndeterminate = total == completed
        binding.progressBarDataLib.max = total
        binding.progressBarDataLib.progress = completed
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: LiberationResultEvent) {
        event.handle(view)
        if (!isAdded) {
            // don't touch views if fragment is not added to activity any longer
            return
        }
        updateFileViews()
        setProgressLock(false)
    }

    private fun setProgressLock(isLocked: Boolean) {
        val binding = binding ?: return
        if (isLocked) {
            binding.buttonDataLibImport.isEnabled = false
        } else {
            updateImportButtonEnabledState()
        }
        binding.progressBarDataLib.visibility = if (isLocked) View.VISIBLE else View.GONE
        binding.checkBoxDataLibFullDump.isEnabled = !isLocked
        binding.buttonDataLibShowsExportFile.isEnabled = !isLocked
        binding.buttonDataLibShowsImportFile.isEnabled = !isLocked
        binding.buttonDataLibListsExportFile.isEnabled = !isLocked
        binding.buttonDataLibListsImportFile.isEnabled = !isLocked
        binding.buttonDataLibMoviesExportFile.isEnabled = !isLocked
        binding.buttonDataLibMoviesImportFile.isEnabled = !isLocked
        binding.checkBoxDataLibShows.isEnabled = !isLocked
        binding.checkBoxDataLibLists.isEnabled = !isLocked
        binding.checkBoxDataLibMovies.isEnabled = !isLocked
    }

    private fun doDataImport() {
        val binding = binding ?: return
        setProgressLock(true)

        val dataLibTask = JsonImportTask(
            requireContext(),
            binding.checkBoxDataLibShows.isChecked, binding.checkBoxDataLibLists.isChecked,
            binding.checkBoxDataLibMovies.isChecked
        )
        model.dataLibTask = dataLibTask
        Utils.executeInOrder(dataLibTask)
    }

    private fun doDataExport(type: Int, uri: Uri?) {
        if (uri == null) return

        tryToPersistUri(uri)
        BackupSettings.storeExportFileUri(context, type, uri, false)
        updateFileViews()

        val binding = binding ?: return
        setProgressLock(true)

        val exportTask = JsonExportTask(
            requireContext(),
            this@DataLiberationFragment,
            binding.checkBoxDataLibFullDump.isChecked, false, type
        )
        model.dataLibJob = exportTask.launch()
    }

    /**
     * Try to persist read and write permission for this URI across device reboots.
     */
    private fun tryToPersistUri(uri: Uri) {
        try {
            requireContext().contentResolver
                .takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
        } catch (e: SecurityException) {
            Timber.e(e, "Could not persist r/w permission for backup file URI.")
        }
    }

    private val createShowExportFileResult =
        registerForActivityResult(CreateExportFileContract()) { uri ->
            doDataExport(JsonExportTask.BACKUP_SHOWS, uri)
        }

    private val createListsExportFileResult =
        registerForActivityResult(CreateExportFileContract()) { uri ->
            doDataExport(JsonExportTask.BACKUP_LISTS, uri)
        }

    private val createMovieExportFileResult =
        registerForActivityResult(CreateExportFileContract()) { uri ->
            doDataExport(JsonExportTask.BACKUP_MOVIES, uri)
        }

    class CreateExportFileContract : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, suggestedFileName: String?): Intent {
            return Intent(Intent.ACTION_CREATE_DOCUMENT)
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                .addCategory(Intent.CATEGORY_OPENABLE)
                // do NOT use the probably correct application/json as it would prevent selecting existing
                // backup files on Android, which re-classifies them as application/octet-stream.
                // also do NOT use application/octet-stream as it prevents selecting backup files from
                // providers where the correct application/json mime type is used, *sigh*
                // so, use application/* and let the provider decide
                .setType("application/*")
                .putExtra(Intent.EXTRA_TITLE, suggestedFileName)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            if (resultCode != Activity.RESULT_OK) {
                return null
            }
            return intent?.data
        }
    }

    private val selectShowsImportFileResult =
        registerForActivityResult(SelectImportFileContract()) { uri ->
            storeImportFileUri(JsonExportTask.BACKUP_SHOWS, uri)
        }

    private val selectListsImportFileResult =
        registerForActivityResult(SelectImportFileContract()) { uri ->
            storeImportFileUri(JsonExportTask.BACKUP_LISTS, uri)
        }

    private val selectMoviesImportFileResult =
        registerForActivityResult(SelectImportFileContract()) { uri ->
            storeImportFileUri(JsonExportTask.BACKUP_MOVIES, uri)
        }

    private fun storeImportFileUri(type: Int, uri: Uri?) {
        if (uri == null) return
        tryToPersistUri(uri)
        BackupSettings.storeImportFileUri(context, type, uri)
        updateFileViews()
    }

    class SelectImportFileContract : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit?): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT)
                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                .addCategory(Intent.CATEGORY_OPENABLE)
                // json files might have mime type of "application/octet-stream"
                // but we are going to store them as "application/json"
                // so filter to show all application files
                .setType("application/*")
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            if (resultCode != Activity.RESULT_OK) {
                return null
            }
            return intent?.data
        }

    }

    private fun updateFileViews() {
        val binding = binding ?: return
        setUriOrPlaceholder(
            binding.textViewDataLibShowsExportFile,
            BackupSettings.getExportFileUri(
                context, JsonExportTask.BACKUP_SHOWS, false
            )
        )
        setUriOrPlaceholder(
            binding.textViewDataLibListsExportFile,
            BackupSettings.getExportFileUri(
                context, JsonExportTask.BACKUP_LISTS, false
            )
        )
        setUriOrPlaceholder(
            binding.textViewDataLibMoviesExportFile,
            BackupSettings.getExportFileUri(
                context, JsonExportTask.BACKUP_MOVIES, false
            )
        )
        setUriOrPlaceholder(
            binding.textViewDataLibShowsImportFile,
            BackupSettings.getImportFileUriOrExportFileUri(
                context, JsonExportTask.BACKUP_SHOWS
            )
        )
        setUriOrPlaceholder(
            binding.textViewDataLibListsImportFile,
            BackupSettings.getImportFileUriOrExportFileUri(
                context, JsonExportTask.BACKUP_LISTS
            )
        )
        setUriOrPlaceholder(
            binding.textViewDataLibMoviesImportFile,
            BackupSettings.getImportFileUriOrExportFileUri(
                context, JsonExportTask.BACKUP_MOVIES
            )
        )
    }

    private fun setUriOrPlaceholder(textView: TextView, uri: Uri?) {
        textView.text = uri?.toString() ?: getString(R.string.no_file_selected)
    }

    class LiberationResultEvent {
        private val message: String?
        private val showIndefinite: Boolean

        constructor() {
            message = null
            showIndefinite = false
        }

        constructor(message: String?, errorCause: String?, showIndefinite: Boolean) {
            val finalMessage = if (errorCause != null) {
                "$message ($errorCause)"
            } else {
                message
            }
            this.message = finalMessage
            this.showIndefinite = showIndefinite
        }

        fun handle(view: View?) {
            if (view != null && message != null) {
                val snackbar = Snackbar.make(
                    view, message,
                    if (showIndefinite) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_SHORT
                )
                val textView = snackbar.view.findViewById<TextView>(
                    com.google.android.material.R.id.snackbar_text
                )
                textView.maxLines = 5
                snackbar.show()
            }
        }
    }
}
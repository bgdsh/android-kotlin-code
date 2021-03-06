package net.goodbai.journaler.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.os.*
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import kotlinx.android.synthetic.main.activity_note.*
import net.goodbai.journaler.R
import net.goodbai.journaler.database.Crud
import net.goodbai.journaler.database.Db
import net.goodbai.journaler.database.Note
import net.goodbai.journaler.execution.TaskExecutor
import net.goodbai.journaler.location.LocationProvider
import net.goodbai.journaler.model.MODE
import net.goodbai.journaler.service.DatabaseService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class NoteActivity : ItemActivity() {
    private var note: Note? = null
    private var location: Location? = null
    private var handler: Handler? = null
    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            updateNote()
        }

        override fun beforeTextChanged(p0: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, start: Int, before: Int, count: Int) {
            p0?.let {
                tryAsync(p0.toString())
            }
        }

    }

    private fun tryAsync(identifier: String) {
        val tryAsync = TryAsync(identifier)
        tryAsync.executeOnExecutor(threadPoolExecutor)
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(p0: Location?) {
            p0?.let {
                LocationProvider.unsubscribe(this)
                location = p0
                val title = getNoteTitle()
                val content = getNoteContent()
                note = Note(title, content, p0)

                //Switching to intent service
                val dbIntent = Intent(this@NoteActivity, DatabaseService::class.java)
                dbIntent.putExtra(DatabaseService.EXTRA_ENTRY, note)
                dbIntent.putExtra(DatabaseService.EXTRA_OPERATION, MODE.CREATE.mode)
                startService(dbIntent)
                // sendMessage(true)

//                executor.execute {
//                    val param = note
//                    var result = false
//                    param?.let {
//                        result = Db.Note.insert(param) > 0
//                    }
//                    if (result) {
//                        Log.i(tag, "Note inserted.")
//                    } else {
//                        Log.e(tag, "Note not inserted.")
//                    }
//
//                    handler?.post {
//                        var color = R.color.vermilion
//                        if (result) {
//                            color = R.color.green
//                        }
//                        indicator.setBackgroundColor(ContextCompat.getColor(this@NoteActivity, color))
//                        sendMessage(result)
//                    }
//                }

            }
        }



        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String?) {
        }

        override fun onProviderDisabled(provider: String?) {
        }

    }
    private val executor = TaskExecutor.getInstance(1)

    private val crudOperationListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val crudResultValue = intent.getIntExtra(Crud.BROADCAST_KEY_CRUD_OPERATION_RESULT, 0)
                Log.i("crudOperationListener", crudResultValue.toString())
                sendMessage(crudResultValue == 1)
            }
        }

    }

    private val threadPoolExecutor = ThreadPoolExecutor(
            3,
            3,
            1,
            TimeUnit.SECONDS,
            LinkedBlockingDeque<Runnable>()
    )

    private class TryAsync(val identifier: String) : AsyncTask<Unit, Int, Unit>() {
        private val tag = "TryAsync"
        override fun onPreExecute() {
            Log.i(tag, "onPreExecute [ $identifier ]")
            super.onPreExecute()
        }

        override fun doInBackground(vararg params: Unit?) {
            Log.i(tag, "doInBackground [ $identifier ] [ START ]")
            Thread.sleep(5000)
            Log.i(tag, "doInBackground [ $identifier ] [ END ]")
            return Unit
        }

        override fun onCancelled() {
            Log.i(tag, "onCancelled [ $identifier ][ END ]")
            super.onCancelled()
        }

        override fun onProgressUpdate(vararg values: Int?) {
            val progress = values.first()
            progress?.let {
                Log.i(tag, "onProgressUpdate [ $identifier ][ $progress ]")
            }
            super.onProgressUpdate(*values)
        }

        override fun onPostExecute(result: Unit?) {
            Log.i(tag, "onPostExecute [ $identifier ]")
            super.onPostExecute(result)
        }
    }

    private fun getNoteContent(): String = note_content.text.toString()
    private fun getNoteTitle(): String = note_title.text.toString()
    private fun updateNote() {
        if (note == null) {
            if (!TextUtils.isEmpty(getNoteTitle()) && !TextUtils.isEmpty(getNoteContent())) {
                LocationProvider.subscribe(locationListener)
            }
        } else {
            note?.title = getNoteTitle()
            note?.message = getNoteContent()

            // Switching to intent service
            val dbIntent = Intent(this@NoteActivity, DatabaseService::class.java)
            dbIntent.putExtra(DatabaseService.EXTRA_ENTRY, note)
            dbIntent.putExtra(DatabaseService.EXTRA_OPERATION, MODE.EDIT.mode)
            startService(dbIntent)
            // sendMessage(true)

//            executor.execute {
//                var result = false
//                val param = note
//                param?.let {
//                    result = Db.Note.update(param) > 0
//                }
//                if (result) {
//                    Log.i(tag, "Note updated.")
//                } else {
//                    Log.e(tag, "Note not updated.")
//                }
//                sendMessage(result)
//            }
        }

    }

    private fun sendMessage(result: Boolean) {
        val msg = handler?.obtainMessage()
        if (result) {
            msg?.arg1 = 1
        } else {
            msg?.arg1 = 0
        }
        handler?.sendMessage(msg)
    }

    override val tag: String = "Note activity"

    override fun getActivityTitle(): Int = R.string.activity_note_title
    override fun getLayout(): Int = R.layout.activity_note
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message?) {
                msg?.let {
                    var color = R.color.vermilion
                    if (msg.arg1 > 0) {
                        color = R.color.green
                    }
                    indicator.setBackgroundColor(ContextCompat.getColor(this@NoteActivity, color))
                }
                super.handleMessage(msg)
            }
        }
        note_title.addTextChangedListener(textWatcher)
        note_content.addTextChangedListener(textWatcher)
        val intentFilter = IntentFilter(Crud.BROADCAST_ACTION)
        registerReceiver(crudOperationListener, intentFilter)
    }

    override fun onDestroy() {
        unregisterReceiver(crudOperationListener)
        super.onDestroy()
    }

}
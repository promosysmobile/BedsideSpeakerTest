package com.cre8iot.bedsidespeakertest

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.cre8iot.bedsidespeakertest.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream


class MainActivity : AppCompatActivity(),AudioManager.OnAudioFocusChangeListener {

    private lateinit var binding: ActivityMainBinding

    private var recordIP = "192.168.7.140"
    private var recordPort = 5050

    private var playbackIP = "192.168.7.140"
    private var playbackPort = 5000

    //private lateinit var mss:MediaStreamServer
    private lateinit var playbackClient: PlaybackClient
    private lateinit var recordClient: RecordClient
    private lateinit var audioManager:AudioManager

    private var myIpAddress = "192.168.7.140"
    private var sendIpAddress = "192.168.7.140"

    private lateinit var audioTcpClient: TcpClient

    private var strLogBuff = StringBuffer()


    private var strStartCall = "|SEVT|13.22.255.1|192.168.7.122|BED-WA|03|-|-|-|-|-|-|-|-|-|"
    private var strEndCall = "|SEVT|13.22.255.1|192.168.7.122|BED-WA|01|-|-|-|-|-|-|-|-|-|"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.edtPlaybackClient.setText(recordIP)
        binding.edtRecordClient.setText(playbackIP)
        binding.edtPlaybackClientPort.setText(playbackPort.toString())
        binding.edtRecordPort.setText(recordPort.toString())

        binding.edtDesktopIp.setText(myIpAddress)

        binding.btnStartCall.setOnClickListener {
            Log.i("MainActivity","myIpAddress: $myIpAddress")
            if( binding.btnStartCall.text.equals("START CALL")){
                binding.btnStartCall.setText("CANCEL CALL")
                sendCall("|SEVT|13.22.255.1|192.168.7.122|BED-WA|03|-|-|-|-|-|-|-|-|-|")

                recordPort = Integer.valueOf(binding.edtRecordPort.getText().toString())
                recordClient = RecordClient(applicationContext, recordIP,recordPort)

                playbackIP = binding.edtPlaybackClient.getText().toString()
                playbackPort = Integer.valueOf(binding.edtPlaybackClientPort.getText().toString())
                playbackClient = PlaybackClient(
                    applicationContext,
                    playbackIP,
                    playbackPort
                )
                binding.textView1.append("Starting clients\n")
            }else{
                binding.btnStartCall.setText("START CALL")
                sendCall("|SEVT|13.22.255.1|192.168.7.122|BED-WA|01|-|-|-|-|-|-|-|-|-|")
                recordClient.stop()
                playbackClient.stop()

                binding.textView1.append("Stopping clients\n")
            }

        }

        binding.btnStartRecordClient.setOnClickListener {
            recordIP = binding.edtPlaybackClient.text.toString()
            playbackIP = binding.edtRecordClient.text.toString()

            if (binding.btnStartRecordClient.getText().toString().equals("START RECORD CLIENT")) {
                binding.btnStartRecordClient.setText("STOP RECORD CLIENT")

                recordPort = Integer.valueOf(binding.edtRecordPort.getText().toString())
                binding.textView1.append("Starting server\n")
                recordClient = RecordClient(applicationContext, recordIP,recordPort)
                //mss = MediaStreamServer(applicationContext, recordPort)

            } else if (binding.btnStartRecordClient.getText().toString().equals("STOP RECORD CLIENT")) {
                binding.btnStartRecordClient.setText("START RECORD CLIENT")
                if (recordClient != null) {
                    binding.textView1.append("Stopping server\n")
                    recordClient.stop()
                }
            }
        }

        binding.btnPlaybackClient.setOnClickListener {
            //val launchIntent = packageManager.getLaunchIntentForPackage("wx.sm.app")
            //launchIntent?.let { startActivity(it) }

            if (binding.btnPlaybackClient.getText().toString().equals("START PLAYBACK CLIENT")) {
                binding.btnPlaybackClient.setText("STOP PLAYBACK CLIENT")

                playbackIP = binding.edtPlaybackClient.getText().toString()
                playbackPort = Integer.valueOf(binding.edtPlaybackClientPort.getText().toString())

                binding.textView1.append("""Starting client, ${playbackIP.toString()}:$playbackPort""".trimIndent())
                playbackClient = PlaybackClient(
                    applicationContext,
                    playbackIP,
                    playbackPort
                )
            }else if(binding.btnPlaybackClient.getText().toString().equals("STOP PLAYBACK CLIENT")){
                binding.btnPlaybackClient.setText("START PLAYBACK CLIENT")
                if (playbackClient != null) {
                    binding.textView1.append("Stopping client\n")
                    playbackClient.stop()
                    //createLogFile()
                }
            }
        }

        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                when(intent.action){
                    "serverStream.ERROR" -> {
                        binding.textView1.append("""Error: ${intent.getStringExtra("msg")}""".trimIndent())

                    }

                    "clientStream.ERROR" -> {
                        binding.textView1.append("""Error: ${intent.getStringExtra("msg")}""".trimIndent())
                    }

                    "saveStream" -> {
                        val myStream = intent.getStringExtra("streamByte")
                        //strLogBuff.append(myStream)
                    }
                }

            }
        }

        val filter = IntentFilter()
        filter.addAction("serverStream.ERROR")
        filter.addAction("clientStream.ERROR")
        filter.addAction("saveStream")
        registerReceiver(receiver, filter)

        getMyIpAddress()

        hideKeyboardFrom(applicationContext,binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        //audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION)
        //audioManager.isSpeakerphoneOn = true

        //createLogFile()

    }

    private fun sendCall(strMessage:String) = GlobalScope.launch(Dispatchers.IO) {
        /*
        try {
            audioTcpClient = TcpClient(object : TcpClient.OnMessageReceived {
                override fun messageReceived(message: String?) {
                    if (message!!.isNotEmpty()) {
                        Log.i("MainActivity","message: $message")
                    }
                }

            },object : TcpClient.OnConnected {
                override fun connectedStatus(isConnected: Boolean) {
                    Log.i("ViewModel","isConnected: $isConnected")
                    if(isConnected){
                        audioTcpClient.sendMessage("$strMessage\r\n")
                    }else{
                        audioTcpClient.stopClient()
                    }
                }

            },object : TcpClient.OnMessageSend {
                override fun sendingStatus(isSend: Boolean) {
                    Log.i("ViewModel","isSend: $isSend")
                }
            },"#CONNECT#\r\n",sendIpAddress,1884)

            audioTcpClient.run()

        }catch (exception:Error){
            Log.e("TcpServerService","err: $exception")
        }
        */
    }

    private fun hideKeyboardFrom(context: Context, view: View) {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }



    private fun checkPermissions(): Boolean {
        /*
        val result = ContextCompat.checkSelfPermission(applicationContext,
            Manifest.permission.RECORD_AUDIO
        )
        */
        val result = ContextCompat.checkSelfPermission(applicationContext,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO),
            1
        )
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onPostResume() {
        /*
        if(!checkPermissions()){
            requestPermissions()
        }
        */
        super.onPostResume()
    }

    private fun getMyIpAddress(){
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        myIpAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        Log.i("MainActivity","ipAddress: $myIpAddress")
    }

    override fun onAudioFocusChange(focusChange: Int) {
        Log.i("MainActivity","focusChange: $focusChange")
    }

    private fun createLogFile(){

        try {
            /*
            val fos = FileOutputStream(externalFile)
            fos.write(strLogBuff.toString().toByteArray())
            fos.close()
            */

            val out: OutputStream
            val contentResolver = binding.root.context.contentResolver
            var filename = "streamMic.txt"
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BedsideSpeaker")
            val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            out = contentResolver.openOutputStream(uri!!)!!
            out.write(strLogBuff.toString().toByteArray())
            out.close()

            Toast.makeText(binding.root.context, "File saved to External Storage..", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

}
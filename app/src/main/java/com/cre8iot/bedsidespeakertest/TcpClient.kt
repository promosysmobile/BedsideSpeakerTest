package com.cre8iot.bedsidespeakertest

import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket

class TcpClient(listener: OnMessageReceived?, connectionListener: OnConnected?, sendingListener: OnMessageSend?,var sendMsg:String, var SERVERIP:String, var SERVERPORT:Int) {
    private var serverMessage: String? = null
    private var mMessageListener: OnMessageReceived? = null
    private var mConnectionListener: OnConnected? = null
    private var mSendingListener: OnMessageSend? = null
    private var mRun = false
    private lateinit var mySocket:Socket
    var input: BufferedReader? = null

    fun sendMessage(strMessage:String){
        try {
            if (mySocket.isConnected){
                val sendFinal = "$strMessage\r\n"
                mySocket.getOutputStream().write(sendFinal.toByteArray())
                //mSendingListener!!.sendingStatus(true)
                if(sendFinal.contains("SHLH") || sendFinal.contains("SMVO") ){
                    stopClient()
                }
            }
        }catch (ex:Exception){
            mConnectionListener!!.connectedStatus(false)
        }
    }

    fun stopClient() {
        mRun = false
    }

    fun run() {
        mRun = true
        try {
            //here you must put your computer's IP address.
            val serverAddr: InetAddress = InetAddress.getByName(SERVERIP)
            mySocket = Socket(serverAddr, SERVERPORT)
            mySocket.soTimeout = 10
            mySocket.keepAlive = false
            mConnectionListener!!.connectedStatus(mySocket.isConnected)
            try {
                    val inputStream = mySocket.getInputStream()
                    while (mRun){
                        if(mySocket.isConnected){
                            if(inputStream.available()>0){
                                val buffer = ByteArray(inputStream.available())
                                mMessageListener!!.messageReceived(String(buffer, 0, inputStream.read(buffer, 0, inputStream.available())))
                                mRun = false
                            }
                        }else{
                            mRun = false
                        }
                    }
                //send the message to the server
            } catch (e: Exception) {
                Log.e("TCP", "S: Error", e)
                mMessageListener!!.messageReceived("socket error")
                mConnectionListener!!.connectedStatus(mySocket.isConnected)
            } finally {
                mConnectionListener!!.connectedStatus(mySocket.isConnected)
                mySocket.close()
                Log.d("TCP Client", "Closing socket.")
                serverMessage = null
            }

        } catch (e: Exception) {
            Log.e("TCP", "C: Error", e)
            if(e.toString().contains("Connection refused")){
                mMessageListener!!.messageReceived("socket error")
            }else {
                mMessageListener!!.messageReceived("socket error")
            }
            mConnectionListener!!.connectedStatus(false)
        }
    }

    //Declare the interface. The method messageReceived(String message) will must be implemented in the MainActivity class
    interface OnMessageReceived {
        fun messageReceived(message: String?)
    }

    interface OnConnected {
        fun connectedStatus(isConnedted: Boolean)
    }

    interface OnMessageSend {
        fun sendingStatus(isSend: Boolean)
    }

    init {
        mMessageListener = listener
        mConnectionListener = connectionListener
        mSendingListener = sendingListener
    }
}
package com.cre8iot.bedsidespeakertest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;

import java.net.ServerSocket;
import java.net.Socket;

public class MediaStreamServer {
    static final int frequency = 44100;
    //static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    static final int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    boolean isRecording;
    int recBufSize;
    ServerSocket sockfd;
    Socket connfd;
    AudioRecord audioRecord;


    public MediaStreamServer(final Context ctx, final int port) {
        recBufSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, recBufSize);

            try {
                //open tcp server
                sockfd = new ServerSocket(port);
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent().setAction("serverStream.ERROR").putExtra("msg", e.toString());
                ctx.sendBroadcast(intent);
                return;
            }

            new Thread() {
                final byte[] buffer = new byte[recBufSize];
                public void run() {
                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                    //stream out data to connected client
                    try {
                        connfd = sockfd.accept();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Intent intent = new Intent().setAction("serverStream.ERROR").putExtra("msg", e.toString());
                        ctx.sendBroadcast(intent);
                        return;
                    }
                    audioRecord.startRecording();
                    isRecording = true;

                    while (isRecording) {
                        int readSize = audioRecord.read(buffer, 0, recBufSize);
                        try {
                            connfd.getOutputStream().write(buffer, 0, readSize);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Intent intent = new Intent().setAction("serverStream.ERROR").putExtra("msg", e.toString());
                            ctx.sendBroadcast(intent);
                            break;
                        }
                    }
                    audioRecord.stop();
                    try {
                        connfd.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    public void stop() {
        isRecording = false;
        try { sockfd.close(); }
        catch (Exception e) { e.printStackTrace(); }
    }
}

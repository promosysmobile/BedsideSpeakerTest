package com.cre8iot.bedsidespeakertest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class RecordClient {
    static final int frequency = 16000;
    static final int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    boolean isRecording;
    int recBufSize;
    Socket connfd;
    AudioRecord audioRecord;

    public RecordClient(final Context ctx, final String ip, final int port) {
        recBufSize = AudioRecord.getMinBufferSize(
                frequency,
                channelConfiguration,
                audioEncoding
        ) * 2;
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, recBufSize);
            AcousticEchoCanceler myAcousticCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
            AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
            myAcousticCanceler.setEnabled(true);

            new Thread() {
                public void run() {
                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                    try {
                        connfd = new Socket(ip, port);
                        connfd.setTcpNoDelay(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }

                    byte[] buffer = new byte[4096];

                    try {
                        Thread.sleep(1000);
                        InputStream fis = ctx.getResources().openRawResource(R.raw.sample3_16k);
                        fis.skip(44);
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            connfd.getOutputStream().write(buffer, 0, bytesRead);
                            Thread.sleep(20);
                        }

                        fis.close();

                        Log.e("RecordClient","Start record");

                        AcousticEchoCanceler echoCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
                        if (echoCanceler != null) echoCanceler.setEnabled(true);

                        audioRecord.startRecording();
                        boolean isRecording = true;
                        while (isRecording) {
                            int readSize = audioRecord.read(buffer, 0, buffer.length);
                            if (readSize > 0) {
                                connfd.getOutputStream().write(buffer, 0, readSize);
                            }
                        }

                        audioRecord.stop();
                        audioRecord.release();

                        connfd.close();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();

            /*
            new Thread() {
                public void run() {
                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    try {
                        connfd = new Socket(ip, port);
                        connfd.setTcpNoDelay(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Intent intent = new Intent().setAction("recordClientStream.ERROR").putExtra("msg", e.toString());
                        ctx.sendBroadcast(intent);
                        return;
                    }
                    byte[] buffer = new byte[recBufSize];
                    audioRecord.startRecording();
                    isRecording = true;
                    while (isRecording) {
                        int readSize;

                        try {
                            readSize = audioRecord.read(buffer, 0, buffer.length);
                            if (readSize > 0) {
                                connfd.getOutputStream().write(buffer, 0, readSize);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Intent intent = new Intent().setAction("recordClientStream.ERROR").putExtra("msg", e.toString());
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

            */
        }

    }

    public void stop() {
        isRecording = false;
    }

}

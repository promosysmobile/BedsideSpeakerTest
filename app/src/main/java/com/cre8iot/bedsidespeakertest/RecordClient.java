package com.cre8iot.bedsidespeakertest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

import androidx.core.app.ActivityCompat;

import java.net.ServerSocket;
import java.net.Socket;

public class RecordClient {
    static final int frequency = 44100;
    //static final int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    boolean isRecording;
    int recBufSize;
    Socket connfd;
    AudioRecord audioRecord;

    public RecordClient(final Context ctx, final String ip, final int port) {
        recBufSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, frequency, channelConfiguration, audioEncoding, recBufSize);
            AcousticEchoCanceler myAcousticCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
            AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
            myAcousticCanceler.setEnabled(true);

            new Thread() {
                byte[] buffer = new byte[recBufSize];
                public void run() {
                    //Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                    //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    try {
                        connfd = new Socket(ip, port);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Intent intent = new Intent().setAction("recordClientStream.ERROR").putExtra("msg", e.toString());
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
        }

    }

    public void stop() {
        isRecording = false;
    }

}

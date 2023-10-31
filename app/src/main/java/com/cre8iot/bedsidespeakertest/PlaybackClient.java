package com.cre8iot.bedsidespeakertest;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class PlaybackClient {
    static final int frequency = 44100;
    static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    //static final int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    boolean isPlaying;
    int playBufSize;
    Socket connfd;
    AudioTrack audioTrack;


    public PlaybackClient(final Context ctx, final String ip, final int port) {

        //playBufSize= AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
        playBufSize= 256;
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, frequency, channelConfiguration, audioEncoding, playBufSize, AudioTrack.MODE_STREAM);
        audioTrack.setVolume(1f);
        //audioTrack.setPlaybackRate(frequency);
        AcousticEchoCanceler.create(audioTrack.getAudioSessionId());
        NoiseSuppressor.create(audioTrack.getAudioSessionId());
        Log.i("PlaybackClient","playBufSize: " + playBufSize);

        //audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        StringBuffer myStrBuff = new StringBuffer();
        new Thread() {
            byte[] buffer = new byte[playBufSize];
            //byte[] buffer = new byte[1000];
            public void run() {
                //Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                try {
                    connfd = new Socket(ip, port);
                } catch (Exception e) {
                    e.printStackTrace();
                    Intent intent = new Intent().setAction("clientStream.ERROR").putExtra("msg", e.toString());
                    ctx.sendBroadcast(intent);
                    return;
                }
                audioTrack.play();
                isPlaying = true;
                //Log.i("MainActivity","mode:" + audioManager.getMode());
                Intent intent;
                //byte[] buffer = new byte[100];
                while (isPlaying) {
                    int readSize = 0;
                    try {
                        readSize = connfd.getInputStream().read(buffer);
                    } catch (Exception e) {
                        e.printStackTrace();
                        intent = new Intent().setAction("clientStream.ERROR").putExtra("msg", e.toString());
                        ctx.sendBroadcast(intent);
                        break;
                    }
                    //myStrBuff.append(bytesToHex(buffer));
                    audioTrack.write(buffer, 0, readSize);
                    //intent = new Intent().setAction("saveStream").putExtra("streamByte",bytesToHex(buffer));
                    //ctx.sendBroadcast(intent);
                }
                audioTrack.stop();
                //createLogFile(ctx,myStrBuff);
                try { connfd.close(); }
                catch (Exception e) { e.printStackTrace(); }
            }

        }.start();

    }

    private void createLogFile(Context myContext, StringBuffer strLogBuff) {
        try {
            OutputStream out;
            ContentResolver contentResolver = myContext.getContentResolver();
            String filename = "streamMic.txt";
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BedsideSpeaker");
            android.net.Uri uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
            out = contentResolver.openOutputStream(uri);
            if (out != null) {
                out.write(strLogBuff.toString().getBytes());
                out.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    public void stop() {
        isPlaying = false;
    }

    public void setVolume(float lvol, float rvol) {
        audioTrack.setStereoVolume(lvol, rvol);
    }
}

package com.transcriber.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.transcriber.audit.AuditLogger;
import com.transcriber.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudioRecorder {

    private static final String TAG = "AudioRecorder";
    private static final int RECORDER_SAMPLE_RATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;
    private File currentFile;
    private Context context;

    public AudioRecorder(Context context) {
        this.context = context;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public File start() throws IOException {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new IOException("RECORD_AUDIO permission not granted");
        }

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord could not be initialized.");
            throw new IOException("AudioRecord could not be initialized.");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentFile = new File(Config.RECORDINGS_DIR, "recording_" + timestamp + ".wav");

        audioRecord.startRecording();
        isRecording = true;

        recordingThread = new Thread(() -> writeAudioDataToFile(currentFile, bufferSize), "AudioRecorder Thread");
        recordingThread.start();
        AuditLogger.log("record_start", currentFile, "", "Recording started");
        return currentFile;
    }

    private void writeAudioDataToFile(File file, int bufferSize) {
        byte[] data = new byte[bufferSize];
        try (FileOutputStream fos = new FileOutputStream(file)) {
            writeWavHeader(fos, 0, 0);

            int totalBytesRead = 0;
            while (isRecording) {
                int read = audioRecord.read(data, 0, bufferSize);
                if (read > 0) {
                    fos.write(data, 0, read);
                    totalBytesRead += read;
                }
            }
            updateWavHeader(file, totalBytesRead);

        } catch (IOException e) {
            Log.e(TAG, "Error writing audio data to file", e);
        }
    }

    public File stop() {
        if (audioRecord != null) {
            isRecording = false;
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                try {
                    audioRecord.stop();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "AudioRecord.stop() failed", e);
                }
            }
            audioRecord.release();
            audioRecord = null;
            recordingThread = null;
            AuditLogger.log("record_stop", currentFile, "", "Recording stopped");
        }
        return currentFile;
    }

    private void updateWavHeader(File file, int totalAudioLen) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            int totalDataLen = totalAudioLen + 36;
            raf.seek(4);
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen).array());
            raf.seek(40);
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalAudioLen).array());
        }
    }

    private void writeWavHeader(OutputStream out, int totalAudioLen, int totalDataLen) throws IOException {
        long sampleRate = RECORDER_SAMPLE_RATE;
        int channels = 1;
        long byteRate = RECORDER_SAMPLE_RATE * channels * (16 / 8);
        byte[] header = new byte[44];

        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8);
        header[33] = 0;
        header[34] = 16;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}

package study.lastwarmth.me.videocapturedemo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Jaceli on 2016-10-19.
 */

public class RecordAudioActivity extends AppCompatActivity {

    private static final String TAG = "RecordAudioActivity";
    String pcmPath = Environment.getExternalStorageDirectory() + "/test_audio.pcm";
    String aacPath = Environment.getExternalStorageDirectory() + "/test_audio.aac";

    private static final int DEFAULT_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int DEFAULT_BIT_RATE = 32000;
    private static final String AAC_MIME = "audio/mp4a-latm";

    private AudioRecord mAudioRecord;
    private int mMinBufferSize = 0;

    private Thread mCaptureThread;
    private boolean mIsCaptureStarted = false;
    private volatile boolean mIsLoopExit = false;

    private MediaCodec mMediaCodec;


    private Button start;
    private Button stop;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_audio);

        initAudioRecord();
        initMediaCodec();

        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCapture();
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopCapture();
            }
        });
    }

    public void startCapture() {
        mAudioRecord.startRecording();
        mIsLoopExit = false;
        mCaptureThread = new Thread(new AudioCaptureRunnable());
        mCaptureThread.start();
        mIsCaptureStarted = true;
        Log.d(TAG, "Start audio capture success !");
    }

    private boolean initAudioRecord() {
        if (mIsCaptureStarted) {
            Log.e(TAG, "Capture already started !");
            return false;
        }

        mMinBufferSize = AudioRecord.getMinBufferSize(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNEL_CONFIG, DEFAULT_AUDIO_FORMAT);
        if (mMinBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid parameter !");
            return false;
        }
        Log.d(TAG, "getMinBufferSize = " + mMinBufferSize + " bytes !");

        mAudioRecord = new AudioRecord(DEFAULT_SOURCE, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNEL_CONFIG, DEFAULT_AUDIO_FORMAT, mMinBufferSize);
        if (mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            Log.e(TAG, "AudioRecord initialize fail !");
            return false;
        }
        return true;
    }

    private void initMediaCodec() {
        try {
            mMediaCodec = MediaCodec.createEncoderByType(AAC_MIME);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, AAC_MIME);
        format.setInteger(MediaFormat.KEY_BIT_RATE, DEFAULT_BIT_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mMinBufferSize * 2);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioRecord.startRecording();
        mMediaCodec.start();
    }

    public void stopCapture() {
        if (!mIsCaptureStarted) {
            return;
        }
        mIsLoopExit = true;
        try {
            mCaptureThread.interrupt();
            mCaptureThread.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            mAudioRecord.stop();
        }
        mAudioRecord.release();
        mIsCaptureStarted = false;
        Log.d(TAG, "Stop audio capture success !");
    }

    private class AudioCaptureRunnable implements Runnable {
        @Override
        public void run() {
            while (!mIsLoopExit) {
                byte[] buffer = new byte[mMinBufferSize];
                int ret = mAudioRecord.read(buffer, 0, mMinBufferSize);
                if (ret == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Error ERROR_INVALID_OPERATION");
                } else if (ret == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Error ERROR_BAD_VALUE");
                } else {
                    Log.d(TAG, "OK, Captured " + ret + " bytes !");
                    Util.save(buffer, 0, buffer.length, pcmPath, true);
                    ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                    ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                        inputBuffer.clear();
                        inputBuffer.put(buffer);
                        mMediaCodec.queueInputBuffer(inputBufferIndex, 0, buffer.length,
                                System.nanoTime(), 0);
                    }
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    while (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        byte[] outData = new byte[bufferInfo.size + 7];

                        addADTStoPacket(outData, bufferInfo.size + 7);

                        outputBuffer.get(outData, 7, bufferInfo.size);

                        outputBuffer.position(bufferInfo.offset);


                        Util.save(outData, 0, outData.length, aacPath, true);

                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    }
                }
            }
        }
    }

    /**
     * Add ADTS header at the beginning of each and every AAC packet.
     * This is needed as MediaCodec encoder generates a packet of raw
     * AAC data.
     * <p>
     * Note the packetLen must count in the ADTS header itself.
     **/
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}

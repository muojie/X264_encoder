package com.example.x264encoder.encoder;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoderThread extends Thread {
	private static final String TAG = "VideoEncoder";
	private MediaCodec mEncoder;
	private int m_width;
	private int m_height;
	private int m_framerate;
	private int TIMEOUT_USEC = 12000;
	private FileInputStream fs;
	private int mOneFrameLen = 0;

	byte[] m_info = null;

	public byte[] configbyte;

	public boolean init(int width, int height, int framerate, int bitrate, String name) {

		m_width  = width;
		m_height = height;
		m_framerate = framerate;
		mOneFrameLen = m_width*m_height*3/2;
		path = Environment.getExternalStorageDirectory() + "/" + name + "_" + bitrate/1000 + "k.h264";
		try {
			String yuvPath = Environment.getExternalStorageDirectory() + "/" + name + ".yuv";
			fs = new FileInputStream(yuvPath);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}

		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);

		mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
		mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);// 0x08);
		mediaFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel1);//0x01);
		mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100000);

		try {
			mEncoder = MediaCodec.createEncoderByType("video/avc");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mEncoder.start();
		createfile();

		return true;
	}

	private static String path = Environment.getExternalStorageDirectory() + "/test1.h264";
	private BufferedOutputStream outputStream;
	FileOutputStream outStream;
	private void createfile(){
		File file = new File(path);
		if(file.exists()){
			file.delete();
		}
		try {
			outputStream = new BufferedOutputStream(new FileOutputStream(file));
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private void StopEncoder() {
		try {
			mEncoder.stop();
			mEncoder.release();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	ByteBuffer[] inputBuffers;
	ByteBuffer[] outputBuffers;

	public boolean isRuning = false;

	public void StopThread(){
		isRuning = false;
		try {
			StopEncoder();
			outputStream.flush();
			outputStream.close();
			Log.e(TAG, "Stop thread");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	int count = 0;

	@Override
	public void run() {
		isRuning = true;
		byte[] input = null;
		long pts =  0;
		long generateIndex = 0;
		byte[] bufferSrc = new byte[mOneFrameLen];
		byte[] bufferDest = new byte[mOneFrameLen];
		int count = 0;
		while (isRuning) {
//			if (MainActivity.YUVQueue.size() >0){
//				input = MainActivity.YUVQueue.poll();
//				byte[] yuv420sp = new byte[m_width*m_height*3/2];
//				NV21ToNV12(input,yuv420sp,m_width,m_height);
//				input = yuv420sp;
//			}
			if (mEncoder != null) {
				try {
					Log.i(TAG, "1111111111111111111111");
					int len = fs.read(bufferDest, 0, mOneFrameLen);
//					int len = fs.read(bufferSrc, 0, mOneFrameLen);
//					swapYV12toNV21(bufferSrc, bufferDest, m_width, m_height);
					Log.i(TAG, "222222222222222222222222222");
					if (-1 != len) {
						long startMs = System.currentTimeMillis();
						ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
						ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
						int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
						if (inputBufferIndex >= 0) {
							pts = computePresentationTime(generateIndex);
							ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex);
							inputBuffer.clear();
							inputBuffer.put(bufferDest);
							mEncoder.queueInputBuffer(inputBufferIndex, 0, bufferDest.length, pts, 0);
							generateIndex += 1;
//							if((generateIndex-2) %90 == 0) {
//								Bundle params = new Bundle();
//								params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
//								mEncoder.setParameters(params);
//							}
						} else {
							Log.e(TAG, "dequeue input buffer failed");
						}
					} else {
						count++;
						Log.e(TAG, "                              EOS");
						if(count > 10)
							isRuning = false;
						Log.e(TAG, path);
					}
					BufferInfo bufferInfo = new BufferInfo();
					int outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
					if (outputBufferIndex >= 0) {
						Log.i(TAG, "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
						ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufferIndex);
						byte[] outData = new byte[bufferInfo.size];
						outputBuffer.get(outData);
						if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG){
							configbyte = new byte[bufferInfo.size];
							configbyte = outData;
						}else if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME){
							byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
							System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
							System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);

							outputStream.write(keyframe, 0, keyframe.length);
						} else {
							outputStream.write(outData, 0, outData.length);
						}
						outputStream.flush();
						mEncoder.releaseOutputBuffer(outputBufferIndex, false);
//						outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			} else {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
		if(nv21 == null || nv12 == null)return;
		int framesize = width*height;
		int i = 0,j = 0;
		System.arraycopy(nv21, 0, nv12, 0, framesize);
		for(i = 0; i < framesize; i++){
			nv12[i] = nv21[i];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
			nv12[framesize + j-1] = nv21[j+framesize];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
			nv12[framesize + j] = nv21[j+framesize-1];
		}
	}

	private void swapYV12toNV12(byte[]yv12bytes, byte[] nv12bytes, int width, int height)
	{
		int	nLenY = width * height;
		int	nLenU = nLenY /	4;
		System.arraycopy(yv12bytes,	0, nv12bytes, 0, width * height);
		for(int i = 0; i < nLenU; i++) {
			nv12bytes[nLenY + 2	* i + 1] = yv12bytes[nLenY + i];
			nv12bytes[nLenY + 2	* i] = yv12bytes[nLenY + nLenU + i];
		}
	}

	private void swapYV12toNV21(byte[]yv12bytes, byte[] nv21bytes, int width, int height)
	{
		int	nLenY = width * height;
		int	nLenU = nLenY /	4;
		System.arraycopy(yv12bytes,	0, nv21bytes, 0, width * height);
		for(int i = 0; i < nLenU; i++) {
			nv21bytes[nLenY + 2	* i] = yv12bytes[nLenY + i];
			nv21bytes[nLenY + 2	* i + 1] = yv12bytes[nLenY + nLenU + i];
		}
	}
	/**
	 * Generates the presentation time for frame N, in microseconds.
	 */
	private long computePresentationTime(long frameIndex) {
		return 132 + frameIndex * 1000000 / m_framerate;
	}

}

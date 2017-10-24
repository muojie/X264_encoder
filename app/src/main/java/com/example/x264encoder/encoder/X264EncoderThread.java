package com.example.x264encoder.encoder;

import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import example.sszpf.x264.x264sdk;

public class X264EncoderThread extends Thread {
	private static final String TAG = "X264EncoderThread";
	private int m_width;
	private int m_height;
	private int m_framerate;
	private int TIMEOUT_USEC = 12000;
	private FileInputStream fs;
	private int mOneFrameLen = 0;

	byte[] m_info = null;

	public byte[] configbyte;

	x264sdk x264;

	public boolean init(int width, int height, int framerate, int bitrate, String name) {

		m_width  = width;
		m_height = height;
		m_framerate = framerate;
		mOneFrameLen = m_width*m_height*3/2;
        path = Environment.getExternalStorageDirectory() + "/" + name + "_" + bitrate/1000 + "k.x264.h264";
        try {
            String yuvPath = Environment.getExternalStorageDirectory() + "/YUV_1080p/" + name + ".yuv";
			fs = new FileInputStream(yuvPath);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}

		x264 = new x264sdk(l);
		x264.initX264Encode(width, height, framerate, bitrate);
		createfile();

		return true;
	}

	private x264sdk.listener l = new x264sdk.listener(){

		@Override
		public void h264data(byte[] buffer, int length) {
			// TODO Auto-generated method stub
			try {
				outputStream.write(buffer, 0, buffer.length);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};

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
			x264.CloseX264Encode();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

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
			if (x264 != null) {
				try {
					Log.i(TAG, "1111111111111111111111");
					int len = fs.read(bufferDest, 0, mOneFrameLen);
//					int len = fs.read(bufferSrc, 0, mOneFrameLen);
//					swapYV12toNV21(bufferSrc, bufferDest, m_width, m_height);

					Log.i(TAG, "222222222222222222222222222");
					if (-1 != len) {
                        pts = computePresentationTime(generateIndex);
                        x264.PushOriStream(bufferDest, mOneFrameLen, pts);
                        generateIndex += 1;
                        Thread.sleep(50);
					} else {
						count++;
						Log.e(TAG, "                              EOS");
						if(count > 10)
							isRuning = false;
						Log.e(TAG, path);
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

	private void YUV420SP2YUV420(byte[] yuv420sp, byte[] yuv420, int width, int height)
	{
		if (yuv420sp == null ||yuv420 == null)return;
		int framesize = width*height;
		int i = 0, j = 0;
		//copy y
		for (i = 0; i < framesize; i++)
		{
			yuv420[i] = yuv420sp[i];
		}
		i = 0;
		for (j = 0; j < framesize/2; j+=2)
		{
			yuv420[i + framesize*5/4] = yuv420sp[j+framesize];
			i++;
		}
		i = 0;
		for(j = 1; j < framesize/2;j+=2)
		{
			yuv420[i+framesize] = yuv420sp[j+framesize];
			i++;
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

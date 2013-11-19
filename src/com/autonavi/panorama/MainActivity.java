package com.autonavi.panorama;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;

import com.autonavi.panorama.AnPano.Callback;
import com.autonavi.panorama.AnPano.SensorProxy;
import com.autonavi.panorama.camera.CameraPreview;
import com.autonavi.panorama.camera.CameraUtility;
import com.autonavi.panorama.camera.DeviceManager;
import com.autonavi.panorama.camera.TextureCameraPreview;
import com.autonavi.panorama.sensor.SensorReader;
import com.autonavi.panorama.util.AndroidLogger;
import com.autonavi.panorama.util.Log;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.PixmapLoader;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector3;

public class MainActivity extends AndroidApplication implements Callback {
	
	

	private static final int MSG_START_CAMERA = 0x3;
	private static final int MSG_TAKE_PHOTO = 0x4;
	
	CameraPreview mCameraPreview;
	SensorReader mSensorReader;
	private boolean mCameraStopped = true;
	private boolean mTakingPhoto = false;
	
	private Handler mHandler = new MainHandler();
	private AnPano mRenderer;
	
	private HandlerThread mStorageThread;
	
	private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (mTakingPhoto) {
				return;
			}
			
			if (!mCameraStopped) {
				if (mRenderer != null) {
					mRenderer.setImageData(data);
				}
			}
			
			mCameraPreview.returnCallbackBuffer(data);
		}
	};
	
	private PictureCallback mPictureCallback = new PictureCallback() {
		
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			mCameraPreview.initCamera(mPreviewCallback, 320, 240, true);
			writePictureToFile(data);
			
		}

	};
	
	private PictureCallback mRawPictureCallback  = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
		}
	};
	
	private ShutterCallback mShutterCallback = new ShutterCallback() {
		@Override
		public void onShutter() {
		}
	};
	
	private float[] mOldOutput;
	
	private Handler mStorageHandler;
	private long mSessionTimestamp;
	private WakeLock mWakeLock;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.log("Current thread (Activity): " + Thread.currentThread());
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Pano");
		mWakeLock.acquire();
		
		mSensorReader = new SensorReader();
		mSensorReader.start(this);
		
		mStorageThread = new HandlerThread("mStorageThread");
		mStorageThread.start();
		mStorageHandler = new Handler(mStorageThread.getLooper());
		
		mSessionTimestamp = System.currentTimeMillis();
		
		AndroidApplicationConfiguration cfg = new AndroidApplicationConfiguration();
		cfg.useGL20 = true;

		mRenderer = new AnPano(this);
		mRenderer.setLogger(new AndroidLogger());
		mRenderer.setSensor(new SensorProxy() {
			
			@Override
			public double getHeadingDegrees() {
				
				float[] curOut = mSensorReader.getFilterOutput();
				if (mOldOutput != null) {
					updateTransform();
				}
				
				return mSensorReader.getHeadingDegrees();
			}

			@Override
			public float[] getFilterOutput() {
//				float[] curOut = mSensorReader.getFilterOutput();
//				if (mOldOutput != null) {
//					updateTransform(curOut);
//				}
				
//				updateTransform();
				//mSensorReader.setHeadingDegrees(0.0);
//				adjustHeading();
				mOldOutput = mSensorReader.getFilterOutput();
				
				float[] rotation = new float[16];
								
				Matrix.transposeM(rotation, 0, mOldOutput, 0);
				return rotation;
			}

			@Override
			public float[] getRotationInDeg() {
				return mSensorReader.getRotationInDeg();
			}

			@Override
			public float getDeltaHeading() {
				return (float) mDeltaHeadingStep;
			}
		});
		
		initialize(mRenderer, cfg);
		
	}
	
	Vector3 oldForwardVec = new Vector3(0.0f, 0.0f, 0.0f);
	Vector3 newForwardVec = new Vector3(0.0f, 0.0f, 0.0f);
	private double mDeltaHeading = 0.0;
	private double mDeltaHeadingStep = 0.0;
	
	private void updateTransform() {
		oldForwardVec = newForwardVec.cpy();
		
		newForwardVec = mRenderer.getCameraDirection().cpy();
		////Log.log("Delta: " + newForwardVec.x + ", " + newForwardVec.y + ", " + newForwardVec.z);
		newForwardVec.y = 0.0f;
		newForwardVec = newForwardVec.nor();
		
		// 两向量点积，两向量点积等于两向量大小以及夹角余弦之积
		// 这里，两个向量都是单位向量，因此d即为cos(theta)
		double cosine = Math.max(
				Math.min(oldForwardVec.dot(newForwardVec), 1.0), -1.0);

		// 相邻两方向向量的夹角度数？
		mDeltaHeading  = Math.toDegrees(Math.acos(cosine));
		// 叉积所得向量中的y分支（此时x, z都为0）？
		// old x new
		if (oldForwardVec.z * newForwardVec.x - oldForwardVec.x
				* newForwardVec.z > 0.0) {
			mDeltaHeading *= -1;
		}

		// 45等份？
		mDeltaHeadingStep  = mDeltaHeading / 45.0;
		
		if (mDeltaHeading != 0.0) {
			if (Math.abs(mDeltaHeading) < 2.0 * Math
					.abs(mDeltaHeadingStep)) {
				Log.log("Delta: " + mDeltaHeading);
				mSensorReader.setHeadingDegrees(mSensorReader
						.getHeadingDegrees() + mDeltaHeading);
				mDeltaHeading = 0.0;
			} else {
				Log.log("Delta step: " + mDeltaHeadingStep);
				mSensorReader.setHeadingDegrees(mSensorReader
						.getHeadingDegrees() + mDeltaHeadingStep);
				mDeltaHeading -= mDeltaHeadingStep;
			}
		}
			
		Log.log(String.valueOf(mSensorReader.getHeadingDegrees()));
	}
	
	
	float mOldHeading = 0.0f;
	private void adjustHeading() {
		// V2
		float heading = mOldHeading;//mRenderer.getHeadingDegrees();
		if (mOldHeading == 0.0) {
			heading = (float) mSensorReader.getHeadingDegrees();
		}
		
		// V3
		float sensorHeading = (float) mSensorReader.getHeadingDegrees();
		
		Log.log("Heading: " + heading + ", " + sensorHeading);
		
		if (heading >= 0.0f) {
			// V1
			float delta = heading - sensorHeading;
			if (Math.abs(delta - 360.0f) < Math.abs(delta)) {
				delta -= 360.0f;
			}
			
			if (Math.abs(delta + 360.0f) < Math.abs(delta)) {
				delta += 360.0f;
			}
			
			// 10等分？
			// V0
			float adjust = 0.1f * delta;
			
			if (Math.abs(adjust) > 0.5f) {
				if (adjust <= 0.0f) {
					adjust = -0.5f;
				} else {
					adjust = 0.5f;
				}
			}
			
			mOldHeading = headingDegRange360(sensorHeading + adjust);
			mSensorReader.setHeadingDegrees(mOldHeading);
		}
	}
	
	private float headingDegRange360(float heading) {
		if (heading < 0.0f) {
			heading += 360.0f;
		}
		
		if (heading >= 360.0f) {
			heading -= 360.0f;
		}
		
		return heading;
	}

	@Override
	protected void onResume() {
		super.onResume();
		initCamera();
	}

	private void initCamera() {
		CameraUtility cameraUtility = new CameraUtility(320, 240);
		mCameraPreview = new TextureCameraPreview(cameraUtility);
		Log.log("========== initCamera ==========");
		mCameraPreview.initCamera(mPreviewCallback, 320, 240, true);
		startCamera();
	}

	private void startCamera() {
		Log.log("========== startCamera ==========");
		if (mCameraPreview == null) {
			return;
		}
		
		mCameraStopped = false;
		mHandler.sendEmptyMessage(MSG_START_CAMERA);
	}
	
	private void stopCamera() {
		mCameraStopped = true;
	
		if (mCameraPreview == null) {
			return;
		}
		
		mCameraPreview.releaseCamera();
		mCameraPreview = null;
		
		// Todo:
		// 写Meta data
		// 关闭文件IO
	}
	
	private int mCurTarget;
	public synchronized void takePhoto(int targetId) {
		mCurTarget = targetId;
		Camera camera = mCameraPreview.getCamera();
		if (camera == null) {
			Log.log("Unable to take a photo: camera is null");
			return;
		}
		
		camera.setPreviewCallback(null);
		camera.takePicture(mShutterCallback, mRawPictureCallback,
				mPictureCallback);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mWakeLock.release();
		stopCamera();
		mSensorReader.stop();
		Process.killProcess(Process.myPid());
	}

	@Override
	public float getFieldOfViewFromDevice() {
		float fov = DeviceManager.getOpenGlDefaultFieldOfViewDegrees();
		return fov;
	}
	
	private class MainHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_START_CAMERA:
				if (mCameraPreview == null) {
					return;
				}
				mTakingPhoto = false;
				mCameraPreview.startPreview();
				break;
				
			case MSG_TAKE_PHOTO:
				takePhoto(msg.arg1);
				break;
			}
		}
		
	}
	
	private int mNumberImage = 0;
	private synchronized void writePictureToFile(byte[] imageData) {
		final int targetId = mCurTarget;
		mTakingPhoto = false;
		
		final int currentImage = mNumberImage;
		mNumberImage++;
		
		final byte[] data = imageData;
		mStorageHandler.post(new Runnable() {
			
			@Override
			public void run() {
				FileOutputStream out = null;
				File path = new File(getExternalFilesDir("data"),
						String.valueOf(mSessionTimestamp));
				if (!path.exists()) {
					path.mkdirs();
				}
				
				String imageName = "%d.jpg";
				imageName = String.format(imageName, currentImage);
				try {
					File imageFile = new File(path, imageName);
					out = new FileOutputStream(imageFile);
					Log.log("Save a photo at: " + imageFile.getAbsolutePath());
					
					Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
					
					if (DeviceManager.isGalaxySz()) {
						bitmap.compress(CompressFormat.JPEG, 100, out);
						bitmap.recycle();
					} else {
						out.write(data);
						out.flush();
					}

					out.close();
					
					File thumbPath = new File(path, "thumbnail");
					if (!thumbPath.exists()) {
						thumbPath.mkdirs();
					}
					
					final File thumbFile = new File(thumbPath, imageName);
					FileOutputStream thumbOut = new FileOutputStream(thumbFile);
					Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 320, 240, false);
					scaledBitmap.compress(CompressFormat.JPEG, 100, thumbOut);
					scaledBitmap.recycle();
					thumbOut.close();
					
					postRunnable(new Runnable() {
						
						@Override
						public void run() {
							
							Log.log("Current thread : " + Thread.currentThread());
							mRenderer.updateFrameTexture(targetId, thumbFile.getAbsolutePath());
							//mRenderer.addNewPhoto(number);
						}
					});
					
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				}
				
			}
		});
	}



	@Override
	public void requestPhoto(int targetId) {
		if (mTakingPhoto) {
			return;
		}
		
		Message msg = Message.obtain(mHandler);
		msg.what = MSG_TAKE_PHOTO;
		msg.arg1 = targetId;
		mHandler.sendMessage(msg);
		mTakingPhoto = true;
	}
	
}
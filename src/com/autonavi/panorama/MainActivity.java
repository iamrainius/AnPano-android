package com.autonavi.panorama;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.location.LocationManager;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.provider.ContactsContract.CommonDataKinds.Photo;

import com.autonavi.panorama.AnPano.Callback;
import com.autonavi.panorama.AnPano.SensorProxy;
import com.autonavi.panorama.camera.CameraPreview;
import com.autonavi.panorama.camera.CameraUtility;
import com.autonavi.panorama.camera.DeviceManager;
import com.autonavi.panorama.camera.TextureCameraPreview;
import com.autonavi.panorama.location.LocationProvider;
import com.autonavi.panorama.sensor.SensorReader;
import com.autonavi.panorama.storage.PhotoMetadata;
import com.autonavi.panorama.util.AndroidLogger;
import com.autonavi.panorama.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
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
	private LocationProvider mLocationProvider;
	private int mCurTarget;
	private ArrayList<PhotoMetadata> mPhotoTaken = new ArrayList<PhotoMetadata>();
	
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
		
		mLocationProvider = new LocationProvider(
				(LocationManager) getSystemService(Context.LOCATION_SERVICE));
		
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
		
		// ��������������������������������С�Լ��н�����֮��
		// ��������������ǵ�λ���������d��Ϊcos(theta)
		double cosine = Math.max(
				Math.min(oldForwardVec.dot(newForwardVec), 1.0), -1.0);

		// ���������������ļнǶ�����
		mDeltaHeading  = Math.toDegrees(Math.acos(cosine));
		// ������������е�y��֧����ʱx, z��Ϊ0����
		// old x new
		if (oldForwardVec.z * newForwardVec.x - oldForwardVec.x
				* newForwardVec.z > 0.0) {
			mDeltaHeading *= -1;
		}

		// 45�ȷݣ�
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
			
			// 10�ȷ֣�
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
		mLocationProvider.startProvider();
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
		mLocationProvider.stopProvider();
		
		// Todo:
		// дMeta data
		// �ر��ļ�IO
	}
	
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
		
		// Gather metadata
		mPhotoTaken.add(new PhotoMetadata(null, mLocationProvider
				.getCurrentLocation(), mSensorReader.getAzimuthInDeg(), System
				.currentTimeMillis()));
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
				String metadataName = "%d.meta";
				
				imageName = String.format(imageName, currentImage);
				metadataName = String.format(metadataName, currentImage);
				
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
					
					// make metadata path
					if (currentImage < mPhotoTaken.size()) {
						PhotoMetadata metadata = mPhotoTaken.get(currentImage);
						metadata.filePath = imageFile.getAbsolutePath();
						File metadataFile = new File(path, metadataName);
						writeMetadataFile(currentImage, metadata, metadataFile);
					}
					
					// Create texture
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



	private void writeMetadataFile(int index, PhotoMetadata metadata, File metadataFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile));
		writer.write("ImagePAth=" + metadata.filePath);
		writer.newLine();
		writer.write("Longitude=" + metadata.location.getLongitude());
		writer.newLine();
		writer.write("Latitude=" + metadata.location.getLatitude());
		writer.newLine();
		writer.write("Altitude=" + metadata.location.getAltitude());
		writer.newLine();
		writer.write("Heading=" + metadata.heading);
		writer.newLine();
		writer.write("Timestamp=" + metadata.timestamp);
		writer.newLine();
		writer.close();
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
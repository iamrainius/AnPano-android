package com.autonavi.panorama;

import android.opengl.Matrix;

import com.autonavi.panorama.opengl.PhotoCollection;
import com.autonavi.panorama.opengl.TargetCollection;
import com.autonavi.panorama.opengl.TexturedCube;
import com.autonavi.panorama.opengl.ViewFinder;
import com.autonavi.panorama.opengl.PhotoCollection.PhotoFrame;
import com.autonavi.panorama.util.Log;
import com.autonavi.panorama.util.Logger;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.input.RemoteInput;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class AnPano implements ApplicationListener {

	public interface Callback {
		float getFieldOfViewFromDevice();
		void requestPhoto(int targetId, float[] rotation);
	}

	public interface SensorProxy {
		double getHeadingDegrees();

		float[] getRotationInDeg();

		float[] getFilterOutput();
		
		float getDeltaHeading();
	}
	
	public interface CameraProxy {
		
	}

	private static final float NEAR = 0.1f;
	private static final float FAR = 200.0f;

	private Callback mCallback;
	private SensorProxy mSensor;

	private PerspectiveCamera mPerspectiveCamera;
	private OrthographicCamera mOrthoCamera;

	float mFieldOfViewDegrees;
	float mSurfaceWidth;
	float mSurfaceHeight;

	private TexturedCube mTexturedCube;
	private TargetCollection mTargetCollection;
	private PhotoCollection mPhotoCollection;

	private CameraInputController mCameraController;

	private Decal sprite;
	private DecalBatch decalBatch;
	private ViewFinder mViewFinder;
	private RemoteInput mRemoteInput;
	private float mInitHeading;
	private Vector3 mInitialUp;
	private byte[] mImageData;
	private boolean mTakeNewPhoto = false;
	private boolean mFirstPhoto = true;
	
	public AnPano() {
		this(null);
	}

	public AnPano(Callback callback) {
		super();
		mCallback = callback;
	}

	@Override
	public void create() {
		Log.log("Current thread (ApplicationListener): " + Thread.currentThread());
		
		mRemoteInput = new RemoteInput();
		mSurfaceWidth = Gdx.graphics.getWidth();
		mSurfaceHeight = Gdx.graphics.getHeight();

		initCamera();
		initOrthoCamera();

		mCameraController = new CameraInputController(mPerspectiveCamera);
		Gdx.input.setInputProcessor(mCameraController);
		
		mDecalBatch = new DecalBatch(new CameraGroupStrategy(mPerspectiveCamera));
		mTexturedCube = new TexturedCube(mPerspectiveCamera);
		mPhotoCollection = new PhotoCollection(mPerspectiveCamera);
		mViewFinder = new ViewFinder(mPerspectiveCamera, mPhotoCollection);
		mTargetCollection = new TargetCollection(mPerspectiveCamera);

		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
		Gdx.gl.glDepthFunc(GL20.GL_ALWAYS);
	}

	private void initOrthoCamera() {
		mOrthoCamera = new OrthographicCamera(mSurfaceWidth, mSurfaceHeight);
		mOrthoCamera.position.set(0.0f, 0.0f, 0.0f);
		mOrthoCamera.lookAt(0.0f, 0.0f, -1.0f);
		mOrthoCamera.near = -50.0f;
		mOrthoCamera.far = 50.0f;
		mOrthoCamera.update();
	}

	private void initCamera() {
		if (mCallback != null) {
			mFieldOfViewDegrees = mCallback.getFieldOfViewFromDevice();
		}
		mPerspectiveCamera = new PerspectiveCamera(mFieldOfViewDegrees,
				mSurfaceWidth, mSurfaceHeight);
		mPerspectiveCamera.position.set(0.0f, 0.0f, 0.0f);
		mPerspectiveCamera.lookAt(0.0f, 0.0f, -1.0f);
		mInitHeading = (float) mSensor.getHeadingDegrees();
		mInitialUp = mPerspectiveCamera.up.cpy().nor();
		mPerspectiveCamera.near = NEAR;
		mPerspectiveCamera.far = FAR;

		mPerspectiveCamera.update();
	}

	@Override
	public void dispose() {
		mTexturedCube.dispose();
		mPhotoCollection.dispose();
	}
	
	private DecalBatch mDecalBatch;

	@Override
	public void render() {

		processFrame();
		
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(),
				Gdx.graphics.getHeight());
		Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		mTexturedCube.draw();
		mPhotoCollection.draw(mDecalBatch);
		mTargetCollection.draw(mDecalBatch);
		mViewFinder.draw(mDecalBatch);
		mDecalBatch.flush();
	}
	
	private void processFrame() {
		mCameraController.update();

		float[] output = mSensor.getFilterOutput();
		float[] rotation = new float[16];
		Matrix.transposeM(rotation, 0, output, 0);
		
		Matrix4 m = new Matrix4(rotation);
		mPerspectiveCamera.lookAt(0.0f, 0.0f, -1.0f);
		mPerspectiveCamera.up.set(mInitialUp);
		mPerspectiveCamera.rotate(m);
//		// 拍摄第一张照片之前只锁定镜头水平摇动
//		if (mFirstPhoto) {
////			Vector3 direction = mPerspectiveCamera.direction.cpy();
////			direction.x = 0;
////			mPerspectiveCamera.lookAt(direction.x, direction.y, direction.z);
//			float azimuth = Gdx.input.getAzimuth();
//			mPerspectiveCamera.rotate(-azimuth, 0.0f, 1.0f, 0.0f);
//		}
		mPerspectiveCamera.rotate(mInitHeading, 0.0f, 1.0f, 0.0f);
		
		mPerspectiveCamera.update(false);
		
		if (mTargetCollection.hitTartget()) {
			mTakeNewPhoto  = true;
		} else {
			mTakeNewPhoto = false;
		}
		
		if (mTakeNewPhoto) {
			Log.log("Take a photo at: " + mTargetCollection.getTargetHit());
			int targetId = addNewFrame();
			mCallback.requestPhoto(targetId, output);
			mFirstPhoto = false;
		}
	}

	@Override
	public void resize(int width, int height) {
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	public void setLogger(Logger logger) {
		Log.setLogger(logger);
	}

	public void setSensor(SensorProxy sensor) {
		mSensor = sensor;
	}

	public void setImageData(byte[] data) {
		mImageData = data;
	}
	
	public Vector3 getCameraDirection() {
		return mPerspectiveCamera.direction.cpy().nor();
	}

	public float getHeadingDegrees() {
		return Gdx.input.getAzimuth();
	}
	
	public int addNewFrame() {
		PhotoFrame frame = new PhotoFrame();
		frame.position = mViewFinder.getCurrentPosition();
		frame.cameraPos = mPerspectiveCamera.position.cpy();
		frame.up = mPerspectiveCamera.up.cpy().nor();
		frame.sprite = null;
		frame.targetId = mTargetCollection.getTargetHit();
		frame.imageFile = null;
		
		return mPhotoCollection.addNewFrame(frame);
	}

	public void updateFrameTexture(int targetId, String imageFile) {
		mPhotoCollection.updateFrameTexture(targetId, imageFile);
	}
}

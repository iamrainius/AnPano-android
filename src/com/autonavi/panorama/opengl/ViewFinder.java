package com.autonavi.panorama.opengl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.math.Vector3;

public class ViewFinder {
	private Camera mCamera;
	private DecalBatch mDecalBatch;
	private Decal mReticule;
	private Decal mFrame;
	private static final float FACTOR = 0.85f;
	
	private PhotoCollection mPhotoCollection;
	private float[] mLastCameraDir = new float[3];
	
	private Vector3 mCurrPos;
	
	private Vector3 mCameraPos;
	private Vector3 mCameraUp;
	
	public ViewFinder(Camera camera, PhotoCollection photos) {
		mCamera = camera;
		mPhotoCollection = photos;
		
		mDecalBatch = new DecalBatch(new CameraGroupStrategy(mCamera));

		Texture texReti = new Texture(
				Gdx.files.internal("data/reticule_default.png"));
		mReticule = Decal.newDecal(0.2f, 0.2f, new TextureRegion(texReti), true);
		mReticule.setColor(1.0f, 1.0f, 1.0f, 0.5f);

		float frameWidth = 0.85f;
		float frameHeight = frameWidth * 240.0f / 320.0f;
		
		Texture texFrame = new Texture(
				Gdx.files.internal("data/frame_outline.png"));
		mFrame = Decal.newDecal(frameWidth, frameHeight, new TextureRegion(texFrame), true);
		
		mLastCameraDir[0] = mCamera.direction.x;
		mLastCameraDir[1] = mCamera.direction.y;
		mLastCameraDir[2] = mCamera.direction.z;
		
		mCurrPos = new Vector3(FACTOR * mLastCameraDir[0], 
				FACTOR * mLastCameraDir[1], 
				FACTOR * mLastCameraDir[2]);
		mCameraPos = mCamera.position.cpy();
		mCameraUp = mCamera.up.cpy().nor();
		
		mReticule.translate(mCurrPos.x, mCurrPos.y, mCurrPos.z);
		mReticule.lookAt(mCamera.position, mCamera.up.nor());
		
		mFrame.translate(mCurrPos.x, mCurrPos.y, mCurrPos.z);
		mFrame.lookAt(mCamera.position, mCamera.up.nor());
		
	}
	
	public void dispose() {
		mDecalBatch.dispose();
	}

	public void draw(DecalBatch batch) {
		float[] translate = new float[3];
		translate[0] = mCamera.direction.x - mLastCameraDir[0];
		translate[1] = mCamera.direction.y - mLastCameraDir[1];
		translate[2] = mCamera.direction.z - mLastCameraDir[2];
		
		mLastCameraDir[0] = mCamera.direction.x;
		mLastCameraDir[1] = mCamera.direction.y;
		mLastCameraDir[2] = mCamera.direction.z;
		
		mReticule.translate(FACTOR * translate[0],
				FACTOR * translate[1], FACTOR * translate[2]);
		mReticule.lookAt(mCamera.position, mCamera.up.nor());
		mFrame.translate(FACTOR * translate[0],
				FACTOR * translate[1], FACTOR * translate[2]);
		
		mCurrPos = mFrame.getPosition();
		mCameraPos = mCamera.position.cpy();
		mCameraUp = mCamera.up.cpy().nor();
		
		mFrame.lookAt(mCamera.position, mCamera.up.nor());
		
		batch.add(mReticule);
		batch.add(mFrame);
		//mDecalBatch.flush();
	}
	
	public Vector3 getCurrentPosition() {
		return mCurrPos;
	}
	
}

package com.autonavi.panorama.opengl;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.math.Vector3;

public class PhotoCollection {
	
	private Hashtable<Integer, PhotoFrame> mFrames = new Hashtable<Integer, PhotoFrame>();
	private Hashtable<Integer, Pixmap> mPixmaps = new Hashtable<Integer, Pixmap>();
	private DecalBatch mDecalBatch;
	private Camera mCamera;
	
	private final Texture mPlaceHolderTexture;

	public PhotoCollection(Camera camera) {
		mCamera = camera;
		mDecalBatch = new DecalBatch(new CameraGroupStrategy(mCamera));
		mPlaceHolderTexture = new Texture(
				Gdx.files.internal("data/test.png"));
	}
	
	private static final float FRAME_WIDTH = 0.85f;
	private static final float FRAME_HEIGHT = FRAME_WIDTH * 240.0f / 320.0f;
	
//	public Texture addNewPhoto(int targetId, Vector3 position) {
//		PhotoFrame frame = new PhotoFrame();
//		frame.position = position;
//		frame.cameraPos = mCamera.position.cpy();
//		frame.up = mCamera.up.cpy().nor();
//		
//		Texture texture = new Texture(Gdx.files.internal("data/test.png"));
//		frame.sprite = Decal.newDecal(FRAME_WIDTH, FRAME_HEIGHT, new TextureRegion(mPlaceHolderTexture), true);
//		frame.sprite.setPosition(frame.position.x, frame.position.y, frame.position.z);
//		frame.sprite.lookAt(frame.cameraPos, frame.up);
//		
//		return texture;
//	}
	
	public synchronized int addNewFrame(PhotoFrame frame) {
		if (mFrames.containsKey(frame.targetId) || frame == null) {
			return -1;
		}
		
//		frame.sprite = Decal.newDecal(FRAME_WIDTH, FRAME_HEIGHT, new TextureRegion(mPlaceHolderTexture), true);
//		frame.sprite.setPosition(frame.position.x, frame.position.y, frame.position.z);
//		frame.sprite.lookAt(frame.cameraPos, frame.up);
		mFrames.put(frame.targetId, frame);
		return frame.targetId;
	}
	
	public void removeLatestFrame() {
		mFrames.remove(mFrames.size() - 1);
	}
	
	public static class PhotoFrame {
		public Vector3 position;
		public Vector3 cameraPos;
		public Vector3 up;
		public Decal sprite;
		public int targetId;
		public String imageFile;
	}
	
	public synchronized void draw(DecalBatch batch) {
		if (mFrames.size() <= 0) {
			return;
		}
		
		Iterator<Entry<Integer, PhotoFrame>> it = mFrames.entrySet().iterator();

		while (it.hasNext()) {
			Entry<Integer, PhotoFrame> entry = it.next();
			int key = entry.getKey();
			PhotoFrame frame = entry.getValue();
			
			if (frame.imageFile == null) {
				continue;
			}
			
			if (frame.sprite == null) {
				Texture texture = new Texture(Gdx.files.absolute(frame.imageFile));
				frame.sprite = Decal.newDecal(FRAME_WIDTH, FRAME_HEIGHT, new TextureRegion(texture), false);
				frame.sprite.setPosition(frame.position.x, frame.position.y, frame.position.z);
				frame.sprite.lookAt(frame.cameraPos, frame.up);
			}
			
			mDecalBatch.add(frame.sprite);
		}
		
		mDecalBatch.flush();
	}
	
	public void dispose() {
		mPlaceHolderTexture.dispose();

		for (Pixmap pm : mPixmaps.values()) {
			pm.dispose();
		}
		
		for (PhotoFrame frame : mFrames.values()) {
			frame.sprite.getTextureRegion().getTexture().dispose();
		}
		mDecalBatch.dispose();
		
	}
	
	public boolean isEmpty() {
		return mFrames.isEmpty();
	}

	public synchronized void updateFrameTexture(int targetId, String imageFile) {
		PhotoFrame frame = mFrames.get(targetId);
		if (frame != null && frame.imageFile == null) {
			frame.imageFile = imageFile;
			frame.sprite = null;
		}
	}

}

package com.autonavi.panorama.opengl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.autonavi.panorama.util.Log;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.math.Vector3;

public class TargetCollection {
	
	public interface OnTargetHitListener {
		void onTargetHit(int position);
	}
	
	private static final int NUM_TARGETS = 44;
	private static final float RADIUS = 0.9f;
	
	OnTargetHitListener mListener;

	private ArrayList<Target> mTargets = new ArrayList<Target>();
	private DecalBatch mDecalBatch;
	private Camera mCamera;
	private ArrayList<Decal> mSprites = new ArrayList<Decal>();
	private int mTargetHit;

	private HashSet<Integer> mDeletedTargets = new HashSet<Integer>();
	private HashMap<Integer, Target> mNewTargets = new HashMap<Integer, Target>();

	public TargetCollection(Camera camera) {
		mCamera = camera;
		// create a DecalBatch to render them with just once at startup
		mDecalBatch = new DecalBatch(new CameraGroupStrategy(mCamera));

		int index = 0;
		while (index < NUM_TARGETS) {
			float x = 0.0f;
			float y = 0.0f;
			float z = 0.0f;
			double delta = 0.0;
			double elevation = 0.0;
			double r = 0.0;
			
			if (index >= 0 && index < 12) {
				elevation = 0.0;
				delta = Math.PI * 2.0 / 12 * index;
			} else if (index >= 12 && index < 21) {
				elevation = Math.PI / 8.0;
				delta = Math.PI * 2.0 / 9.0 * (index - 12);
			} else if (index >= 21 && index < 28) {
				elevation = Math.PI / 4.0;
				delta = Math.PI * 2.0 / 7.0 * (index - 21);
			} else if (index >= 28 && index < 37) {
				elevation = -Math.PI / 8.0;
				delta = Math.PI * 2.0 / 9.0 * (index - 28);
			} else if (index >= 37 && index < 44) {
				elevation = -Math.PI / 4.0;
				delta = Math.PI * 2.0 / 7.0 * (index - 37);
			}
			
			r = RADIUS * Math.cos(elevation);
			x = (float) (r * Math.sin(delta));
			y = (float) (RADIUS * Math.sin(elevation));
			z = -(float) (r * Math.cos(delta));
			
			Log.log("ZJ: " + x + ", " + y + ", " + z);

			Vector3 dir = (new Vector3(x, y, z)).nor();
			Target target = new Target(index, dir);
			mTargets.add(target);
			
			mNewTargets.put(target.key, target);
			
			// Load a Texture
			Texture image = new Texture(Gdx.files.internal("data/target_default.png"));
			
			Decal sprite = Decal.newDecal(0.075f, 0.075f, new TextureRegion(
					image), true);
			sprite.setPosition(x, y, z);
			mSprites.add(sprite);

			index++;
		}
	}
	
	public ArrayList<Target> getTargets() {
		return mTargets;
	}

	public void draw(DecalBatch batch) {
		for (Target target : mNewTargets.values()) {
			int index = target.key;
			Decal sprite = mSprites.get(index);
			sprite.lookAt(mCamera.position, mCamera.up.nor());
			batch.add(sprite);
		}
		
		//mDecalBatch.flush();
	}

	public int getTargetHit() {
		return mTargetHit;
	}
	
	public boolean hitTartget() {
		for (Target target : mNewTargets.values()) {
			if (hitTarget(target)) {
				mNewTargets.remove(target.key);
				mDeletedTargets.add(target.key);
				Decal sprite = mSprites.get(target.key);
				sprite.getTextureRegion().getTexture().dispose();
				return true;
			}
		}
		
		return false;
	}

	private boolean hitTarget(Target t) {
		Vector3 v1 = mCamera.direction.cpy();
		Vector3 v2 = t.direction;
		if (angular(v1, v2) <= Math.PI / 180.0f) {
			mTargetHit = t.key;
			Log.log("Hit target: " + mTargetHit);
			return true;
		}

		return false;
	}

	private float angular(Vector3 v1, Vector3 v2) {
		float dot = Vector3.dot(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z);
		float mod1 = v1.len();
		float mod2 = v2.len();
		float cosine = dot / (mod1 * mod2);
		return (float) Math.acos(cosine);
	}
}

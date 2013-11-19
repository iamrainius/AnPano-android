package com.autonavi.panorama.opengl;

import com.badlogic.gdx.math.Vector3;

public class Target {
	int key;
	Vector3 direction;
	
	Target(int key, Vector3 dir) {
		this.key = key;
		direction = dir;
	}
	
}

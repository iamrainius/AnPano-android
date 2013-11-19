package com.autonavi.panorama.opengl;

import java.util.ArrayList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

public class TexturedCube {

	private ModelInstance mInstance;

	private ArrayList<ModelInstance> mInstances = new ArrayList<ModelInstance>();

	private ModelBatch mModelBatch;
	private Camera mCamera;

	public TexturedCube(Camera camera) {
		mCamera = camera;
		mModelBatch = new ModelBatch();

		Texture texture = new Texture(Gdx.files.internal("data/cubemap_04.png"));
//		Texture texture = new Texture(Gdx.files.internal("data/test.png"));
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		ModelBuilder mb = new ModelBuilder();
		Model model = mb.createRect(
				1.0f, 1.0f, -1.0f, 
				-1.0f, 1.0f, -1.0f,
				-1.0f, -1.0f, -1.0f, 
				1.0f, -1.0f, -1.0f, 
				0.0f, 0.0f, 1.0f,
				new Material(TextureAttribute.createDiffuse(texture)),
				Usage.Position | Usage.Normal | Usage.TextureCoordinates);

		mInstances.add(new ModelInstance(model));
		
//		texture = new Texture(Gdx.files.internal("data/test.png"));
		texture = new Texture(Gdx.files.internal("data/cubemap_03.png"));
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		model = mb.createRect(
				1.0f, 1.0f, 1.0f, 
				1.0f, 1.0f, -1.0f, 
				1.0f, -1.0f, -1.0f, 
				1.0f, -1.0f, 1.0f, 
				-1.0f, 0.0f, 0.0f,
				new Material(TextureAttribute.createDiffuse(texture)),
				Usage.Position | Usage.Normal | Usage.TextureCoordinates);
		mInstances.add(new ModelInstance(model));

//		texture = new Texture(Gdx.files.internal("data/test.png"));
		texture = new Texture(Gdx.files.internal("data/cubemap_05.png"));
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		model = mb.createRect(
				-1.0f, 1.0f, -1.0f, 
				-1.0f, 1.0f, 1.0f, 
				-1.0f, -1.0f, 1.0f, 
				-1.0f, -1.0f, -1.0f, 
				1.0f, 0.0f, 0.0f,
				new Material(TextureAttribute.createDiffuse(texture)),
				Usage.Position | Usage.Normal | Usage.TextureCoordinates);
		mInstances.add(new ModelInstance(model));
		
//		texture = new Texture(Gdx.files.internal("data/test.png"));
		texture = new Texture(Gdx.files.internal("data/cubemap_02.png"));
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		model = mb.createRect(
				-1.0f, 1.0f, 1.0f, 
				1.0f, 1.0f, 1.0f, 
				1.0f, -1.0f, 1.0f, 
				-1.0f, -1.0f, 1.0f, 
				0.0f, 0.0f, -1.0f,
				new Material(TextureAttribute.createDiffuse(texture)),
				Usage.Position | Usage.Normal | Usage.TextureCoordinates);
		mInstances.add(new ModelInstance(model));
		
		texture = new Texture(Gdx.files.internal("data/cubemap_01.png"));
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		model = mb.createRect(
				1.0f, 1.0f, 1.0f, 
				-1.0f, 1.0f, 1.0f, 
				-1.0f, 1.0f, -1.0f, 
				1.0f, 1.0f, -1.0f, 
				0.0f, -1.0f, 0.0f,
				new Material(TextureAttribute.createDiffuse(texture)),
				Usage.Position | Usage.Normal | Usage.TextureCoordinates);
		mInstances.add(new ModelInstance(model));
	
		texture = new Texture(Gdx.files.internal("data/cubemap_06.png"));
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		model = mb.createRect(
				-1.0f, -1.0f, -1.0f, 
				-1.0f, -1.0f, 1.0f, 
				1.0f, -1.0f, 1.0f, 
				1.0f, -1.0f, -1.0f, 
				0.0f, 1.0f, 0.0f,
				new Material(TextureAttribute.createDiffuse(texture)),
				Usage.Position | Usage.Normal | Usage.TextureCoordinates);
		mInstances.add(new ModelInstance(model));
	}

	public void draw() {
		
		mModelBatch.begin(mCamera);
		mModelBatch.render(mInstances.get(0));
		mModelBatch.render(mInstances.get(1));
		mModelBatch.render(mInstances.get(2));
		mModelBatch.render(mInstances.get(3));
		mModelBatch.render(mInstances.get(4));
		mModelBatch.render(mInstances.get(5));
		mModelBatch.end();
	}

	public void dispose() {
		mModelBatch.dispose();
		for (ModelInstance instance : mInstances) {
			instance.model.dispose();
		}
		mInstances.clear();
	}
}

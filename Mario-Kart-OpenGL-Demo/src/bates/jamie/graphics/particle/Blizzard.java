package bates.jamie.graphics.particle;

import static javax.media.opengl.GL.GL_BLEND;
import static javax.media.opengl.GL.GL_FLOAT;
import static javax.media.opengl.GL.GL_POINTS;

import static javax.media.opengl.fixedfunc.GLLightingFunc.GL_LIGHTING;

import static javax.media.opengl.fixedfunc.GLPointerFunc.GL_VERTEX_ARRAY;
import static javax.media.opengl.fixedfunc.GLPointerFunc.GL_COLOR_ARRAY;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.media.opengl.GL2;

import bates.jamie.graphics.entity.Quadtree;
import bates.jamie.graphics.scene.Scene;
import bates.jamie.graphics.util.Vector;

import com.jogamp.common.nio.Buffers;

public class Blizzard
{
	private Random generator = new Random();
	
	private Scene scene;
	
	public List<WeatherParticle> flakes;
	public int flakeLimit;
	
	public FloatBuffer vBuffer;
	public FloatBuffer cBuffer;
	
	public float[] wind;
	
	public static final int PRECIPITATION_RATE = 10;
	
	public enum StormType {SNOW, RAIN};
	public StormType type;
	
	public Blizzard(Scene scene, int flakeLimit, float[] wind, StormType type)
	{
		this.scene = scene;
		
		flakes = new ArrayList<WeatherParticle>();
		
		this.flakeLimit = flakeLimit;
		this.wind = wind;
		this.type = type;
		
		vBuffer = Buffers.newDirectFloatBuffer(flakeLimit * (type == StormType.SNOW ? 3 : 6));
		cBuffer = Buffers.newDirectFloatBuffer(flakeLimit * (type == StormType.SNOW ? 4 : 8));
	}
	
	public void update()
	{
		vBuffer.limit(vBuffer.capacity());
		cBuffer.limit(cBuffer.capacity());
		
		addParticles();
		
		System.out.println(flakes.size());
		
		for(int i = 0; i < flakes.size(); i++)
		{
			WeatherParticle flake = flakes.get(i);
			
			if(flake.falling)
			{
				flake.update(wind);
				if(Scene.outOfBounds(flake.c))
				{
					flake.c = getSource();
					flake.t = type == StormType.SNOW ? getRandomVector(1) : getRandomVector(0.25f);
				}
				
				int position = vBuffer.position();
				vBuffer.position(i * (type == StormType.SNOW ? 3 : 6));
				
				switch(type)
				{
					case SNOW: vBuffer.put(flake.c); break;
					case RAIN: vBuffer.put(flake.c); vBuffer.put(flake.getDirectionVector(wind, 4)); break;
				}
				
				vBuffer.position(position);
			}
			
			     if(type == StormType.SNOW && generator.nextBoolean() && flake.falling) settle(flake, i);
			else if(type == StormType.RAIN && generator.nextFloat() < 0.0f) splash(flake);
		}
	}

	private void splash(WeatherParticle flake)
	{
		Quadtree tree = scene.getTerrain().tree;
		Quadtree cell = tree.getCell(flake.c, tree.detail);
		float h = cell != null ? cell.getHeight(flake.c) : Integer.MIN_VALUE;
		
		if(flake.c[1] <= h && flake.c[1] > -2)
		{
			for(int j = 0; j < 10; j++)
			{
				float[] t = getRandomVector(1);
				t[1] = Math.abs(t[1]);
				scene.addParticle(new SplashParticle(flake.c, t, new float[] {1, 1, 1, generator.nextFloat() * 0.3f}));
			}
		}
	}

	private void settle(WeatherParticle flake, int index)
	{
		Quadtree tree = scene.getTerrain().tree;
		Quadtree cell = tree.getCell(flake.c, tree.detail);
		float h = cell != null ? cell.getHeight(flake.c) : Integer.MIN_VALUE;
		
		if(flake.c[1] <= h)
		{
			flake.falling = false;
			flake.c[1] = h + 0.01f;
			
			int position = vBuffer.position();
			vBuffer.position(index * 3); vBuffer.put(flake.c);
			vBuffer.position(position);
		}
	}

	private void addParticles()
	{
		if(flakes.size() < flakeLimit - PRECIPITATION_RATE)
		{
			int i = 1 + generator.nextInt(PRECIPITATION_RATE);
			
			while(i > 0)
			{
				float[] source = getSource();
				float[] vector = type == StormType.SNOW ? getRandomVector(1) : getRandomVector(0.25f);
				float alpha = type == StormType.SNOW ? generator.nextFloat() : generator.nextFloat() * 0.40f;
				
				flakes.add(new WeatherParticle(source, vector, alpha));
				
				switch(type)
				{
					case SNOW:
					{
						vBuffer.put(source);
						cBuffer.put(new float[] {1, 1, 1, alpha});
						break;
					}
					case RAIN:
					{
						vBuffer.put(source);
						vBuffer.put(source);
						cBuffer.put(new float[] {1, 1, 1,     0});
						cBuffer.put(new float[] {1, 1, 1, alpha});
						break;
					}
				}
				
				i--;
			}
		}
	}
	
	private float[] getSource()
	{
		float x = generator.nextFloat() * (generator.nextBoolean() ? 150 : -150);
		float y = generator.nextFloat() * (generator.nextBoolean() ?  10 : - 10);
		float z = generator.nextFloat() * (generator.nextBoolean() ? 150 : -150);
		
		return new float[] {x, y + 200, z};
	}
	
	private float[] getRandomVector(float scalar)
	{
		float xVel = (generator.nextBoolean()) ? generator.nextFloat() : -generator.nextFloat();
		float yVel = (generator.nextBoolean()) ? generator.nextFloat() : -generator.nextFloat();
		float zVel = (generator.nextBoolean()) ? generator.nextFloat() : -generator.nextFloat();
		
		return Vector.multiply(new float[] {xVel, yVel, zVel}, scalar);
	}
	
	public void render(GL2 gl)
	{
		switch(type)
		{
			case RAIN: renderRain(gl); break;
			case SNOW: renderSnow(gl); break;
		}
	}
	
	public void renderSnow(GL2 gl)
	{
		gl.glPushMatrix();
		{	
			gl.glDisable(GL2.GL_TEXTURE_2D);
						
			gl.glEnableClientState(GL_VERTEX_ARRAY);
			gl.glEnableClientState(GL_COLOR_ARRAY);
			
			gl.glDisable(GL_LIGHTING);
			gl.glEnable(GL_BLEND);
				
			gl.glEnable(GL2.GL_POINT_SMOOTH);
			gl.glPointSize(6);
			gl.glHint(GL2.GL_POINT_SMOOTH_HINT, GL2.GL_NICEST);
			
			vBuffer.flip();
			cBuffer.flip();
			
			gl.glVertexPointer(3, GL_FLOAT, 0, vBuffer);
			gl.glColorPointer (4, GL_FLOAT, 0, cBuffer);
			gl.glDrawArrays(GL_POINTS, 0, flakes.size());
			
			vBuffer.position(vBuffer.limit()); vBuffer.limit(vBuffer.capacity());
			cBuffer.position(cBuffer.limit()); cBuffer.limit(cBuffer.capacity());
			
			gl.glDisable(GL_BLEND);
			gl.glEnable(GL_LIGHTING);
			
			gl.glDisableClientState(GL_VERTEX_ARRAY);
			gl.glDisableClientState(GL_COLOR_ARRAY);
					
			gl.glEnable(GL2.GL_TEXTURE_2D);
		}
		gl.glPopMatrix();
	}
	
	public void renderRain(GL2 gl)
	{
		gl.glPushMatrix();
		{	
			gl.glDisable(GL2.GL_TEXTURE_2D);
						
			gl.glEnableClientState(GL_VERTEX_ARRAY);
			gl.glEnableClientState(GL_COLOR_ARRAY);
			
			gl.glDisable(GL_LIGHTING);
			gl.glEnable(GL_BLEND);
				
			gl.glEnable(GL2.GL_POINT_SMOOTH);
			gl.glPointSize(6);
			gl.glHint(GL2.GL_POINT_SMOOTH_HINT, GL2.GL_NICEST);
			
			vBuffer.flip();
			cBuffer.flip();
			
//			FloatBuffer vertices = Buffers.newDirectFloatBuffer(flakes.size() * 2 * 3);
//			
//			for(WeatherParticle particle : flakes)
//			{
//				vertices.put(particle.c);
//				vertices.put(particle.getDirectionVector(wind, 4));
//			}
//			vertices.position(0);  
//			
//			FloatBuffer colors = Buffers.newDirectFloatBuffer(flakes.size() * 2 * 4);
//			
//			for(WeatherParticle particle : flakes)
//			{
//				colors.put(new float[] {1, 1, 1, 0.00f});
//				colors.put(new float[] {1, 1, 1, particle.alpha});
//			}
//			colors.position(0);
			
			gl.glVertexPointer(3, GL_FLOAT, 0, vBuffer);
			gl.glColorPointer (4, GL_FLOAT, 0, cBuffer);
//			gl.glVertexPointer(3, GL_FLOAT, 0, vertices);
//			gl.glColorPointer (4, GL_FLOAT, 0, colors);
			gl.glDrawArrays(GL2.GL_LINES, 0, flakes.size() * 2);
			
			vBuffer.position(vBuffer.limit()); vBuffer.limit(vBuffer.capacity());
			cBuffer.position(cBuffer.limit()); cBuffer.limit(cBuffer.capacity());
			
			gl.glDisable(GL_BLEND);
			gl.glEnable(GL_LIGHTING);
			
			gl.glDisableClientState(GL_VERTEX_ARRAY);
			gl.glDisableClientState(GL_COLOR_ARRAY);
					
			gl.glEnable(GL2.GL_TEXTURE_2D);
		}
		gl.glPopMatrix();
	}
}
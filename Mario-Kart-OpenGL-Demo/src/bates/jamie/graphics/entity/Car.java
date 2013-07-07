package bates.jamie.graphics.entity;

import static bates.jamie.graphics.util.Matrix.getRotationMatrix;
import static bates.jamie.graphics.util.Renderer.displayPartiallyTexturedObject;
import static bates.jamie.graphics.util.Vector.add;
import static bates.jamie.graphics.util.Vector.multiply;
import static bates.jamie.graphics.util.Vector.normalize;
import static bates.jamie.graphics.util.Vector.subtract;
import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.asin;
import static java.lang.Math.atan;
import static java.lang.Math.toDegrees;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import bates.jamie.graphics.collision.Bound;
import bates.jamie.graphics.collision.OBB;
import bates.jamie.graphics.collision.Sphere;
import bates.jamie.graphics.io.GamePad;
import bates.jamie.graphics.io.HUD;
import bates.jamie.graphics.io.HoloTag;
import bates.jamie.graphics.item.Banana;
import bates.jamie.graphics.item.BlueShell;
import bates.jamie.graphics.item.FakeItemBox;
import bates.jamie.graphics.item.GreenShell;
import bates.jamie.graphics.item.Item;
import bates.jamie.graphics.item.ItemRoulette;
import bates.jamie.graphics.item.ItemState;
import bates.jamie.graphics.item.RedShell;
import bates.jamie.graphics.item.Shell;
import bates.jamie.graphics.particle.LightningParticle;
import bates.jamie.graphics.particle.ParticleGenerator;
import bates.jamie.graphics.scene.AnchorPoint;
import bates.jamie.graphics.scene.Camera;
import bates.jamie.graphics.scene.Material;
import bates.jamie.graphics.scene.Model;
import bates.jamie.graphics.scene.OBJParser;
import bates.jamie.graphics.scene.Scene;
import bates.jamie.graphics.scene.SceneGraph;
import bates.jamie.graphics.scene.SceneNode;
import bates.jamie.graphics.util.Face;
import bates.jamie.graphics.util.Shader;

/* TODO
 * 
 * Improve vehicle collision response with spheres
 * Driving onto a slope from the side is jittery
 * 
 * Add exhaust particles
 * 
 * Vehicle collisions could be more realistic (focus on top + bottom collisions)
 * Star collisions should be different to normal collisions
 * 
 * Combine heights calculated by height maps and bounding volumes
 * 
 * Lightning Bolts should cause players to drop items
 */

public class Car
{
	/** Model Fields **/
	public  static List<Face> car_faces;
	private static List<Face> wheel_faces;
	private static List<Face> window_faces;
	private static List<Face> door_window_faces;
	private static List<Face> door_faces;
	private static List<Face> base_faces;
	
	private static Model all_windows;
	private static Model car_body;
	private static Model head_lights;
	
	private static final float[] ORIGIN = {0, 1.8f, 0};
	
	private static int carList = -1;
	
	public TerrainPatch patch;
	
	private float[]  color = {1.0f, 0.4f, 0.4f};
	private float[] _color = {1.0f, 0.0f, 0.0f};
	private float[] windowColor = {1.0f, 1.0f, 1.0f};
	
	public boolean displayModel = true;
	public int renderMode = 0;
	
	
	/** Vehicle Fields **/
	public float trajectory; 
	
	private float scale = 1.5f;
	
	private float yRotation_Wheel = 0.0f;
	private float zRotation_Wheel = 0.0f;
	
	private float[][] offsets_Wheel =
		{{ 2.4f, -0.75f,  1.75f},  //back-left
		 {-2.4f, -0.75f,  1.75f},  //front-left
		 { 2.4f, -0.75f, -1.75f},  //back-right
		 {-2.4f, -0.75f, -1.75f}}; //front-right
	
	private float[] offsets_LeftDoor =  {-1.43f, 0.21f,  1.74f};
	private float[] offsets_RightDoor = {-1.43f, 0.21f, -1.74f};
	
	
	/** Motion Fields **/
	public float velocity = 0;
	public static final float TOP_SPEED = 1.2f;
	public float acceleration = 0.012f;
	public boolean accelerating = false;
	public boolean reversing = false;
	public boolean invertReverse = false;
	public float friction = 1;
	
	public float gravity = 0.05f;
	public boolean falling = false;
	private float fallRate = 0.0f;
	private static final float TOP_FALL_RATE = 2.5f;
	
	public float turnRate = 0;
	private static final float TOP_TURN_RATE = 2.0f;
	private float turnIncrement = 0.1f;
	public boolean turning = false;
	
	private enum Direction {STRAIGHT, LEFT, RIGHT};
	private Direction direction = Direction.STRAIGHT;
	
	private Direction drift = Direction.STRAIGHT;
	private boolean driftCounter = false;
	private enum DriftState {YELLOW, RED, BLUE};
	private DriftState driftState = DriftState.YELLOW;
	
	public float distance = 0;
	
	private AnchorPoint anchor;
	private Motion motionMode = Motion.DEFAULT;
	
	
	/** Collision Detection Fields **/
	public OBB bound;
	public boolean colliding = false;
	public List<Bound> collisions = new ArrayList<Bound>();
	public float[] heights = {0, 0, 0, 0};
	
	
	/** Particle Fields **/
	private ParticleGenerator generator = new ParticleGenerator();
	
	
	/** Item Fields **/
	private ItemRoulette roulette = new ItemRoulette();
	private ItemState itemState = ItemState.NO_ITEM;
	private Queue<Item> items = new LinkedList<Item>();
	private Queue<Integer> itemCommands = new ArrayBlockingQueue<Integer>(100);
	
	private boolean slipping = false;
	private float[] slipVector = ORIGIN;
	public float slipTrajectory = 0;
	private int slipDuration = 0;
	
	private boolean miniature = false;
	private int miniatureDuration = 0;
	
	private boolean cursed = false;
	private int curseDuration = 0;
	
	private boolean boosting = false;
	private boolean superBoosting = false;
	private int boostDuration = 0;
	
	private boolean starPower = false;
	private int starDuration = 0;
	private float[] starColor = {255, 0, 0};
	private int spectrum = 0;
	private boolean whiten = true;
	private static final int COLOR_INCREMENT = 17; //1, 3, 5, 15, 17, 51, 85, 255
	
	private boolean invisible = false;
	private int booDuration = 0;
	private float booColor = 0.5f;
	private float fadeIncrement = 0.01f;
	
	public int itemDuration = 0;
	
	private enum Aim {FORWARDS, DEFAULT, BACKWARDS};
	private Aim aiming = Aim.DEFAULT;
	
	/** Scene Fields **/
	private Scene scene;
	public Camera camera;
	
	/** Controller Fields **/
	private GamePad controller;
	
	private HUD hud;
	private HoloTag tag;
	
	public boolean smooth = false;
	
	public SceneGraph high_graph;
	public SceneGraph low_graph;
	
	public boolean high_quality = true;
	
	
	public Car(GL2 gl, float[] c, float xrot, float yrot, float zrot, Scene scene)
	{
		if(carList == -1)
		{
			if(scene.enableQuality)
			{
				car_faces         = OBJParser.parseTriangles("car");
				window_faces      = OBJParser.parseTriangles("windows");
				door_window_faces = OBJParser.parseTriangles("door_windows");
				door_faces        = OBJParser.parseTriangles("door");
				
				carList = gl.glGenLists(1);
				gl.glNewList(carList, GL2.GL_COMPILE);
				displayPartiallyTexturedObject(gl, car_faces, color);
				gl.glEndList();
			}
			
			wheel_faces       = OBJParser.parseTriangles("wheel");
			base_faces        = OBJParser.parseTriangles("car_base");
			
			all_windows = OBJParser.parseTriangleMesh("windows_all");
			car_body    = OBJParser.parseTriangleMesh("car_body");
			head_lights = OBJParser.parseTriangleMesh("head_lights");
		}
	    
	    bound = new OBB(
				c[0], c[1], c[2],
	    		xrot, yrot, zrot,
	    		5.5f, 2.0f, 2.7f);
	    
	    trajectory = yrot;
	    
	    this.scene = scene; 
	    camera = new Camera();
	    
	    controller = new GamePad();
	    
	    hud = new HUD(scene, this);
	    tag = new HoloTag(String.format("(%+3.2f, %+3.2f, %+3.2f)", c[0], c[1], c[2]), c);
	    
	    anchor = new AnchorPoint();
	    
	    resetGraph();
	}
	
	public boolean enableChrome = true;
	
	public void setupHighGraph()
	{
		Material shiny = new Material(new float[] {1, 1, 1});
		Material mat = new Material(new float[] {0, 0, 0});
		
		SceneNode body = new SceneNode(null, carList, car_body, SceneNode.MatrixOrder.T_M_S, shiny);
		body.setColor(color);
		body.setTranslation(bound.c);
		body.setOrientation(getRotationMatrix(bound.u));
		body.setScale(new float[] {scale, scale, scale});
		
		if(enableChrome)
		{
			body.setRenderMode(SceneNode.RenderMode.REFLECT);
			body.setReflector(scene.reflector);
		}
		else body.setRenderMode(SceneNode.RenderMode.COLOR);
		
		SceneNode headlights = new SceneNode(null, carList, head_lights, SceneNode.MatrixOrder.T, shiny);
		headlights.setColor(new float[] {0.6f, 0.6f, 1.0f});
		headlights.setTranslation(new float[] {0.2925f, 0, 0});
		headlights.setRenderMode(SceneNode.RenderMode.COLOR);
		
		body.addChild(headlights);
		
		SceneNode car_base = new SceneNode(base_faces, -1, null, SceneNode.MatrixOrder.T, shiny);
		car_base.setColor(new float[] {1, 1, 1});
		car_base.setTranslation(new float[] {0.2925f, 0, 0});
		car_base.setRenderMode(SceneNode.RenderMode.TEXTURE);
		
		body.addChild(car_base);
		
		for(int i = 0; i < 4; i++)
		{
			SceneNode wheel = new SceneNode(wheel_faces, -1, null, SceneNode.MatrixOrder.T_RX_RY_RZ_S, mat);
			wheel.setColor(new float[] {1, 1, 1});
			wheel.setRenderMode(SceneNode.RenderMode.TEXTURE);
			wheel.setTranslation(offsets_Wheel[i]);
			wheel.setRotation(ORIGIN);
			wheel.setScale(new float[] {0.6f, 0.6f, 0.6f});
			
			body.addChild(wheel);
		}
		
		SceneNode windows = new SceneNode(null, -1, all_windows, SceneNode.MatrixOrder.NONE, shiny);
		windows.setColor(windowColor);
		windows.setRenderMode(SceneNode.RenderMode.GLASS);
		
		body.addChild(windows);
		
		high_graph = new SceneGraph(body);
	}
	
	public void setupLowGraph()
	{
		Material shiny = new Material(new float[] {1, 1, 1});
		Material mat = new Material(new float[] {0, 0, 0});
		
		SceneNode car_body = new SceneNode(car_faces, carList, null, SceneNode.MatrixOrder.T_M_S, shiny);
		car_body.setColor(new float[] {1, 1, 1});
		car_body.setTranslation(bound.c);
		car_body.setOrientation(getRotationMatrix(bound.u));
		car_body.setRenderMode(SceneNode.RenderMode.TEXTURE);
		car_body.setScale(new float[] {scale, scale, scale});
		
		for(int i = 0; i < 4; i++)
		{
			SceneNode wheel = new SceneNode(wheel_faces, -1, null, SceneNode.MatrixOrder.T_RX_RY_RZ_S, mat);
			wheel.setColor(new float[] {1, 1, 1});
			wheel.setRenderMode(SceneNode.RenderMode.TEXTURE);
			wheel.setTranslation(offsets_Wheel[i]);
			wheel.setRotation(ORIGIN);
			wheel.setScale(new float[] {0.6f, 0.6f, 0.6f});
			
			car_body.addChild(wheel);
		}
		
		SceneNode left_door = new SceneNode(door_faces, -1, null, SceneNode.MatrixOrder.T_S, shiny);
		left_door.setColor(color);
		left_door.setRenderMode(SceneNode.RenderMode.COLOR);
		left_door.setTranslation(offsets_LeftDoor);
		left_door.setScale(new float[] {1, 1, 1});
		
		SceneNode left_window = new SceneNode(door_window_faces, -1, null, SceneNode.MatrixOrder.NONE, null);
		left_window.setColor(windowColor);
		left_window.setRenderMode(SceneNode.RenderMode.GLASS);
			
		left_door.addChild(left_window);
		car_body.addChild(left_door);
		
		SceneNode right_door = new SceneNode(door_faces, -1, null, SceneNode.MatrixOrder.T_S, shiny);
		right_door.setColor(color);
		right_door.setRenderMode(SceneNode.RenderMode.COLOR);
		right_door.setTranslation(offsets_RightDoor);
		right_door.setScale(new float[] {1, 1, -1});
		
		SceneNode right_window = new SceneNode(door_window_faces, -1, null, SceneNode.MatrixOrder.NONE, null);
		right_window.setColor(windowColor);
		right_window.setRenderMode(SceneNode.RenderMode.GLASS);
			
		right_door.addChild(right_window);
		car_body.addChild(right_door);
		
		SceneNode windows = new SceneNode(window_faces, -1, null, SceneNode.MatrixOrder.T, null);
		windows.setColor(windowColor);
		windows.setRenderMode(SceneNode.RenderMode.GLASS);
		windows.setTranslation(new float[] {0.3f, -1.2f, 0});
		
		car_body.addChild(windows);
		
		low_graph = new SceneGraph(car_body);
	}
	
	public void updateGraph(SceneGraph graph)
	{
		SceneNode car_body = graph.getRoot();
		
		car_body.setTranslation(bound.c);
		car_body.setOrientation(getRotationMatrix(bound.u));
		
		for(int i = 0; i < 4; i++)
		{
			if(i % 2 != 0)
			{
				SceneNode wheel = car_body.getChildren().get(i);
				wheel.setRotation(new float[] {0, yRotation_Wheel, zRotation_Wheel});
			}
		}
	}
	
	public HUD getHUD() { return hud; }
	
	public Bound getBound() { return bound; }
	
	public void collide(Car car) { collisions.add(car.getBound()); }
	
	public Queue<Item> getItems() { return items; }
	
	public Queue<Integer> getItemCommands() { return itemCommands; }
	
	private void useItem()
	{
		if(hasItem())
		{
			pressItem();
			if(!roulette.secondary) roulette.update();
		}
		
		else if(roulette.hasItem())
		{
			int itemID = roulette.getItem();
			roulette.secondary = false;
			ItemState state = ItemState.get(itemID);
			setItemState(state);
			
			itemCommands.add(itemID);
			
			if(ItemState.isTimed(state)) roulette.setTimer();
			
			if(ItemState.isInstantUse(state)) pressItem();
			roulette.update();
		}
	}
	
	public ItemRoulette getRoulette() { return roulette; }
	
	public float[][] getRotation() { return bound.u; }
	
	public void aimForwards()  { aiming = Aim.FORWARDS;  }
	
	public void aimBackwards() { aiming = Aim.BACKWARDS; }
	
	public void resetAim()     { aiming = Aim.DEFAULT;   }
	
	public void removeItems()
	{
		for(int i = 0; i < Item.removeItems(items); i++)
		{
			itemState = ItemState.press(itemState);
			itemState = ItemState.release(itemState);
		}
	}
	
	public boolean hasItem() { return itemState != ItemState.NO_ITEM; }

	public ItemState getItemState() { return itemState; }
		
	public void setItemState(ItemState state) { this.itemState = state; }
	
	public void registerItem(GL2 gl, int itemID)
	{
		switch(itemID)
		{
			case  0: items.add(new GreenShell(gl, scene, this, trajectory, false)); break;
			case  1:
			{
				items.add(new GreenShell(gl, scene, this, trajectory,       true));
				items.add(new GreenShell(gl, scene, this, trajectory + 120, true));
				items.add(new GreenShell(gl, scene, this, trajectory - 120, true));

				break;
			}
			case  2: items.add(new RedShell(gl, scene, this, trajectory, false)); break;
			case  3:
			{
				items.add(new RedShell(gl, scene, this, trajectory,       true));
				items.add(new RedShell(gl, scene, this, trajectory + 120, true));
				items.add(new RedShell(gl, scene, this, trajectory - 120, true));

				break;
			}
			case  6: itemDuration = 400; break;
			case  7: items.add(new FakeItemBox(gl, scene, this)); break;
			case  8: items.add(new Banana(gl, scene, this, 1)); break;
			case  9:
			{
				items.add(new Banana(gl, scene, this, 3));
				items.add(new Banana(gl, scene, this, 2));
				items.add(new Banana(gl, scene, this, 1));
				break;
			}
			case 13:
			{
				BlueShell shell = new BlueShell(gl, scene, this, trajectory);
					
				shell.throwUpwards();
					
				scene.addItem(shell);
				break;
			}
			default: break;
		}
	}
	
	public void pressItem()
	{
		switch(itemState)
		{
			case THREE_ORBITING_GREEN_SHELLS:
			case TWO_ORBITING_GREEN_SHELLS:
			case ONE_ORBITING_GREEN_SHELL:
			case THREE_ORBITING_RED_SHELLS:
			case TWO_ORBITING_RED_SHELLS:
			case ONE_ORBITING_RED_SHELL:
			{			
				Shell shell = (Shell) items.remove();
					
				switch(aiming)
				{
					case FORWARDS:
					case DEFAULT:   shell.throwForwards();  break;
					case BACKWARDS: shell.throwBackwards(); break;
				}
					
				scene.addItem(shell);
				break;
			}
			
			case ONE_MUSHROOM:
			case TWO_MUSHROOMS:
			case THREE_MUSHROOMS: boost(); break;

			case GOLDEN_MUSHROOM: superBoosting = true; break;
			
			case ONE_BANANA:
			case TWO_BANANAS:
			case THREE_BANANAS:
			{
				Banana banana = (Banana) items.remove();
					
				switch(aiming)
				{
					case FORWARDS: banana.throwUpwards(); break;
					default: break;
				}
					
				scene.addItem(banana);
				break;
			}
			
			case LIGHTNING_BOLT: useLightningBolt(); break;
			case POWER_STAR: usePowerStar(); break;
			case BOO: useBoo(); break;
			
			default: break;
		}
		
		itemState = ItemState.press(itemState);
	}

	public void releaseItem()
	{
		switch(itemState)
		{
			case HOLDING_GREEN_SHELL:
			case HOLDING_RED_SHELL:
			{			
				Shell shell = (Shell) items.remove();
				
				switch(aiming)
				{
					case FORWARDS:
					case DEFAULT:   shell.throwForwards();  break;
					case BACKWARDS: shell.throwBackwards(); break;
				}
					
				scene.addItem(shell);
				break;
			}
			
			case FAKE_ITEM_BOX:
			case HOLDING_BANANA:
			{
				Item item = items.remove();
					
				switch(aiming)
				{
					case FORWARDS: item.throwUpwards(); break;
					default: break;
				}
						
				scene.addItem(item);
				break;
			}

			case GOLDEN_MUSHROOM: superBoosting = false; break;
			
			default: break;
		}
		
		itemState = ItemState.release(itemState);
	}
	
	public float getThrowVelocity() { return TOP_SPEED * 1.5f + abs(velocity); }

	public void setRotation(float x, float y, float z) { bound.u = getRotationMatrix(x, y, z); }
	
	public float[] getForwardVector() { return multiply(bound.u[0], velocity); }
	
	public float[] getSlipVector() { return subtract(bound.c, multiply(slipVector, velocity)); }
	
	public void setRotation(float[] angles) { bound.u = getRotationMatrix(angles[0], angles[1], angles[2]); }
	
	public void setPosition(float[] c) { bound.setPosition(c); }
	
	public float[] getPosition() { return bound.getPosition(); }
	
	public float[] getColor() { return color; }
	
	public void setColor(float[] color)
	{
		this.color = color;
		resetGraph();
	}
	
	public void resetGraph()
	{
		setupHighGraph();
	    if(scene.enableQuality) setupLowGraph();
	}
	
	public boolean enableAberration = true;
	public float opacity = 0.25f;

	public void render(GL2 gl)
	{
		updateColor();
		
		if(renderMode == 1)
		{
			if(smooth)
			{
				gl.glEnable(GL2.GL_BLEND);
				gl.glEnable(GL2.GL_LINE_SMOOTH);
			}
			
			gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_LINE);
		}
		
		if(displayModel)
		{	
			SceneGraph graph = high_quality ? high_graph : low_graph;
			
			if(invisible)
			{
				String name = enableAberration ? "aberration" : "ghost";
				Shader shader = Shader.enabled ? Scene.shaders.get(name) : null;
				
				if(shader != null)
				{
					shader.enable(gl);
					float fade = opacity + (1 - opacity) * (booColor * 2);
					shader.setUniform(gl, "opacity", fade);
					Shader.disable(gl);
				}

				graph.renderGhost(gl, booColor, shader);
			}
			else if(starPower) graph.renderColor(gl, _color);
			else graph.render(gl);
		}
		
		gl.glColor3f(1, 1, 1);
		
		for(Item item : items)
		{
			if(Item.renderMode == 1) item.renderWireframe(gl, trajectory);
			else item.render(gl, trajectory);
		}
		
		gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_FILL);
		
		gl.glDisable(GL2.GL_BLEND);
		gl.glDisable(GL2.GL_LINE_SMOOTH);
		
		if(displayTag && !camera.isFirstPerson())
		{
			float ry = slipping ? slipTrajectory : trajectory; 
			if(camera.isFree()) ry = camera.getRotation()[1];
			
			tag.render(gl, ry);
		}
	}
	
	public boolean displayTag = true;

	private void updateColor()
	{
		if(starPower)
		{
			cycleColor();
			_color = new float[] {starColor[0]/255, starColor[1]/255, starColor[2]/255};
		}
		if(invisible)
		{
			if(booColor > 0 && booDuration > 0) booColor -= fadeIncrement;
		}
	}

	private void cycleColor()
	{
		if(starColor[0] == 255 && starColor[1] == 255 && starColor[2] == 255) whiten = false;
		
		if(whiten)
		{
			if(starColor[0] < 255) starColor[0] += COLOR_INCREMENT;
			if(starColor[1] < 255) starColor[1] += COLOR_INCREMENT;
			if(starColor[2] < 255) starColor[2] += COLOR_INCREMENT;
		}
		else
		{
			switch(spectrum)
			{
				case 0: if(starColor[0] == 255 && starColor[1] ==   0 && starColor[2] ==   0) { whiten = true; spectrum++; }
				else { starColor[1] -= COLOR_INCREMENT; starColor[2] -= COLOR_INCREMENT; } break;
				
				case 1: if(starColor[0] == 255 && starColor[1] == 255 && starColor[2] ==   0) { whiten = true; spectrum++; }
				else { starColor[2] -= COLOR_INCREMENT; } break;
				
				case 2: if(starColor[0] ==   0 && starColor[1] == 255 && starColor[2] ==   0) { whiten = true; spectrum++; }
				else { starColor[0] -= COLOR_INCREMENT; starColor[2] -= COLOR_INCREMENT; } break;
				
				case 3: if(starColor[0] ==   0 && starColor[1] == 255 && starColor[2] == 255) { whiten = true; spectrum++; }
				else { starColor[0] -= COLOR_INCREMENT; } break;
				
				case 4: if(starColor[0] ==   0 && starColor[1] ==   0 && starColor[2] == 255) { whiten = true; spectrum++; }
				else { starColor[0] -= COLOR_INCREMENT; starColor[1] -= COLOR_INCREMENT; } break;
				
				case 5: if(starColor[0] == 255 && starColor[1] ==   0 && starColor[2] == 255) { whiten = true; spectrum++; }
				else { starColor[1] -= COLOR_INCREMENT; } break;
			}
		}
		
		spectrum = spectrum % 6;
	}

	public long renderHUD(GL2 gl, GLU glu) { return hud.render(gl, glu); }

	public float[] getHeights()
	{
		falling = true;
		
		float[] _heights = heights;
		heights = new float[] {0, 0, 0, 0};
		
		for(Bound collision : collisions)
		{
			if(collision instanceof OBB) setHeights((OBB) collision);
		}
		
		if(collisions.isEmpty()) heights = _heights;
		
		return heights;
	}
	
	public float[] getHeights(Quadtree tree, int lod)
	{
		float[][] vertices = bound.getVertices();
		
		long start = System.nanoTime();
		
		for(int i = 0; i < 4; i++)
		{
			Quadtree cell = tree.getCell(vertices[i], lod);
			heights[i] = (cell != null) ? cell.getHeight(vertices[i]) : 0;
		}
		
		if(accelerating)
		{	
			float ratio = abs(velocity / TOP_SPEED);
			float k = ratio > 0.5 ? 0.5f : 1 - ratio;
			      k = ratio < 0.1 ? 0.1f : k;
			float depression = k * 0.05f;
			
			for(int i = 0; i < 4; i++)
			{
				Quadtree cell = tree.getCell(vertices[i], Quadtree.MAXIMUM_LOD);
				if(cell != null) cell.subdivide();
				tree.deform(vertices[i], 1.5f, -depression);
			}
		}
		
		scene.updateTimes[scene.frameIndex][2] = System.nanoTime() - start;
		scene.updateTimes[scene.frameIndex][3] = 0;
		
		float h = (heights[0] + heights[1] + heights[2] + heights[3]) / 4;
		h += bound.e[1];
		bound.c[1] = h;
		
		return heights;
	}
	
	public float[] getHeights(Collection<Quadtree> trees)
	{
		float[][] vertices = bound.getVertices();
		
		long start = System.nanoTime();
		
		Quadtree[] _trees = new Quadtree[4];
		
		for(int i = 0; i < 4; i++)
		{
			float max = Integer.MIN_VALUE;
			
			for(Quadtree tree : trees)
			{
				Quadtree cell = tree.getCell(vertices[i], tree.detail);
				float h = (cell != null) ? cell.getHeight(vertices[i]) : 0;
				if(h > max && !tree.enableBlending) { max = h; _trees[i] = tree; }
			}

			heights[i] = max;
		}
		
		if(accelerating)
		{		
			float ratio = abs(velocity / TOP_SPEED);
			float k = ratio > 0.5 ? 0.5f : 1 - ratio;
			      k = ratio < 0.1 ? 0.1f : k;
			float depression = k * 0.05f;
			
			for(int i = 0; i < 4; i++)
			{
				Quadtree cell = _trees[i].getCell(vertices[i], Quadtree.MAXIMUM_LOD);
				if(cell != null) cell.subdivide();
				_trees[i].deform(vertices[i], 1.5f, -depression);
			}
		}
		
		scene.updateTimes[scene.frameIndex][2] = System.nanoTime() - start;
		scene.updateTimes[scene.frameIndex][3] = 0;
		
		float h = (heights[0] + heights[1] + heights[2] + heights[3]) / 4;
		h += bound.e[1];
		bound.c[1] = h;
		
		return heights;
	}
	
	public float[] getHeights(Terrain map)
	{
		if(map.enableQuadtree) return getHeights(map.trees.values());
		
		float[][] vertices = bound.getVertices();

		for(int i = 0; i < 4; i++)
		{
			float h = map.getHeight(vertices[i]);
			heights[i] = h;
		}
		
		float h = (heights[0] + heights[1] + heights[2] + heights[3]) / 4;
		h += bound.e[1];
		bound.c[1] = h;
		
		return heights;
	}
	
	// TODO Gravity causes car to pass through surfaces (example: bridges)

	/**
	 * This method calculates the height of the vehicles as determined by the OBB
	 * passed as a parameter; note that it is assumed that the vehicle is colliding
	 * with the top of the OBB (see resolveOBB(OBB) for side and bottom collisions)
	 */
	private void setHeights(OBB obb)
	{
		float[] face = obb.getFaceVector(getPosition());

		//if the side of collision is the upwards face
		if(Arrays.equals(face, obb.getUpVector(1)))
		{
			float[][] vertices = bound.getVertices();

			//calculate the height at each wheel
			for(int i = 0; i < 4; i++)
			{
				float h = obb.closestPointOnPerimeter(vertices[i])[1];
				if(h > heights[i]) heights[i] = h;
			}

			//calculate the height at the centre of the vehicle
			float h = obb.closestPointOnPerimeter(getPosition())[1]
					+ (bound.e[1] * 0.99f);
			
			if(h > bound.c[1])
			{
				bound.c[1] = h;
				
				if(motionMode == Motion.ANCHOR)
				{
					float[] p = anchor.getPosition();
					anchor.setPosition(new float[] {p[0], h, p[2]});
				}
			}

			falling = false; fallRate = 0; //disable falling
		}
	}
	
	public float[] getRotationAngles(float[] h)
	{
		float frontHeight = (h[0] + h[1]) / 2; 
		float backHeight  = (h[2] + h[3]) / 2;
		float leftHeight  = (h[1] + h[3]) / 2;
		float rightHeight = (h[0] + h[2]) / 2;
	
		float xrot = (float) -toDegrees(atan((leftHeight - rightHeight) / (bound.e[2] * 2)));
		float zrot = (float) -toDegrees(atan((frontHeight - backHeight) / (bound.e[0] * 2)));
		
		return new float[] {xrot, trajectory, zrot};
	}

	public void detectCollisions()
	{
		colliding = false;
		
		collisions.clear();
		
		for(Bound collision : scene.getBounds())
		{
			if(bound.testBound(collision))
			{
				colliding = true;
				collisions.add(collision);
			}
		}
		
		//TODO car-car collisions could be improved
		for(Car car : scene.getCars())
		{
			if(!car.equals(this) && !invisible && !car.isInvisible() &&
					bound.testBound(car.getBound()))
			{
				colliding = true;
				collisions.add(car.getBound());
			}
		}
	}

	private void resolveCollisions()
	{
		List<float[]> vectors = new ArrayList<float[]>();
		
		for(Bound collision : collisions)
		{
			if     (collision instanceof OBB   ) vectors.add(resolveOBB   ((OBB)    collision));
			else if(collision instanceof Sphere) vectors.add(resolveSphere((Sphere) collision));
		}
		
		for(float[] vector : vectors) setPosition(add(getPosition(), vector));
		
		if(motionMode == Motion.ANCHOR) anchor.setPosition(getPosition());
	}

	/*
	 * TODO
	 * 
	 * There are currently no obstacles in the scene that use a sphere as its
	 * bounding geometry; therefore, this method is not used and may be inaccurate
	 * 
	 */
	private float[] resolveSphere(Sphere sphere)
	{
		float[] face = sphere.getFaceVector(getPosition());
		face[1] = 0;

		float s = this.bound.getPenetration(sphere);

		velocity *= (slipping) ? 0 : 0.9;

		return multiply((normalize(subtract(face, sphere.getPosition()))), s);
	}

	/**
	 * This method resolves a collision with an OBB passed as a parameter;
	 * note that it is assumed that the vehicle is colliding with the side or
	 * bottom of the OBB (see setHeights(OBB) for top collisions) 
	 */
	private float[] resolveOBB(OBB obb)
	{
		float[] face = obb.getFaceVector(getPosition());
		
		/*
		 * the vehicles must be colliding with the side or bottom of the OBB
		 * the face must be a valid collision (not all sides are considered)
		 * the bottom of the vehicle must be lower than the top of the OBB
		 */
		if(!Arrays.equals(face, obb.getUpVector(1)) && obb.isValidCollision(face)
		   && (getPosition()[1] - bound.e[1]) < (obb.c[1] + obb.e[1]))
		{
			float p = bound.getPenetration(obb);
			
			if(slipping) velocity = 0;
			else velocity *= 0.9;
			
			return multiply(subtract(face, obb.getPosition()), p);
		}
		else return new float[] {0, 0, 0};
	}

	public void update()
	{	
		if(scene.enableTerrain) getHeights(scene.getTerrain());
		else getHeights();
		
		if(motionMode == Motion.ANCHOR)
		{
			setRotation(anchor.getRotation());
			trajectory = anchor.getRotation()[1];
		}
		else setRotation(getRotationAngles(heights));
		
		if(motionMode == Motion.ANCHOR) setPosition(anchor.getPosition());
		else if(!slipping) setPosition(getPositionVector());
		else setPosition(getSlipVector());
		
		detectCollisions();
		if(colliding) resolveCollisions();
		
		if(superBoosting && !slipping) boost();
		
		if(accelerating && !slipping) accelerate();
		else decelerate();
		
		if(velocity <= 0 || slipping)
		{
			drift = Direction.STRAIGHT;
			driftState = DriftState.YELLOW;
			driftCounter = false;
		}
		
		if     (drift == Direction.LEFT ) turnLeft();
		else if(drift == Direction.RIGHT) turnRight();
		
		else if(turning && !slipping)
		{
			if(reversing && invertReverse)
			{
				if(direction == Direction.RIGHT) turnLeft();
				else turnRight();
			}
			else
			{
				if(direction == Direction.LEFT ) turnLeft();
				else turnRight();
			}
		}
		
		else stabilize();
		
		float currentDistance = distance;
		
		if(falling && motionMode != Motion.ANCHOR) fall();
	
		velocity = (velocity > 2 * TOP_SPEED) ? (2 * TOP_SPEED) : velocity;
		
		distance += velocity;
		
		turnWheels();
		
		//The wheels are rotated in relation to the distance travelled
		zRotation_Wheel += 360 * (distance - currentDistance) / (2 * PI * 0.5); //0.5 is the wheel radius
		
		if(drift != Direction.STRAIGHT && !falling)
		{
			for(float[] source : getDriftVectors())
			{
				scene.addParticles(generator.generateDriftParticles(source, 10, driftState.ordinal(), miniature));
				scene.addParticles(generator.generateSparkParticles(source,  2, miniature));
			}
		}
		
		if(scene.enableTerrain && !scene.getTerrain().enableQuadtree && patch != null && velocity != 0)
		{
			for(float[] source : getDriftVectors())
			{
				scene.addParticles(generator.generateTerrainParticles(source, 15, patch.texture));
			}
		}
		
		float[] p = {bound.c[0], bound.c[1] + 5, bound.c[2]};
		
		tag.setPosition(p);
		tag.displayPosition();
		
		if(high_quality) updateGraph(high_graph);
		else updateGraph(low_graph);
		
		updateStatus();
		
		updateController();
	}
	
	public void updateController()
	{
		if(!controller.isNull() && controller.isEnabled() && !camera.isFree())
		{
			if(motionMode == Motion.ANCHOR)
			{
				controller.update();
				anchor.update(controller);
			}
			else if(controller.getXAxis() > (isDrifting() ?  0.9f : 0)) steerLeft();
			else if(controller.getXAxis() < (isDrifting() ? -0.9f : 0)) steerRight();
			else 
			{
				turning = false;
				straighten();
			}
		}
		
		if(!controller.isNull())
		{
			controller.update();
			
			while(!controller.getPressEvents().isEmpty()) buttonPressed(controller.getPressEvents().poll());
			while(!controller.getReleaseEvents().isEmpty()) buttonReleased(controller.getReleaseEvents().poll());
		}
	}

	/**
	 * This method updates the status effects currently inflicted on the player;
	 * these effects are caused by using or collising with items certain items
	 */
	private void updateStatus()
	{
		if(miniatureDuration > 0) miniatureDuration--;
		else if(miniature)
		{
			miniature = false;
			bound.e = multiply(bound.e, 2);
			scale *= 2;
		}
		
		if(boostDuration > 0) boostDuration--;
		else boosting = false;
		
		if(boosting)
		{
			for(float[] source : getBoostVectors())
				scene.addParticles(generator.generateBoostParticles(source, boostDuration / 4, superBoosting, miniature));
		}
		
		if(curseDuration > 0) curseDuration--;
		else cursed = false;
		
		if(cursed)
			scene.addParticles(generator.generateFakeItemBoxParticles(getPosition(), 2, miniature));
		
		if(slipDuration > 0) slipDuration--;
		else slipping = false;
		
		if(slipping) trajectory += 15;
		
		if(starDuration > 0) starDuration--;
		else
		{
			starPower = false;
			turnIncrement = 0.1f;
		}
		
		if(starPower)
			scene.addParticles(generator.generateStarParticles(getPosition(), 2, miniature));
		
		if(booDuration > 0) booDuration--;
		else if(booColor < 0.5f) booColor += 0.0125f;
		else invisible = false;
		
		if(itemDuration > 0) itemDuration--;
		else if(ItemState.isTimed(itemState))
		{
			superBoosting = false;
			itemState = ItemState.NO_ITEM;
		}
	}

	private void fall()
	{
		if(fallRate < TOP_FALL_RATE) fallRate += gravity;
		bound.c[1] -= fallRate;
	}
	
	public void drift() { drift = direction; }
	
	public void miniTurbo()
	{
		drift = Direction.STRAIGHT;
		
		if(driftState == DriftState.BLUE)
		{
			boosting = true;
			boostDuration = 20;
			velocity += 0.6;
			
			velocity = (velocity > 2 * TOP_SPEED) ? (2 * TOP_SPEED) : velocity; 
		}
		
		driftState = DriftState.YELLOW;
	}

	/**
	 * Increase the speed of the car unless it is at its top speed
	 */
	public void accelerate()
	{
		if(reversing) velocity += (velocity < -TOP_SPEED) ? acceleration : (velocity > 0 ? -acceleration * 2 : -acceleration);
		else velocity += (velocity < TOP_SPEED) ? (velocity < 0 ? acceleration * 2 : acceleration) : -acceleration;
	}

	/**
	 * Decrease the speed of the car until it comes to a standstill
	 */
	public void decelerate()
	{
		if(velocity > acceleration) velocity -= acceleration;
		else if(velocity < 0) velocity += acceleration;
		else velocity = 0;
	}

	public void steerLeft()
	{
		turning = true;
		direction = Direction.LEFT;
		
		if(!falling)
		{
			if(drift == Direction.RIGHT) driftCounter = true;
			else if(drift == Direction.LEFT && driftCounter && driftState != DriftState.BLUE)
			{
				driftState = DriftState.values()[driftState.ordinal() + 1];
				driftCounter = false;
			}
		}
	}

	public void steerRight()
	{
		turning = true;
		direction = Direction.RIGHT;
		
		if(!falling)
		{
			if(drift == Direction.LEFT) driftCounter = true;
			else if(drift == Direction.RIGHT && driftCounter && driftState != DriftState.BLUE)
			{
				driftState = DriftState.values()[driftState.ordinal() + 1];
				driftCounter = false;
			}
		}
	}

	private void turnLeft()
	{
		if(!controller.isEnabled() || controller.getXAxis() <= 0)
		{
			if(turnRate < TOP_TURN_RATE) turnRate += turnIncrement;
		}
		else
		{
			if(turnRate < TOP_TURN_RATE * controller.getXAxis()) turnRate += turnIncrement;
		}
		
		float k = 1; 
		
		if(drift == Direction.LEFT)
		{
			if(direction == Direction.LEFT) k = 1.25f;
			else if(direction == Direction.RIGHT) k = 0.5f;
		}
		
		if(velocity != 0) trajectory += turnRate * k;
	}

	private void turnRight()
	{
		if(!controller.isEnabled() || controller.getXAxis() >= 0)
		{
			if(turnRate > -TOP_TURN_RATE) turnRate -= turnIncrement;
		}
		else
		{
			if(turnRate > TOP_TURN_RATE * controller.getXAxis()) turnRate -= turnIncrement;
		}
		
		float k = 1;
		
		if(drift == Direction.RIGHT)
		{
			if(direction == Direction.LEFT) k = 0.5f;
			else if(direction == Direction.RIGHT) k = 1.25f;
		}
		
		if(velocity != 0) trajectory += turnRate * k;
	}
	
	public void straighten() { direction = Direction.STRAIGHT; }

	public void stabilize()
	{
		if(turnRate > turnIncrement) turnRate -= turnIncrement;
		else if(turnRate < 0) turnRate += turnIncrement;
		else turnRate = 0;
		
		trajectory += turnRate * (velocity != 0 ? 1 : 0);
	}

	public void turnWheels()
	{
		float turnRatio = turnRate / TOP_TURN_RATE;
	
		if(turnRatio > 1) turnRatio = 1;
		else if(turnRatio < -1) turnRatio = -1;
	
		yRotation_Wheel = (float) (toDegrees(asin(turnRatio)) / 2);
		if(velocity < 0) yRotation_Wheel = -yRotation_Wheel;
	}

	public float[] getPositionVector()
	{
		float _velocity = (miniature) ?  velocity * 0.75f :  velocity;
		      _velocity = (starPower) ? _velocity * 1.25f : _velocity;
		
		return subtract(bound.c, multiply(bound.u[0], _velocity * friction));
	}

	public float[][] getBoostVectors()
	{	
		float[] eu0 = multiply(bound.u[0], bound.e[0]);
		float[] eu2 = multiply(bound.u[2], bound.e[2] * 0.75f);
		
		return new float[][]
		{
			subtract(add(getPosition(), eu0), eu2), //right exhaust
			     add(add(getPosition(), eu0), eu2)  //left exhaust
		};
	}
	
	public float[][] getLightVectors()
	{	
		float[] eu0 = multiply(bound.u[0], bound.e[0]);
		
		float[][] boost = getBoostVectors();
		
		return new float[][]
		{
			subtract(getPosition(), eu0),
			subtract(bound.c, boost[0])
		};
	}
	
	public float[][] getDriftVectors()
	{
		float[] eu0 = multiply(bound.u[0], bound.e[0] * 0.75f);
		float[] eu1 = multiply(bound.u[1], bound.e[1] * 0.75f);
		float[] eu2 = multiply(bound.u[2], bound.e[2] * 1.25f);
		
		return new float[][]
		{
			subtract(subtract(add(getPosition(), eu0), eu1), eu2),
			     add(subtract(add(getPosition(), eu0), eu1), eu2)
		};
	}
	
	public float[] getLightningVector()
	{
		return add(bound.c, multiply(bound.u[1], 20));
	}
	
	public float[] getBackwardItemVector(Item item, int iteration)
	{
		float radius = item.getMaximumExtent() * 1.5f * iteration;
		
		return add(bound.c, multiply(bound.u[0], bound.e[0] + radius));
	}
	
	public float[] getBackwardItemVector(Item item)
	{
		float radius = item.getMaximumExtent() * 1.5f;
		
		return add(getUpItemVector(item), multiply(bound.u[0], bound.e[0] + radius));
	}
	
	public float[] getForwardItemVector(Item item)
	{
		float radius = item.getMaximumExtent() * 1.5f;
		
		return subtract(getUpItemVector(item), multiply(bound.u[0], bound.e[0] + radius));
	}
	
	public float[] getUpItemVector(Item item)
	{
		float radius = item.getMaximumExtent() * 1.5f;
		
		return add(bound.c, multiply(bound.u[1], bound.e[1] + radius));
	}
	
	public void reset()
	{
		trajectory = 0;
		
		bound.setRotation(0, 0, 0);
		bound.setPosition(ORIGIN);
		
		yRotation_Wheel = zRotation_Wheel = 0.0f;

		turnRate = velocity = 0.0f;
		
		accelerating = reversing = turning = false;
		
		direction = Direction.STRAIGHT;
	}
	
	public void boost()
	{
		boosting = true;
		boostDuration = 60;
		velocity = 2 * TOP_SPEED;
	}

	public void spin()
	{
		if(!slipping)
		{
			slipVector = bound.u[0];
			slipTrajectory = trajectory;
			slipping = true;
			slipDuration = 48;
			turnRate = 0;
		}
	}
	
	public void useLightningBolt()
	{
		for(Car car : scene.getCars())
			if(!car.equals(this)) car.struckByLightning();
	}
	
	public void struckByLightning()
	{
		if(!starPower && !invisible)
		{
			if(!miniature)
			{
				bound.e = multiply(bound.e, 0.5f);
				scale /= 2;
			}
			
			miniature = true;
			miniatureDuration = 400;
			velocity = 0;
			
			if(slipping) slipDuration += 24;
			else spin();
		}
		
		float[] source = getLightningVector(); 
		scene.addParticle(new LightningParticle(source));
	}
	
	public void curse()
	{
		cursed = true;
		curseDuration = 500;
		itemDuration = 0; 
	}
	
	private void useBoo()
	{
		invisible = true;
		booDuration = 400;
	}

	public void usePowerStar()
	{
		starPower = true;
		cursed = false;
		starDuration = 500;
		turnIncrement = 0.15f;
	}
	
	public boolean isSlipping()   { return slipping;  }

	public boolean isCursed()     { return cursed;    }
	
	public boolean isMiniature()  { return miniature; }
	
	public boolean hasStarPower() { return starPower; }
	
	public boolean isInvisible()  { return invisible; }
	
	public boolean isBoosting()   { return boosting;  }
	
	public boolean isDrifting()   { return drift != Direction.STRAIGHT; }

	public void keyPressed(KeyEvent e)
	{
		if(motionMode == Motion.ANCHOR) anchor.keyPressed(e);
		else if(camera.isFree()) camera.keyPressed(e);
		else
		{
			controller.disable();
			
			switch(e.getKeyCode())
			{
				case KeyEvent.VK_W:
				case KeyEvent.VK_UP:
					accelerating = true; break;
					
				case KeyEvent.VK_S:
				case KeyEvent.VK_DOWN:
					reversing = true; accelerating = true; break;
					
				case KeyEvent.VK_A:
				case KeyEvent.VK_LEFT:
					steerLeft(); break;
				
				case KeyEvent.VK_D:
				case KeyEvent.VK_RIGHT:
					steerRight(); break;
					
				case KeyEvent.VK_E: camera.setRearview(true); break;
			}
		}
		
		switch(e.getKeyCode())
		{
			case KeyEvent.VK_R: if(!cursed && !slipping) { aimForwards();  useItem(); } break;
			case KeyEvent.VK_F: if(!cursed && !slipping) { resetAim();     useItem(); } break;
			case KeyEvent.VK_C: if(!cursed && !slipping) { aimBackwards(); useItem(); } break;
			
			case KeyEvent.VK_N: roulette.next(); break;
			case KeyEvent.VK_B: roulette.repeat(); break;
			case KeyEvent.VK_V: roulette.previous(); break;
			
			case KeyEvent.VK_Q: if(turning && !falling) drift(); break;
			
			case KeyEvent.VK_M: camera.cycleMode(); displayModel = true; break;
			case KeyEvent.VK_G:
			{
				motionMode = Motion.cycle(motionMode);
				
				anchor.setPosition(getPosition()); 
				anchor.setRotation(getRotationAngles(heights));
				
				break;
			}
			
			case KeyEvent.VK_9: if(!camera.isFirstPerson()) displayModel = !displayModel; break;
			
			case KeyEvent.VK_F2: renderMode++; renderMode %= 2; break;
			
			case KeyEvent.VK_F4: hud.setVisibility(!hud.getVisibility()); break;
			case KeyEvent.VK_F5: hud.decreaseStretch(); break; 
			case KeyEvent.VK_F6: hud.increaseStretch(); break; 
			case KeyEvent.VK_F7: hud.cycleGraphMode(); break;
			case KeyEvent.VK_F8: hud.nextComponent(); break;
			
			case KeyEvent.VK_F11: if(scene.enableQuality) high_quality = !high_quality; break;
			
			case KeyEvent.VK_BACK_SPACE: reset(); break;
		}
	}

	public void keyReleased(KeyEvent e)
	{
		switch(e.getKeyCode())
		{		
			case KeyEvent.VK_UP:
			case KeyEvent.VK_W:
			case KeyEvent.VK_DOWN:
			case KeyEvent.VK_S:
				accelerating = false; reversing = false; break;
			case KeyEvent.VK_A:
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_D:
			case KeyEvent.VK_RIGHT:
				turning = false; straighten(); break;
			
			case KeyEvent.VK_R:
			case KeyEvent.VK_C:
			case KeyEvent.VK_F: if(hasItem()) releaseItem(); break;
		
			case KeyEvent.VK_Q: miniTurbo(); break;
			
			case KeyEvent.VK_E: camera.setRearview(false); break;
		}
	}
	
	public void buttonPressed(int e)
	{
		if(motionMode == Motion.ANCHOR)
		{
			
		}
		else if(!camera.isFree())
		{
			switch(e)
			{
				case  3: roulette.next(); break;
				case -3: roulette.previous(); break;
				case  4: if(!cursed && !slipping) { aimForwards();  useItem(); } break;
				case -4: if(!cursed && !slipping) { aimBackwards(); useItem(); } break; 
				case  5: accelerating = true; break;
				case  6: camera.setRearview(true); break;
				case  7: reversing = true; accelerating = true; break;
				case  9: 
				case 10: if(turning && !falling) drift(); break;
				case 14: roulette.repeat(); break;
			}
		}
		else
		{
			switch(e)
			{
				case  5: camera.increaseSensitivity(); break;
				case  7: camera.decreaseSensitivity(); break;
				
				case  6: camera.setSpeed(15); break;
			}
		}
		
		switch(e)
		{
			case  8: camera.cycleMode(); displayModel = true; break;
		}
	}
	
	public void buttonReleased(int e)
	{
		if(!camera.isFree())
		{
			switch(e)
			{
				case  4: if(hasItem()) releaseItem(); break; 
				case  5:
				case  7: accelerating = false; reversing = false; break;
				case  6: camera.setRearview(false); break;
				case  9:
				case 10: miniTurbo(); break;
			}
		}
		else
		{
			switch(e)
			{
				case  6: camera.setSpeed(5); break;
			}
		}
	}
	
	public void setupCamera(GL2 gl, GLU glu)
	{
		float _trajectory = trajectory;
		
		switch(camera.getMode())
		{	
			case DYNAMIC_VIEW:
			{
				camera.setPosition(getPosition());
				if(slipping) _trajectory = slipTrajectory;
				camera.setRotation(_trajectory);
				break;
			}
			case BIRDS_EYE_VIEW: break;
			case DRIVERS_VIEW:
			{
				camera.setPosition(getPosition());
				camera.setRotation(_trajectory);
				camera.setOrientation(bound.u);
				displayModel = false;
				break;
			}
			case FREE_LOOK_VIEW:
			{	
				if(scene.moveLight) scene.light.setPosition(camera.getPosition());
				if(controller.isEnabled()) camera.update(controller);
				break;
			}
			
			default: break;	
		}
		
		camera.setupView(gl, glu);
	}
	
	public enum Motion
	{
		DEFAULT,
		ANCHOR;
		
		public static Motion cycle(Motion mode)
		{
			return values()[(mode.ordinal() + 1) % values().length];
		}
	}
}
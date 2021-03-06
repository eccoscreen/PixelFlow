/**
 * 
 * PixelFlow | Copyright (C) 2017 Thomas Diewald (www.thomasdiewald.com)
 * 
 * src  - www.github.com/diwi/PixelFlow
 * 
 * A Processing/Java library for high performance GPU-Computing.
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */

package Skylight.Skylight_BulletPhysics_Breakable;

import java.io.File;
import java.util.Locale;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.broadphase.Dispatcher;
import com.bulletphysics.collision.narrowphase.ManifoldPoint;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import com.jogamp.opengl.GL2;

import peasy.*;
import bRigid.*;
import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.antialiasing.SMAA.SMAA;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTextureUtils;
import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DepthOfField;
import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DwFilter;
import com.thomasdiewald.pixelflow.java.render.skylight.DwSceneDisplay;
import com.thomasdiewald.pixelflow.java.render.skylight.DwScreenSpaceGeometryBuffer;
import com.thomasdiewald.pixelflow.java.render.skylight.DwSkyLight;
import com.thomasdiewald.pixelflow.java.utils.DwBoundingSphere;
import com.thomasdiewald.pixelflow.java.utils.DwFrameCapture;

import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PMatrix3D;
import processing.core.PShape;
import processing.opengl.PGL;
import processing.opengl.PGraphics3D;



public class Skylight_BulletPhysics_Breakable3 extends PApplet {
  
  //
  // author: Thomas Diewald
  //
  //
  // This Example shows how to combine the PixelFlow Skylight-Renderer and 
  // Bullet-Physics. Rigid Bodies are created from a Voronoi-Tesselation.
  //
  // Features:
  //
  // - CellFracture from Voronoi-Tesselation - HE_Mesh
  // - Rigid Body Simulation - bRigid, Bullet Physics
  // - Skylight Renderer, Sun + AO
  // - DoF
  // - Bloom
  // - SMAA
  // - shooting
  // - ...
  //
  // required Libraries to run this example (PDE contribution manager):
  //
  // - PeasyCam
  //   library for camera control, by Jonathan Feinberg
  //   http://mrfeinberg.com/peasycam/
  //
  // - bRigid (jBullet-Physics for Processing),
  //   library for rigid body simulation, by Daniel Koehler
  //   http://www.lab-eds.org/bRigid
  //
  // - HE_Mesh
  //   library for creating and manipulating polygonal meshes, by Frederik Vanhoutte
  //   https://github.com/wblut/HE_Mesh (manual installation)
  //
  // - PixelFlow
  //   library for skylight, post-fx, lots of GLSL, etc..., by Thomas Diewald
  //   https://github.com/diwi/PixelFlow
  //
  //


  
  int viewport_w = 1280;
  int viewport_h = 720;
  int viewport_x = 230;
  int viewport_y = 0;

  // Camera
  PeasyCam cam;
  
  // Bullet Physics
  MyBPhysics physics;
  
  // Bullet bodies, group-shape
  PShape group_bulletbodies;
  
  // PixelFlow Context
  DwPixelFlow context;
  
  // PixelFlow Filter, for post fx
  DwFilter filter;
  
  // some render-targets
  PGraphics3D pg_render;
  PGraphics3D pg_aa;
  
  // SkyLight Renderer
  DwSkyLight skylight;
  PMatrix3D mat_scene_view;
  PMatrix3D mat_scene_bounds;
  
  // AntiAliasing - SMAA
  SMAA smaa;

  // Depth of Field - DoF
  DepthOfField dof;
  DwScreenSpaceGeometryBuffer geombuffer;
  PGraphics3D pg_tmp;
  
  PFont font12;
  
  
  DwFrameCapture capture;
  
  // switches
  public boolean UPDATE_PHYSICS    = true;
  public boolean APPLY_DOF         = true;
  public boolean APPLY_BLOOM       = true;
  public boolean DISPLAY_WIREFRAME = false;
  
  public void settings() {
    size(viewport_w, viewport_h, P3D);
    smooth(0);
  }

  public void setup() {
    
    surface.setLocation(viewport_x, viewport_y);
    
    float SCENE_SCALE = 1000;
    
    // for screenshot
    capture = new DwFrameCapture(this, "examples/");
    
    font12 = createFont("../data/SourceCodePro-Regular.ttf", 12);

    cam = new PeasyCam(this, 0, 0, 0, SCENE_SCALE);
    perspective(60 * DEG_TO_RAD, width/(float)height, 2, SCENE_SCALE * 250);

    group_bulletbodies = createShape(GROUP);
    
    physics = new MyBPhysics(); // no bounding box
    physics.world.setGravity(new Vector3f(0, 0, -100));
   
    pg_render = (PGraphics3D) createGraphics(width, height, P3D);
    pg_render.smooth(0);
    pg_render.beginDraw();
    pg_render.endDraw();
    
    
    // compute scene bounding-sphere
    DwBoundingSphere scene_bs = new DwBoundingSphere();
    scene_bs.set(0, 0, 200, 450);
    PMatrix3D mat_bs = scene_bs.getUnitSphereMatrix();

    // matrix, to place (centering, scaling) the scene in the viewport
    mat_scene_view = new PMatrix3D();
    mat_scene_view.scale(SCENE_SCALE);
    mat_scene_view.apply(mat_bs);

    // matrix, to place the scene in the skylight renderer
    mat_scene_bounds = mat_scene_view.get();
    mat_scene_bounds.invert();
    mat_scene_bounds.preApply(mat_bs);

    // callback for rendering the scene
    DwSceneDisplay scene_display = new DwSceneDisplay(){
      @Override
      public void display(PGraphics3D canvas) {
        displayScene(canvas);  
      }
    };
    
    // library context
    context = new DwPixelFlow(this);
    context.print();
    context.printGL();
    
    // postprocessing filters
    filter = DwFilter.get(context);
    
    // init skylight renderer
    skylight = new DwSkyLight(context, scene_display, mat_scene_bounds);
    
    // parameters for sky-light
    skylight.sky.param.iterations     = 50;
    skylight.sky.param.solar_azimuth  = 0;
    skylight.sky.param.solar_zenith   = 0;
    skylight.sky.param.sample_focus   = 1; // full sphere sampling
    skylight.sky.param.intensity      = 1.0f;
    skylight.sky.param.rgb            = new float[]{1,1,1};
    skylight.sky.param.shadowmap_size = 512; // quality vs. performance
    
    // parameters for sun-light
    skylight.sun.param.iterations     = 50;
    skylight.sun.param.solar_azimuth  = 35;
    skylight.sun.param.solar_zenith   = 65;
    skylight.sun.param.sample_focus   = 0.1f;
    skylight.sun.param.intensity      = 1.0f;
    skylight.sun.param.rgb            = new float[]{1,1,1};
    skylight.sun.param.shadowmap_size = 512;
    
    // postprocessing AA
    smaa = new SMAA(context);
    pg_aa = (PGraphics3D) createGraphics(width, height, P3D);
    pg_aa.smooth(0);
    pg_aa.textureSampling(5);
    
    
    dof = new DepthOfField(context);
    geombuffer = new DwScreenSpaceGeometryBuffer(context, scene_display);
    
    pg_tmp = (PGraphics3D) createGraphics(width, height, P3D);
    pg_tmp.smooth(0);
    
    DwGLTextureUtils.changeTextureFormat(pg_tmp, GL2.GL_RGBA16F, GL2.GL_RGBA, GL2.GL_FLOAT);
    pg_tmp.beginDraw();
    pg_tmp.endDraw();
    
    // fresh start
    reset();
    
    createFractureShape();
    
    frameRate(60);
  }
  
  


  
  boolean slow = true;
  
  
  PShape group_collisions;
  
  
  public void getCollisionPoints() {
    
    if(group_collisions == null){
      group_collisions = createShape(GROUP);
    }
    
    
    Dispatcher dispatcher = physics.world.getDispatcher();

    int numManifolds = dispatcher.getNumManifolds();

    float min_vel_sq = 50; min_vel_sq = min_vel_sq*min_vel_sq;
    
    for (int i = 0; i < numManifolds; i++) {
      PersistentManifold contactManifold = dispatcher.getManifoldByIndexInternal(i);
      if(contactManifold == null){
        continue;
      }
      int numCon = contactManifold.getNumContacts();
      
      RigidBody rb0 = (RigidBody) contactManifold.getBody0();
      RigidBody rb1 = (RigidBody) contactManifold.getBody1();

      BreakableBody window_bodyrb0 = null;
      BreakableBody window_bodyrb1 = null;
      
      Object rb0_usr_ptr = rb0.getUserPointer();
      Object rb1_usr_ptr = rb1.getUserPointer();
      
      if(rb0_usr_ptr instanceof BreakableBody) {
        window_bodyrb0 = (BreakableBody) rb0_usr_ptr;
      } else if(rb1_usr_ptr instanceof BreakableBody) {
        
        RigidBody tmp = rb0;
        rb0 = rb1;
        rb1 = tmp;
        
        rb0_usr_ptr = rb0.getUserPointer();
        rb1_usr_ptr = rb1.getUserPointer();
        
        window_bodyrb0 = (BreakableBody) rb0_usr_ptr;
      } else {
        continue;
      }
      
      
//      boolean isbullet = false;
//      if(rb1_usr_ptr instanceof BObject) {
//        BObject body = (BObject) rb1_usr_ptr;
//        String name = body.displayShape.getName();
//        if(name != null && name.contains("bullet")){
//          isbullet = true;
//        }
//      }
// 
//      if(!isbullet){
//        window_bodyrb0 = null;
//      }
      
      if(window_bodyrb0 != null && numCon > 0){
        ManifoldPoint mp_max = contactManifold.getContactPoint(0);
        for(int k = 1; k < numCon; k++){
          ManifoldPoint mp_cur = contactManifold.getContactPoint(k);
          if(mp_max.appliedImpulse < mp_cur.appliedImpulse){
            mp_max = mp_cur;
          }
        }
        
        
        Vector3f vel0 = new Vector3f();
        Vector3f vel1 = new Vector3f();
        rb0.getLinearVelocity(vel0);
        rb1.getLinearVelocity(vel1);


        float vel0_sq = vel0.lengthSquared();
        float vel1_sq = vel1.lengthSquared();
        
        float vel_max = Math.max(vel0_sq, vel1_sq);
        
        
//        if(mp_max.appliedImpulse * vel_max > 5000000 * min_vel_sq){
        if(mp_max.appliedImpulse > 5000000){

//          Vector3f vel0 = new Vector3f();
//          Vector3f vel1 = new Vector3f();
//          rb0.getLinearVelocity(vel0);
//          rb1.getLinearVelocity(vel1);
//
//          float vel0_sq = vel0.lengthSquared();
//          float vel1_sq = vel1.lengthSquared();
          
//          if(vel0_sq > min_vel_sq || vel1_sq > min_vel_sq){
          
  //          float vel0_sq = window_bodyrb0.body.getVelocity().lengthSquared();
  //          float vel1_sq = window_bodyrb1.body.getVelocity().lengthSquared();
            
//            System.out.println();
//            System.out.printf(Locale.ENGLISH, "vel0: %10.2f, \n", vel_max);
//            System.out.printf(Locale.ENGLISH, "impulse: %10.2f, mass: %10.2f\n", mp_max.appliedImpulse, window_bodyrb0.mass);
//          System.out.printf(Locale.ENGLISH, "impulse: %10.2f, impulse2: %10.2f, impulse3: %10.2f\n", mp_max.appliedImpulse, mp_max.appliedImpulseLateral1, mp_max.appliedImpulseLateral2);
//            System.out.println(mp_max.localPointA);
//            System.out.println(mp_max.localPointB);
  //          Vector3f pos0 = new Vector3f();
  //          mp_max.getPositionWorldOnA(pos0);
            
            Vector3f pos0 = new Vector3f();
            pos0.add(mp_max.positionWorldOnA);
            pos0.add(mp_max.positionWorldOnB);
            pos0.scale(0.5f);
            window_bodyrb0.createCellFracture(pos0);
  //          window_bodyrb0.createCellFracture(mp_max.positionWorldOnA);
  //          window_bodyrb0.createCellFracture(mp_max.localPointA);
            if(contactManifold != null){
              dispatcher.clearManifold(contactManifold);
            }
//          }
//          break;
        }
        
    
      }
      

    }
    
    for (int i = 0; i < numManifolds; i++) {
      PersistentManifold contactManifold = dispatcher.getManifoldByIndexInternal(i);
      if(contactManifold != null){
        dispatcher.clearManifold(contactManifold);
      } 
    }
  }
  

  public void draw() {
    
    // handle bullet physics update, etc...
    if(UPDATE_PHYSICS){
      
      physics.update();
//      physics.update(30);
      
      getCollisionPoints();
      
      
      removeLostBodies();
      
      for (BObject body : physics.rigidBodies) {
        updateShapes(body);
      }
      
     
      
    }
    
 
    // when the camera moves, the renderer restarts
    updateCamActiveStatus();
    if(CAM_ACTIVE || UPDATE_PHYSICS){
      skylight.reset();
    }

    // update renderer
    skylight.update();
    

    // apply AntiAliasing
    smaa.apply(skylight.renderer.pg_render, pg_aa);
    
    // apply bloom
    if(APPLY_BLOOM){
      filter.bloom.param.mult   = 0.15f; //map(mouseX, 0, width, 0, 1);
      filter.bloom.param.radius = 0.5f; // map(mouseY, 0, height, 0, 1);
      filter.bloom.apply(pg_aa, null, pg_aa);
    }  
    
    // apply DoF
    if(APPLY_DOF){
      int mult_blur = 5;
      
      geombuffer.update(skylight.renderer.pg_render);
      
      filter.gaussblur.apply(geombuffer.pg_geom, geombuffer.pg_geom, pg_tmp, mult_blur);

      dof.param.focus_pos = new float[]{0.5f, 0.5f};
//      dof.param.focus_pos[0] =   map(mouseX, 0, width , 0, 1);
//      dof.param.focus_pos[1] = 1-map(mouseY, 0, height, 0, 1);
      dof.param.mult_blur = mult_blur;
      dof.apply(pg_aa, pg_render, geombuffer);
      filter.copy.apply(pg_render, pg_aa);
    }
    
    // display result
    cam.beginHUD();
    {
      background(255);
      noLights();
      image(pg_aa, 0, 0);
      
      displayCross();
      
      displayHUD();
    }
    cam.endHUD();
    
    // info
    String txt_fps = String.format(getClass().getName()+ "  [fps %6.2f]  [bodies %d]", frameRate, physics.rigidBodies.size());
    surface.setTitle(txt_fps);
  }
  
  
  
  public void displayCross(){
    pushMatrix();
    float cursor_s = 10;
    float fpx = (       dof.param.focus_pos[0]) * width;
    float fpy = (1.0f - dof.param.focus_pos[1]) * height;
    blendMode(EXCLUSION);
    translate(fpx, fpy);
    strokeWeight(1);
    stroke(255,200);
    line(-cursor_s, 0, +cursor_s, 0);
    line(0, -cursor_s, 0, +cursor_s);
    blendMode(BLEND);
    popMatrix();
  }

  
  
  
  public void displayHUD(){
    
    String txt_fps            = String.format(Locale.ENGLISH, "fps: %6.2f", frameRate);
    String txt_num_bodies     = String.format(Locale.ENGLISH, "rigid bodies: %d", physics.rigidBodies.size());
    String txt_samples_sky    = String.format(Locale.ENGLISH, "sky/sun: %d/%d (samples)", skylight.sky.param.iterations,  skylight.sun.param.iterations);
 
//    String txt_model          = String.format(Locale.ENGLISH, "[1-9] model: %d", BUILDING);
    String txt_reset          = String.format(Locale.ENGLISH, "[r] reset");
    String txt_update_physics = String.format(Locale.ENGLISH, "[t] physics:   %b", UPDATE_PHYSICS);
    String txt_apply_bloom    = String.format(Locale.ENGLISH, "[q] bloom:     %b", APPLY_BLOOM);
    String txt_apply_dof      = String.format(Locale.ENGLISH, "[w] DoF:       %b", APPLY_DOF);
    String txt_wireframe      = String.format(Locale.ENGLISH, "[e] wireframe: %b", DISPLAY_WIREFRAME);
    String txt_shoot          = String.format(Locale.ENGLISH, "[ ] shoot");

    int tx, ty, sy;
    tx = 10;
    ty = 10;
    sy = 13;
    
    fill(0, 100);
    noStroke();
    stroke(0, 200);
    rectMode(CORNER);
    rect(5, 5, 200, 170);
    
    textFont(font12);
//    textMode(SCREEN);
    fill(220);
    text(txt_fps            , tx, ty+=sy);
    text(txt_num_bodies     , tx, ty+=sy);
    text(txt_samples_sky    , tx, ty+=sy);
    ty+=sy;
//    text(txt_model          , tx, ty+=sy);
    text(txt_reset          , tx, ty+=sy);
    text(txt_update_physics , tx, ty+=sy);
    text(txt_apply_bloom    , tx, ty+=sy);
    text(txt_apply_dof      , tx, ty+=sy);
    text(txt_wireframe      , tx, ty+=sy);
    text(txt_shoot          , tx, ty+=sy);

  }
  
  
  
  // reset scene
  public void reset(){
    // remove bodies
    for(int i = physics.rigidBodies.size() - 1; i >= 0; i--){
      BObject body = physics.rigidBodies.get(i);
      physics.removeBody(body);
    }
    
    // just in case, i am actually not not sure if PShape really needs this to 
    // avoid memoryleaks.
    for(int i = group_bulletbodies.getChildCount() - 1; i >= 0; i--){
      group_bulletbodies.removeChild(i);
    }

    addGround();
  }
  

  // bodies that have fallen outside of the scene can be removed
  public void removeLostBodies(){
    for(int i = physics.rigidBodies.size() - 1; i >= 0; i--){
      BObject body = physics.rigidBodies.get(i);
      Vector3f pos = body.getPosition();
      
      if(pos.z < -1000){
        int idx = group_bulletbodies.getChildIndex(body.displayShape);
        if(idx >= 0){
          group_bulletbodies.removeChild(idx);
        }
        physics.removeBody(body);
      }
    }
  }
  
  
  // toggle shading/wireframe display
  public void toggleDisplayWireFrame(){
    DISPLAY_WIREFRAME = !DISPLAY_WIREFRAME;
    for (BObject body : physics.rigidBodies) {
      PShape shp = body.displayShape;
      String name = shp.getName();
      if(name != null && name.contains("[wire]")){
        shp.setFill(!DISPLAY_WIREFRAME);
        shp.setStroke(DISPLAY_WIREFRAME);
      }
    }
    skylight.reset();
  }
  
  
  // check if camera is moving
  float[] cam_pos = new float[3];
  boolean CAM_ACTIVE = false;
  public void updateCamActiveStatus(){
    float[] cam_pos_curr = cam.getPosition();
    CAM_ACTIVE = false;
    CAM_ACTIVE |= cam_pos_curr[0] != cam_pos[0];
    CAM_ACTIVE |= cam_pos_curr[1] != cam_pos[1];
    CAM_ACTIVE |= cam_pos_curr[2] != cam_pos[2];
    cam_pos = cam_pos_curr;
  }
  
  

  public void keyReleased(){
    if(key == 't') UPDATE_PHYSICS = !UPDATE_PHYSICS;
    if(key == 'q') APPLY_BLOOM = !APPLY_BLOOM;
    if(key == 'w') APPLY_DOF = !APPLY_DOF;
    if(key == 'e') toggleDisplayWireFrame();
    if(key == 'r') createFractureShape();
    if(key == ' ') addShootingBody();
    if(key == 's') saveScreenshot();
  }
  

  
  // shoot body into the scene
  int shooter_count = 0;
  PMatrix3D mat_mvp     = new PMatrix3D();
  PMatrix3D mat_mvp_inv = new PMatrix3D();
  
  public void addShootingBody(){
    
    float vel = 1000;
    float mass = 300000;
    float dimr = 40;
    
    PGraphics3D pg = (PGraphics3D) skylight.renderer.pg_render;
    mat_mvp.set(pg.modelview);
    mat_mvp.apply(mat_scene_view);
    mat_mvp_inv.set(mat_mvp);
    mat_mvp_inv.invert();
    
    float[] cam_start = {0, 0, -0, 1};
    float[] cam_aim   = {0, 0, -400, 1};
    float[] world_start = new float[4];
    float[] world_aim   = new float[4];
    mat_mvp_inv.mult(cam_start, world_start);
    mat_mvp_inv.mult(cam_aim, world_aim);
    
    Vector3f pos = new Vector3f(world_start[0], world_start[1], world_start[2]);
    Vector3f aim = new Vector3f(world_aim[0], world_aim[1], world_aim[2]);
    Vector3f dir = new Vector3f(aim);
    dir.sub(pos);
    dir.normalize();
    dir.scale(vel);

    BObject obj;
    
//    if((shooter_count % 2) == 0){
      obj = new BSphere(this, mass, 0, 0, 0, dimr*0.5f);
//    } else {
//      obj = new BBox(this, mass, dimr, dimr, dimr);
//    }
    BObject body = new BObject(this, mass, obj, pos, true);
    
    body.setPosition(pos);
    body.setVelocity(dir);
    body.setRotation(new Vector3f(random(-1, 1),random(-1, 1),random(-1, 1)), random(PI));

    body.rigidBody.setRestitution(0.9f);
    body.rigidBody.setFriction(1);
//    body.rigidBody.setHitFraction(1);
    body.rigidBody.setDamping(0.1f, 0.1f);
    body.rigidBody.setUserPointer(body);
    
    body.displayShape.setStroke(false);
    body.displayShape.setFill(true);
    body.displayShape.setFill(color(255,200,0));
    body.displayShape.setStrokeWeight(1);
    body.displayShape.setStroke(color(0));
//    body.displayShape.setName("bullet");

    physics.addBody(body);
    group_bulletbodies.addChild(body.displayShape);
    
    body.displayShape.setName("[bullet_"+shooter_count+"] [wire]");
    shooter_count++;
  }
  


  
  BreakableBody window1;
  BreakableBody window2;
  BreakableBody window3;
  
  public void createFractureShape(){
//    reset();
//    
//    Vector3f window1_dim = new Vector3f(500, 200, 4);
//    Vector3f window2_dim = new Vector3f(400, 400, 4);
//    Vector3f window3_dim = new Vector3f(200, 500, 4);
//   
//    window1 = new BreakableBody(this, physics, group_bulletbodies);
//    window1.color = new float[]{96,180,255};
//    window1.thickness = window1_dim.z;
////    window1.createWindow();
//    
//    window2 = new BreakableBody(this, physics, group_bulletbodies);
//    window2.color = new float[]{255,128,96};
//    window2.thickness  = window2_dim.z;
////    window2.createWindow();
//    
//    window3 = new BreakableBody(this, physics, group_bulletbodies);
//    window3.color = new float[]{255,255,96};
//    window3.thickness = window3_dim.z;
//    
//
//    
//    window1.createRectBody(window1_dim);
//    window2.createRectBody(window2_dim);
//    window3.createRectBody(window3_dim);
//    
//
//    PMatrix3D mat_window = new PMatrix3D();
//    
//    mat_window.reset();
//    mat_window.rotateX(PI/2f);
//    mat_window.translate(0, 20f + window1_dim.y * 0.5f, 0);
//    window1.setTransform(mat_window);
//    
//    createFitting(mat_window, window1_dim, true);
//    createFitting(mat_window, window1_dim, false);
//    
//    mat_window.reset();
//    mat_window.rotateX(PI/2f);
//    mat_window.translate(0, 20f + window2_dim.y * 0.5f, 100);
////    mat_window.rotateY(5 * PI/180f);
//    window2.setTransform(mat_window);
//    
//    createFitting(mat_window, window2_dim, true);
//    createFitting(mat_window, window2_dim, false);
//    
//    mat_window.reset();
////    mat_window.rotateY(PI/20f);
//    mat_window.rotateX(PI/2f);
////    mat_window.rotateY(PI/20f);
////    mat_window.rotateZ(PI/10f);
//    mat_window.translate(0, 0f + window3_dim.y * 0.5f, -100);
////    mat_window.rotateZ(PI/10f);
////    mat_window.translate(0, 0, 800);
////    mat_window.rotateY(10 * PI/180f);
//    window3.setTransform(mat_window);
//    
//    createFitting(mat_window, window3_dim, true);
//    createFitting(mat_window, window3_dim, false);
//    

  }
  
 
 
  
  
  public void createFitting(PMatrix3D mat_wall, Vector3f wall_dim, boolean right){
    float dimx = wall_dim.x;
    float dimy = wall_dim.y;
    float dimz = wall_dim.z;

    float dimx2 = dimz*3;
    
    float tx  =      dimx * 0.5f + dimz * 0.5f;
    float tx2 = tx - dimx2*0.5f + dimz*0.5f;
    
    
    float side = right ? 1f : -1f;
    
    tx  *= side;
    tx2 *= side;
    
    {
      PMatrix3D mat_pillar = mat_wall.get();
      mat_pillar.translate(tx, 0, 0);
      Transform transform = asBulletTransform(mat_pillar);
      
      BObject body = new BBox(this, 0, dimz, dimy, dimz);
  
      body.rigidBody.setWorldTransform(transform);
      body.rigidBody.getMotionState().setWorldTransform(transform);
  
      body.displayShape = createBoxShape(new Vector3f(dimz, dimy, dimz));
      group_bulletbodies.addChild(body.displayShape);
      physics.addBody(body);
    }
    

    {

      
      PMatrix3D mat_pillar = mat_wall.get();
      mat_pillar.translate(tx2, 0, dimz);
      Transform transform = asBulletTransform(mat_pillar);
      
      BObject body = new BBox(this, 0, dimx2, dimy, dimz);
  
      body.rigidBody.setWorldTransform(transform);
      body.rigidBody.getMotionState().setWorldTransform(transform);
      body.rigidBody.setFriction(0.99f);
  
      body.displayShape = createBoxShape(new Vector3f(dimx2, dimy, dimz));
      group_bulletbodies.addChild(body.displayShape);
      physics.addBody(body);
    }
    
    {
      PMatrix3D mat_pillar = mat_wall.get();
      mat_pillar.translate(tx2, 0, -dimz);
      Transform transform = asBulletTransform(mat_pillar);
      
      BObject body = new BBox(this, 0, dimx2, dimy, dimz);
  
      body.rigidBody.setWorldTransform(transform);
      body.rigidBody.getMotionState().setWorldTransform(transform);
      
      body.rigidBody.setFriction(0.99f);
  
      body.displayShape = createBoxShape(new Vector3f(dimx2, dimy, dimz));
      group_bulletbodies.addChild(body.displayShape);
      physics.addBody(body);
    }
  }

  

  
  public PShape createBoxShape(Vector3f dim){
    PShape shp = createShape(BOX, dim.x, dim.y, dim.z);
    shp.setStroke(false);
    shp.setFill(true);
    shp.setFill(color(16));
    shp.setStrokeWeight(1);
    shp.setStroke(color(0));
    return shp;
  }
  
  
  public Transform asBulletTransform(PMatrix3D mat_p5){
    Matrix4f mat = new Matrix4f();
    mat.setRow(0, mat_p5.m00, mat_p5.m01, mat_p5.m02, mat_p5.m03);
    mat.setRow(1, mat_p5.m10, mat_p5.m11, mat_p5.m12, mat_p5.m13);
    mat.setRow(2, mat_p5.m20, mat_p5.m21, mat_p5.m22, mat_p5.m23);
    mat.setRow(3, mat_p5.m30, mat_p5.m31, mat_p5.m32, mat_p5.m33);
    return new Transform(mat);
  }

  
  
  // add ground bodies
  public void addGround(){
    {
      Vector3f pos = new Vector3f(0,0,10);
      BObject body = new BBox(this, 0, 650, 650, 20);
//      BObject body = new BBox(this, 0, pos.x, pos.y, pos.z, 650, 650, 20);
//      BObject body = new BObject(this, 0, obj, new Vector3f(), true);
      
//      body.setPosition(pos);
//      Transform transform = new Transform();
//      body.rigidBody.getMotionState().getWorldTransform(transform);
//      body.rigidBody.setWorldTransform(transform);
      
//      Transform transform = new Transform();
//      transform.set
      
//      body.rigidBody.getWorldTransform(transform);
//      transform.basis.transform(arg0);
      
//      body.rigidBody.setWorldTransform(transform);
//      body.rigidBody.getMotionState().setWorldTransform(transform);
      
      
//      PMatrix3D mat = new PMatrix3D();
//      mat.translate(pos.x, pos.y, pos.z);
//      Transform transform = asBulletTransform(mat);
//      body.rigidBody.setWorldTransform(transform);
//      body.rigidBody.getMotionState().setWorldTransform(transform);
      
      Transform transform = new Transform();
      body.rigidBody.getWorldTransform(transform);
      
      transform.origin.set(pos);
      body.rigidBody.setWorldTransform(transform);
      body.rigidBody.getMotionState().setWorldTransform(transform);
      
      
  
      body.displayShape = createShape(BOX, 650, 650, 20);
      body.displayShape.setStroke(false);
      body.displayShape.setFill(true);
      body.displayShape.setFill(color(200, 96, 16));
      body.displayShape.setFill(color(255));
      body.displayShape.setStrokeWeight(1);
      body.displayShape.setStroke(color(0));
//      if(body instanceof BBox){
//        fixBoxNormals(body.displayShape);
//      }
      physics.addBody(body);
      group_bulletbodies.addChild(body.displayShape);
      body.displayShape.setName("ground_box");
    }
  }
  
  

  
  
  
  
  
  
  
  
  
  
  

  // render scene
  public void displayScene(PGraphics3D pg){
    if(pg == skylight.renderer.pg_render){
      pg.background(16);
    }
    
    if(pg == geombuffer.pg_geom){
      pg.background(255, 255);
      pg.pgl.clearColor(1, 1, 1, 6000);
      pg.pgl.clear(PGL.COLOR_BUFFER_BIT);
    }
    
    pg.pushMatrix();
    pg.applyMatrix(mat_scene_view);
    pg.shape(group_bulletbodies);
    pg.shape(group_collisions);
    pg.popMatrix();
  }
  
  
  // update PShape matrices
  Transform transform = new Transform();
  Matrix4f out = new Matrix4f();
  
  public void updateShapes(BObject body){
    if (body.displayShape != null) {
      body.displayShape.resetMatrix();
      if (body.getMass() < 0) {
        transform = body.rigidBody.getMotionState().getWorldTransform(transform);
        out = transform.getMatrix(out);
        body.transform.set(transform);
        body.displayShape.applyMatrix(out.m00, out.m01, out.m02, out.m03, out.m10, out.m11, out.m12, out.m13, out.m20, out.m21, out.m22, out.m23, out.m30, out.m31, out.m32, out.m33);

      } else {
//        transform = body.rigidBody.getWorldTransform(transform);
//        body.transform.set(transform);
//        body.displayShape.translate(transform.origin.x, transform.origin.y, transform.origin.z);
        
        transform = body.rigidBody.getMotionState().getWorldTransform(transform);
        out = transform.getMatrix(out);
        body.transform.set(transform);
        body.displayShape.applyMatrix(out.m00, out.m01, out.m02, out.m03, out.m10, out.m11, out.m12, out.m13, out.m20, out.m21, out.m22, out.m23, out.m30, out.m31, out.m32, out.m33);

      }
    }
  }

  
  public void saveScreenshot(){
    File file = capture.createFilename();
//    pg_aa.save(file.getAbsolutePath());
    save(file.getAbsolutePath());
    System.out.println(file);
  }


  public static void main(String args[]) {
    PApplet.main(new String[] { Skylight_BulletPhysics_Breakable3.class.getName() });
  }
}

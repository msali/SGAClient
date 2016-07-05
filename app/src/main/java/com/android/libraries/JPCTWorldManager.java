package com.android.libraries;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.location.Location;
import android.opengl.GLSurfaceView;
import android.service.dreams.DreamService;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;

import com.android.libraries.jpctutils.Terrain;
import com.threed.jpct.Camera;
import com.threed.jpct.Config;
import com.threed.jpct.*;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Object3D;
import com.threed.jpct.Primitives;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.SkyBox;

import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Mario Salierno on 21/03/2016.
 */
public class JPCTWorldManager implements GLSurfaceView.Renderer{

    private World world = null;
    private SkyBox skybox = null;
    private int SKYBOX_DIM = 8192;//1024;
    private int CAMERA_HEIGHT = 0;//default value
    private int GROUND_ALTITUDE = -20;
    //private World sky = null;
    private Light sun = null;
    //ground
    private Object3D ground;
    private SimpleVector groundTransformedCenter;
    private final String groundID = "groundobjID";


    private HashMap<String, TranslationObject> worldObjects = new HashMap<String, TranslationObject>();
    RGBColor background = new RGBColor(0,0,0,0);//bigtransparent background
    private FrameBuffer frameBuffer = null;

    //boolean to set whether gles2 is available..?
    private boolean gl2 = true;

    private TextureFromCameraActivity activity;
    private GLSurfaceView mGLView;
    private SimParameters simulation;
    private GPSLocator gpsLocator;
    //Location lastLocation;
    //Location myLoc;
    Location zeroLoc=null;
    private float xCAMERA=0, yCAMERA=0, zCAMERA=0;
    /*
    JPCT COORDINATES
    positive axis:
    x -> right of the screen
    y -> down to the screen
    z -> inside the screen

    mAzimuthView heading -> Z JPCT
    mPitchView -> x JPCT
    mRollView -> y JPCT
    */
    private float roll=0,pitch=0,head=0;
    private boolean facedown = false;

    public void setRPHCam(float r,float p,float h,boolean fdown){
        facedown=fdown;
        roll=r;
        pitch=p;
        head=h;

    }

    public JPCTWorldManager(TextureFromCameraActivity activity, SimParameters simulation, GPSLocator gpsLocator, Integer CAMERA_HEIGHT) {
        this.activity = activity;
        this.simulation=simulation;
        this.gpsLocator=gpsLocator;

        if(CAMERA_HEIGHT!=null)
            this.CAMERA_HEIGHT=CAMERA_HEIGHT;
    }


    private void configureEnvironment(){

        /*
        The first two lines (glAvoid... and maxPolys...) are mainly to save some memory.
        We don't need copies of your textures in main memory once uploaded to the graphics card
        and we are using Compiled objects, which is why our VisList won't get very huge,
        so 1000 polygons are more than enough here.
        */
        //Config.glAvoidTextureCopies = true;
        Config.maxPolysVisible = 1000;
        //Config.glColorDepth = 24;
        //Config.glFullscreen = false;
        Config.farPlane = 4000;
        //Config.glShadowZBias = 0.8f;
        //Config.lightMul = 1;

        /*
        collideOffset is needed to make the Collision detection work with the plane.
        It's a common problem when using collision detection, that your collision sources
        are passing through the obstacles, if this value is too low.
        500 is a reasonable value for this example. The last line (glTrilinear)
        makes jPCT use trilinear filtering.
        It simply looks better that way.
         */
        //Config.collideOffset = 500;
        Config.glTrilinear = true;


    }

    public void setUpWorld(){

        configureEnvironment();

        world = new World();
        //world.setAmbientLight(20, 20, 20);


        sun = new Light(world);
        sun.setIntensity(128, 128, 128);
        //sun.setIntensity(255, 255, 255);
        world.getCamera().setPosition(0,0,CAMERA_HEIGHT);

        /*
            Two worlds because one is for the scene itself and one is for the sky dome.
            This makes it much easier to handle the dome, but you don't have to do it this way.
            Adding the dome to the scene itself would work too.
        */
        //sky = new World();
        //sky.setAmbientLight(255, 255, 255);

        //world.getLights().setRGBScale(Lights.RGB_SCALE_2X);
        //sky.getLights().setRGBScale(Lights.RGB_SCALE_2X);
        setUpSkyBox();

    }



    private void setUpSkyBox(){
        String textureID = "groundTexture";

        TextureManager txtManager = TextureManager.getInstance();
        //Drawable groundImage = activity.getResources().getDrawable(R.drawable.bigoffice);
        Drawable groundImage = ResourcesCompat.getDrawable(activity.getResources(), R.drawable.bigoffice, null);
        Texture groundTexture = new Texture(groundImage);
        txtManager.addTexture(textureID,groundTexture);


        String transparentID = "transparentback";
        //Drawable transpImage = activity.getResources().getDrawable(activity.getResources(),R.drawable.transparentback, null);
        //Drawable transpImage = ResourcesCompat.getDrawable(activity.getResources(), R.drawable.transparentback, null);

        //
        //int skyBWidth = groundImage.getIntrinsicWidth();
        //int skyBHeight = groundImage.getIntrinsicWidth();
        //Log.e("GROUND", "W:"+groundImage.getIntrinsicWidth()+" H:"+groundImage.getIntrinsicHeight());
        //Log.e("GROUND", "W:"+groundImage.getMinimumWidth()+" H:"+groundImage.getMinimumHeight());
        Bitmap transparentbmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
        this.makeTransparent(transparentbmp);
        Drawable transpImage = new BitmapDrawable(activity.getResources(), transparentbmp);

        //


        Texture transpTexture = new Texture(transpImage);//new Texture(1024,1024, RGBColor.BLACK);

        txtManager.addTexture(transparentID,transpTexture);

        //TextureManager.getInstance().addTexture("panda", new Texture(getBitmapFromAssetsARGB8888(256,256,"gfx/alpha.png", AppContext), true);


        skybox = new SkyBox(transparentID,
                transparentID,
                transparentID,
                textureID,
                transparentID,
                transparentID,
                SKYBOX_DIM);
        //skybox.setCenter(world.getCamera().getPosition());
        SimpleVector skyBoxPos = new SimpleVector(world.getCamera().getPosition());
        //SimpleVector skyBoxPos = world.getCamera().getPosition();
        skyBoxPos.x=skyBoxPos.x+SKYBOX_DIM/2;
        skyBoxPos.y=skyBoxPos.y+SKYBOX_DIM/2;
        //skyBoxPos.z-=100;
        //skyBoxPos.z=skyBoxPos.z+SKYBOX_DIM/2-80;
        skybox.setCenter(skyBoxPos);
        Log.e("setUpSkyBox", "skybox created");

    }

    public void setUpFrameBuffer(int width, int height){

        if(frameBuffer!=null)frameBuffer.dispose();

        frameBuffer=new FrameBuffer(width, height);

    }


    public void createGroundPlane(){
        String id = "groundPlaneID";
        float x = 0, y=0, z=0;
        Log.e("ground:"+id, "CREATED at:"+x+" y:"+y+" z:"+z);
        TextureManager txtManager = TextureManager.getInstance();
        Texture txt = new Texture(128,128, RGBColor.BLACK);
        txtManager.addTexture(id, txt);


        int dim = 1;//(int) (500 / z);

        Object3D plane = Primitives.getPlane(1,128);

        plane.translate(x, y, z);
        plane.rotateX(30);
        plane.setTexture(id);
        plane.setName(id);
        world.addObject(plane);
        worldObjects.put(id, new TranslationObject(id, plane, x,y,z));

    }
    /*
    public void createGroundPlane(){

        String textureID = "groundTexture";

        TextureManager txtManager = TextureManager.getInstance();
        Drawable groundImage = activity.getResources().getDrawable(R.drawable.office);
        Texture groundTexture = new Texture(groundImage);
        txtManager.addTexture(textureID,groundTexture);
        //ground = Terrain.generateDynamicGround(128,128,activity.getResources(),txtManager,textureID,GROUND_ALTITUDE);

        ground=Primitives.getPlane(1, 1024);




        SimpleVector sv = world.getCamera().getPosition();
        ground.translate(sv.x, sv.y, sv.z);
        ground.setTexture(textureID);
        ground.setName(groundID);
        world.addObject(ground);
        groundTransformedCenter = ground.getTransformedCenter();
        Log.e("createGround", "ground created");

    }
    */

    public void createGround(){

        String textureID = "groundTexture";

        TextureManager txtManager = TextureManager.getInstance();
        Drawable groundImage = activity.getResources().getDrawable(R.drawable.office);
        Texture groundTexture = new Texture(groundImage);
        txtManager.addTexture(textureID,groundTexture);
        ground = Terrain.generateDynamicGround(128,128,activity.getResources(),txtManager,textureID,GROUND_ALTITUDE);



        int dim = 1;//(int) (500 / z);


        //cube.translate(x, y, z);
        ground.setTexture(textureID);
        ground.setName(groundID);
        world.addObject(ground);
        groundTransformedCenter = ground.getTransformedCenter();
        Log.e("createGround", "ground created");

    }

    public void createPrimitiveCube(String id, float x, float y, float z){
        Log.e("object:"+id, "CREATED at:"+x+" y:"+y+" z:"+z);
        TextureManager txtManager = TextureManager.getInstance();
        Texture txt;
        if(!txtManager.containsTexture(id)) {
            //txtManager.removeTexture(id);
            if(id.equals("rosina")){
                txt = new Texture(64,64,RGBColor.RED);
            }
            else if(id.equals("asobrero")){
                txt = new Texture(64,64,new RGBColor(255, 255, 0));

                txtManager.addTexture(id, txt);
                int dim = 20;//(int) (500 / z);

                Object3D cube = Primitives.getCube(dim);

                cube.translate(x, y, z);
                cube.setTexture(id);
                cube.setName(id);
                world.addObject(cube);
                return;
            }
            else if(id.equals("pirandello")){
                txt = new Texture(64,64,RGBColor.WHITE);
            }
            else if(id.equals("pirandello10")){
                txt = new Texture(64,64,RGBColor.GREEN);
            }
            else if(id.equals("traiano")){
                txt = new Texture(64,64,RGBColor.BLACK);
            }
            else if(id.equals("moscovio")){
                txt = new Texture(64,64,new RGBColor(144,132,53));
            }
            else if(id.equals("duomo")){
                txt = new Texture(64,64,new RGBColor(255,153,0));
            }
            else if(id.equals("nikila")){
                txt = new Texture(64,64,new RGBColor(223,115,255));
            }
            else if(id.equals("XAXIS")){
                txt = new Texture(64,64,new RGBColor(0,0,255));//BLU MYSPOT
            }
            else if(id.equals("XAXISBACK")){
                txt = new Texture(64,64,new RGBColor(179, 179, 255));
            }
            else if(id.equals("YAXIS")){
                txt = new Texture(64,64,new RGBColor(255, 0, 0));//ROSSO MLN
            }
            else if(id.equals("YAXISBACK")){
                txt = new Texture(64,64,new RGBColor(255, 179, 179));
            }
            else if(id.equals("ZAXIS")){
                txt = new Texture(64,64,new RGBColor(0, 255, 0));//VERDE su
            }
            else if(id.equals("ZAXISBACK")){
                txt = new Texture(64,64,new RGBColor(179, 255, 179));
            }

            else
                txt = new Texture(64,64,RGBColor.BLACK);

            txtManager.addTexture(id, txt);
        }
        else
            txt = txtManager.getTexture(id);

        int dim = 1;//(int) (500 / z);

        Object3D cube = Primitives.getCube(dim);

        cube.translate(x, y, z);
        cube.setTexture(id);
        cube.setName(id);
        world.addObject(cube);
        worldObjects.put(id, new TranslationObject(id, cube, x,y,z));

    }



    //GLSurfaceView.Renderer methods onSurfaceCreated onSurfaceChanged onDrawFrame
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        TextureManager.getInstance().flush();

        setUpWorld();


        manageObjectsCreation();
        createCubesOnTheJPCTAxis();
        //createGroundPlane();
        /*
        Runnable r = new Runnable() {
            @Override
            public void run() {

                createGround();

            }
        };
        Thread t = new Thread(r);
        t.start();
        */

        //lastLocation = gpsLocator.requestLocationUpdate();
        //manageMovementUpdate();
        //world.getCamera().lookAt(fakeCubeOnZAxis.getTransformedCenter());
        //polyline disegnata sopra tutto il resto e non coinvolta nelle collisioni
        //Polyline p = new Polyline();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        setUpFrameBuffer(width, height);

    }



    @Override
    public void onDrawFrame(GL10 gl) {

        //check user interaction and reacts
        frameBuffer.clear(background);//set background
        //background is drawn first
        //this.setUpSkyBox();

        //perform lights and transformations to stored object
        //manageMovementUpdate();
        //manageGroundPositionUpdate();
        //manageObjectsPositionUpdate();
        //handleCameraPosition();
        handleCameraPositionSpherical();
        handleCameraRotations();
        world.renderScene(frameBuffer);
         //the scene is drawn on the frame buffer.
        skybox.render(world,frameBuffer);

        world.draw(frameBuffer);//also world.drawWireframe can be used to just draw the borders

        //stored image is presented onto the screen
        frameBuffer.display();
    }



    // transform gps-points to the correspending screen-points on the android device
    public void createCubesOnTheJPCTAxis(){


        final Double DISTANCE = 25.0;


        Double xx = DISTANCE;
        Double yx = 0.0;
        Double zx = 0.0;

        createPrimitiveCube("XAXIS", xx.floatValue(), yx.floatValue(), zx.floatValue());
        createPrimitiveCube("XAXISBACK", - xx.floatValue(), yx.floatValue(), zx.floatValue());

        Double xy = 0.0;
        Double yy = DISTANCE;
        Double zy = 0.0;

        createPrimitiveCube("YAXIS", xy.floatValue(), yy.floatValue(), zy.floatValue());
        createPrimitiveCube("YAXISBACK", xy.floatValue(), - yy.floatValue(), zy.floatValue());

        Double xz = 0.0;
        Double yz = 0.0;
        Double zz = DISTANCE;

        createPrimitiveCube("ZAXIS", xz.floatValue(), yz.floatValue(), zz.floatValue());
        createPrimitiveCube("ZAXISBACK", xz.floatValue(), yz.floatValue(), - zz.floatValue());



    }

    private Double toRad(Double value) {
        return value * Math.PI / 180;
    }

    // transform gps-points to the correspending screen-points on the android device
    /*
    mAzimuthView heading -> Z JPCT
    mPitchView -> x JPCT
    mRollView -> y JPCT
    */
    //distanza sull'asse
    public void manageObjectsCreation(){

        world.removeAllObjects();

        int attempt = 10;
        while(zeroLoc==null && attempt>0) {
            zeroLoc = gpsLocator.getLocation();
            attempt--;
        }


        if(zeroLoc!=null) {
            for (String targetID : simulation.getTargetLocations().keySet()) {
                Location target = simulation.getTargetLocations().get(targetID);

                float ray = zeroLoc.distanceTo(target);
                float bearingAngleOfView = head + (-1 * pitch);
                float azimuth = zeroLoc.bearingTo(target) - bearingAngleOfView;
                Double azim = toRad((double) azimuth);
                float altitude = 90 + roll;

                //if (facedown) {
                if(roll>-90 && roll < 90){//looking down
                    altitude = altitude * -1;
                }

                Double alti = toRad((double) altitude);

                //Log.e("isFacedown", facedown + "");

                Double x = ray * Math.sin(azim);
                Double z = ray * Math.cos(alti) * Math.cos(azim);
                Double y = -1 * ray * Math.cos(azim) * Math.sin(alti);

                createPrimitiveCube(targetID, x.floatValue(), y.floatValue(), z.floatValue());
            }
        }
        else{
            Log.e("JPCTWorldManager", "manageObjectsPositionUpdate: null location");
        }

    }



    /*
    public void handleCameraPosition(){


        Location newLoc = gpsLocator.getLocation();

        if(newLoc!=null) {
            for (String targetID : worldObjects.keySet()) {
                //Location target = simulation.getTargetLocations().get(targetID);
                if(zeroLoc==null){
                    //Log.e("manageObjPositionUpdate", "id:"+targetID+" target=null,it shouldn't");
                    continue;
                }
                float ray = newLoc.distanceTo(zeroLoc);
                float bearingAngleOfView = head + (-1 * pitch);
                float azimuth = newLoc.bearingTo(zeroLoc) - bearingAngleOfView;
                Double azim = toRad((double) azimuth);
                float altitude = 90 + roll;

                //if (facedown) {
                if(roll>-90 && roll < 90){//looking down
                    altitude = altitude * -1;
                }

                Double alti = toRad((double) altitude);

                //Log.e("isFacedown", facedown + "");

                Double x = ray * Math.sin(azim);
                Double y = -1 * ray * Math.cos(azim) * Math.sin(alti);
                Double z = ray * Math.cos(alti) * Math.cos(azim);

                z=z+CAMERA_HEIGHT;
                //Log.e("CAMERA POSITION", "x:"+x+" y:"+y+" z:"+z);

                world.getCamera().setPosition(x.floatValue(),y.floatValue(),z.floatValue());

                //createPrimitiveCube(targetID, x.floatValue(), y.floatValue(), z.floatValue());
            }
        }
        else{
            Log.e("JPCTWorldManager", "manageObjectsPositionUpdate: null location");
        }

    }
    */


    //http://tutorial.math.lamar.edu/Classes/CalcIII/SphericalCoords.aspx
    public void handleCameraPositionSpherical(){
        //if(true)return;

        //Log.e("handleCamPosSpherical", "1");
        Location newLoc = new Location(gpsLocator.getLocation());

        if(newLoc!=null) {
                //Log.e("handleCamPosSpherical", "2");
                //Location target = simulation.getTargetLocations().get(targetID);
                if(zeroLoc==null){
                    //Log.e("handleCamPosSpherical", "zeroLOC==null");
                    //zeroLoc=gpsLocator.requestLocationUpdate();
                    return;
                }
                //p stands for ro
                float p = zeroLoc.distanceTo(newLoc);
                if(p<1)return;
                //float bearingAngleOfView = head + (-1 * pitch);
                //float azimuth = newLoc.bearingTo(zeroLoc) - bearingAngleOfView;
                //Log.e("bearingAngleOfView",bearingAngleOfView+"");

                float theta = zeroLoc.bearingTo(newLoc);// - bearingAngleOfView;
                //Log.e("theta", theta+"");
                Double thetaD = toRad((double) theta);
                //float altitude = 90 + roll;
                float phi = 90 + roll;

                //if (facedown) {
                if(roll>-90 && roll < 90){//looking down
                    phi = phi * -1;
                }


                Double z = newLoc.getAltitude() - zeroLoc.getAltitude();
                // z == p * Math.cos(phiD);
                // cosPhi = z/p
                //Log.e("handleCamPosSpherical", "z/p="+z.floatValue()+"/"+p);
                Double cosPhi;
                if(p!=0)
                    cosPhi = z/p;
                else
                    cosPhi=0.0;
                //Log.e("handleCamPosSpherical", "cosPhi="+cosPhi);
                //Double phiD = toRad((double) phi);
                Double phiD = Math.acos(cosPhi);

                //double r = p * Math.sin(phiD);
                //Log.e("isFacedown", facedown + "");


                //the code would be this...
                Double x = p * Math.sin(phiD) * Math.cos(thetaD);
                Double y = p * Math.sin(phiD) * Math.sin(thetaD);

                z=z+CAMERA_HEIGHT;
                //Log.e("CAMERA POSITION", "x:"+x+" y:"+y+" z:"+z);


                if(x.floatValue()-xCAMERA>=1 || y.floatValue()-yCAMERA>=1 || z.floatValue()-zCAMERA>=1) {

                    xCAMERA = x.floatValue();
                    yCAMERA = y.floatValue();
                    zCAMERA = z.floatValue();

                    world.getCamera().setPosition(xCAMERA, yCAMERA, zCAMERA);

                    TextureFromCameraActivity.MainHandler mainH = activity.getMainHandler();
                    if (mainH != null) {

                        //if(zeroLoc==null)
                        boolean zerolocisnull = zeroLoc==null;
                        String vcoors = "x:"+xCAMERA+"\n"+
                                        "y:"+yCAMERA+"\n"+
                                        "z:"+zCAMERA+"\n"+
                                        "zerolocisNULL="+zerolocisnull+"\n"+
                                        "accuracyRay="+newLoc.getAccuracy();
                        mainH.sendVirtualCoordinates(vcoors);
                    }

                }
            //Log.e("handleCamPosSpherical", "cam pos:  "+x.floatValue()+" "+y.floatValue()+" "+z.floatValue() );
                //createPrimitiveCube(targetID, x.floatValue(), y.floatValue(), z.floatValue());

        }
        else{
            Log.e("handleCamPosSpherical", "newLoc null location");
        }

    }


    // transform gps-points to the correspending screen-points on the android device
    /*
    mAzimuthView heading -> Z JPCT
    mPitchView -> x JPCT
    mRollView -> y JPCT
    */
    //distanza sull'asse
    /*
    public void manageObjectsPositionUpdate(){


        Location myLoc = gpsLocator.getLocation();

        if(myLoc!=null) {
            for (String targetID : worldObjects.keySet()) {
                Location target = simulation.getTargetLocations().get(targetID);
                if(target==null){
                    //Log.e("manageObjPositionUpdate", "id:"+targetID+" target=null,it shouldn't");
                    continue;
                }
                float ray = myLoc.distanceTo(target);
                float bearingAngleOfView = head + (-1 * pitch);
                float azimuth = myLoc.bearingTo(target) - bearingAngleOfView;
                Double azim = toRad((double) azimuth);
                float altitude = 90 + roll;

                //if (facedown) {
                if(roll>-90 && roll < 90){//looking down
                    altitude = altitude * -1;
                }

                Double alti = toRad((double) altitude);

                //Log.e("isFacedown", facedown + "");

                Double x = ray * Math.sin(azim);
                Double z = ray * Math.cos(alti) * Math.cos(azim);
                Double y = -1 * ray * Math.cos(azim) * Math.sin(alti);

                TranslationObject obj = worldObjects.get(targetID);
                moveObjectToNewPosition(obj, x.floatValue(), y.floatValue(), z.floatValue());
                //createPrimitiveCube(targetID, x.floatValue(), y.floatValue(), z.floatValue());
            }
        }
        else{
            Log.e("JPCTWorldManager", "manageObjectsPositionUpdate: null location");
        }

    }

    public void manageGroundPositionUpdate(){


        Location myLoc = gpsLocator.getLocation();

        if(myLoc!=null) {

                //Location target = simulation.getTargetLocations().get(targetID);
                if(lastLocation==null){
                    //Log.e("manageObjPositionUpdate", "id:"+targetID+" target=null,it shouldn't");
                    return;
                }
                float ray = myLoc.distanceTo(lastLocation);
                float bearingAngleOfView = head + (-1 * pitch);
                float azimuth = myLoc.bearingTo(lastLocation) - bearingAngleOfView;
                Double azim = toRad((double) azimuth);
                float altitude = 90 + roll;

                //if (facedown) {
                if(roll>-90 && roll < 90){//looking down
                    altitude = altitude * -1;
                }

                Double alti = toRad((double) altitude);

                //Log.e("isFacedown", facedown + "");

                Double x = ray * Math.sin(azim);
                Double z = ray * Math.cos(alti) * Math.cos(azim);
                Double y = -1 * ray * Math.cos(azim) * Math.sin(alti);


                moveObjectToNewPosition(ground, groundTransformedCenter, x.floatValue(), y.floatValue(), z.floatValue());
                //createPrimitiveCube(targetID, x.floatValue(), y.floatValue(), z.floatValue());

        }
        else{
            Log.e("JPCTWorldManager", "manageObjectsPositionUpdate: null location");
        }


    }

    private void moveObjectToNewPosition(TranslationObject toBeMoved,float px, float py, float pz){

        Log.e("moveObjectToNewPosition","translation");
        float x = px-toBeMoved.x;
        float y = py-toBeMoved.y;
        float z = pz-toBeMoved.z;

        toBeMoved.obj.translate(x,y,z);

        toBeMoved.x=x;
        toBeMoved.y=y;
        toBeMoved.z=z;
    }

    private void moveObjectToNewPosition(Object3D toBeMoved, SimpleVector oldPos,float px, float py, float pz){

        Log.e("moveObjectToNewPosition","translation");
        float x = px-oldPos.x;
        float y = py-oldPos.y;
        float z = pz-oldPos.z;

        toBeMoved.translate(x,y,z);


        oldPos.x=x;
        oldPos.y=y;
        oldPos.z=z;

    }
    */


    /*
    // method used for debug purposes
    float grade = 0;
    public void panoramicView(){
        if(grade>=360)grade=0;
        world.getCamera().rotateCameraY(toRad(new Double(grade)).floatValue());
        grade++;
    }
    */





    //IPaintListener
    /*
        you have two methods that will be called before and after a frame is being drawn.
        i.e it can be used to count frames per second
    */



    private boolean landscape=true;
    private float[] rotationMatrix = null;
    public void setRotationMatrix(float[] mat){
        rotationMatrix=mat;
    }

    public void handleCameraRotations(){

        if(rotationMatrix==null)return;

        //Log.e("handleCameraRotations()", "HANDLED");
        Camera cam = world.getCamera();

        if (landscape) {
            // in landscape mode first remap the
            // rotationMatrix before using
            // it with camera.setBack:
            float[] result = new float[9];
            SensorManager.remapCoordinateSystem(
                    rotationMatrix, SensorManager.AXIS_MINUS_Y,
                                    SensorManager.AXIS_MINUS_X, result);
            com.threed.jpct.Matrix mResult = new com.threed.jpct.Matrix();
            copyMatrix(result, mResult);
            cam.setBack(mResult);
            //Log.e("Camera is looking at:")

        } else {
            // WARNING: This solution doesn't work in portrait mode
            // See the explanation below
        }

    }

    private void copyMatrix(float[] src, com.threed.jpct.Matrix dest) {
        dest.setRow(0, src[0], src[1], src[2],   0);
        dest.setRow(1, src[3], src[4], src[5],   0);
        dest.setRow(2, src[6], src[7], src[8],   0);
        dest.setRow(3,     0f,     0f,     0f,  1f);
    }


    int axis = 2;
    public void getCameraPosition(){




        SimpleVector s = world.getCamera().getUpVector();
        Log.e("JPCT:getUpVector", "x:"+s.x+" y:"+s.y+" z:"+s.z);

        //axis++;
    }


    // Convert transparentColor to be transparent in a Bitmap.
    public static Bitmap makeTransparent(Bitmap bit) {
        int width =  bit.getWidth();
        int height = bit.getHeight();
        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int [] allpixels = new int [ myBitmap.getHeight()*myBitmap.getWidth()];
        bit.getPixels(allpixels, 0, myBitmap.getWidth(), 0, 0, myBitmap.getWidth(),myBitmap.getHeight());
        myBitmap.setPixels(allpixels, 0, width, 0, 0, width, height);

        for(int i =0; i<myBitmap.getHeight()*myBitmap.getWidth();i++){
            //if( allpixels[i] == transparentColor)

                allpixels[i] = Color.alpha(Color.TRANSPARENT);
        }

        myBitmap.setPixels(allpixels, 0, myBitmap.getWidth(), 0, 0, myBitmap.getWidth(), myBitmap.getHeight());
        return myBitmap;
    }

    private class TranslationObject{
        public String objID;
        public Object3D obj;
        public float x, y, z;

        public TranslationObject(String objID, Object3D obj, float x, float y, float z){

            this.objID=objID;
            this.obj=obj;
            this.x=x;
            this.y=y;
            this.z=z;

        }



    }

}

package com.mousebird.maply;

import java.util.ArrayList;
import java.util.List;

import android.app.*;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Looper;
import android.view.*;
import android.widget.Toast;

/**
 * The MaplyController is the main object in the Maply library.  Toolkit
 * users add and remove their geometry through here.
 * <p>
 * The controller starts by creating an OpenGL ES surface and handling
 * all the various setup between Maply and that surface.  It also kicks off
 * a LayerThread, which it uses to queue requests to the rest of Maply.
 * <p>
 * Once the controller is set up and running the toolkit user can make
 * calls to add and remove geometry.  Those calls are thread safe.
 * 
 * @author sjg
 *
 */
public class MaplyController implements View.OnTouchListener
{	
	private GLSurfaceView glSurfaceView;
	Activity activity = null;
	
	// Represents an ID that doesn't have data associated with it
	public static long EmptyIdentity = 0;
	
	/**
	 * This is how often we'll kick off a render when the frame sync comes in.
	 * We get a notification when the render for a given frame starts, this is
	 * usually 60 times a second.  This tells us how many to skip to achieve
	 * our desired frame rate.  2 means 30hz.  3 means 20hz and so forth.
	 */
	public int frameInterval = 2;
	
	// Implements the GL renderer protocol
	RendererWrapper renderWrapper;
	
	// Coordinate system to display conversion
	CoordSystemDisplayAdapter coordAdapter;
	
	// Scene stores the objects
	MapScene mapScene;
	
	// MapView defines how we're looking at the data
	MapView mapView;

	// Managers are thread safe objects for handling adding and removing types of data
	VectorManager vecManager;
	MarkerManager markerManager;
	LabelManager labelManager;
	
	// Manage bitmaps and their conversion to textures
	TextureManager texManager = new TextureManager();
	
	// Layer thread we use for data manipulation
	LayerThread layerThread = null;
	
	// Gesture handler
	GestureHandler gestureHandler = null;
	
	// Bounding box we're allowed to move within
	Point2d viewBounds[] = null;
	
	/**
	 * Returns the layer thread we used for processing requests.
	 */
	public LayerThread getLayerThread() { return layerThread; }
	
	/**
	 * Construct the maply controller with an Activity.  We need access to a few
	 * of the usual Android resources.
	 * <p>
	 * On construction the Controller will create the scene, the view, kick off
	 * the OpenGL ES surface, and construct a layer thread for handling data
	 * requests.
	 * <p>
	 * The controller also sets up some default gestures and handles those
	 * callbacks.
	 * <p>
	 * @param mainActivity Your main activity that we'll attach ourselves to.
	 */
	public MaplyController(Activity mainActivity) 
	{
//		System.loadLibrary("Maply");
		activity = mainActivity;
		
		// Need a coordinate system to display conversion
		// For now this just sets up spherical mercator
		coordAdapter = new CoordSystemDisplayAdapter(new SphericalMercatorCoordSystem());

		// Create the scene and map view 
		mapScene = new MapScene(coordAdapter);
		mapView = new MapView(coordAdapter);		
		
		// Fire up the managers.  Can't do anything without these.
		vecManager = new VectorManager(mapScene);
		markerManager = new MarkerManager(mapScene);
		labelManager = new LabelManager(mapScene);

		// Now for the object that kicks off the rendering
		renderWrapper = new RendererWrapper(this);
		renderWrapper.mapScene = mapScene;
		renderWrapper.mapView = mapView;
		
		// Set up the bounds
		Point3d ll = new Point3d(),ur = new Point3d();
		coordAdapter.getBounds(ll,ur);
		// Allow E/W wraping
		ll.setValue(Float.MAX_VALUE, ll.getY(), ll.getZ());
		ur.setValue(-Float.MAX_VALUE, ur.getY(), ur.getZ());
		setViewExtents(new Point2d(ll.getX(),ll.getY()),new Point2d(ur.getX(),ur.getY()));

		// Create the layer thread
        layerThread = new LayerThread("Maply Layer Thread",mapView,mapScene);
		
        ActivityManager activityManager = (ActivityManager) mainActivity.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        
        final boolean supportsEs2 = configurationInfo.reqGlEsVersion > 0x20000 || isProbablyEmulator();
        if (supportsEs2)
        {
        	glSurfaceView = new GLSurfaceView(mainActivity);
        	glSurfaceView.setOnTouchListener(this);
        	gestureHandler = new GestureHandler(this,glSurfaceView);
        	
        	if (isProbablyEmulator())
        	{
        		glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        	}
        	
        	glSurfaceView.setEGLContextClientVersion(2);
        	glSurfaceView.setRenderer(renderWrapper);        	        

        	// Attach to the Choreographer on the main thread
        	// Note: I'd rather do this on the render thread, but there's no Looper
//        	Choreographer.getInstance().postFrameCallbackDelayed(this, 15);
        } else {
        	Toast.makeText(mainActivity,  "This device does not support OpenGL ES 2.0.", Toast.LENGTH_LONG).show();
        	return;
        }        
	}
	
	/**
	 * Return the main content view used to represent the Maply Control.
	 */
	public View getContentView()
	{
		return glSurfaceView;
	}
	
	// Tear down views
	public void shutdown()
	{
//		Choreographer.getInstance().removeFrameCallback(this);
		layerThread.shutdown();
		
		glSurfaceView = null;
		renderWrapper = null;
		coordAdapter = null;
		mapScene = null;
		mapView = null;
		vecManager = null;
		markerManager = null;
		texManager = null;
	}
	
	// Called by the render wrapper when the surface is created.
	// Can't start doing anything until that happens
	void surfaceCreated(RendererWrapper wrap)
	{
        // Kick off the layer thread for background operations
		layerThread.setRenderer(renderWrapper.maplyRender);
		renderWrapper.maplyRender.setPerfInterval(perfInterval);

		// Set up a periodic update for the renderer
//    	glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

//	long lastRender = 0;
//	// Called by the Choreographer to render a frame
//	@Override
//	public void doFrame(long frameTimeNanos) 
//	{		
//		// Need to do this every frame
//    	Choreographer.getInstance().postFrameCallbackDelayed(this, 15);
//
//    	// Compare how long since the last render
//    	long diff = frameTimeNanos - lastRender;
//		if (diff >= 16666700*(frameInterval-0.25))
//		{
//			glSurfaceView.queueEvent(new Runnable()
//			{
//				public void run()
//				{
//					glSurfaceView.requestRender();
//				}
//			});
//			glSurfaceView.requestRender();
//			lastRender = frameTimeNanos;
//		}
//	}
			
	public void dispose()
	{
		vecManager.dispose();
		vecManager = null;
		
		mapScene.dispose();
		mapScene = null;
		mapView.dispose();
		mapView = null;
		
		renderWrapper = null;
	}

	/**
	 * Set the viewport the user is allowed to move within.  These are lat/lon coordinates
	 * in radians.
	 * @param ll Lower left corner.
	 * @param ur Upper right corner.
	 */
	void setViewExtents(Point2d ll,Point2d ur)
	{
		CoordSystemDisplayAdapter coordAdapter = mapView.getCoordAdapter();
		CoordSystem coordSys = coordAdapter.getCoordSystem();
		
		viewBounds = new Point2d[4];
		viewBounds[0] = coordAdapter.localToDisplay(coordSys.geographicToLocal(new Point3d(ll.getX(),ll.getY(),0.0))).toPoint2d();
		viewBounds[1] = coordAdapter.localToDisplay(coordSys.geographicToLocal(new Point3d(ur.getX(),ll.getY(),0.0))).toPoint2d();
		viewBounds[2] = coordAdapter.localToDisplay(coordSys.geographicToLocal(new Point3d(ur.getX(),ur.getY(),0.0))).toPoint2d();
		viewBounds[3] = coordAdapter.localToDisplay(coordSys.geographicToLocal(new Point3d(ll.getX(),ur.getY(),0.0))).toPoint2d();
	}
	
	// Pass the touches on to the gesture handler
	@Override
	public boolean onTouch(View view, MotionEvent e) {
		return gestureHandler.onTouch(view, e);
	}
	
	int perfInterval = 0;
	/**
	 * Report performance stats in the console ever few frames.
	 * Setting this to zero turns it off.
	 * @param perfInterval
	 */
	public void setPerfInterval(int inPerfInterval)
	{
		perfInterval = inPerfInterval;
		if (renderWrapper != null && renderWrapper.maplyRender != null)
			renderWrapper.maplyRender.setPerfInterval(perfInterval);
	}

	/**
	 * Add a single VectorObject.  See addVectors() for details.
	 */
	public ComponentObject addVector(final VectorObject vec,final VectorInfo vecInfo)
	{
		ArrayList<VectorObject> vecObjs = new ArrayList<VectorObject>();
		vecObjs.add(vec);
		return addVectors(vecObjs,vecInfo);
	}

	/**
	 * Add vectors to the MaplyController to display.  Vectors are linear or areal
	 * features with line width, filled style, color and so forth defined by the
	 * VectorInfo class.
	 * 
	 * @param vecs A list of VectorObject's created by the user or read in from various sources.
	 * @param vecInfo A description of how the vectors should look.
	 * @return The ComponentObject representing the vectors.  This is necessary for modifying
	 * or deleting the vectors once created.
	 */
	public ComponentObject addVectors(final List<VectorObject> vecs,final VectorInfo vecInfo)
	{
		final ComponentObject compObj = new ComponentObject();
		
		// Do the actual work on the layer thread
		Runnable run =
		new Runnable()
		{		
			@Override
			public void run()
			{
				// Vectors are simple enough to just add
				ChangeSet changes = new ChangeSet();
				long vecId = vecManager.addVectors(vecs,vecInfo,changes);
				mapScene.addChanges(changes);
	
				// Track the vector ID for later use
				if (vecId != EmptyIdentity)
					compObj.addVectorID(vecId);
			}
		};
		if (Looper.myLooper() == layerThread.getLooper())
			run.run();
		else
			layerThread.addTask(run);
		
		return compObj;
	}

	/**
	 * Add a single screen marker.  See addScreenMarkers() for details.
	 */
	public ComponentObject addScreenMarker(final ScreenMarker marker,final MarkerInfo markerInfo)
	{
		ArrayList<ScreenMarker> markers = new ArrayList<ScreenMarker>();
		markers.add(marker);
		return addScreenMarkers(markers,markerInfo);
	}

	/**
	 * Add screen markers to the visual display.  Screen markers are 2D markers that sit
	 * on top of the screen display, rather than interacting with the geometry.  Their
	 * visual look is defined by the MarkerInfo class.
	 * 
	 * @param markers The markers to add to the display
	 * @param markerInfo How the markers should look.
	 * @return This represents the screen markers for later modification or deletion.
	 */
	public ComponentObject addScreenMarkers(final List<ScreenMarker> markers,final MarkerInfo markerInfo)
	{		
		final ComponentObject compObj = new ComponentObject();

		// Do the actual work on the layer thread
		Runnable run =
		new Runnable()
		{		
			@Override
			public void run()
			{
				ChangeSet changes = new ChangeSet();
		
				// Convert to the internal representation of the engine
				ArrayList<InternalMarker> intMarkers = new ArrayList<InternalMarker>();
				for (ScreenMarker marker : markers)
				{
					InternalMarker intMarker = new InternalMarker(marker,markerInfo);
					// Map the bitmap to a texture ID
					long texID = EmptyIdentity;
					if (marker.image != null)
						texID = texManager.addTexture(marker.image, changes);
					if (texID != EmptyIdentity)
						intMarker.addTexID(texID);
					
					intMarkers.add(intMarker);
				}
						
				// Add the markers and flush the changes
				long markerId = markerManager.addMarkers(intMarkers, markerInfo, changes);
				mapScene.addChanges(changes);
				
				if (markerId != EmptyIdentity)
				{
					compObj.addMarkerID(markerId);
				}
			}
		};
		if (Looper.myLooper() == layerThread.getLooper())
			run.run();
		else
			layerThread.addTask(run);

		return compObj;
	}

	/**
	 * Add a single screen label.  See addScreenLabels() for details.
	 */
	public ComponentObject addScreenLabel(ScreenLabel label,final LabelInfo labelInfo)
	{
		ArrayList<ScreenLabel> labels = new ArrayList<ScreenLabel>();
		labels.add(label);
		return addScreenLabels(labels,labelInfo);
	}

	/**
	 * Add screen labels to the display.  Screen labels are 2D labels that float above the 3D geometry
	 * and stay fixed in size no matter how the user zoom in or out.  Their visual appearance is controlled
	 * by the LabelInfo class.
	 * 
	 * @param labels Labels to add to the display.
	 * @param labelInfo The visual appearance of the labels.
	 * @return This represents the labels for modification or deletion.
	 */
	public ComponentObject addScreenLabels(final List<ScreenLabel> labels,final LabelInfo labelInfo)
	{
		final ComponentObject compObj = new ComponentObject();

		// Do the actual work on the layer thread
		Runnable run =
		new Runnable()
		{		
			@Override
			public void run()
			{
				ChangeSet changes = new ChangeSet();
				
				// Convert to the internal representation for the engine
				ArrayList<InternalLabel> intLabels = new ArrayList<InternalLabel>();
				for (ScreenLabel label : labels)
				{
					InternalLabel intLabel = new InternalLabel(label,labelInfo);
					intLabels.add(intLabel);
				}

				long labelId = labelManager.addLabels(intLabels, labelInfo, changes);
				if (labelId != EmptyIdentity)
					compObj.addLabelID(labelId);
		
				// Flush the text changes
				mapScene.addChanges(changes);
			}
		};
		if (Looper.myLooper() == layerThread.getLooper())
			run.run();
		else
			layerThread.addTask(run);
		
		return compObj;
	}

	/**
	 * Disable the given objects. These were the objects returned by the various
	 * add calls.  Once called, the objects will be invisible, but can be made
	 * visible once again with enableObjects()
	 * 
	 * @param compObjs Objects to disable in the display.
	 */
	public void disableObjects(final List<ComponentObject> compObjs)
	{
		if (compObjs == null || compObjs.size() == 0)
			return;
		
		final MaplyController control = this;
		Runnable run = new Runnable()
		{		
			@Override
			public void run()
			{
				ChangeSet changes = new ChangeSet();
				for (ComponentObject compObj : compObjs)
					compObj.enable(control, false, changes);
				mapScene.addChanges(changes);
			}
		};
		if (Looper.myLooper() == layerThread.getLooper())
			run.run();
		else
			layerThread.addTask(run);
	}

	/**
	 * Enable the display for the given objects.  These objects were returned
	 * by the various add calls.  To disable the display, call disableObjects().
	 *
	 * @param compObjs Objects to disable.
	 */
	public void enableObjects(final List<ComponentObject> compObjs)
	{
		if (compObjs == null || compObjs.size() == 0)
			return;
		
		final MaplyController control = this;
		Runnable run = 
		new Runnable()
		{		
			@Override
			public void run()
			{
				ChangeSet changes = new ChangeSet();
				for (ComponentObject compObj : compObjs)
					compObj.enable(control, true, changes);
				mapScene.addChanges(changes);
			}
		};
		if (Looper.myLooper() == layerThread.getLooper())
			run.run();
		else
			layerThread.addTask(run);
	}

	/**
	 * Remove a single objects from the display.  See removeObjects() for details.
	 */
	public void removeObject(final ComponentObject compObj)
	{
		if (compObj == null)
			return;

		ArrayList<ComponentObject> compObjs = new ArrayList<ComponentObject>();
		compObjs.add(compObj);
		removeObjects(compObjs);
	}

	/**
	 * Remove the given component objects from the display.  This will permanently remove them
	 * from Maply.  The component objects were returned from the various add calls.
	 * 
	 * @param compObjs Component Objects to remove.
	 */
	public void removeObjects(final List<ComponentObject> compObjs)
	{
		if (compObjs == null || compObjs.size() == 0)
			return;
		
		final MaplyController control = this;
		Runnable run =
		new Runnable()
		{		
			@Override
			public void run()
			{
				ChangeSet changes = new ChangeSet();
				for (ComponentObject compObj : compObjs)
					compObj.clear(control, changes);
				mapScene.addChanges(changes);
			}
		};
		if (Looper.myLooper() == layerThread.getLooper())
			run.run();
		else
			layerThread.addTask(run);
	}
	
	/**
	 * Set the current view position.
	 * @param x Horizontal location of the center of the screen in radians (not degrees).
	 * @param y Vertical location of the center of the screen in radians (not degrees).
	 * @param z Height above the map in display units.
	 */
	public void setPosition(double x,double y,double z)
	{
		mapView.cancelAnimation();
		mapView.setLoc(new Point3d(x,y,z));
	}
	
    private boolean isProbablyEmulator() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
                && (Build.FINGERPRINT.startsWith("generic")
                        || Build.FINGERPRINT.startsWith("unknown")
                        || Build.MODEL.contains("google_sdk")
                        || Build.MODEL.contains("Emulator")
                        || Build.MODEL.contains("Android SDK built for x86"));
    }
}
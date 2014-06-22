package com.sybco.spellbeecast;

import java.io.IOException;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

/*
 * The following sections cover the details of the typical execution flow for a sender application; here is a high-level list of the steps:

Sender app starts MediaRouter device discovery: MediaRouter.addCallback
MediaRouter informs sender app of the route the user selected: MediaRouter.Callback.onRouteSelected
Sender app retrieves CastDevice instance: CastDevice.getFromBundle
Sender app creates a GoogleApiClient: GoogleApiClient.Builder
Sender app connects the GoogleApiClient: GoogleApiClient.connect
SDK confirms that GoogleApiClient is connected: GoogleApiClient.ConnectionCallbacks.onConnected
Sender app launches the receiver app: Cast.CastApi.launchApplication
SDK confirms that the receiver app is connected: ResultCallback<Cast.ApplicationConnectionResult>
Sender app creates a communication channel: Cast.CastApi.setMessageReceivedCallbacks
Sender sends a message to the receiver over the communication channel: Cast.CastApi.sendMessage
 */

public class MainActivity extends ActionBarActivity {

	
	private static final String TAG = MainActivity.class.getSimpleName();
	
	private MediaRouter mediaRouter;
	private MediaRouteSelector mediaRouteSelector;
	
	private MediaRouterCallback mediaRouterCallback;
	private HelloWorldChannel helloWorldChannel;
	
	
	private String sessionId;
	
	private CastDevice selectedDevice;
	private GoogleApiClient apiClient;
	
	private boolean applicationStarted;
	private boolean waitingForReconnect;
	
	
	

	
	private class HelloWorldChannel implements Cast.MessageReceivedCallback {

		public String getNamespace() {
			return "urn:x-cast:com.sybco.spellbeecast";
		}
		
		@Override
		public void onMessageReceived(CastDevice castDevice, String namespace,
				String message) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onMessageReceived: " + message);
		}
		
	}
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			
			PlaceholderFragment fragment = new PlaceholderFragment();
			fragment.setActivity(this);
			
			getFragmentManager().beginTransaction()
					.add(R.id.container, fragment).commit();
		}
		
		
		
		
		
		mediaRouter = MediaRouter.getInstance(getApplicationContext());
		mediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(getResources().getString(R.string.app_id))).build();
		mediaRouterCallback = new MediaRouterCallback();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		
		MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
		MediaRouteActionProvider mediaRouteActionProvider = 
		    (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
		
		mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		MainActivity activity;
		
		public PlaceholderFragment() {
		}
		
		public void setActivity(MainActivity act)
		{
			activity = act;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			final View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			
			
			Button button = (Button) rootView.findViewById(R.id.button1);
			button.setOnClickListener(new OnClickListener()
		    {
		      public void onClick(View v)
		      {
		    	  EditText txt = (EditText)rootView.findViewById(R.id.editText1);
		    	  activity.sendMessage(txt.getText().toString());
		      }
		    });
			
			return rootView;
		}
	}

	private class MediaRouterCallback extends MediaRouter.Callback {
		 @Override
		   public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route)
		   {
		       selectedDevice = CastDevice.getFromBundle(route.getExtras());
		       setSelectedDevice(selectedDevice);
		   }

		   @Override
		   public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route)
		   {
		       setSelectedDevice(null);
			   selectedDevice = null;
		   }
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
	}

	@Override
	protected void onPause() {
		if (isFinishing()) {
			mediaRouter.removeCallback(mediaRouterCallback);
		}
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		setSelectedDevice(null);
		super.onDestroy();
	}

	private void setSelectedDevice(CastDevice device)
	{
	    Log.d(TAG, "setSelectedDevice: " + device);

	    selectedDevice = device;

	    if (selectedDevice != null)
	    {
	    	try
		   	{
		   		 stopApplication();
		   		 disconnectApiClient();
		   		 connectApiClient();
		   	}
		   	catch (IllegalStateException e)
		   	{
		   		 Log.w(TAG, "Exception while connecting API client", e);
		   		 disconnectApiClient();
		   	}
	    }
	    else
	    {
		   	if (apiClient != null)
		   	{
		   		disconnectApiClient();
		   	}

		   	mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
	    }
	}
	
	private void connectApiClient()
	{
	    Cast.CastOptions apiOptions = Cast.CastOptions.builder(selectedDevice, castClientListener).build();
	    apiClient = new GoogleApiClient.Builder(this)
	   		 .addApi(Cast.API, apiOptions)
	   		 .addConnectionCallbacks(connectionCallback)
	   		 .addOnConnectionFailedListener(connectionFailedListener)
	   		 .build();
	    apiClient.connect();
	}

	private void disconnectApiClient() 
	{
	    if (apiClient != null)
	    {
	    	Cast.CastApi.stopApplication(apiClient, sessionId);
	    	
		   	apiClient.disconnect();
		   	apiClient = null;
	    }
	}

	private void stopApplication()
	{
	    if (apiClient == null) return;

	    if (applicationStarted)
	    {
	    	Cast.CastApi.stopApplication(apiClient);
	    	applicationStarted = false;
	    }
	}


	private final Cast.Listener castClientListener = new Cast.Listener()
	{
	    @Override
	    public void onApplicationDisconnected(int statusCode)
	    {
	    	setSelectedDevice(null);
	    }

	    @Override
	    public void onVolumeChanged()
	    {
	    }
	};

	private final GoogleApiClient.ConnectionCallbacks connectionCallback = new GoogleApiClient.ConnectionCallbacks()
	{
	    @Override
	    public void onConnected(Bundle bundle)
	    {
	    	Log.d(TAG, "onConnected");
		   	try
		   	{
		   		Cast.CastApi.launchApplication(apiClient, getResources().getString(R.string.app_id), false).setResultCallback(connectionResultCallback);
		   	}
		   	catch (Exception e)
		   	{
		   		Log.e(TAG, "Failed to launch application", e);
		   	}
	    }

	    @Override
	    public void onConnectionSuspended(int i)
	    {
	    }
	};
	
	private final GoogleApiClient.OnConnectionFailedListener connectionFailedListener = new GoogleApiClient.OnConnectionFailedListener()
	{
	    @Override
	    public void onConnectionFailed(ConnectionResult connectionResult)
	    {
	   	 setSelectedDevice(null);
	    }
	};

	private final ResultCallback<Cast.ApplicationConnectionResult> connectionResultCallback = new ResultCallback<Cast.ApplicationConnectionResult>()
	{
	    @Override
	    public void onResult(Cast.ApplicationConnectionResult result)
	    {
	    	Status status = result.getStatus();
	    	
	    	if (status.isSuccess())
	   	 	{
	    		ApplicationMetadata applicationMetadata = result.getApplicationMetadata();
	    		sessionId = result.getSessionId();
	   		 	applicationStarted = true;
	   		 	String applicationStatus = result.getApplicationStatus();
	   		 	boolean wasLaunched = result.getWasLaunched();
	   		 	helloWorldChannel = new HelloWorldChannel();
		   		try
		        {
		            Cast.CastApi.setMessageReceivedCallbacks(apiClient, helloWorldChannel.getNamespace(), helloWorldChannel);
		        }
		        catch (IOException e)
		        {
		            Log.e(TAG, "Exception while creating channel", e);
		        }
	   	 	}
	    	else
	    	{
	    		
	    	}
	    }

	};
	
	
	

	private void teardown() {
		Log.d(TAG, "teardown");
		
		if (apiClient != null) {
			if (applicationStarted) {
				if (apiClient.isConnected()) {
					try {
						Cast.CastApi.stopApplication(apiClient, sessionId);
		            
			            if (helloWorldChannel != null) {
			            	
			            	Cast.CastApi.removeMessageReceivedCallbacks(apiClient, helloWorldChannel.getNamespace());
			            	helloWorldChannel = null;
			            }
			            
					} catch (IOException e) {
		                 Log.e(TAG, "Exception while removing channel", e);
		            }
		          
					apiClient.disconnect();
		        }
				
		        applicationStarted = false;
		    }
			
			apiClient = null;
		}
		  
		selectedDevice = null;
		//waitingForReconnect = false;
		sessionId = null;
	}
	
	
	
	private void sendMessage(String message)
	
	{
	   if (apiClient != null && helloWorldChannel != null)
	   {
	       try
	       {
	           Cast.CastApi.sendMessage(apiClient, helloWorldChannel.getNamespace(), message)
	                   .setResultCallback(new ResultCallback<Status>()
	                   {
	                       @Override
	                       public void onResult(Status result)
	                       {
	                           if (!result.isSuccess())
	                           {
	                               Log.e(TAG, "Sending message failed");
	                           }
	                       }
	                   });
	       }
	       catch (Exception e)
	       {
	           Log.e(TAG, "Exception while sending message", e);
	       }
	   }
	}
	
}

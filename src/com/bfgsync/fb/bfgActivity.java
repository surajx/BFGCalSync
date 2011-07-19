package com.bfgsync.fb;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bfgsync.R.id;
import com.bfgsync.R.layout;
import com.bfgsync.fb.BaseRequestListener;
import com.bfgsync.fb.SessionEvents.AuthListener;
import com.bfgsync.fb.SessionEvents.LogoutListener;
import com.bfgsync.gcal.CalActivity;
import com.bfgsync.R;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import com.facebook.android.Util;

public class bfgActivity extends Activity {
	
	//APP_ID as received from FB apps
	public static final String APP_ID = "100917329990307";

	//Progress bar to show Fetch friends birthday progress.
    private ProgressBar mProgressBar;
    private int mProgress = 0;

	//UI controls
	private LoginButton mLoginButton;
	private TextView mText;
	private Button mRequestButton;
	
	//FB Objects
	private Facebook mFacebook;
	private AsyncFacebookRunner mAsyncRunner;

	//A collection for storing birthday Data.
	private HashMap<String,FFriend> mFriendData;
	
	//Flag variables.
	private boolean fLogout= false;
	private boolean fComplete= false;

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        //Setting UI controls
        mLoginButton = (LoginButton) findViewById(R.id.login);
        mText = (TextView) bfgActivity.this.findViewById(R.id.txt);
        mRequestButton = (Button) findViewById(R.id.requestButton);
        mProgressBar=(ProgressBar)findViewById(R.id.progressbar_Horizontal);
        mProgressBar.setVisibility(View.INVISIBLE);
        
        //Initialising Facebook controls.
        mFacebook = new Facebook(APP_ID);
        mAsyncRunner = new AsyncFacebookRunner(mFacebook);
        
        //Restore any any existing FB sessions.
        SessionStore.restore(mFacebook, this);
        
        //Creating Login/Logout Listners.
        SessionEvents.addAuthListener(new SampleAuthListener());
        SessionEvents.addLogoutListener(new SampleLogoutListener());
        
        
        mLoginButton.init(this, mFacebook, new String[] { "friends_birthday" });   
        mRequestButton. setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	if(!fComplete){
	            	final Bundle params = new Bundle();
	            	mProgressBar.setVisibility(View.VISIBLE);
	            	mRequestButton.setVisibility(View.INVISIBLE);
	            	mText.setText("Requesting birthday list...");
	                mAsyncRunner.request("me/friends", params, "GET", new FriendsListListener(), null);
            	}
            	else{
                	Intent gData = new Intent(bfgActivity.this, CalActivity.class);
                	Collection<FFriend> f_col= mFriendData.values();
                	FFriend fArray[] = new FFriend[mFriendData.size()]; 
                	fArray = f_col.toArray(fArray);
                	Bundle newAct = new Bundle();
                	for(int i=0;i<fArray.length;i++ )
                	{	                		
                		String val= fArray[i].getName()+":"+fArray[i].getBday();
                		newAct.putString(Integer.toString(i), val);
                	}
                	newAct.putString("tot", Integer.toString(fArray.length));
                	gData.putExtra("fBundle", newAct);
                	startActivity(gData);
            	}
            }
        });
        mRequestButton.setVisibility(mFacebook.isSessionValid() ?
                View.VISIBLE :
                View.INVISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        mFacebook.authorizeCallback(requestCode, resultCode, data);
    }
    
    public class SampleAuthListener implements AuthListener {

        public void onAuthSucceed() {
            mText.setText("You have logged in! ");
            mRequestButton.setVisibility(View.VISIBLE);
        }

        public void onAuthFail(String error) {
            mText.setText("Login Failed: " + error);
        }
    }

    public class SampleLogoutListener implements LogoutListener {
        public void onLogoutBegin() {
            mText.setText("Logging out...");
            fLogout = true;            
        }

        public void onLogoutFinish() {
            mText.setText("You have logged out! ");
            mRequestButton.setVisibility(View.INVISIBLE);
            mProgressBar.setVisibility(View.INVISIBLE);
        }
    }
    
    public class FriendsListListener extends BaseRequestListener {

        public void onComplete(final String response, final Object state) {
            try {
                Log.d("bfgActivity", "Response: " + response.toString());
                JSONObject json = Util.parseJson(response);
                JSONArray jArray = json.getJSONArray("data");                
                final int jArrayLen = jArray.length();
            	Bundle params = new Bundle();
            	params.putString("fields", "birthday");
            	mFriendData = new HashMap<String, FFriend>(jArrayLen);
            	final int progressStep = (int) mProgressBar.getMax()/jArrayLen;
                for (int i=0;i<jArrayLen && !fLogout;i++) 
                {
                	JSONObject fNameJSON= jArray.getJSONObject(i);
                	FFriend ff = new FFriend();
                	ff.setName(fNameJSON.getString("name"));
                	ff.setFID(fNameJSON.getString("id"));
                	mFriendData.put(ff.getFID(), ff);
                	FriendsBdayListener fBDListener = new FriendsBdayListener();
                    try {
                        String resp = mFacebook.request(ff.getFID(), params, "GET");                        
                        fBDListener.onComplete(resp, state);
                    } catch (FileNotFoundException e) {
                    	fBDListener.onFileNotFoundException(e, state);
                    } catch (MalformedURLException e) {
                    	fBDListener.onMalformedURLException(e, state);
                    } catch (IOException e) {
                    	fBDListener.onIOException(e, state);
                    }
                    bfgActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            mProgress = mProgress + progressStep;
                            mProgressBar.setProgress(mProgress);
                        }
                    });
                }
                if(!fLogout) fComplete= true;            
                bfgActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                    	if(!fLogout) {
                    		mRequestButton.setVisibility(View.VISIBLE);
                    		mText.setText("Birthday Retrieval Complete!");                    		
                    		mRequestButton.setText("Continue...");
                    	}
                    	else {
                    		mRequestButton.setVisibility(View.INVISIBLE);
                    		mText.setText("Birthday Retrieval Incomplete!");
                    	}
                    	mProgressBar.setVisibility(View.INVISIBLE);
                    }
                });
            } catch (JSONException e) {
                Log.w("bfgActivity", "JSON Error in response");
            } catch (FacebookError e) {
                Log.w("bfgActivity", "Facebook Error: " + e.getMessage());
            }
        }
    }
    
    public class FriendsBdayListener extends BaseRequestListener {

        public void onComplete(final String response, final Object state) {
        	FFriend ff = new FFriend();
            try {
                Log.d("bfgActivity", "Response: " + response.toString());
                JSONObject json = Util.parseJson(response);
                ff = (FFriend)mFriendData.get(json.getString("id"));
                final String frndsBday = json.getString("birthday");
                final String frndsName = ff.getName();
                ff.setBday(frndsBday);
                bfgActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        mText.setText(frndsName+"'s B'Day: " + frndsBday);
                    }
                });
                mFriendData.remove(json.getString("id"));
                mFriendData.put(ff.getFID(), ff);
            } catch (JSONException e) {
            	ff.setBday("");
            	mFriendData.remove(ff.getFID());
                mFriendData.put(ff.getFID(), ff);
                Log.w("bfgActivity", "JSON ERROR: Possibly birthday not available.");
            } catch (FacebookError e) {
                Log.w("bfgActivity", "Facebook Error: " + e.getMessage());
            }
        }
    }
}
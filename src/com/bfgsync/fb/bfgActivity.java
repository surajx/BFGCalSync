package com.bfgsync.fb;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bfgsync.fb.BaseRequestListener;
import com.bfgsync.fb.SessionEvents.AuthListener;
import com.bfgsync.fb.SessionEvents.LogoutListener;
import com.bfgsync.R;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import com.facebook.android.Util;

public class bfgActivity extends Activity 
{
	
	//APP_ID as received from FB apps
	public static final String APP_ID = "100917329990307";

	//Progress bar to show Fetch friends birthday progress.
    private ProgressBar mProgressBar;
    private int mProgress = 0;

	//UI controls
	private LoginButton mLoginButton;
	private TextView mText;
	private Button mRequestButton;
	private Button mCancelButton;
	QueryHandler mQueryHandler;
	private long calID;
	MyCalendarChooser mCalendar;
	
	//FB Objects
	private Facebook mFacebook;
	private AsyncFacebookRunner mAsyncRunner;
	
	//Flag variables.
	private boolean fLogout= false;
	private boolean fCancel= false;
	private boolean fProgress;

	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        //Setting UI controls
        mLoginButton = (LoginButton) findViewById(R.id.login);
        mText = (TextView) findViewById(R.id.txt);
        mRequestButton = (Button) findViewById(R.id.requestButton);
        mCancelButton = (Button) findViewById(R.id.cancelButton);
        mProgressBar=(ProgressBar)findViewById(R.id.progressbar_Horizontal);
        mQueryHandler = new QueryHandler(this);
        
        //Initialising Facebook controls.
        mFacebook = new Facebook(APP_ID);
        mAsyncRunner = new AsyncFacebookRunner(mFacebook);
        
        //Restore any any existing FB sessions.
        SessionStore.restore(mFacebook, this);
        
        //Creating Login/Logout Listners.
        SessionEvents.addAuthListener(new SampleAuthListener());
        SessionEvents.addLogoutListener(new SampleLogoutListener());
        
        
        mLoginButton.init(this, mFacebook, new String[] { "friends_birthday" });   
        mRequestButton.setOnClickListener(
        		new OnClickListener() 
        		{
		            public void onClick(View v) 
		            {
		            	fCancel=false;
		            	final Bundle params = new Bundle();
		            	mProgressBar.setVisibility(View.VISIBLE);
		            	mRequestButton.setVisibility(View.INVISIBLE);
		            	mText.setText("Requesting friends list...");
		                mAsyncRunner.request("me/friends", params, "GET", new BFGProcessor(), null);
		                mText.setText("Processing...");
		            }
        		});
        mCancelButton.setOnClickListener(
        			new OnClickListener() {
												
						public void onClick(View v) {
							fCancel=true;
							mRequestButton.setVisibility(View.VISIBLE);
							mCancelButton.setVisibility(View.INVISIBLE);
						}
					}
        		);
    }
    
    @Override
    protected void onResume() 
    {
        SharedPreferences savedSession = this.getSharedPreferences("fProgress", Context.MODE_PRIVATE);
        fProgress=savedSession.getBoolean("fProgress", false);            
    	Log.d("bfgActivity", "fProgress: " + fProgress);
    	if (!fProgress){
    		if(mFacebook.isSessionValid()) new SampleAuthListener().onAuthSucceed();    		
    	}
    	else{
    		mProgressBar.setProgress(mProgress);
    		mProgressBar.setVisibility(View.VISIBLE);
    		mCancelButton.setVisibility(View.VISIBLE);
    	}
    	super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        mFacebook.authorizeCallback(requestCode, resultCode, data);
    }    
    
    /**
     * sets the main activity variable for calID to the value returned from the Dialog.
     * @param itemIndex
     */
    public void getSelectedCalId(int itemIndex){    	
    	this.calID= mCalendar.calIDArray[itemIndex];
    	Log.d("bfgActivity", "CalId set as: " + this.calID);
    }
    
    
    public class SampleAuthListener implements AuthListener 
    {
        public void onAuthSucceed() 
        {
            fLogout= false;
            mText.setText("You have logged in! ");
            mCalendar = new MyCalendarChooser();
            mCalendar.calInit();
            mCalendar.showChooser();
            mRequestButton.setVisibility(View.VISIBLE);
        }

        public void onAuthFail(String error) 
        {
            mText.setText("Login Failed: " + error);
        }
    }

    public class SampleLogoutListener implements LogoutListener 
    {
        public void onLogoutBegin() 
        {
            mText.setText("Logging out...");
            fLogout = true;
        }

        public void onLogoutFinish() 
        {
            mText.setText("You have logged out! ");
            mRequestButton.setVisibility(View.INVISIBLE);
            mCancelButton.setVisibility(View.INVISIBLE);
            mProgressBar.setVisibility(View.INVISIBLE);
        }
    }
    
    /**
     * Thinking of making each calendar insert when each b'day fetch from FB is over.
     * @author surajx
     *
     */
    public class BFGProcessor extends BaseRequestListener 
    {
    	/**
    	 * BaseRequestListner gives a callback to onComplete when FBs AsyncFacebookRunner returns the response.
    	 */
        public void onComplete(final String response, final Object state)
        {
            try
            {
                Log.d("bfgActivity", "Response: " + response.toString());
                JSONObject json = Util.parseJson(response);
                JSONArray jArray = json.getJSONArray("data");
                final int jArrayLen = jArray.length();
            	Bundle params = new Bundle();
            	params.putString("fields", "birthday");
            	final int progressStep = (int) mProgressBar.getMax()/jArrayLen;
                bfgActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                    	mCancelButton.setVisibility(View.VISIBLE);
                    	mProgress = 0;
                        fProgress=true;
                    	Editor editor = bfgActivity.this.getSharedPreferences("fProgress", Context.MODE_PRIVATE).edit();
                    	editor.putBoolean("fProgress", fProgress);
                        editor.commit();
                    }
                });
                for (int i=0;i<jArrayLen && !fLogout && !fCancel;i++) 
                {
                	JSONObject fNameJSON= jArray.getJSONObject(i);
                	FFriend ff = new FFriend();
                	ff.setName(fNameJSON.getString("name"));
                	ff.setFID(fNameJSON.getString("id"));
                    try {
                        String resp = mFacebook.request(ff.getFID(), params, "GET");
                        ff= bdayResposeHandler(ff, resp); //this should set the UI text indicating processing birthday and give a async call to calendar to insert the birthday.
                    } catch (FileNotFoundException e) {
                    	Log.w("bfgActivity", "FileNotFound Exception " + e);
                    } catch (MalformedURLException e) {
                    	Log.w("bfgActivity", "MalformedURLException Exception " + e);
                    } catch (IOException e) {
                    	Log.w("bfgActivity", "IOException Exception " + e);
                    }
                    bfgActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            mProgress = mProgress + progressStep;
                            mProgressBar.setProgress(mProgress);
                        }
                    });
                }
                bfgActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        fProgress=false;
                        if(!fCancel) mText.setText("Birthdays added Successfully!!!");
                        else mText.setText("Birthday sync partial!");
                    }
                });
        		Log.d("bfgActivity", "END of adding to calendar!!");
            } catch (JSONException e) {
                Log.w("bfgActivity", "JSON Error in response");
            } catch (FacebookError e) {
                Log.w("bfgActivity", "Facebook Error: " + e.getMessage());
            }
        }
        
        /**
         * This should handle retrieval of birthday from FB response and inserting the calendar event. 
         * @param ff
         * @param response
         * @return
         */
        public FFriend bdayResposeHandler(FFriend ff, final String response) {
            try {
                Log.d("bfgActivity", "Response: " + response.toString());
                JSONObject json = Util.parseJson(response);
                final String frndsBday = json.getString("birthday");
                final String frndsName = ff.getName();
                ff.setBday(frndsBday);
                bfgActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        mText.setText(frndsName+"'s B'Day: " + frndsBday);
                    }
                });
                insertBdayEvent(ff);
                return ff;                
            } catch (JSONException e) {            	
                Log.w("bfgActivity", "JSON ERROR: Possibly birthday not available.");
            } catch (FacebookError e) {
                Log.w("bfgActivity", "Facebook Error: " + e.getMessage());
            }
            return ff;
        }
        
        /**
         * 
         * @param ff
         */
        public void insertBdayEvent(FFriend ff){
			String raw_bday= ff.getBday();
			if("".equals(raw_bday) || raw_bday == null) return;
			int mm = Integer.parseInt(raw_bday.split("/")[0]);
			int dd = Integer.parseInt(raw_bday.split("/")[1]);
			int yyyy = Calendar.getInstance().get(Calendar.YEAR);
    		long epoch=0;
    		double rand =Math.random();    		
    		try {
    			String eventTime= ("0"+Double.toString(rand*10)).substring(0, 2) + ":00:00";
    			epoch= new java.text.SimpleDateFormat ("MM/dd/yyyy HH:mm:ss").parse(mm + "/" + dd + "/" + yyyy + " " + eventTime).getTime();
    			Log.d("bfgActivity", "Event Time: " + eventTime);
    			Log.d("bfgActivity", "Rand Value: " + rand);
				Log.d("bfgActivity", "epoch: " + epoch);
			}
    		catch (ParseException e) { 
				e.printStackTrace();
			}
    		ContentValues values = new ContentValues();
    		values.put(Events.DTSTART, epoch);
    		values.put(Events.TITLE, ff.getName() + "'s BirthDay");
    		values.put(Events.DESCRIPTION, "Bithday Reminder Added by FBBG Cal Sync");
    		values.put(Events.CALENDAR_ID, calID);
    		values.put(Events.EVENT_TIMEZONE, "Asia/Kolkata");
    		values.put(Events.DURATION, "PT1H");
    		values.put(Events.RRULE, "FREQ=YEARLY");
    		//mQueryHandler.startInsert(0, ff, Events.CONTENT_URI, values);
        }
    }
    
    private class MyCalendarChooser
    {
    	long []calIDArray;
    	
        public void calInit()
        {        	
    		Uri uri1 = CalendarContract.Calendars.CONTENT_URI;
    		String[] projection = new String[] {
    		       CalendarContract.Calendars._ID,
    		       CalendarContract.Calendars.ACCOUNT_NAME,
    		       CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
    		       CalendarContract.Calendars.NAME,
    		       CalendarContract.Calendars.CALENDAR_COLOR
    		};
    		Cursor calendarCursor = managedQuery(uri1, projection, null, null, null);
    		Log.d("bfgActivity", "calID Column Name : " + calendarCursor.getColumnName(0) + " Cursor row Count: "+calendarCursor.getCount() + " Cursor obj string: "+calendarCursor.toString());
    		long calID = 0;
    		String calName= "";
    		MyAlertDialogFragment.items = new String[calendarCursor.getCount()];
    		calIDArray= new long[calendarCursor.getCount()];
    		while (calendarCursor.moveToNext()) {
    			calID = calendarCursor.getLong(0);
    			calName = calendarCursor.getString(2);
    			MyAlertDialogFragment.items[calendarCursor.getPosition()]= calName;
    			calIDArray[calendarCursor.getPosition()]= calID;
    			Log.d("bfgActivity", "current Position: " + calendarCursor.getPosition());
    			Log.d("bfgActivity", "current Item: " + MyAlertDialogFragment.items[calendarCursor.getPosition()]);
    			Log.d("bfgActivity", "calID: " + calendarCursor.getLong(0));
    			Log.d("bfgActivity", "Account Name: " + calendarCursor.getString(1));
    			Log.d("bfgActivity", "Calendar Display Name: " + calendarCursor.getString(2));
    			Log.d("bfgActivity", "Name: " + calendarCursor.getString(3));
    			Log.d("bfgActivity", "Calendar Color: " + calendarCursor.getString(4));
    		}
        }
        
        public void showChooser()
        {
        	DialogFragment newFragment = MyAlertDialogFragment.newInstance(R.string.alert_dialog_title);
            newFragment.show(getFragmentManager(), "dialog");
        }
    }
    
    /**
     * Does the calendar insert jobs in a different background thread.
     * @author surajx
     *
     */
    private static final class QueryHandler extends AsyncQueryHandler {
    	
    	public QueryHandler(Context context){
    		super(context.getContentResolver());    		
    	}
    	
    	@Override
    	protected void onInsertComplete(int TOKEN, Object frnd, Uri uri){
    		long eventID = Long.parseLong(uri.getLastPathSegment());
    		Log.d("bfgActivity", "Calendar Insert Completed for FB friend : " + ((FFriend) frnd).getName() + "\nEvent ID: " + eventID);
    	}
    }
    
    /**
     * This Class creates an Alert dialog class which basically deals with how to 
     * implements by calendar chooser dialog
     * @author surajx
     *
     */
    public static class MyAlertDialogFragment extends DialogFragment {

    	public static CharSequence[] items;
        public static MyAlertDialogFragment newInstance(int title) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("title", title);
            frag.setArguments(args);
            return frag;
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int title = getArguments().getInt("title");            
            return new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setSingleChoiceItems(items, 0, 
                    		new DialogInterface.OnClickListener() {
                            	public void onClick(DialogInterface dialog, int item) {
                                ((bfgActivity)getActivity()).getSelectedCalId(item);                                
                            	}
                        	})
                    .setPositiveButton(R.string.alert_dialog_ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {                                    
                                    ((bfgActivity)getActivity()).getSelectedCalId(((AlertDialog)dialog).getListView().getCheckedItemPosition());
                                    try{
                                    	dialog.dismiss();
                                    }catch(IllegalStateException ise) {
                                    	Log.d("bfgActivity", "IllegalStateException: " + ise.toString());
                                    }
                                }
                            })
                    .setCancelable(false)
                    .create();
        }
    }
}

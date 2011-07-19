package com.bfgsync.gcal;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.bfgsync.R;
import com.bfgsync.R.id;
import com.bfgsync.R.layout;
import com.bfgsync.fb.bfgActivity.FriendsListListener;
import com.bfgsync.gcal.CalendarEntry;
import com.bfgsync.gcal.CalendarFeed;
import com.bfgsync.gcal.CalendarUrl;
import com.bfgsync.gcal.RedirectHandler;
import com.bfgsync.gcal.Util;
import com.google.api.client.util.DateTime;
import com.google.api.client.xml.atom.AtomParser;
import com.google.common.collect.Lists;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CalActivity extends Activity
{
	private static final String AUTH_TOKEN_TYPE = "cl";
	private static final String TAG = "CalendarSample";
	private static final boolean LOGGING_DEFAULT = false;
	private static final int MENU_ADD = 0;
	private static final int MENU_ACCOUNTS = 1;
	private static final int CONTEXT_EDIT = 0;
	private static final int CONTEXT_DELETE = 1;
	private static final int CONTEXT_LOGGING = 2;
	private static final int REQUEST_AUTHENTICATE = 0;
	private static final String PREF = "MyPrefs";
	
	private static final int DIALOG_ACCOUNTS = 0;
	private static final int DIALOG_CALENDARS = 1;
	
	private static HttpTransport transport;
	private String authToken;
	private final List<CalendarEntry> calendars = Lists.newArrayList();
	
	private Button mRequestButton;
	
	private static final int FROYO = 8;
	
	public CalActivity() {
		if (Build.VERSION.SDK_INT <= FROYO) {
		  transport = new ApacheHttpTransport();
		} else {
		  transport = new NetHttpTransport();
		}
		GoogleHeaders headers = new GoogleHeaders();
		headers.setApplicationName("Google-CalendarAndroidSample/1.0");
		headers.gdataVersion = "2";
		transport.defaultHeaders = headers;
		AtomParser parser = new AtomParser();
		parser.namespaceDictionary = Util.DICTIONARY;
		transport.addParser(parser);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.googlay);				
		mRequestButton = (Button) findViewById(R.id.greqBut);
		mRequestButton. setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
        		gotAccount(false);            	
            }
        });
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch (id) {
			case DIALOG_ACCOUNTS:				
				builder.setTitle("Select a Google account");
				final AccountManager manager = AccountManager.get(this);
				final Account[] accounts = manager.getAccountsByType("com.google");
				final int size = accounts.length;
				String[] names = new String[size];
				for (int i = 0; i < size; i++) {
				  names[i] = accounts[i].name;
				}
				builder.setItems(names, new DialogInterface.OnClickListener() {
				  public void onClick(DialogInterface dialog, int which) {
				    gotAccount(manager, accounts[which]);
				  }
				});
				return builder.create();
			case DIALOG_CALENDARS:
			    String[] calendarNames;
			    List<CalendarEntry> calendars = this.calendars;
			    calendars.clear();
			    try {
			      CalendarUrl url = CalendarUrl.forAllCalendarsFeed();
			      // page through results
			      while (true) {
			        CalendarFeed feed = CalendarFeed.executeGet(transport, url);
			        if (feed.calendars != null) {
			          calendars.addAll(feed.calendars);
			        }
			        String nextLink = feed.getNextLink();
			        if (nextLink == null) {
			          break;
			        }
			      }
			      int numCalendars = calendars.size();
			      calendarNames = new String[numCalendars];
			      for (int i = 0; i < numCalendars; i++) {
			        calendarNames[i] = calendars.get(i).title;
			      }
			    } 
			    catch (IOException e) {
			      handleException(e);
			      calendarNames = new String[] {e.getMessage()};
			      calendars.clear();
			    }				
				builder.setTitle("Select a Google Calendar");
				builder.setItems(calendarNames, new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface dialog, int which) {
						Bundle fData= getIntent().getExtras();
						//to-do
					}
					});
				return builder.create();
		}
		return null;
	}
	
	private void gotAccount(boolean tokenExpired) {
	    SharedPreferences settings = getSharedPreferences(PREF, 0);
	    String accountName = settings.getString("accountName", null);
	    if (accountName != null) {
	      AccountManager manager = AccountManager.get(this);
	      Account[] accounts = manager.getAccountsByType("com.google");
	      int size = accounts.length;
	      for (int i = 0; i < size; i++) {
	        Account account = accounts[i];
	        if (accountName.equals(account.name)) {
	          if (tokenExpired) {
	            manager.invalidateAuthToken("com.google", this.authToken);
	          }
	          gotAccount(manager, account);
	          return;
	        }
	      }
	    }
	    showDialog(DIALOG_ACCOUNTS);
	}

	void gotAccount(final AccountManager manager, final Account account) {
		SharedPreferences settings = getSharedPreferences(PREF, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("accountName", account.name);
		editor.commit();
		new Thread() {
		
		  @Override
		  public void run() {
		    try{
				final Bundle bundle = manager.getAuthToken(account, AUTH_TOKEN_TYPE, true, null, null).getResult();
				runOnUiThread(new Runnable() {
					public void run() {
						try {
							if (bundle.containsKey(AccountManager.KEY_INTENT)) {
							  Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
							  int flags = intent.getFlags();
							  flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
							  intent.setFlags(flags);
							  startActivityForResult(intent, REQUEST_AUTHENTICATE);
							} 
							else if (bundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
							  authenticated(bundle.getString(AccountManager.KEY_AUTHTOKEN));
							}
						} 
						catch (Exception e) {
							handleException(e);
					  }
					}
				});
		    }
		    catch (Exception e) {
		    	handleException(e);
		    }
		  }
		}.start();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case REQUEST_AUTHENTICATE:
				if (resultCode == RESULT_OK) {
					gotAccount(false);
				} 
				else {
					showDialog(DIALOG_ACCOUNTS);
				}
				break;
		}		
	}
	
	void authenticated(String authToken) {
	    this.authToken = authToken;
	    ((GoogleHeaders) transport.defaultHeaders).setGoogleLogin(authToken);
	    RedirectHandler.resetSessionId(transport);
	    showDialog(DIALOG_CALENDARS);
	}

	void handleException(Exception e) {
	    e.printStackTrace();
	    SharedPreferences settings = getSharedPreferences(PREF, 0);
	    boolean log = settings.getBoolean("logging", false);
	    if (e instanceof HttpResponseException) {
	      HttpResponse response = ((HttpResponseException) e).response;
	      int statusCode = response.statusCode;
	      try {
	        response.ignore();
	      } catch (IOException e1) {
	        e1.printStackTrace();
	      }
	      if (statusCode == 401 || statusCode == 403) {
	        gotAccount(true);
	        return;
	      }
	      if (log) {
	        try {
	          Log.e(TAG, response.parseAsString());
	        } catch (IOException parseException) {
	          parseException.printStackTrace();
	        }
	      }
	    }
	    if (log) {
	      Log.e(TAG, e.getMessage(), e);
	    }
	}
}

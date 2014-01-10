package edu.rit.csh.cshnews2;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.widget.SlidingPaneLayout;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class NewsgroupActivity extends Activity implements ActionBar.OnNavigationListener{
    private SlidingPaneLayout mSlidingLayout;
    private ListView mList;
    //private TextView mContent;
    private LinearLayout mPosts;
    boolean mBound = false;
    CshNewsService mService;
    JSONArray threadMetadatas;
    JSONArray newsgroups;
    NewsgroupsSpinnerAdapter newsgroupsSpinner;

    String selectedNewsgroup = "csh.test";

    private ActionBarHelper mActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_newsgroup);


        mSlidingLayout = (SlidingPaneLayout) findViewById(R.id.sliding_pane_layout);
        mList = (ListView) findViewById(R.id.left_pane);
        //mContent = (TextView) findViewById(R.id.content_text);
        mPosts = (LinearLayout) findViewById(R.id.posts);

        mSlidingLayout.setPanelSlideListener(new SliderListener());
        mSlidingLayout.openPane();

        String[] loadingText = {"Loading..."};
        mList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                loadingText));
        mList.setOnItemClickListener(new ListItemClickListener());

        newsgroupsSpinner = new NewsgroupsSpinnerAdapter(this, new JSONArray());

        mActionBar = createActionBarHelper();
        mActionBar.init();

        mSlidingLayout.getViewTreeObserver().addOnGlobalLayoutListener(new FirstLayoutListener());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        /*
         * The action bar up action should open the slider if it is currently closed,
         * as the left pane contains content one level up in the navigation hierarchy.
         */
        if (item.getItemId() == android.R.id.home && !mSlidingLayout.isOpen()) {
            mSlidingLayout.openPane();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(this, CshNewsService.class);
        i.putExtra("action", "startService");
        startService(i);
        // Bind to LocalService
        Intent intent = new Intent(this, CshNewsService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onDestroy();
        if(mBound)
        {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onBackPressed() {
        if(!mSlidingLayout.isOpen())
            mSlidingLayout.openPane();
        else
            super.onBackPressed();
    }

    private boolean threadHasUnread(JSONObject threadMetadata)
    {
        try {
            boolean hasUnread = false;
            JSONArray children = threadMetadata.getJSONArray("children");
            for(int i= 0; i < children.length(); i++)
                hasUnread |= threadHasUnread(children.getJSONObject(i));
            String unread_class = mService.getPost(selectedNewsgroup, threadMetadata.getJSONObject("post")
                    .getString("number")).getJSONObject("post").getString("unread_class");
            hasUnread |= !unread_class.equals("null");
            return hasUnread;
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for threadHasUnread");
            Log.d("Hi", "Error " + e.toString());
        }
        return false;
    }

    //Changes the detail view to show the information from the selected thread
    private void onThreadSelected(int threadSelected)
    {
        mPosts.removeAllViews();

        try {
            JSONObject postjson = threadMetadatas.getJSONObject(threadSelected);
            addPostView(postjson, 0);
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for onThreadSelected");
            Log.d("Hi", "Error " + e.toString());
        }
    }

    private void addPostView(JSONObject postjsonmetadata, int depth) throws JSONException
    {
        JSONObject post = mService.getPost(selectedNewsgroup, postjsonmetadata.getJSONObject("post").getString("number"));
        if(!post.getJSONObject("post").getString("unread_class").equals("null"))
            mService.changeReadStatusOfPost(selectedNewsgroup, postjsonmetadata.getJSONObject("post").getString("number"), true);
        View postView = buildPostView(post.getJSONObject("post"),
                depth, postjsonmetadata.getJSONArray("children").length() == 0 && depth == 0);
        mPosts.addView(postView);
        JSONArray children = postjsonmetadata.getJSONArray("children");
        for(int i = 0; i < children.length(); i++)
            addPostView(children.getJSONObject(i), depth + 1);
    }

    //Returns a linearlayout containing the information given in post
    private View buildPostView(JSONObject post, int depth, boolean onlyOne)
    {
        LayoutInflater infalInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View rootView = infalInflater.inflate(R.layout.post_item, null);

        View nameAndTimeHolder = rootView.findViewById(R.id.nameAndTimeHolder);
        View subjectAndMenuHolder = rootView.findViewById(R.id.subjectAndMenuHolder);
        final View bodyHolder = rootView.findViewById(R.id.bodyHolder);
        View quotedTextHolder = rootView.findViewById(R.id.quotedTextHolder);

        final Button quotedTextButton = (Button) rootView.findViewById(R.id.quotedTextButton);

        TextView nameView = (TextView) rootView.findViewById(R.id.nameView);
        TextView timeView = (TextView) rootView.findViewById(R.id.timeView);
        TextView subjectView = (TextView) rootView.findViewById(R.id.subjectView);
        TextView bodyView1 = (TextView) rootView.findViewById(R.id.bodyView1);
        final TextView bodyView2 = (TextView) rootView.findViewById(R.id.bodyView2);
        TextView bodyView3 = (TextView) rootView.findViewById(R.id.bodyView3);

        try {
            nameView.setText(post.getString("author_name"));
            String date = post.getString("date");
            timeView.setText(date.substring(11,16)
                    + " " + date.substring(5,7)
                    + "/" + date.substring(8,10)
                    + "/" + date.substring(0,4));
            subjectView.setText(post.getString("subject"));

            String bodyString = post.getString("body");
            bodyString = bodyString.replace("\n", "<br/>");
            if(shouldBeCondensed(bodyString))
            {
                String[] parts = splitQuotedText(bodyString);
                bodyView1.setText(Html.fromHtml(parts[0], null, null));
                bodyView2.setText(Html.fromHtml(parts[1], null, null));
                bodyView3.setText(Html.fromHtml(parts[2], null, null));
                quotedTextHolder.setVisibility(View.VISIBLE);

                quotedTextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(bodyView2.getVisibility() == View.VISIBLE)
                        {
                            quotedTextButton.setText("Show Quoted Text");
                            bodyView2.setVisibility(View.GONE);
                        }
                        else
                        {
                            quotedTextButton.setText("Hide Quoted Text");
                            bodyView2.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
            else
            {
                bodyView1.setText(Html.fromHtml(bodyString, null, null));
            }
            bodyView1.setMovementMethod(LinkMovementMethod.getInstance());

            if(!post.getString("unread_class").equals("null"))
            {
                nameView.setTypeface(null, Typeface.BOLD);
                timeView.setTypeface(null, Typeface.BOLD);
                subjectView.setTypeface(null, Typeface.BOLD);
            }
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for buildPostView");
            Log.d("Hi", "Error " + e.toString());
        }

        try {
            if(!post.getString("unread_class").equals("null"))
            {
                nameView.setTypeface(null, Typeface.BOLD);
                timeView.setTypeface(null, Typeface.BOLD);
                subjectView.setTypeface(null, Typeface.BOLD);
            }
            else if (!onlyOne)
                bodyHolder.setVisibility(View.GONE);
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for buildpostview");
            Log.d("Hi", "Error " + e.toString());
        }

        View.OnClickListener toggleBody = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bodyHolder.getVisibility() == View.VISIBLE)
                    bodyHolder.setVisibility(View.GONE);
                else
                    bodyHolder.setVisibility(View.VISIBLE);
            }
        };

        nameAndTimeHolder.setOnClickListener(toggleBody);
        subjectAndMenuHolder.setOnClickListener(toggleBody);

        if(depth > 7)
            depth = 7;
        LinearLayout.LayoutParams rootViewParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rootViewParams.setMargins(5 + 20 * depth, 10, 5, 10);
        rootView.setPadding(15, 15, 15, 15);
        rootView.setLayoutParams(rootViewParams);

        return rootView;
    }

    public String[] splitQuotedText(String input)
    {
        int start = input.indexOf("<div class=\"quoted_text\">");
        if(input.contains("<br/><div class=\"quoted_text\">"))
            start = input.indexOf("<br/><div class=\"quoted_text\">");
        int end = input.lastIndexOf("</div>") + 6;
        String[] returnValue = {
            input.substring(0, start),
            input.substring(start, end),
            input.substring(end, input.length())
        };
        return returnValue;
    }

    public boolean shouldBeCondensed(String input)
    {
        return input.contains("<div class=\"quoted_text\">");
    }

    public void newsgroupChanged(boolean stealFocus)
    {
        newsgroupsSpinner.setJSONArray(newsgroups);
        newsgroupsSpinner.notifyDataSetChanged();
        threadMetadatas = mService.getThreadsForNewsgroup(selectedNewsgroup);
        if(threadMetadatas != null)
        {
            ArrayList<String[]> threads = new ArrayList<String[]>();
            for(int i = 0; i < threadMetadatas.length(); i++)
                try {
                    String[] data = {
                            threadMetadatas.getJSONObject(i).getJSONObject("post").getString("author_name"),
                            threadMetadatas.getJSONObject(i).getJSONObject("post").getString("date"),
                            threadMetadatas.getJSONObject(i).getJSONObject("post").getString("subject"),
                            (threadHasUnread(threadMetadatas.getJSONObject(i)) ? "y" : "n")
                    };
                    threads.add(data);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            mList.setAdapter(new ThreadsListAdapter(NewsgroupActivity.this, 0, threads));
        }
        if(!mSlidingLayout.isOpen() && stealFocus)
            mSlidingLayout.openPane();
        if(stealFocus)
            mPosts.removeAllViews();
    }

    public void updateFinished()
    {
        newsgroups = mService.getNewsgroups();
        newsgroupChanged(false);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CshNewsService.LocalBinder binder = (CshNewsService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            newsgroups = mService.getNewsgroups();
            newsgroupChanged(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        try {
            selectedNewsgroup = newsgroups.getJSONObject(itemPosition).getString("name");
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for onnavigaitonitemselected");
            Log.d("Hi", "Error " + e.toString());
            return false;
        }
        newsgroupChanged(true);
        return true;
    }

    /**
     * This list item click listener implements very simple view switching by changing
     * the primary content text. The slider is closed when a selection is made to fully
     * reveal the content.
     */
    private class ListItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            onThreadSelected(position);
            mSlidingLayout.closePane();
        }
    }

    /**
     * This panel slide listener updates the action bar accordingly for each panel state.
     */
    private class SliderListener extends SlidingPaneLayout.SimplePanelSlideListener {
        @Override
        public void onPanelOpened(View panel) {
            mActionBar.onPanelOpened();
        }

        @Override
        public void onPanelClosed(View panel) {
            mActionBar.onPanelClosed();
        }
    }

    /**
     * This global layout listener is used to fire an event after first layout occurs
     * and then it is removed. This gives us a chance to configure parts of the UI
     * that adapt based on available space after they have had the opportunity to measure
     * and layout.
     */
    private class FirstLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            mActionBar.onFirstLayout();
            mSlidingLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }

    /**
     * Create a compatible helper that will manipulate the action bar if available.
     */
    private ActionBarHelper createActionBarHelper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return new ActionBarHelperICS();
        } else {
            return new ActionBarHelper();
        }
    }

    /**
     * Stub action bar helper; this does nothing.
     */
    private class ActionBarHelper {
        public void init() {}
        public void onPanelClosed() {}
        public void onPanelOpened() {}
        public void onFirstLayout() {}
        public void setTitle(CharSequence title) {}
    }

    /**
     * Action bar helper for use on ICS and newer devices.
     */
    private class ActionBarHelperICS extends ActionBarHelper {
        private final ActionBar mActionBar;
        private CharSequence mDrawerTitle;
        private CharSequence mTitle;

        ActionBarHelperICS() {
            mActionBar = getActionBar();
        }

        @Override
        public void init() {
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
            mTitle = mDrawerTitle = getTitle();
            mActionBar.setListNavigationCallbacks(newsgroupsSpinner, NewsgroupActivity.this);
        }

        @Override
        public void onPanelClosed() {
            super.onPanelClosed();
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            mActionBar.setDisplayShowTitleEnabled(true);
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
            mActionBar.setTitle(mTitle);
        }

        @Override
        public void onPanelOpened() {
            super.onPanelOpened();
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            mActionBar.setDisplayShowTitleEnabled(false);
            mActionBar.setHomeButtonEnabled(false);
            mActionBar.setDisplayHomeAsUpEnabled(false);
            mActionBar.setTitle(mDrawerTitle);
        }

        @Override
        public void onFirstLayout() {
            if (mSlidingLayout.isSlideable() && !mSlidingLayout.isOpen()) {
                onPanelClosed();
            } else {
                onPanelOpened();
            }
        }

        @Override
        public void setTitle(CharSequence title) {
            mTitle = title;
        }
    }

}
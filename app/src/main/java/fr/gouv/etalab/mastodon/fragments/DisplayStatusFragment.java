package fr.gouv.etalab.mastodon.fragments;
/* Copyright 2017 Thomas Schneider
 *
 * This file is a part of Mastalab
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastalab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastalab; if not,
 * see <http://www.gnu.org/licenses>. */
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveRepliesAsyncTask;
import fr.gouv.etalab.mastodon.client.APIResponse;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.drawers.StatusListAdapter;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveRepliesInterface;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;
import mastodon.etalab.gouv.fr.mastodon.R;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveFeedsAsyncTask;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveFeedsInterface;


/**
 * Created by Thomas on 24/04/2017.
 * Fragment to display content related to status
 */
public class DisplayStatusFragment extends Fragment implements OnRetrieveFeedsInterface, OnRetrieveRepliesInterface {


    private boolean flag_loading;
    private Context context;
    private AsyncTask<Void, Void, Void> asyncTask;
    private StatusListAdapter statusListAdapter;
    private String max_id;
    private List<Status> statuses;
    private RetrieveFeedsAsyncTask.Type type;
    private RelativeLayout mainLoader, nextElementLoader, textviewNoAction;
    private boolean firstLoad;
    private SwipeRefreshLayout swipeRefreshLayout;
    private String targetedId;
    private String tag;
    private boolean swiped;
    private ListView lv_status;
    private boolean isOnWifi;
    private int behaviorWithAttachments;
    private boolean showMediaOnly;
    private int positionSpinnerTrans;
    private boolean hideHeader;
    private String instanceValue;
    private String lastReadStatus;
    private String userId;

    public DisplayStatusFragment(){
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_status, container, false);
        statuses = new ArrayList<>();
        context = getContext();
        Bundle bundle = this.getArguments();
        boolean comesFromSearch = false;
        hideHeader = false;
        showMediaOnly = false;
        if (bundle != null) {
            type = (RetrieveFeedsAsyncTask.Type) bundle.get("type");
            targetedId = bundle.getString("targetedId", null);
            tag = bundle.getString("tag", null);
            instanceValue = bundle.getString("hideHeaderValue", null);
            hideHeader = bundle.getBoolean("hideHeader", false);
            showMediaOnly = bundle.getBoolean("showMediaOnly",false);
            if( bundle.containsKey("statuses")){
                ArrayList<Parcelable> statusesReceived = bundle.getParcelableArrayList("statuses");
                assert statusesReceived != null;
                for(Parcelable status: statusesReceived){
                    statuses.add((Status) status);
                }
                comesFromSearch = true;
            }
        }
        max_id = null;
        flag_loading = true;
        firstLoad = true;
        swiped = false;

        final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        isOnWifi = Helper.isOnWIFI(context);
        positionSpinnerTrans = sharedpreferences.getInt(Helper.SET_TRANSLATOR, Helper.TRANS_YANDEX);
        swipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeContainer);
        behaviorWithAttachments = sharedpreferences.getInt(Helper.SET_ATTACHMENT_ACTION, Helper.ATTACHMENT_ALWAYS);
        userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        lastReadStatus = sharedpreferences.getString(Helper.LAST_HOMETIMELINE_MAX_ID + userId, null);
        lv_status = (ListView) rootView.findViewById(R.id.lv_status);
        mainLoader = (RelativeLayout) rootView.findViewById(R.id.loader);
        nextElementLoader = (RelativeLayout) rootView.findViewById(R.id.loading_next_status);
        textviewNoAction = (RelativeLayout) rootView.findViewById(R.id.no_action);
        mainLoader.setVisibility(View.VISIBLE);
        nextElementLoader.setVisibility(View.GONE);
        statusListAdapter = new StatusListAdapter(context, type, targetedId, isOnWifi, behaviorWithAttachments, positionSpinnerTrans, this.statuses);
        lv_status.setAdapter(statusListAdapter);
        if( !comesFromSearch){

            //Hide account header when scrolling for ShowAccountActivity
            if (hideHeader && Build.VERSION.SDK_INT >= 21)
                ViewCompat.setNestedScrollingEnabled(lv_status, true);

            lv_status.setOnScrollListener(new AbsListView.OnScrollListener() {
                int lastFirstVisibleItem = 0;
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (hideHeader && Build.VERSION.SDK_INT < 21) {
                        if(firstVisibleItem == 0 && Helper.listIsAtTop(lv_status)){
                            Intent intent = new Intent(Helper.HEADER_ACCOUNT+instanceValue);
                            intent.putExtra("hide", false);
                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                        }else if (view.getId() == lv_status.getId() && totalItemCount > visibleItemCount) {
                            final int currentFirstVisibleItem = lv_status.getFirstVisiblePosition();
                            if (currentFirstVisibleItem > lastFirstVisibleItem) {
                                Intent intent = new Intent(Helper.HEADER_ACCOUNT + instanceValue);
                                intent.putExtra("hide", true);
                                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                            } else if (currentFirstVisibleItem < lastFirstVisibleItem) {
                                Intent intent = new Intent(Helper.HEADER_ACCOUNT + instanceValue);
                                intent.putExtra("hide", false);
                                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                            }
                            lastFirstVisibleItem = currentFirstVisibleItem;
                        }
                    }
                    if(firstVisibleItem + visibleItemCount == totalItemCount ) {
                        if(!flag_loading ) {
                            flag_loading = true;
                            if( type == RetrieveFeedsAsyncTask.Type.USER)
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            else if( type == RetrieveFeedsAsyncTask.Type.TAG)
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            else
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                            nextElementLoader.setVisibility(View.VISIBLE);
                        }
                    } else {
                        nextElementLoader.setVisibility(View.GONE);
                    }
                }
            });


            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    max_id = null;
                    statuses = new ArrayList<>();
                    firstLoad = true;
                    flag_loading = true;
                    swiped = true;
                    if( type == RetrieveFeedsAsyncTask.Type.USER)
                        asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    else if( type == RetrieveFeedsAsyncTask.Type.TAG)
                        asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    else
                        asyncTask = new RetrieveFeedsAsyncTask(context, type, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            });
            swipeRefreshLayout.setColorSchemeResources(R.color.mastodonC4,
                    R.color.mastodonC2,
                    R.color.mastodonC3);

            if( type == RetrieveFeedsAsyncTask.Type.USER)
                asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            else if( type == RetrieveFeedsAsyncTask.Type.TAG)
                asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            else
                asyncTask = new RetrieveFeedsAsyncTask(context, type, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }else {
            statusListAdapter.notifyDataSetChanged();
            mainLoader.setVisibility(View.GONE);
            nextElementLoader.setVisibility(View.GONE);
            if( statuses == null || statuses.size() == 0 )
                textviewNoAction.setVisibility(View.VISIBLE);
        }

        return rootView;
    }


    @Override
    public void onCreate(Bundle saveInstance)
    {
        super.onCreate(saveInstance);
    }



    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDestroy (){
        super.onDestroy();
        if(asyncTask != null && asyncTask.getStatus() == AsyncTask.Status.RUNNING)
            asyncTask.cancel(true);
    }



    @Override
    public void onRetrieveFeeds(APIResponse apiResponse, boolean refreshData) {
        mainLoader.setVisibility(View.GONE);
        nextElementLoader.setVisibility(View.GONE);
        //Discards 404 - error which can often happen due to toots which have been deleted
        final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        if( apiResponse.getError() != null && !apiResponse.getError().getError().startsWith("404 -")){
            boolean show_error_messages = sharedpreferences.getBoolean(Helper.SET_SHOW_ERROR_MESSAGES, true);
            if( show_error_messages)
                Toast.makeText(context, apiResponse.getError().getError(),Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            swiped = false;
            flag_loading = false;
            return;
        }
        List<Status> statuses = apiResponse.getStatuses();
        String since_id = apiResponse.getSince_id();
        max_id = apiResponse.getMax_id();

        flag_loading = (max_id == null );
        if( !swiped && firstLoad && (statuses == null || statuses.size() == 0))
            textviewNoAction.setVisibility(View.VISIBLE);
        else
            textviewNoAction.setVisibility(View.GONE);
        if( swiped ){
            statusListAdapter = new StatusListAdapter(context, type, targetedId, isOnWifi, behaviorWithAttachments, positionSpinnerTrans, this.statuses);
            lv_status.setAdapter(statusListAdapter);
            swiped = false;
        }
        //Avoids to add a second time the same status, can happen due call in on resume
        ArrayList<String> added = new ArrayList<>();
        for(Status status : this.statuses){
            added.add(status.getId());
        }
        if( statuses != null && statuses.size() > 0) {
            for(Status tmpStatus: statuses){
                if( !added.contains(tmpStatus.getId())) {
                    this.statuses.add(tmpStatus);
                    added.add(tmpStatus.getId());
                    if( Long.parseLong(tmpStatus.getId()) > Long.parseLong(lastReadStatus)){
                        tmpStatus.setNew(true);
                    }else {
                        tmpStatus.setNew(false);
                    }
                }
            }
            statusListAdapter.notifyDataSetChanged();
        }
        swipeRefreshLayout.setRefreshing(false);

        //Store last toot id for home timeline to avoid to notify for those that have been already seen
        if(statuses != null && statuses.size()  > 0 && type == RetrieveFeedsAsyncTask.Type.HOME ){
            //acct is null when used in Fragment, data need to be retrieved via shared preferences and db

            SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
            Account currentAccount = new AccountDAO(context, db).getAccountByID(userId);
            if( currentAccount != null && firstLoad && since_id != null){
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString(Helper.LAST_HOMETIMELINE_MAX_ID + currentAccount.getId(), statuses.get(0).getId());
                editor.apply();
            }
        }
        firstLoad = false;

        //Retrieves replies
        if(statuses != null && statuses.size()  > 0 && type == RetrieveFeedsAsyncTask.Type.HOME ) {
            boolean showPreview = sharedpreferences.getBoolean(Helper.SET_PREVIEW_REPLIES, false);
            //Retrieves attached replies to a toot
            if (showPreview) {
                new RetrieveRepliesAsyncTask(context, statuses, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    public void refresh(Status status){
        //New data are available
        if( type == RetrieveFeedsAsyncTask.Type.HOME ) {
            if (context == null)
                return;
            if (status != null) {
                statuses.add(0,status);
                boolean isOnWifi = Helper.isOnWIFI(context);
                final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                int behaviorWithAttachments = sharedpreferences.getInt(Helper.SET_ATTACHMENT_ACTION, Helper.ATTACHMENT_ALWAYS);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                editor.putString(Helper.LAST_HOMETIMELINE_MAX_ID + userId, status.getId());
                editor.apply();
                statusListAdapter = new StatusListAdapter(context, type, targetedId, isOnWifi, behaviorWithAttachments, positionSpinnerTrans, statuses);
                lv_status.setAdapter(statusListAdapter);
                if (textviewNoAction.getVisibility() == View.VISIBLE)
                    textviewNoAction.setVisibility(View.GONE);
            }
        }
    }

    public void scrollToTop(){
        if( lv_status != null)
            lv_status.setAdapter(statusListAdapter);
    }

    @Override
    public void onRetrieveReplies(APIResponse apiResponse) {
        if( apiResponse.getError() != null || apiResponse.getStatuses() == null || apiResponse.getStatuses().size() == 0){
            return;
        }
        List<Status> modifiedStatus = apiResponse.getStatuses();
        for(Status stmp: modifiedStatus){
            for(Status status: statuses){
                if( status.getId().equals(stmp.getId()))
                    status.setReplies(stmp.getReplies());
            }
        }
        statusListAdapter.notifyDataSetChanged();
    }
}

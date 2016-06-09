package com.project.sabeen.textme;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.ProgressBar;

import com.project.sabeen.textme.adapter.ContributerListAdapter;
import com.project.sabeen.textme.model.Contributor;
import com.project.sabeen.textme.model.ContributorUrlList;
import com.project.sabeen.textme.service.ContributorService;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Activity for loading Contributors of Repository
 * Uses ContributorListAdapter for displaying Contributors List in Recycle View
 * Created by sabeen on 6/3/16.
 */
public class ContributorAcitivity extends AppCompatActivity {

    private String globalSearchUrl;
    private List<String> contributorUrl;
    private List<Contributor> contributors;
    private String clientId;
    private String clientSecret;
    private RecyclerView recyclerView;
    private ContributerListAdapter contributerListAdapter;
    private Realm realm;
    private ProgressDialog progressDialog;
    private static Integer checkCount = 0;
    private SwipeRefreshLayout swipeRefreshLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contributer);

        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.contribSwipe);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadContributorsUrlFromNW();
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbarContrib);
        toolbar.setTitle("TextMe GitHub");
        toolbar.setTitleTextColor(Color.WHITE);
        setSupportActionBar(toolbar);

//      Getting Stored Client Id and Client Secret stored in Application Class
        SharedPreferences prefs = getSharedPreferences("AUTH", MODE_PRIVATE);
        clientId = prefs.getString("client_id", null);
        clientSecret = prefs.getString("client_secret", null);

//      For Using Realm Database
        realm = Realm.getInstance(getApplicationContext());

        contributorUrl = new ArrayList<>();
        contributors = new ArrayList<>();
        recyclerView = (RecyclerView) findViewById(R.id.contributerRV);
        contributerListAdapter = new ContributerListAdapter(contributors);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(contributerListAdapter);

//      Getting contributor_url from selected Repository sent from RepoListAdapter
        Intent i = getIntent();
        globalSearchUrl = i.getStringExtra("url");
        loadContributorUrlFromDB();
        loadContributorsUrlFromNW();
    }

    /**
     * Method for Loading contributor_url From API Call Using Retrofit
     */
    private void loadContributorsUrlFromNW() {
        Call<List<ContributorUrlList>> repos = listContributorsUrl(globalSearchUrl);
        progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Loading...");
        progressDialog.show();
        repos.enqueue(new Callback<List<ContributorUrlList>>() {
            @Override
            public void onResponse(Call<List<ContributorUrlList>> call, Response<List<ContributorUrlList>> response) {
                try {
                    for (ContributorUrlList contribUrl : response.body()) {
                        ContributorUrlList contributorUrlList = new ContributorUrlList();
                        contributorUrlList.setSearchUrl(globalSearchUrl);
                        contributorUrlList.setUrl(contribUrl.getUrl());
                        realm.beginTransaction();
//                      Creates new record or updates if it already exits
                        realm.copyToRealmOrUpdate(contributorUrlList);
                        realm.commitTransaction();
                    }

//                  Load contributor_url From DB to recycle view
                    loadContributorUrlFromDB();
//                  Load Contributor List From Network
                    loadContributerFromNW();
                } catch (Exception e) {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<List<ContributorUrlList>> call, Throwable t) {
                Log.d("contributor_url error", t.getLocalizedMessage());
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        });

    }

    /**
     * Method for Loading List of Contributor Information from API call using Retrofit
     */
    private void loadContributerFromNW() {
        Call<Contributor> repos = null;
        for (String url : contributorUrl) {
            repos = listContributor(url);

            repos.enqueue(new Callback<Contributor>() {
                @Override
                public void onResponse(Call<Contributor> call, Response<Contributor> response) {
//                  To track number of entry in response
                    checkCount++;
                    try {
                        realm.beginTransaction();
                        Contributor contributor = new Contributor();
                        contributor.setAvatarUrl(response.body().getAvatarUrl());
                        contributor.setHtmlUrl(response.body().getHtmlUrl());
                        contributor.setName(response.body().getName());
                        contributor.setLogin(response.body().getLogin());
                        contributor.setSearchUrl(globalSearchUrl);
//                      Creates new record or updates if it already exits
                        realm.copyToRealmOrUpdate(contributor);
                        realm.commitTransaction();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
//                  Load Contributor Details From DB if number of contributor url equals count
                    if (checkCount == contributorUrl.size()) {
                        loadContributorDetailsFromDB();
                        checkCount = 0;
                    }

                }

                @Override
                public void onFailure(Call<Contributor> call, Throwable t) {
                    Log.d("contributro_url error", t.getLocalizedMessage());
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                }
            });
        }


    }

    /**
     *List contributor_url by making Retrofit API call
     *@return call for making asynchronous Http request
     *@param url the string
     */
    private Call<List<ContributorUrlList>> listContributorsUrl(String url) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        ContributorService contributorService = retrofit.create(ContributorService.class);
//      using client_id and client_secret for preventing api call max_limit
        return contributorService.listContributorsUrl(url + "?client_id=" + clientId + "&client_secret=" + clientSecret);
    }

    /**
     *List Contributor Details by making Retrofit API call
     *@return call for making asynchronous Http request
     *@param url the string
     */
    private Call<Contributor> listContributor(String url) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        ContributorService contributorService = retrofit.create(ContributorService.class);
//      using client_id and client_secret for preventing api call max_limit
        return contributorService.listContributor(url + "?client_id=" + clientId + "&client_secret=" + clientSecret);
    }

    /**
     *Load contributor_url from Realm Database based on globalSearchUrl
     */
    private void loadContributorUrlFromDB() {
        RealmResults<ContributorUrlList> results =
                realm.where(ContributorUrlList.class).equalTo("searchUrl", globalSearchUrl).findAll();
        contributorUrl.clear();
        for (ContributorUrlList contributorUrlList : results) {
            contributorUrl.add(contributorUrlList.getUrl());
        }
        loadContributorDetailsFromDB();

    }

    /**
     *Load contributor details from Realm Database based on globalSearchUrl
     */
    private void loadContributorDetailsFromDB() {
        RealmResults<Contributor> results =
                realm.where(Contributor.class).equalTo("searchUrl", globalSearchUrl).findAll();
        contributors.clear();
        for (Contributor contributor : results) {
            contributors.add(contributor);
        }
        contributerListAdapter.notifyDataSetChanged();
        swipeRefreshLayout.setRefreshing(false);
    }

}
package com.example.friendcircle;

import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Toast;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;

import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mRefreshLayout;
    private ImageLoader mImageLoader;

    /**
     * below 2 fields is from ImageLoader, we should copy pointer to avoid data conflict when refresh
     */
    public LinkedList<TweetBean> mTweetList;   // tweets list
    public UserBean mUser;

    private RecyclerView.OnScrollListener onScrollListener;

    // because the images included in given url cannot be access, use my own test url.
    private static final String USER_URL = "http://192.168.3.16/test/user";    //"http://thoughtworks-ios.herokuapp.com/user/jsmith"
    private static final String TWEETS_LIST_URL = "http://192.168.3.16/test/tweets";   //"http://thoughtworks-ios.herokuapp.com/user/jsmith/tweets"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageLoader = new ImageLoader(getApplicationContext(), mCallBack);
        mRecyclerView = findViewById(R.id.tweet_list);
        LinearLayoutManager linearLayoutManager =  new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        mRecyclerView.setAdapter(new TweetListAdapter(MainActivity.this, mImageLoader));

        // set onScrollListener
        onScrollListener = new RecyclerView.OnScrollListener() {
            // flag of pull up
            boolean flagPullup = false;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

                // when current state is idle
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    //last visiable item position
                    int lastVisibleItem = manager.findLastCompletelyVisibleItemPosition();
                    int totalItemCount = manager.getItemCount();

                    // if scroll to end and scroll action is pull up, we should load more tweets
                    if (lastVisibleItem == (totalItemCount - 1) && flagPullup) {
                        loadMoreTweets(mImageLoader);
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // set flag of pull up action
                if (dy > 0) {
                    flagPullup = true;
                } else {
                    flagPullup = false;
                }
            }
        };
        mRecyclerView.addOnScrollListener(onScrollListener);

        // refresh when pulling down, add listener
        mRefreshLayout = findViewById(R.id.layout_swipe_refresh);
        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.w(TAG, "refresh...");
                mImageLoader.startFetchData(USER_URL, TWEETS_LIST_URL);
            }
        });

        // start fetch data from network
        mImageLoader.startFetchData(USER_URL, TWEETS_LIST_URL);
    }

    /**
     * load more tweets when pulling up
     * @param imageLoader
     */
    private void loadMoreTweets(ImageLoader imageLoader) {
        // cancel refresh when pull up to load more
        if (mRefreshLayout.isRefreshing()) {
            mRefreshLayout.setRefreshing(false);
        }

        int currentAdapted = 0;
        if (mRecyclerView.getAdapter() instanceof TweetListAdapter
                && null != ((TweetListAdapter)mRecyclerView.getAdapter()).getTweetList()) {
            currentAdapted = ((TweetListAdapter)mRecyclerView.getAdapter()).getTweetList().size();
        }
        if (null == mTweetList || currentAdapted == mTweetList.size()) {
            System.out.println("no more tweet data");
            return;
        }

        // add 5 more
        LinkedList<TweetBean> tweetsToAdapter = ((TweetListAdapter)mRecyclerView.getAdapter()).getTweetList();
        for (int i = currentAdapted; i < currentAdapted + TweetListAdapter.LOAD_TWEETS_NUM_EACH_TIME
                && i < mTweetList.size(); ++i) {
            tweetsToAdapter.add(mTweetList.get(i));
        }
        mRecyclerView.getAdapter().notifyDataSetChanged();
    }

    /**
     * download json and images in sub-thread, this callback is called on UI thread when task is done
     */
    private ImageLoader.LoaderCallBack mCallBack = new ImageLoader.LoaderCallBack() {
        @Override
        public void onLoadDone(final ImageLoader imageLoader) {
            // This refresh action has been cancelled
            if (null != mTweetList && !mRefreshLayout.isRefreshing()) {
                return;
            }

            // set UI show no refreshing
            mRefreshLayout.setRefreshing(false);

            // copy a pointer avoid to data conflict when refresh
            mTweetList = imageLoader.getmTweetList();
            mUser = imageLoader.getmUser();

            // just show 5 of them
            final LinkedList<TweetBean> tweetsToAdapter = new LinkedList<>();
            if (null != mTweetList) {
                for (int i = 0; i < mTweetList.size() && i < TweetListAdapter.LOAD_TWEETS_NUM_EACH_TIME; ++i) {
                    tweetsToAdapter.add(mTweetList.get(i));
                }
            }
            if (mRecyclerView.getAdapter() instanceof TweetListAdapter) {
                TweetListAdapter adapter = (TweetListAdapter)mRecyclerView.getAdapter();
                adapter.setmUser(mUser);
                adapter.setTweetList(tweetsToAdapter);
                adapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onLoadFailure(ImageLoader imageLoader) {
            // This refresh action has been cancelled
            if (null != mTweetList && !mRefreshLayout.isRefreshing()) {
                return;
            }

            // set UI show no refreshing
            mRefreshLayout.setRefreshing(false);
            showErrorPage();
        }
    };

    private void showErrorPage() {
        Toast.makeText(this, R.string.network_access_error, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecyclerView.removeOnScrollListener(onScrollListener);
    }
}

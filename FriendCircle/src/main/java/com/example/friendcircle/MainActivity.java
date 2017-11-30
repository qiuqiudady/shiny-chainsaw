package com.example.friendcircle;

import android.graphics.Bitmap;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;

import java.util.HashMap;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mRefreshLayout;
    private ImageLoaderUtil mImageLoaderUtil;

    /**
     * below 3 fields is from ImageLoaderUtil, we should copy pointer to avoid data conflict when refresh
     */
    public LinkedList<TweetBean> mTweetList;   // tweets list
    public UserBean mUser;
    // load all images to memory, key:MD5 of url, value:Bitmap object
    public HashMap<String, Bitmap> mBitmapSet;
    private RecyclerView.OnScrollListener onScrollListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecyclerView = findViewById(R.id.tweet_list);
        LinearLayoutManager linearLayoutManager =  new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        mRecyclerView.setAdapter(new TweetListAdapter(MainActivity.this));
        mImageLoaderUtil = ImageLoaderUtil.getInstance(this);
        mImageLoaderUtil.addCallBack(mCallBack);

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
                        loadMoreTweets(mImageLoaderUtil);
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
                startFetchAll(mImageLoaderUtil);
            }
        });

        // start fetch data from network
        startFetchAll(mImageLoaderUtil);
    }

    /**
     * load more tweets when pulling up
     * @param imageLoaderUtil
     */
    private void loadMoreTweets(ImageLoaderUtil imageLoaderUtil) {
        // cancel refresh when pull up to load more
        if (mRefreshLayout.isRefreshing()) {
            mRefreshLayout.setRefreshing(false);
            imageLoaderUtil.getmHandler().removeMessages(ImageLoaderUtil.MSG_CODE_GET_ALL);
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
     * download json and images in sub-thread, this callback is called on Sub-thread when task is done
     */
    private ImageLoaderUtil.CallBack mCallBack = new ImageLoaderUtil.CallBack() {
        @Override
        public void onResponse(final ImageLoaderUtil imageLoaderUtil, final int msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    response(imageLoaderUtil, msg);
                }
            });
        }

        private void response(ImageLoaderUtil imageLoaderUtil, int msg) {
            // fetch user and tweets list Json successfully, now we should set adapter for RecyclerView
            if (ImageLoaderUtil.MSG_CODE_GET_ALL == msg) {
                if (!imageLoaderUtil.getmUser().isValid()) {
                    // TODO show error page
                    return;
                }

                // current is refresh action, but it's been cancelled
                if (null != mTweetList && !mRefreshLayout.isRefreshing()) {
                    return;
                }

                // set UI show no refreshing
                mRefreshLayout.setRefreshing(false);

                // release memory of old bitmap hashmap
                HashMap<String,Bitmap> oldToRelease = mBitmapSet;

                // copy a pointer avoid to data conflict when refresh
                mTweetList = imageLoaderUtil.getmTweetList();
                mUser = imageLoaderUtil.getmUser();
                mBitmapSet = imageLoaderUtil.getmBitmapSet();

                // just show 5 of them
                final LinkedList<TweetBean> tweetsToAdapter = new LinkedList<>();
                if (null != mTweetList) {
                    int count = mTweetList.size() < TweetListAdapter.LOAD_TWEETS_NUM_EACH_TIME
                            ? mTweetList.size() : TweetListAdapter.LOAD_TWEETS_NUM_EACH_TIME;
                    for (int i = 0; i < count; ++i) {
                        tweetsToAdapter.add(mTweetList.get(i));
                    }
                }
                if (mRecyclerView.getAdapter() instanceof TweetListAdapter) {
                    TweetListAdapter adapter = (TweetListAdapter)mRecyclerView.getAdapter();
                    adapter.setmUser(mUser);
                    adapter.setTweetList(tweetsToAdapter);
                    adapter.setmBitmapSet(mBitmapSet);
                    adapter.notifyDataSetChanged();
                }

                // release old images hashmap
                ImageLoaderUtil.releaseImagesHashMap(oldToRelease);
            }
        }

        @Override
        public void onFailure(ImageLoaderUtil imageLoaderUtil, int msg) {
            // TODO show error page
        }
    };

    /**
     * send a message to download JSON from server and parse them(include user and tweets list);
     * Then download all images.
     * @param imageLoaderUtil
     */
    private void startFetchAll(ImageLoaderUtil imageLoaderUtil) {
        Message msg = new Message();
        msg.what = ImageLoaderUtil.MSG_CODE_GET_ALL;
        Bundle data = new Bundle();
        data.putString(ImageLoaderUtil.KEY_USER_URL, "http://thoughtworks-ios.herokuapp.com/user/jsmith");
        data.putString(ImageLoaderUtil.KEY_TWEET_LIST_URL, "http://thoughtworks-ios.herokuapp.com/user/jsmith/tweets");
        msg.setData(data);
        imageLoaderUtil.getmHandler().removeMessages(ImageLoaderUtil.MSG_CODE_GET_ALL);
        imageLoaderUtil.getmHandler().sendMessage(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecyclerView.removeOnScrollListener(onScrollListener);
        ImageLoaderUtil.releaseImagesHashMap(mBitmapSet);
        mImageLoaderUtil.removeCallBack(mCallBack);
        mImageLoaderUtil.releaseResForMainListPage();
    }
}

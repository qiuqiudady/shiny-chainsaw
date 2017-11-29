package com.example.friendcircle;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.widget.GridLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * @author yaobaocheng
 * *
 */
public class TweetListAdapter extends RecyclerView.Adapter<TweetListAdapter.BaseViewHolder> {
    private Activity mContext;
    private LinkedList<TweetBean> mTweetList;
    private UserBean mUser;

    public LinkedList<TweetBean> getmTweetList() {
        return mTweetList;
    }

    public void setmTweetList(LinkedList<TweetBean> mTweetList) {
        this.mTweetList = mTweetList;
    }

    public UserBean getmUser() {
        return mUser;
    }

    public void setmUser(UserBean mUser) {
        this.mUser = mUser;
    }

    // 每条content展开/收起的状态
    private SparseArray<Integer> mTextStateList;

    // whether has header view
    public boolean hasHeader() {
        return null != mUser && mUser.isValid();
    }

    /**
     * recyclerview 中的布局类型
     */
    private static final int TYPE_HEADER         = 0;  //header布局标志
    private static final int TYPE_NORMAL         = 1;  //普通布局标志

    /**
     * content 默认展开显示的最大行数，超过限制可以收到展开／收起
     */
    private static final int MAX_LINE_COUNT      = 6;
    private static final int STATE_UNKNOWN       = -1;
    private static final int STATE_NOT_OVERFLOW  = 1;       //文本行数没有超过限定行数
    private static final int STATE_COLLAPSED     = 2;       //文本行数超过限定行数，进行折叠
    private static final int STATE_EXPANDED      = 3;       //文本超过限定行数，被点击全文展开

    private static final int IMAGES_MAX_COLUMN = 3;

    public static final int LOAD_TWEETS_NUM_EACH_TIME = 5;    // load 5 tweets each time

    // all images in memory, key:MD5 of url, value:Bitmap object
    private HashMap<String, Bitmap> mBitmapSet;

    public void setmBitmapSet(HashMap<String, Bitmap> mBitmapSet) {
        this.mBitmapSet = mBitmapSet;
    }

    /**
     * find bitmap from HashMap with url string
     * @param url
     * @return
     */
    private Bitmap getBitmap(String url) {
        if (null == mBitmapSet || null == url) return null;
        return mBitmapSet.get(ImageLoaderUtil.decodeMD5(url));
    }

    public TweetListAdapter(Activity context) {
        mContext = context;
        mTextStateList = new SparseArray();
    }

    @Override
    public int getItemViewType(int position) {
        if (!hasHeader()) return TYPE_NORMAL;
        if (position == 0) return TYPE_HEADER;
        return TYPE_NORMAL;
    }

    @Override
    public BaseViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutId = R.layout.tweet_item;
        if (viewType == TYPE_HEADER)
            layoutId = R.layout.header;

        return new BaseViewHolder(mContext.getLayoutInflater().inflate(layoutId, parent, false));
    }

    @Override
    public void onBindViewHolder(final BaseViewHolder holder, final int position) {
        if (0 == position && hasHeader()) {
            bindHeader(holder);
        } else {
            bindTweet(holder, position);
        }
    }

    /**
     * bind user head
     * @param holder
     */
    private void bindHeader(final BaseViewHolder holder) {
        ImageView profile = holder.getImageView(R.id.profile);
        ImageView avatar = holder.getImageView(R.id.avatar);
        TextView nick = holder.getTextView(R.id.nick);

        //set profile and avatar
        profile.setImageBitmap(getBitmap(mUser.getProfileimage()));
        avatar.setImageBitmap(getBitmap(mUser.getAvatar()));
        nick.setText(mUser.getNick());
    }

    /**
     * bind a tweet to adapter
     * @param holder
     * @param position
     */
    private void bindTweet(final BaseViewHolder holder, final int position) {
        // get Tweet object
        final int dataPosition = (hasHeader() ? position-1 : position);
        TweetBean tweet = mTweetList.get(dataPosition);
        if (null == tweet || !tweet.isValid()) return;

        final ImageView avatar = holder.getImageView(R.id.avatar);
        final TextView nick = holder.getTextView(R.id.nick);
        final TextView content = holder.getTextView(R.id.content);
        final TextView expandOrCollapse = holder.getTextView(R.id.content_expand_or_collapse);
        final GridLayout images = (GridLayout)holder.getView(R.id.images_layout);
        final LinearLayout comments = (LinearLayout)holder.getView(R.id.comments_layout);

        avatar.setImageBitmap(getBitmap(tweet.getSender().getAvatar()));
        nick.setText(tweet.getSender().getNick());
        bindContent(dataPosition, tweet.getContent(), content, expandOrCollapse);
        bindImages(images, tweet.getImages());
        bindComments(comments, tweet.getComments());
    }

    /**
     * bind comments to adapter
     * @param commentsView
     * @param comments
     */
    private void bindComments(LinearLayout commentsView, List<TweetBean.CommentBean> comments) {
        //remove old all
        commentsView.removeAllViews();
        if (null == comments || comments.isEmpty()) return;

        for (TweetBean.CommentBean comment : comments) {
            TextView tv = new TextView(mContext);
            float size = mContext.getResources().getDimension(R.dimen.text_size);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);

            // discard comment has no content or sender nick
            if (TextUtils.isEmpty(comment.getSender().getNick()) || TextUtils.isEmpty(comment.getContent())) {
                continue;
            }
            String text = comment.getSender().getNick() + ":" + comment.getContent();

            // set different color for comment sender's nick
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            ForegroundColorSpan blueSpan = new ForegroundColorSpan(Color.BLUE);
            ForegroundColorSpan blackSpan = new ForegroundColorSpan(Color.BLACK);
            builder.setSpan(blueSpan, 0, comment.getSender().getNick().length()+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(blackSpan, comment.getSender().getNick().length()+1, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tv.setText(builder);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(params);
            commentsView.addView(tv);
        }
    }

    /**
     * bind images to adapter
     * @param imagesLayout
     * @param images
     */
    private void bindImages(GridLayout imagesLayout, List<TweetBean.ImagesBean> images) {
        //remove old all
        imagesLayout.removeAllViews();
        if (null == images || images.isEmpty()) return;

        // a single image need to be add, now the image is not Square Image View
        if (images.size() == 1) {
            addSingleImage(imagesLayout, images.get(0));
            return;
        }

        // when there are multi iamges, it's Square Image View
        addMultiImages(imagesLayout, images);
    }

    /**
     * add multi images to tweet, it't not more than 9.
     * When there are 4 images, we should fill 3'rd column with invisiable view
     * @param imagesLayout
     * @param images
     */
    private void addMultiImages(GridLayout imagesLayout, List<TweetBean.ImagesBean> images) {
        int columnCount = IMAGES_MAX_COLUMN;
        imagesLayout.setColumnCount(columnCount);

        //add all images to GridLayout
        for (int i = 0, size = images.size(); i < size; i++) {
            // fill invisiable ImageView in 3'rd column, when the images size equals to 4
            int specLocation = fillInvisiableView(imagesLayout, i, size);

            // create a SquareImageView instance
            ImageView imageView = new SquareImageView(mContext);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(getBitmap(images.get(i).getUrl()));

            //set width to 0, using weight value
            GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams(new ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
            GridLayout.Spec rowSpec = GridLayout.spec(specLocation / columnCount);
            GridLayout.Spec columnSpec = GridLayout.spec(specLocation % columnCount, 1.0f);
            layoutParams.rowSpec = rowSpec;
            layoutParams.columnSpec = columnSpec;
            int margins = mContext.getResources().getDimensionPixelOffset(R.dimen.image_margins);
            layoutParams.setMargins(margins, margins, margins, margins);

            imagesLayout.addView(imageView, layoutParams);
        }
    }

    private static final int IMAGE_COUNT_FOUR = 4;
    private static final int IMAGE_INVISIABLE_VIEW_INDEX = 2;
    /**
     * fill invisiable ImageView in 3'rd column, when the images size equals to 4
     * @param imagesLayout
     * @param i
     * @param size
     * @return
     */
    private int fillInvisiableView(GridLayout imagesLayout, int i, int size) {
        int specLocation = i;
        if (IMAGE_COUNT_FOUR == size) {

            // add a invisible imageView to get 1/3 weight for each one
            if (IMAGE_INVISIABLE_VIEW_INDEX == i) {
                ImageView placeHolder = new SquareImageView(mContext);
                placeHolder.setVisibility(View.INVISIBLE);

                GridLayout.LayoutParams placeHolderParams = new GridLayout.LayoutParams(new ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
                GridLayout.Spec rowSpec = GridLayout.spec(0);
                GridLayout.Spec columnSpec = GridLayout.spec(2, 1.0f);
                placeHolderParams.rowSpec = rowSpec;
                placeHolderParams.columnSpec = columnSpec;
                imagesLayout.addView(placeHolder, placeHolderParams);
            }

            // add one for Spec row and column
            if (i > 1) {
                specLocation = i + 1;
            }
        }
        return specLocation;
    }

    /**
     * add a single image to tweet, it's not Square Image View
     * @param imagesLayout
     * @param imagesBean
     */
    private void addSingleImage(GridLayout imagesLayout, TweetBean.ImagesBean imagesBean) {
        ImageView imageView = new ImageView(mContext);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setAdjustViewBounds(true);
        imageView.setMaxHeight(mContext.getResources().getDimensionPixelOffset(R.dimen.max_height_single_image));
        imageView.setMaxWidth(mContext.getResources().getDimensionPixelOffset(R.dimen.max_width_single_image));
        imageView.setImageBitmap(getBitmap(imagesBean.getUrl()));

        GridLayout.Spec rowSpec = GridLayout.spec(0);
        GridLayout.Spec columnSpec = GridLayout.spec(0);
        GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams
                (new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layoutParams.rowSpec = rowSpec;
        layoutParams.columnSpec = columnSpec;

        imagesLayout.addView(imageView, layoutParams);
    }

    /**
     * bind content to adapter
     * @param dataPosition
     * @param contentString
     * @param content
     * @param expandOrCollapse
     */
    private void bindContent(final int dataPosition, String contentString, final TextView content, final TextView expandOrCollapse) {
        if (TextUtils.isEmpty(contentString)) {
            content.setText("");
            expandOrCollapse.setText("");
            return;
        }

        int state = mTextStateList.get(dataPosition, STATE_UNKNOWN);

        //如果该item是第一次初始化，则取获取文本的行数
        if (state == STATE_UNKNOWN){
            content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    //这个回掉会调用多次，获取玩行数后记得注销监听
                    content.getViewTreeObserver().removeOnPreDrawListener(this);

                    content.setMaxLines(MAX_LINE_COUNT); //设置最大显示行数

                    //如果内容显示的行数大于限定显示行数
                    if (content.getLineCount() > MAX_LINE_COUNT) {
                        expandOrCollapse.setVisibility(View.VISIBLE);    //让其显示全文的文本框状态为显示
                        expandOrCollapse.setText("全文");  //设置其文字为全文
                        mTextStateList.put(dataPosition, STATE_COLLAPSED);
                    } else {
                        expandOrCollapse.setVisibility(View.GONE);   //显示全文隐藏
                        mTextStateList.put(dataPosition,STATE_NOT_OVERFLOW);    //让其不能超过限定的行数
                    }
                    return true;
                }
            });
            content.setMaxLines(Integer.MAX_VALUE);
        } else {
            //如果之前已经初始化过了，则使用保存的状态，无需在获取一次
            switch (state){
                case STATE_NOT_OVERFLOW:
                    expandOrCollapse.setVisibility(View.GONE);
                    break;
                case STATE_COLLAPSED:
                    content.setMaxLines(MAX_LINE_COUNT);
                    expandOrCollapse.setVisibility(View.VISIBLE);
                    expandOrCollapse.setText("全文");
                    break;
                case STATE_EXPANDED:
                    content.setMaxLines(Integer.MAX_VALUE);
                    expandOrCollapse.setVisibility(View.VISIBLE);
                    expandOrCollapse.setText("收起");
                    break;
            }
        }

        // set content
        content.setText(contentString);

        //设置显示和收起的点击事件
        expandOrCollapse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int state = mTextStateList.get(dataPosition, STATE_UNKNOWN);
                if (state == STATE_COLLAPSED){
                    content.setMaxLines(Integer.MAX_VALUE);
                    expandOrCollapse.setText("收起");
                    mTextStateList.put(dataPosition, STATE_EXPANDED);
                }else if (state == STATE_EXPANDED){
                    content.setMaxLines(MAX_LINE_COUNT);
                    expandOrCollapse.setText("全文");
                    mTextStateList.put(dataPosition, STATE_COLLAPSED);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        if (null == mTweetList || mTweetList.size() == 0) {
            return hasHeader() ? 1 : 0;
        }
        return mTweetList.size() + (hasHeader() ? 1 : 0);
    }

    public class BaseViewHolder extends RecyclerView.ViewHolder {
        private SparseArray<View> mViews;

        public BaseViewHolder(View itemView) {
            super(itemView);
            mViews = new SparseArray<>();
        }

        private <T extends View> T findView(int id) {
            View view = mViews.get(id);
            if (null == view) {
                view = itemView.findViewById(id);
                mViews.put(id, view);
            }
            return (T) view;
        }

        public View getView(int id) {
            return findView(id);
        }

        public TextView getTextView(int id) {
            return findView(id);
        }

        public ImageView getImageView(int id) {
            return findView(id);
        }

        public Button getButton(int id) {
            return findView(id);
        }
    }

    public abstract static class EndLessOnScrollListener extends  RecyclerView.OnScrollListener{

        //声明一个LinearLayoutManager
        private LinearLayoutManager mLinearLayoutManager;

        //当前页，从0开始
        private int currentPage = 0;

        //已经加载出来的Item的数量
        private int totalItemCount;

        //主要用来存储上一个totalItemCount
        private int previousTotal = 0;

        //在屏幕上可见的item数量
        private int visibleItemCount;

        //在屏幕可见的Item中的第一个
        private int firstVisibleItem;

        //是否正在上拉数据
        private boolean loading = true;

        public EndLessOnScrollListener(LinearLayoutManager linearLayoutManager) {
            this.mLinearLayoutManager = linearLayoutManager;
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            visibleItemCount = recyclerView.getChildCount();
            totalItemCount = mLinearLayoutManager.getItemCount();
            firstVisibleItem = mLinearLayoutManager.findFirstVisibleItemPosition();
            if(loading){
                //Log.d("wnwn","firstVisibleItem: " +firstVisibleItem);
                //Log.d("wnwn","totalPageCount:" +totalItemCount);
                //Log.d("wnwn", "visibleItemCount:" + visibleItemCount)；

                if(totalItemCount > previousTotal){
                    //说明数据已经加载结束
                    loading = false;
                    previousTotal = totalItemCount;
                }
            }
            //这里需要好好理解
            if (!loading && totalItemCount-visibleItemCount <= firstVisibleItem){
                currentPage ++;
                onLoadMore(currentPage);
                loading = true;
            }
        }

        /**
         * 提供一个抽闲方法，在Activity中监听到这个EndLessOnScrollListener
         * 并且实现这个方法
         * */
        public abstract void onLoadMore(int currentPage);
    }
}
package com.example.friendcircle;

import android.app.Activity;
import android.support.v7.widget.GridLayout;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;

import java.util.LinkedList;
import java.util.List;

/**
 * @author yaobaocheng
 *
 */
public class TweetListAdapter extends RecyclerView.Adapter<TweetListAdapter.BaseViewHolder> {
    private Activity mContext;
    private LinkedList<TweetBean> mTweetList;
    private UserBean mUser;

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
    private static final int STATE_NOT_OVERFLOW  = 1;    //文本行数没有超过限定行数
    private static final int STATE_COLLAPSED     = 2;       //文本行数超过限定行数，进行折叠
    private static final int STATE_EXPANDED      = 3;        //文本超过限定行数，被点击全文展开

    private static final int IMAGES_MAX_ROW = 3;
    private static final int IMAGES_MAX_COLUMN = 3;

    public TweetListAdapter(Activity context, LinkedList<TweetBean> tweetBeans, UserBean user) {
        mContext = context;
        mTweetList = tweetBeans;
        mUser = user;
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

    private void bindHeader(final BaseViewHolder holder) {
        ImageView profile = holder.getImageView(R.id.profile);
        ImageView avatar = holder.getImageView(R.id.avatar);
        TextView nick = holder.getTextView(R.id.nick);
        // TODO   set profile and avatar
        nick.setText(mUser.getNick());
    }

    private void bindTweet(final BaseViewHolder holder, final int position) {
        // get Tweet object
        final int dataPosition = (hasHeader() ? position-1 : position);
        TweetBean tweet = mTweetList.get(dataPosition);
        if (null == tweet) return;

        final ImageView avatar = holder.getImageView(R.id.avatar);
        final TextView nick = holder.getTextView(R.id.nick);
        final TextView content = holder.getTextView(R.id.content);
        final TextView expandOrCollapse = holder.getTextView(R.id.content_expand_or_collapse);
        final GridLayout images = (GridLayout)holder.getView(R.id.images_layout);
        final LinearLayout comments = (LinearLayout)holder.getView(R.id.comments_layout);


        // TODO
        nick.setText(tweet.getSender().getNick());

        bindContent(dataPosition, tweet.getContent(), content, expandOrCollapse);

        bindImages(images, tweet.getImages());

        bindComments(comments, tweet.getComments());
    }

    private void bindComments(LinearLayout commentsView, List<TweetBean.CommentBean> comments) {
        if (null == comments || comments.isEmpty()) return;

        for (TweetBean.CommentBean comment : comments) {
            TextView tv = new TextView(mContext);
            tv.setText(comment.getSender().getNick() + ":" + comment.getContent());

//            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams

        }
    }

    private void bindImages(GridLayout imagesLayout, List<TweetBean.ImagesBean> images) {
        if (null == images || images.isEmpty()) return;

        //清空子视图 防止原有的子视图影响
        imagesLayout.removeAllViews();

        // single image show
        if (images.size() == 1) {
            // TODO
            // only one image, show it fully, not grid
            ImageView iv = new ImageView(mContext);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setMaxWidth(400);
            // TODO  add image src
            iv.setImageResource(R.drawable.profile);
            imagesLayout.setColumnCount(1);
            GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            imagesLayout.addView(iv, layoutParams);
            return;
        }

        int columnCount = IMAGES_MAX_COLUMN;
        imagesLayout.setColumnCount(columnCount);

        //遍历集合 动态添加
        for (int i = 0, size = images.size(); i < size; i++) {
            GridLayout.Spec rowSpec = GridLayout.spec(i / columnCount);//行数
            GridLayout.Spec columnSpec = GridLayout.spec(i % columnCount, 1.0f);//列数 列宽的比例 weight=1
            ImageView imageView = new SquareImageView(mContext);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageResource(R.drawable.profile);  // TODO
            //由于宽（即列）已经定义权重比例 宽设置为0 保证均分
            GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams(new ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
            layoutParams.rowSpec = rowSpec;
            layoutParams.columnSpec = columnSpec;
            layoutParams.setMargins(0, 0, 0, 0);

            imagesLayout.addView(imageView, layoutParams);
        }

    }

    /**
     * bind content to adapter
     * @param dataPosition
     * @param contentString
     * @param content
     * @param expandOrCollapse
     */
    private void bindContent(final int dataPosition, String contentString, final TextView content, final TextView expandOrCollapse) {
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
}
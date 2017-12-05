package com.example.friendcircle;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.view.WindowManager;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Created by pc on 2017/11/27.
 * This class is used for download json and images in Sub-thread。
 * It's a single instance, but not multi-thread safe.
 * We can use it in some activities or services, they can add own callback.
 * Because its life cycle is the same with application, so no need to release its whole scope res.
 */
public class ImageLoaderUtil {

    private static ImageLoaderUtil mInstance;
    private OkHttpClient mOkHttpClient;

    // because it's a single instance, so mContext should be Application Context at best
    private Context mContext;

    // sub-thread handler, receive message to handle download and parse work
    private Handler mHandler;
    private LinkedList<TweetBean> mTweetList;   // tweets list
    private UserBean mUser;

    // load all images to memory, key:MD5 of url, value:Bitmap object
    private HashMap<String, Bitmap> mBitmapSet;
    public Handler getmHandler() {
        return mHandler;
    }

    public LinkedList<TweetBean> getmTweetList() {
        return mTweetList;
    }

    public UserBean getmUser() {
        return mUser;
    }

    public HashMap<String, Bitmap> getmBitmapSet() {
        return mBitmapSet;
    }

    /**
     * Here we can expand other time waste action
     */
    public static final int MSG_CODE_GET_ALL = 0x01;
    public static final String KEY_USER_URL = "user_url";
    public static final String KEY_TWEET_LIST_URL = "tweet_list_url";
    private static final int DISK_CACHE_SIZE = 10*1024*1024;

    /**
     * callback after time-waste action, it runs on Sub-thread
      */
    private ArrayList<CallBack> mCallBacks = new ArrayList<>();

    public interface CallBack {
        void onResponse(final ImageLoaderUtil imageLoaderUtil, final int msgCode);
        void onFailure(final ImageLoaderUtil imageLoaderUtil, final int msgCode);
    }

    /**
     * add a callback listener
     * @param callBack
     */
    public void addCallBack(CallBack callBack) {
        if (null != callBack) {
            mCallBacks.add(callBack);
        }
    }

    /**
     * remove a callback listener
     * @param callBack
     * @return
     */
    public boolean removeCallBack(CallBack callBack) {
        if (null == callBack) return false;
        return mCallBacks.remove(callBack);
    }

    /**
     * After time-waste action done, execute all callbacks
     * @param msgCode  action type
     */
    private void executeCallBacksOnResponse(int msgCode) {
        for (CallBack callBack : mCallBacks) {
            callBack.onResponse(this, msgCode);
        }
    }

    /**
     * After time-waste action done, execute all callbacks
     * @param msgCode  action type
     */
    private void executeCallBacksOnFailure(int msgCode) {
        for (CallBack callBack : mCallBacks) {
            callBack.onFailure(this, msgCode);
        }
    }

    private ImageLoaderUtil(final Context context) {
        mContext = context;
        mOkHttpClient = new OkHttpClient();
        mOkHttpClient.setConnectTimeout(5, TimeUnit.SECONDS);
        mOkHttpClient.setReadTimeout(5, TimeUnit.SECONDS);
        mOkHttpClient.setWriteTimeout(10, TimeUnit.SECONDS);
        HandlerThread handlerThread = new HandlerThread("network_thread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_CODE_GET_ALL:
                        // download User and tweets list json, and then parse them to object
                        boolean ret = fetchUserAndTweets(msg);
                        if (ret) {
                            // For images,download from network or load from disk cache into memory
                            fetchAllImages();
                            executeCallBacksOnResponse(MSG_CODE_GET_ALL);
                        } else {
                            executeCallBacksOnFailure(MSG_CODE_GET_ALL);
                        }
                        // avoid frequent refresh action
                        removeMessages(MSG_CODE_GET_ALL);
                }
            }
        };
    }

    /**
     * get the single instance
     * @param context
     * @return
     */
    public static ImageLoaderUtil getInstance(final Context context) {
        if (null == mInstance) {
            mInstance = new ImageLoaderUtil(context);
        }
        return mInstance;
    }

    /**
     * download user and tweets list, and then parse them
     * @param msg
     */
    private boolean fetchUserAndTweets(Message msg) {
        boolean ret = true;
        try {
            // get user from server
            String userUrl = msg.getData().getString(KEY_USER_URL);
            final Request getUserReq = new Request.Builder().url(userUrl).build();
            Response userResponse = mOkHttpClient.newCall(getUserReq).execute();

            // if download successfully, then parse it as JSON
            if (userResponse.isSuccessful()) {
                mUser = JsonParserUtil.parseUserFromJson(userResponse.body().string());
            }

            // get tweets list from server
            String tweetListUrl = msg.getData().getString(KEY_TWEET_LIST_URL);
            final Request getTweetListReq = new Request.Builder().url(tweetListUrl).build();
            Response tweetListResponse = mOkHttpClient.newCall(getTweetListReq).execute();

            // if download successfully, then parse it as JSON
            if (tweetListResponse.isSuccessful()) {
                mTweetList = JsonParserUtil.filterInvalidTweet(JsonParserUtil.parseTweetFromJson(tweetListResponse.body().string()));
            }
        } catch (IOException e) {
            System.out.println("Network access exception");
            e.printStackTrace();
            ret = false;
        }
        return ret;
    }

    /**
     * Fetch all images for user and tweets list.
     * If it exists in disk cache, load it into memory directly,
     * otherwise, download from network into disk cache and load it into memory.
     * Here we decode bitmaps with used size
     */
    private void fetchAllImages() {
        mBitmapSet = new HashMap<>();

        // open disk cache
        openDiskLruCache();

        int screenWidth = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getWidth();
        Resources res = mContext.getResources();

        // if User is valid, load its profile and avatar
        if (mUser.isValid()) {
            if (!TextUtils.isEmpty(mUser.getProfileimage())) {
                loadImage(mUser.getProfileimage(), screenWidth, res.getDimensionPixelOffset(R.dimen.profile_height));
            }
            if (!TextUtils.isEmpty(mUser.getAvatar())) {
                loadImage(mUser.getAvatar(), res.getDimensionPixelOffset(R.dimen.user_avatar_size),
                        res.getDimensionPixelOffset(R.dimen.user_avatar_size));
            }
        }

        // load images for tweets list
        for (TweetBean tweetBean : mTweetList) {
            String url = tweetBean.getSender().getAvatar();
            if (!TextUtils.isEmpty(url)) {
                loadImage(url, res.getDimensionPixelOffset(R.dimen.sender_avatar_size),
                        res.getDimensionPixelOffset(R.dimen.sender_avatar_size));
            }
            if (null == tweetBean.getImages()) {continue;}
            for (TweetBean.ImagesBean imagesBean : tweetBean.getImages()) {
                if (TextUtils.isEmpty(imagesBean.getUrl())) continue;

                // when a single image
                if (1 == tweetBean.getImages().size()) {
                    loadImage(imagesBean.getUrl(), res.getDimensionPixelOffset(R.dimen.max_width_single_image),
                            res.getDimensionPixelOffset(R.dimen.max_height_single_image));
                } else {
                    // when multi images. now just use 1/3 of screen width
                    loadImage(imagesBean.getUrl(), screenWidth/3,screenWidth/3);
                }
            }
        }
        try {
            mDiskLruCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * If the image exists in disk cache, then load it with reqWidth and reqHeight.
     * Otherwise, download from network, write into disk cache, and then load with reqWidth and reqHeight.
     * @param url
     * @param reqWidth
     * @param reqHeight
     */
    private void loadImage(String url, int reqWidth, int reqHeight){
        // use MD5 decode url for key of disk cache
        String key = decodeMD5(url);

        try {
            // use MD5 of url as Disk cache file name, and mBitmapSet's key
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);

            // current image file has no disk cache, download
            if (null == snapshot) {
                final Request req = new Request.Builder().url(url).build();
                Response res = mOkHttpClient.newCall(req).execute();
                if (res.isSuccessful()) {
                    Bitmap bitmap = decodeBitmapFromBytes(res.body().bytes(), reqWidth, reqHeight);
                    mBitmapSet.put(key, bitmap);
                    writeBitmapToDiskCache(key, bitmap);
                }
            } else {
                // current image has disk cache, use it
                Bitmap bitmap = decodeBitmapFromStream(snapshot.getInputStream(0), reqWidth, reqHeight);
                mBitmapSet.put(key, bitmap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * decode bitmap from InputStream
     * @param is
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static Bitmap decodeBitmapFromStream(InputStream is, int reqWidth, int reqHeight) {
        ByteArrayOutputStream baos = null;
        BufferedInputStream bis = null;
        try {
            baos = new ByteArrayOutputStream();
            byte[] bt = new byte[1024];
            int read;
            bis = new BufferedInputStream(is, 2*1024);
            while ((read = bis.read(bt, 0, bt.length)) != -1) {
                baos.write(bt, 0, read);
            }
            baos.close();
            bis.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != bis) {
                    bis.close();
                }
                if (null != baos) {
                    baos.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        Bitmap bitmap = decodeBitmapFromBytes(baos.toByteArray(), reqWidth, reqHeight);
        return bitmap;
    }

    /**
     * write bitmap to disk cache
     * @param key
     * @param bitmap
     */
    private void writeBitmapToDiskCache(String key, Bitmap bitmap) {
        // write into disk cache
        DiskLruCache.Editor editor = null;
        try {
            editor = mDiskLruCache.edit(key);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, editor.newOutputStream(0));
            editor.commit();
            mDiskLruCache.flush();
        } catch (IOException e) {
            if (null != editor) {
                try {
                    editor.abort();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private DiskLruCache mDiskLruCache;

    /**
     * open DiskLruCache
     */
    private void openDiskLruCache() {
        if (null != mDiskLruCache) return;

        try {
            // make cache directory
            File cacheDir = getDiskCacheDir(mContext,"images");
            if(!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // get current app version code
            int versionCode = 1;
            try {
                PackageInfo info = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(),0);
                versionCode = info.versionCode;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            mDiskLruCache = DiskLruCache.open(cacheDir, versionCode, 1, DISK_CACHE_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * get disk cache directory
     * @param context
     * @param uniqueName
     * @return
     */
    private static File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
                cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * decode bitmap with size of reqWidth and reqHeight from inputstream
     * @param bytes
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static Bitmap decodeBitmapFromBytes(byte[] bytes, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0 ,bytes.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0 ,bytes.length, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image  
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if(height > reqHeight || width > reqWidth) {
            if(width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }
        }
        return inSampleSize;
    }

    /**
     * decode with MD5 for url
     * @param key
     * @return
     */
    public static String decodeMD5(String key) {
        String md5Val;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            md5Val = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            md5Val = String.valueOf(key.hashCode());
        }
        return md5Val;
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * release iamges hashmap(call Bitmap.recycle for each one)
     * @param imagesMap
     */
    public static void releaseImagesHashMap(HashMap<String, Bitmap> imagesMap) {
        if (null != imagesMap && !imagesMap.isEmpty()) {
            Iterator it = imagesMap.entrySet().iterator();
            while (it.hasNext()) {
                HashMap.Entry entry = (HashMap.Entry) it.next();
                if (entry.getValue() instanceof Bitmap) {
                    ((Bitmap) entry.getValue()).recycle();
                }
            }
        }
    }

    /**
     * release resources related to MainActivity.Keep other scope res.
     * If sub-thread is using these res, we should queue to release.
     */
    public void releaseResForMainListPage() {
        mHandler.removeMessages(MSG_CODE_GET_ALL);
        mHandler.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                // just discard pointer of these instance, gc will recycle them.
                // Keep their life cycle the same with MainActivity
                mUser = null;
                mTweetList = null;
                mBitmapSet = null;
            }
        });
    }
}

package com.example.friendcircle;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by pc on 2017/11/27.
 * This class is used for download json and images in Sub-thread
 */
public class ImageLoaderUtil {

    private OkHttpClient mOkHttpClient;
    private Activity mActivity;

    // sub-thread handler, receive message to handle download and parse work
    public Handler mHandler;

    public LinkedList<TweetBean> mTweetList;   // tweets list
    public UserBean mUser;

    // load all images to memory, key:MD5 of url, value:Bitmap object
    public HashMap<String, Bitmap> mBitmapSet;

    public static final int MSG_CODE_GET_ALL = 0x01;
    public static final String KEY_USER_URL = "user_url";
    public static final String KEY_TWEET_LIST_URL = "tweet_list_url";
    private static final int DISK_CACHE_SIZE = 10*1024*1024;

    /**
     * callback after network request, it runs on UI thread
      */
    private CallBack mCallBack;

    public interface CallBack {
        void onResponse(final ImageLoaderUtil imageLoaderUtil, final int msgCode);
        void onFailure(final ImageLoaderUtil imageLoaderUtil, final int msgCode);
    }

    public ImageLoaderUtil(final Activity activity, final CallBack callBack) {
        mActivity = activity;
        mOkHttpClient = new OkHttpClient();
        mCallBack = callBack;
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

                            // if callback is not null , call it
                            if (null != mCallBack) {
                                mActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mCallBack.onResponse(ImageLoaderUtil.this, MSG_CODE_GET_ALL);
                                    }
                                });
                            }
                        } else {
                            // if callback is not null , call it
                            if (null != mCallBack) {
                                mActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mCallBack.onFailure(ImageLoaderUtil.this, MSG_CODE_GET_ALL);
                                    }
                                });
                            }
                        }
                        // avoid frequent refresh action
                        removeMessages(MSG_CODE_GET_ALL);
                }
            }
        };
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
            ret = false;
        }
        return ret;
    }

    /**
     * Fetch all images for user and tweets list.
     * If it exists in disk cache, load it into memory directly,
     * otherwise, download from network into disk cache then load it into memory.
     */
    private void fetchAllImages() {
        mBitmapSet = new HashMap<>();

        // open disk cache
        openDiskLruCache();

        // if User is valid, load its profile and avatar
        if (mUser.isValid()) {
            if (!TextUtils.isEmpty(mUser.getProfileimage())) {
                loadImage(mUser.getProfileimage());
            }
            if (!TextUtils.isEmpty(mUser.getAvatar())) {
                loadImage(mUser.getAvatar());
            }
        }

        // load images for tweets list
        for (TweetBean tweetBean : mTweetList) {
            String url = tweetBean.getSender().getAvatar();
            if (!TextUtils.isEmpty(url)) {
                loadImage(url);
            }
            if (null == tweetBean.getImages()) {continue;}
            for (TweetBean.ImagesBean imagesBean : tweetBean.getImages()) {
                if (!TextUtils.isEmpty(imagesBean.getUrl())) {
                    loadImage(imagesBean.getUrl());
                }
            }
        }
        try {
            mDiskLruCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadImage(String url){
        // use MD5 decode url for key of disk cache
        String key = decodeMD5(url);
        DiskLruCache.Snapshot snapshot = null;
        DiskLruCache.Editor editor = null;
        BufferedOutputStream bos = null;
        BufferedInputStream bis = null;
        try {
            // use MD5 of url as Disk cache file name, and mBitmapSet's key
            snapshot = mDiskLruCache.get(key);

            // current image file has no disk cache, download
            if (null == snapshot) {
                final Request req = new Request.Builder().url("http://192.168.3.16/docs/images/tomcat.png").build();  // TODO  revert url
                Response res = mOkHttpClient.newCall(req).execute();
                editor = mDiskLruCache.edit(key);

                // write to disk cache
                if (null != editor) {
                    OutputStream outputStream = editor.newOutputStream(0);

                    byte[] bt = new byte[1024];
                    int read;
                    bos = new BufferedOutputStream(outputStream, 2*1024);
                    bis = new BufferedInputStream(res.body().byteStream(), 2*1024);
                    while ((read = bis.read(bt, 0, bt.length)) != -1) {
                        bos.write(bt, 0, read);
                    }
                    bos.close();
                    bos = null;
                    bis.close();
                    bis = null;
                    editor.commit();
                    editor = null;
                    mDiskLruCache.flush();
                }
            }
        } catch (IOException e) {
            System.out.println("IOException");
            if (null != editor) {
                try {
                    editor.abort();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        } finally {
            try {
                if (null != bis) {
                    bis.close();
                }
                if (null != bos) {
                    bos.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        if (null == snapshot) {
            // read snap from disk cache again
            try {
                snapshot = mDiskLruCache.get(key);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (null != snapshot) {
            // TODO
            // Bitmap bitmap = decodeBitmapFromStream(snapshot.getInputStream(0), 120, 120);
            Bitmap bitmap = BitmapFactory.decodeStream(snapshot.getInputStream(0));
            mBitmapSet.put(key, bitmap);
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
            File cacheDir = getDiskCacheDir(mActivity,"images");
            if(!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // get current app version code
            int versionCode = 1;
            try {
                PackageInfo info = mActivity.getPackageManager().getPackageInfo(mActivity.getPackageName(),0);
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
     * @param is
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static Bitmap decodeBitmapFromStream(InputStream is, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        try {
            is.reset();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeStream(is, null, options);
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
     * release resources for this instance
     */
    public void release() {
        if (null != mHandler) {
            mHandler.getLooper().quit();
        }
    }
}

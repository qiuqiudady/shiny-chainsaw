package com.example.friendcircle;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by pc on 2017/11/27.
 * This class is used for download json and load images.It's thread safely.
 */
public class ImageLoader {
    public static final String TAG = "ImageLoader";
    private OkHttpClient mOkHttpClient;
    private Context mContext;
    private LinkedList<TweetBean> mTweetList;   // tweets list
    private UserBean mUser;

    private static final int DISK_CACHE_SIZE = 50*1024*1024;
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;

    public LinkedList<TweetBean> getmTweetList() {
        return mTweetList;
    }

    public UserBean getmUser() {
        return mUser;
    }

    public LruCache<String, Bitmap> getmMemoryCache() {
        return mMemoryCache;
    }

    /**
     * callback after load-all action done, it runs on UI thread
      */
    private LoaderCallBack mLoaderCallBack;
    public interface LoaderCallBack {
        void onLoadDone(final ImageLoader imageLoader);
        void onLoadFailure(final ImageLoader imageLoader);
    }

    //Indicates whether all tasks of getting images have finished, when taskType is TYPE_GET_IMAGES
    private final AtomicInteger mCount = new AtomicInteger(0);
    public class RunnableTask implements Runnable {
        public static final int TYPE_GET_USER_AND_TWEETS_LIST = 0;
        public static final int TYPE_GET_IMAGES = 1;   // load all images

        private int mTaskType = TYPE_GET_IMAGES;
        private String mUrl;
        private String extra;
        private int mRequestWidth;   // Bitmap decode width
        private int mRequestHeight;  // Bitmap decode height

        public RunnableTask(int requestType) {
            mTaskType = requestType;
            if (TYPE_GET_IMAGES == mTaskType) {
                mCount.getAndIncrement();
            }
        }

        public RunnableTask setUrl(String url) {
            this.mUrl = url;
            return this;
        }

        public RunnableTask setExtra(String extra) {
            this.extra = extra;
            return this;
        }

        public RunnableTask setRequestSize(int requestWidth, int requestHeight) {
            this.mRequestWidth = requestWidth;
            this.mRequestHeight = requestHeight;
            return this;
        }

        @Override
        public void run() {
            if (TYPE_GET_USER_AND_TWEETS_LIST == mTaskType) {
                // When user and tweets list downloaded successfully, then load images.
                if (loadUser(mUrl) && loadTweetsList(extra)) {
                    fetchAllImages();
                } else {
                    if (null != mLoaderCallBack) {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mLoaderCallBack.onLoadFailure(ImageLoader.this);
                            }
                        });
                    }
                }
            } else if (TYPE_GET_IMAGES == mTaskType) {
                loadBitmap(mUrl, mRequestWidth, mRequestHeight);

                // when mCount equals 0, it indicates that all tasks of getting image have been done, now we should notify UI.
                // All images have been loaded, we should notify UI.
                if (0 == mCount.decrementAndGet()) {
                    if(null != mLoaderCallBack) {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mLoaderCallBack.onLoadDone(ImageLoader.this);
                            }
                        });
                    }
                }
            }
        }
    }

    public ImageLoader(final Context context, final LoaderCallBack callBack) {
        mContext = context.getApplicationContext();
        mLoaderCallBack = callBack;

        mOkHttpClient = new OkHttpClient();
        mOkHttpClient.setConnectTimeout(5, TimeUnit.SECONDS);
        mOkHttpClient.setReadTimeout(10, TimeUnit.SECONDS);
        mOkHttpClient.setWriteTimeout(10, TimeUnit.SECONDS);

        // create memory cache for bitmap
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 4;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };

        // open disk cache for bitmap
        openDiskLruCache();
    }

    /**
     * Start fetching data
     * @param userUrl
     * @param tweetListUrl
     */
    public synchronized void startFetchData(String userUrl, String tweetListUrl) {
           AsyncTask.THREAD_POOL_EXECUTOR.execute(new RunnableTask(RunnableTask.TYPE_GET_USER_AND_TWEETS_LIST)
                .setUrl(userUrl)
                .setExtra(tweetListUrl));
    }

    /**
     * @param url
     * @return  whether the loaded user is valid
     */
    private boolean loadUser(String url) {
        try {
            // get user from server;
            final Request getUserReq = new Request.Builder().url(url).build();
            Response response = mOkHttpClient.newCall(getUserReq).execute();

            // if download successfully, then parse it as JSON
            if (response.isSuccessful()) {
                UserBean user = JsonParserUtil.parseUserFromJson(response.body().string());
                synchronized (ImageLoader.this) {
                    mUser = user;
                    return mUser.isValid();
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "load User failed");
        }
        return false;
    }

    /**
     * @param url
     * @return whether load tweets list successfully
     */
    private boolean loadTweetsList(String url) {
        try {
            // get tweets list from server
            final Request getTweetListReq = new Request.Builder().url(url).build();
            Response response = mOkHttpClient.newCall(getTweetListReq).execute();
            // if download successfully, then parse it as JSON
            if (response.isSuccessful()) {
                LinkedList<TweetBean> tweets = JsonParserUtil.filterInvalidTweet(JsonParserUtil.parseTweetFromJson(response.body().string()));
                synchronized (ImageLoader.this) {
                    mTweetList = tweets;
                }
                return true;
            }
        } catch (IOException e) {
            Log.w(TAG, "load Tweets List failed");
        }
        return false;
    }

    /**
     * Fetch all images for user and tweets list.
     * If it exists in disk cache, load it into memory directly,
     * otherwise, download from network into disk cache and load it into memory.
     * Here we decode bitmaps with used size
     */
    private synchronized void fetchAllImages() {
        int screenWidth = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getWidth();
        Resources res = mContext.getResources();

        // if User is valid, load its profile and avatar
        if (mUser.isValid()) {
            if (!TextUtils.isEmpty(mUser.getProfileimage())) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new RunnableTask(RunnableTask.TYPE_GET_IMAGES)
                        .setUrl(mUser.getProfileimage())
                        .setRequestSize(screenWidth, res.getDimensionPixelOffset(R.dimen.profile_height)));
            }
            if (!TextUtils.isEmpty(mUser.getAvatar())) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new RunnableTask(RunnableTask.TYPE_GET_IMAGES)
                        .setUrl(mUser.getAvatar())
                        .setRequestSize(res.getDimensionPixelOffset(R.dimen.user_avatar_size),
                                res.getDimensionPixelOffset(R.dimen.user_avatar_size)));
            }
        }

        // load images for tweets list
        for (TweetBean tweetBean : mTweetList) {
            String url = tweetBean.getSender().getAvatar();
            if (!TextUtils.isEmpty(url)) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new RunnableTask(RunnableTask.TYPE_GET_IMAGES)
                        .setUrl(url)
                        .setRequestSize(res.getDimensionPixelOffset(R.dimen.sender_avatar_size),
                                res.getDimensionPixelOffset(R.dimen.sender_avatar_size)));
            }
            if (null == tweetBean.getImages()) {continue;}
            for (TweetBean.ImagesBean imagesBean : tweetBean.getImages()) {
                if (TextUtils.isEmpty(imagesBean.getUrl())) continue;

                // when a single image
                if (1 == tweetBean.getImages().size()) {
                    AsyncTask.THREAD_POOL_EXECUTOR.execute(new RunnableTask(RunnableTask.TYPE_GET_IMAGES)
                            .setUrl(imagesBean.getUrl())
                            .setRequestSize(res.getDimensionPixelOffset(R.dimen.max_width_single_image),
                                    res.getDimensionPixelOffset(R.dimen.max_height_single_image)));
                } else {
                    // when multi images. now just use 1/3 of screen width
                    AsyncTask.THREAD_POOL_EXECUTOR.execute(new RunnableTask(RunnableTask.TYPE_GET_IMAGES)
                            .setUrl(imagesBean.getUrl())
                            .setRequestSize(screenWidth/3,screenWidth/3));
                }
            }
        }
    }

    /**
     * bind bitmap to ImageView.It should be called in UI thread.
     * @param imageView
     * @param url
     */
    public void bindBitmap(ImageView imageView, String url) {
        bindBitmap(imageView, url, 0, 0);
    }

    /**
     * bind bitmap to ImageView. It should be called in UI thread.
     * @param imageView
     * @param url
     * @param reqWidth
     * @param reqHeight
     */
    public void bindBitmap(final ImageView imageView, final String url, final int reqWidth, final int reqHeight) {
        imageView.setTag(url);

        // If we can load bitmap from memory, set it to ImageView directly.
        Bitmap bitmap = loadBitmapFromMemory(url);
        if (null != bitmap) {
            imageView.setImageBitmap(bitmap);
        }

        // We should start an async task to load bitmap
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, reqWidth, reqHeight);
                if (null != bitmap) {
                    Message result = mMainHandler.obtainMessage(MSG_CODE_POST_RESULT,
                            new BindBitmapResult(imageView, url, bitmap));
                    result.sendToTarget();
                }
            }
        });
    }

    /**
     * UI thread handler, after load bitmap, set it to ImageView.
     */
    private static final int MSG_CODE_POST_RESULT = 1;
    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (MSG_CODE_POST_RESULT == msg.what) {
                if (msg.obj instanceof BindBitmapResult) {
                    BindBitmapResult result = (BindBitmapResult) msg.obj;
                    ImageView imageView = result.imageView;
                    if (imageView.getTag().equals(result.url)) {
                        imageView.setImageBitmap(result.bitmap);
                    } else {
                        Log.w(TAG, "set bitmap, but image's url has changed, ignore");
                    }
                }
            }
        }
    };

    /**
     * load bitmap from memory/disk cache/http
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public Bitmap loadBitmap(String url, int reqWidth, int reqHeight) {
        Bitmap bitmap = loadBitmapFromMemory(url);
        if (null == bitmap) {
            bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight);
        }
        if (null == bitmap) {
            bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight);
        }
        return bitmap;
    }

    /**
     * Download a bitmap into disk cache, and put into memory cache, return it
     * @param url
     * @param reqWidth
     * @param reqHeight
     */
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight){
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from http cannot run on UI thread.");
            return null;
        }

        Bitmap bitmap = null;
        final Request req = new Request.Builder().url(url).build();
        try {
            Response response = mOkHttpClient.newCall(req).execute();

            // download image successfully, decode it with request size, write it to disk cache, and load to memory
            if (response.isSuccessful()) {
                // because disk space is not enough or some other reasons, disk cache not created succussfully.
                // we should download to memory directly.
                if (null == mDiskLruCache) {
                    bitmap = decodeBitmapFromBytes(response.body().bytes(), reqWidth, reqHeight);
                    putBitmapToMemory(url, bitmap);
                } else {
                    writeStreamToDiskCache(url, response.body().byteStream());
                    bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * load bitmap to memory cache
     * @param key
     * @param bitmap
     */
    private void putBitmapToMemory(String key, Bitmap bitmap) {
        if (null != bitmap && mMemoryCache.get(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * load bitmap from memory cache
     * @param key
     * @return
     */
    private Bitmap loadBitmapFromMemory(String key) {
        return mMemoryCache.get(key);
    }

    /**
     * load Bitmap from disk cache with request size, and load it to memory cache.
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from disk cache cannot run on UI thread.");
            return null;
        }

        if (null == mDiskLruCache) {
            return null;
        }

        try {
            // use MD5 decode url for key of disk cache
            String key = decodeMD5(url);
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
            if (null != snapshot) {
                // current image has disk cache, use it
                Bitmap bitmap = decodeBitmapFromFD(((FileInputStream)snapshot.getInputStream(0)).getFD(), reqWidth, reqHeight);
                putBitmapToMemory(url, bitmap);
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * write bitmap to disk cache, because DiskLruCache.edit is thread-safely, so no need to do synchronized
     * @param key
     * @param is
     */
    private void writeStreamToDiskCache(String key, InputStream is) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from disk cache cannot run on UI thread.");
            return;
        }

        if (null == mDiskLruCache) return;

        key = decodeMD5(key);
        // write into disk cache
        DiskLruCache.Editor editor = null;
        BufferedOutputStream bos = null;
        BufferedInputStream bis = null;
        try {
            editor = mDiskLruCache.edit(key);
            if (null != editor) {
                bos = new BufferedOutputStream(editor.newOutputStream(0));
                bis = new BufferedInputStream(is);
                byte[] bts = new byte[1024];
                int read = -1;
                while((read = bis.read(bts, 0, bts.length)) != -1) {
                    bos.write(bts, 0, read);
                }
                bos.flush();
                editor.commit();
            }
        } catch (IOException e) {
            if (null != editor) {
                try {
                    editor.abort();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        } finally {
            closeInStream(bis);
            closeOutStream(bos);
        }
    }

    private static void closeInStream(InputStream is) {
        try {
            if (null != is) {
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void closeOutStream(OutputStream os) {
        try {
            if (null != os) {
                os.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

            // If disk has enough space, create cache
            if (getUsableSpace(cacheDir) >= DISK_CACHE_SIZE) {
                mDiskLruCache = DiskLruCache.open(cacheDir, versionCode, 1, DISK_CACHE_SIZE);
            }
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
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeOutStream(baos);
            closeInStream(bis);
        }

        Bitmap bitmap = decodeBitmapFromBytes(baos.toByteArray(), reqWidth, reqHeight);
        return bitmap;
    }

    public static Bitmap decodeBitmapFromFD(FileDescriptor fd, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd ,null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd ,null, options);
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
        if (0 == reqWidth || 0 == reqHeight) {
            return 1;
        }

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
    private static String decodeMD5(String key) {
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

    private static long getUsableSpace(File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }

    /**
     * method bindBitmap post a instance of BindBitmapResult for UI handler to refresh view.
     */
    public static class BindBitmapResult {
        public ImageView imageView;
        public String url;
        public Bitmap bitmap;

        public BindBitmapResult(ImageView imageView, String url, Bitmap bitmap) {
            this.imageView = imageView;
            this.url = url;
            this.bitmap = bitmap;
        }
    }
}

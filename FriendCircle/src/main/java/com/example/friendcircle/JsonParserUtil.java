package com.example.friendcircle;

import com.example.friendcircle.bean.TweetBean;
import com.example.friendcircle.bean.UserBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * @author yaobaocheng
 * This Util class is used for parsering Json to User and Tweets list
 */
public class JsonParserUtil {
    /**
     * filter invalid tweets and return valid ones
     * @param list
     * @return
     */
    public static LinkedList<TweetBean> filterInvalidTweet(LinkedList<TweetBean> list) {
        if (null == list || list.size() == 0) {
            return list;
        }

        for (int i = 0; i < list.size(); ++i) {
            TweetBean tweetBean = list.get(i);
            if (!tweetBean.isValid()) {
                list.remove(tweetBean);
                --i;
            }
        }
        return list;
    }

    /**
     * parse Json string to tweets list
     * @param jsonData
     * @return
     */
    public static LinkedList<TweetBean> parseTweetFromJson(String jsonData) {
        Type listType = new TypeToken<LinkedList<TweetBean>>(){}.getType();
        Gson gson = new Gson();
        return gson.fromJson(jsonData, listType);
    }

    /**
     * parse Json string to User object
     * @param jsonData
     * @return
     */
    public static UserBean parseUserFromJson(String jsonData) {
        Gson gson = new Gson();
        return gson.fromJson(jsonData, UserBean.class);
    }

    /**
     * used for debug
     * @param tweetList
     */
    public static void printTweetList(LinkedList<TweetBean> tweetList) {
        for (Iterator iterator = tweetList.iterator(); iterator.hasNext(); ) {
            TweetBean tweet = (TweetBean) iterator.next();
            System.out.println(tweet.toString());
            System.out.println();
        }
    }
}

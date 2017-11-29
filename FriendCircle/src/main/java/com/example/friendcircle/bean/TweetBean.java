package com.example.friendcircle.bean;

import android.text.TextUtils;

import java.util.List;

/**
 * Created by pc on 2017/11/27.
 * This bean used in multi-thread
 */
public class TweetBean {
    /**
     * content : 沙发！
     * images : [{"url":"https://encrypted-tbn1.gstatic.com/images?q=tbn:ANd9GcRDy7HZaHxn15wWj6pXE4uMKAqHTC_uBgBlIzeeQSj2QaGgUzUmHg"},{"url":"https://encrypted-tbn1.gstatic.com/images?q=tbn:ANd9GcTlJRALAf-76JPOLohBKzBg8Ab4Q5pWeQhF5igSfBflE_UYbqu7"},{"url":"http://i.ytimg.com/vi/rGWI7mjmnNk/hqdefault.jpg"}]
     * sender : {"username":"jport","nick":"Joe Portman","avatar":"https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcRJm8UXZ0mYtjv1a48RKkFkdyd4kOWLJB0o_l7GuTS8-q8VF64w"}
     * comments : [{"content":"Good.","sender":{"username":"outman","nick":"Super hero","avatar":"https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcRJm8UXZ0mYtjv1a48RKkFkdyd4kOWLJB0o_l7GuTS8-q8VF64w"}},{"content":"Like it too","sender":{"username":"inman","nick":"Doggy Over","avatar":"https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcRJm8UXZ0mYtjv1a48RKkFkdyd4kOWLJB0o_l7GuTS8-q8VF64w"}}]
     */
    private String content;
    private SenderBean sender;
    private java.util.List<ImagesBean> images;
    private java.util.List<CommentBean> comments;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public SenderBean getSender() {
        return sender;
    }

    public void setSender(SenderBean sender) {
        this.sender = sender;
    }

    public List<ImagesBean> getImages() {
        return images;
    }

    public void setImages(List<ImagesBean> images) {
        this.images = images;
    }

    public List<CommentBean> getComments() {
        return comments;
    }

    public void setComments(List<CommentBean> comments) {
        this.comments = comments;
    }

    public boolean isValid() {
       if (null == sender
               || (TextUtils.isEmpty(content) && (null == images || images.size() == 0))) {
           return false;
       }
       return true;
   }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TweetBean{" +
                "content='" + content + '\'' +
                ", sender={" + sender + "}" +
                ", images={");
        for (ImagesBean image: images) {
            sb.append(image.toString()+",");
        }
        sb.append("}");

        sb.append(", comments={");
        for (CommentBean comment: comments) {
            sb.append(comment.toString()+",");
        }
        sb.append("}");
        return sb.toString();
    }

    public static class SenderBean {
        /**
         * username : jport
         * nick : Joe Portman
         * avatar : https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcRJm8UXZ0mYtjv1a48RKkFkdyd4kOWLJB0o_l7GuTS8-q8VF64w
         */
        private String username;
        private String nick;
        private String avatar;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNick() {
            return nick;
        }

        public void setNick(String nick) {
            this.nick = nick;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        @Override
        public String toString() {
            return "SenderBean{" +
                    "username='" + username + '\'' +
                    ", nick='" + nick + '\'' +
                    ", avatar='" + avatar + '\'' +
                    '}';
        }
    }

    public static class ImagesBean {
        /**
         * url : https://encrypted-tbn1.gstatic.com/images?q=tbn:ANd9GcRDy7HZaHxn15wWj6pXE4uMKAqHTC_uBgBlIzeeQSj2QaGgUzUmHg
         */

        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @Override
        public String toString() {
            return "ImagesBean{" +
                    "url='" + url + '\'' +
                    '}';
        }
    }

    public static class CommentBean {
        private String content;
        private SenderBean sender;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public SenderBean getSender() {
            return sender;
        }

        public void setSender(SenderBean sender) {
            this.sender = sender;
        }

        @Override
        public String toString() {
            return "CommentBean{" +
                    "content='" + content + '\'' +
                    ", sender=" + sender +
                    '}';
        }
    }
}

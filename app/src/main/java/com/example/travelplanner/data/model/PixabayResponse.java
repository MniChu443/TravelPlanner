package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PixabayResponse {

    @SerializedName("totalHits")
    private int totalHits;

    @SerializedName("hits")
    private List<Hit> hits;

    public int getTotalHits() {
        return totalHits;
    }

    public List<Hit> getHits() {
        return hits;
    }

    public String getFirstImageUrl() {
        if (hits != null && !hits.isEmpty()) {
            Hit first = hits.get(0);
            if (first.webformatURL != null && !first.webformatURL.isEmpty()) {
                return first.webformatURL;
            }
            if (first.largeImageURL != null && !first.largeImageURL.isEmpty()) {
                return first.largeImageURL;
            }
        }
        return null;
    }

    public static class Hit {
        @SerializedName("id")
        private long id;

        @SerializedName("tags")
        private String tags;

        @SerializedName("webformatURL")
        private String webformatURL;

        @SerializedName("largeImageURL")
        private String largeImageURL;

        public long getId() {
            return id;
        }

        public String getTags() {
            return tags;
        }

        public String getWebformatURL() {
            return webformatURL;
        }

        public String getLargeImageURL() {
            return largeImageURL;
        }
    }
}

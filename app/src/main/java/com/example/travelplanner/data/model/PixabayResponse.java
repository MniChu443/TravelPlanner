package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from Pixabay image search.
 * Endpoint: https://pixabay.com/api/?key=...&q=Paris&image_type=photo
 *
 * Only the first "largeImageURL" / "webformatURL" are used.
 */
public class PixabayResponse {

    @SerializedName("totalHits")
    private int totalHits;

    @SerializedName("hits")
    private List<Hit> hits;

    public int getTotalHits() { return totalHits; }
    public List<Hit> getHits() { return hits; }

    public static class Hit {
        @SerializedName("id")
        private long id;

        @SerializedName("tags")
        private String tags;

        @SerializedName("previewURL")
        private String previewURL;

        @SerializedName("webformatURL")
        private String webformatURL;

        @SerializedName("largeImageURL")
        private String largeImageURL;

        public long getId() { return id; }
        public String getTags() { return tags; }
        public String getPreviewURL() { return previewURL; }
        public String getWebformatURL() { return webformatURL; }
        public String getLargeImageURL() { return largeImageURL; }
    }
}

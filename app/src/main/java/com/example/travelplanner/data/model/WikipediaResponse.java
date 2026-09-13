package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

public class WikipediaResponse {
    @SerializedName("originalimage")
    private ImageInfo originalImage;

    @SerializedName("thumbnail")
    private ImageInfo thumbnail;

    public String getImageUrl() {
        if (originalImage != null && originalImage.source != null) {
            return originalImage.source;
        }
        if (thumbnail != null && thumbnail.source != null) {
            return thumbnail.source;
        }
        return null;
    }

    public static class ImageInfo {
        @SerializedName("source")
        public String source;
    }
}

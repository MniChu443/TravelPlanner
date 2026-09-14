package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

public class WikipediaResponse {
    @SerializedName("originalimage")
    private ImageInfo originalImage;

    @SerializedName("thumbnail")
    private ImageInfo thumbnail;

    public String getImageUrl() {
        if (thumbnail != null && thumbnail.source != null) {
            // Ładujemy szybką i lekką miniaturę HD zamiast kilkudysięciomegabitowego pliku źródłowego
            return thumbnail.source.replace("/330px-", "/1000px-");
        }
        if (originalImage != null && originalImage.source != null && !originalImage.source.toLowerCase().endsWith(".svg")) {
            return originalImage.source;
        }
        return null;
    }

    public static class ImageInfo {
        @SerializedName("source")
        public String source;
    }
}

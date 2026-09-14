package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

public class WikipediaResponse {
    @SerializedName("originalimage")
    private ImageInfo originalImage;

    @SerializedName("thumbnail")
    private ImageInfo thumbnail;

    public String getImageUrl() {
        if (thumbnail != null && thumbnail.source != null) {
            // Ładujemy szybką i lekką miniaturę HD zamiast ogromnego pliku źródłowego.
            // Używamy Regex, aby zamienić dowolny rozmiar (np. /320px-, /500px-) na /1000px-
            return thumbnail.source.replaceAll("/\\d+px-", "/1000px-");
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

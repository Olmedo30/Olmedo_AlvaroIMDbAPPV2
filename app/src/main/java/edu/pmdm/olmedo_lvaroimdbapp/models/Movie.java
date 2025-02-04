package edu.pmdm.olmedo_lvaroimdbapp.models;

import android.os.Parcel;
import android.os.Parcelable;

public class Movie implements Parcelable {
    private String tconst;
    private String title;
    private String description;
    private String releaseDate;
    private double rating;
    private String imageUrl;
    private int rank;

    public Movie() {}

    public String getTconst() { return tconst; }
    public void setTconst(String tconst) { this.tconst = tconst; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public Movie(String id, String title, String imageUrl) {
        this.tconst = id;
        this.title = title;
        this.imageUrl = imageUrl;
    }

    protected Movie(Parcel in) {
        tconst = in.readString();
        title = in.readString();
        description = in.readString();
        releaseDate = in.readString();
        rating = in.readDouble();
        imageUrl = in.readString();
        rank = in.readInt();
    }

    public static final Creator<Movie> CREATOR = new Creator<Movie>() {
        @Override
        public Movie createFromParcel(Parcel in) {
            return new Movie(in);
        }

        @Override
        public Movie[] newArray(int size) {
            return new Movie[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(tconst);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(releaseDate);
        dest.writeDouble(rating);
        dest.writeString(imageUrl);
        dest.writeInt(rank);
    }
}

package edu.pmdm.olmedo_lvaroimdbapp.api;

import org.json.JSONObject;

import java.io.IOException;

public class MovieDetails {
    private String releaseDate;
    private String rating;

    public MovieDetails(String releaseDate, String rating) {
        this.releaseDate = releaseDate;
        this.rating = rating;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public String getRating() {
        return rating;
    }

    @Override
    public String toString() {
        return "Fecha de salida: " + releaseDate + ", Rating: " + rating;
    }

    /**
     * Obtiene la fecha de salida y el rating de una película desde la API.
     * Usa la clase IMDbAPIService para pedir por los endpoints los
     * datos que se necesitan, que en este caso son la fecha y el rating.
     */
    public static MovieDetails getReleaseDateAndRating(String tconst) {
        IMDbApiService apiService = new IMDbApiService();
        try {
            //Obtiene la respuesta JSON desde la API
            String jsonResponse = apiService.getMovieOverview(tconst);

            //Parsea el JSON para cargar los datos
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject data = jsonObject.getJSONObject("data");
            JSONObject titleObj = data.getJSONObject("title");

            //Extrae la fecha de salida
            String releaseDate = "Fecha no disponible";
            if (titleObj.has("releaseDate")) {
                JSONObject releaseDateObj = titleObj.getJSONObject("releaseDate");
                int year = releaseDateObj.getInt("year");
                int month = releaseDateObj.getInt("month");
                int day = releaseDateObj.getInt("day");
                releaseDate = String.format("%d-%02d-%02d", year, month, day);
            }

            //Obtiene el rating
            String rating = "Rating no disponible";
            if (titleObj.has("ratingsSummary")) {
                JSONObject ratingsSummary = titleObj.getJSONObject("ratingsSummary");
                double aggregateRating = ratingsSummary.optDouble("aggregateRating", -1);
                if (aggregateRating!=-1) {
                    rating = String.format("Rating: %.1f", aggregateRating);
                }
            }

            //Devuelve un nuevo objeto MovieDetails con los datos obtenidos
            return new MovieDetails(releaseDate, rating);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Si da error devuelve valores predeterminados
        return new MovieDetails("Fecha no disponible", "Rating no disponible");
    }
}
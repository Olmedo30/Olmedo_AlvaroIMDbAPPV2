package edu.pmdm.olmedo_lvaroimdbapp.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class IMDbApiService {

    private static final String HOST = "imdb-com.p.rapidapi.com";

    public static String getMovieOverview(String tconst) throws IOException {
        String url = "https://" + HOST + "/title/get-overview?tconst=" + tconst;
        return makeRequest(url);
    }

    public String getTopMeterMovies() throws IOException {
        String url = "https://" + HOST + "/title/get-top-meter?topMeterTitlesType=ALL&limit=10";
        return makeRequest(url);
    }

    private static String makeRequest(String urlString) throws IOException {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("x-rapidapi-key", IMDbApiClient.getApiKey());
            connection.setRequestProperty("x-rapidapi-host", HOST);

            int responseCode = connection.getResponseCode();
            if (responseCode == 429) {
                // Cambiar clave API y reintentar
                IMDbApiClient.switchApiKey();
                return makeRequest(urlString);
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Error: Código de estado " + responseCode);
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}

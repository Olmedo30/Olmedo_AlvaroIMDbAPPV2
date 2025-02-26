package edu.pmdm.olmedo_lvaroimdbapp.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TMDbApiService {
    private static final String API_KEY = "1ff89b9fc0100a85c8feab8b12eab6e5"; // Tu clave API
    private static final String TOKEN = "YOUR_ACCESS_TOKEN"; // Token de acceso
    private static final String BASE_URL = "https://api.themoviedb.org/3";

    /**
     * Realiza una búsqueda de películas por género y año de lanzamiento.
     */
    public String searchMoviesByGenreAndYear(String genreId, String year) throws IOException {
        String url = BASE_URL + "/discover/movie?with_genres=" + genreId +
                "&primary_release_year=" + year +
                "&language=en-US&api_key=" + API_KEY;
        return makeRequest(url);
    }

    /**
     * Obtiene la lista de géneros de películas desde la API de TMDb.
     */
    public String getGenres() throws IOException {
        String url = BASE_URL + "/genre/movie/list?language=en-US&api_key=" + API_KEY;
        return makeRequest(url);
    }

    /**
     * Obtiene los detalles de una película específica desde la API de TMDb.
     */
    public String getMovieDetails(String movieId) throws IOException {
        String url = BASE_URL + "/movie/" + movieId + "?language=en-US&api_key=" + API_KEY;
        return makeRequest(url);
    }

    /**
     * Método  para realizar peticiones HTTP GET a la API de
     * TMDb. Usamos la key de nuestra API y el link del host
     * que hemos declarado como variables al inicio del código.
     *
     * Después de pedir a la API lo que necesitemos, lo leeremos con
     * un BufferedReader y la parasermos a un String leyendo línea
     * por línea.
     */
    private String makeRequest(String urlString) throws IOException {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            //Configurar la conexión
            connection.setRequestProperty("Authorization", "Bearer " + TOKEN);
            connection.setRequestProperty("accept", "application/json");

            //Verifica código de respuesta
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Error en la petición: Código de estado " + responseCode);
            }

            //Lee la respuesta
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } finally {
            //Cerramos el bufferedReader
            if (reader != null) {
                reader.close();
            }
            //Cerramos la conexión
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
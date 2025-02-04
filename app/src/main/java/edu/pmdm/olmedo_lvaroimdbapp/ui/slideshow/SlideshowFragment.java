package edu.pmdm.olmedo_lvaroimdbapp.ui.slideshow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import edu.pmdm.olmedo_lvaroimdbapp.R;
import edu.pmdm.olmedo_lvaroimdbapp.api.TMDbApiService;
import edu.pmdm.olmedo_lvaroimdbapp.models.Movie;
import edu.pmdm.olmedo_lvaroimdbapp.ui.SearchResultsActivity;

public class SlideshowFragment extends Fragment {

    private Spinner genreSpinner;
    private EditText yearInput;
    private Button searchButton;
    private Map<String, String> genreMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_slideshow, container, false);
        genreSpinner = root.findViewById(R.id.genre_spinner);
        yearInput = root.findViewById(R.id.year_input);
        searchButton = root.findViewById(R.id.search_button);

        //Rellena el spinner con los géneros
        fetchGenres();

        //Configura el botón de búsqueda
        searchButton.setOnClickListener(v -> {
            //Obtene el género seleccionado y el año ingresado
            String selectedGenre = genreSpinner.getSelectedItem().toString();
            String genreId = genreMap.get(selectedGenre); // Obtener el ID del género
            String enteredYear = yearInput.getText().toString().trim();
            //Verifica si el género es válido
            if (genreId == null) {
                Toast.makeText(getContext(), "Por favor, selecciona un género válido.", Toast.LENGTH_SHORT).show();
                return;
            }
            //Verifica si el año ingresado es válido
            if (enteredYear.isEmpty()) {
                //Muestra mensaje si el campo está vacío
                Toast.makeText(requireContext(), "El año no puede estar vacío.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int yearInt = Integer.parseInt(enteredYear);
                //Valida si el año está fuera del rango permitido (1890 a 2026)
                if (yearInt < 1890 || yearInt > 2026) {
                    Toast.makeText(requireContext(), "El año debe estar entre 1890 y 2026.", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                //Muestra mensaje si el valor ingresado no es un número válido
                Toast.makeText(requireContext(), "Introduce un año en formato numérico.", Toast.LENGTH_SHORT).show();
                return;
            }
            //Si las validaciones son exitosas, realizar la búsqueda
            performSearch(genreId, enteredYear);
        });

        return root;
    }

    //Obtiene todos los géneros de películas y los añade al spinner
    private void fetchGenres() {
        new Thread(() -> {
            TMDbApiService apiService = new TMDbApiService();
            try {
                String genresJson = apiService.getGenres();
                List<String> genreNames = parseGenres(genresJson);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, genreNames);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        genreSpinner.setAdapter(adapter);
                    });
                }
            } catch (IOException e) {
                Log.e("SlideshowFragment", "Error al obtener géneros: " + e.getMessage(), e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Error al obtener géneros.", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        }).start();
    }

    //Parsea los géneros que obtiene de la API para poder después añadir al Spinner
    private List<String> parseGenres(String genresJson) {
        List<String> genreNames = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(genresJson);
            JSONArray genresArray = jsonObject.getJSONArray("genres");

            for (int i = 0; i < genresArray.length(); i++) {
                JSONObject genreObject = genresArray.getJSONObject(i);
                String name = genreObject.getString("name");
                String id = genreObject.getString("id");
                genreNames.add(name);
                genreMap.put(name, id); // Guardar en el mapa
            }
        } catch (Exception e) {
            Log.e("SlideshowFragment", "Error al parsear géneros: " + e.getMessage(), e);
        }
        return genreNames;
    }

    //Busca las películas por el año y el género, las guarda para enviarlas en un intent
    private void performSearch(String genreId, String year) {
        new Thread(() -> {
            TMDbApiService apiService = new TMDbApiService();
            try {
                String moviesJson = apiService.searchMoviesByGenreAndYear(genreId, year);
                List<Movie> movies = parseMovies(moviesJson);
                if (!movies.isEmpty()) {
                    Intent intent = new Intent(getContext(), SearchResultsActivity.class);
                    intent.putParcelableArrayListExtra("MOVIES_LIST", new ArrayList<>(movies));
                    startActivity(intent);
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "No se encontraron películas.", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            } catch (IOException e) {
                Log.e("SlideshowFragment", "Error al buscar películas: " + e.getMessage(), e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Error de red al realizar la búsqueda.", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        }).start();
    }

    //Parsea las películas de la API de TMDb para pasarlas a la actividad donde se mostrarán después
    private List<Movie> parseMovies(String moviesJson) {
        List<Movie> movies = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(moviesJson);
            JSONArray resultsArray = jsonObject.getJSONArray("results");

            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject movieObject = resultsArray.getJSONObject(i);
                String posterPath = movieObject.optString("poster_path", "");
                String movieId = movieObject.optString("id", "");
                String title = movieObject.optString("title", "Título no disponible");

                if (!posterPath.isEmpty() && !movieId.isEmpty()) {
                    String posterUrl = "https://image.tmdb.org/t/p/w500" + posterPath;
                    Movie movie = new Movie(movieId, title, posterUrl);
                    movies.add(movie);
                }
            }
        } catch (Exception e) {
            Log.e("SlideshowFragment", "Error al parsear películas: " + e.getMessage(), e);
        }
        return movies;
    }
}
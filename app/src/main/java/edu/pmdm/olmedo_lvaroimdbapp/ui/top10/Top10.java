package edu.pmdm.olmedo_lvaroimdbapp.ui.top10;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import edu.pmdm.olmedo_lvaroimdbapp.R;
import edu.pmdm.olmedo_lvaroimdbapp.api.IMDbApiService;
import edu.pmdm.olmedo_lvaroimdbapp.models.FavoriteDBHelper;
import edu.pmdm.olmedo_lvaroimdbapp.models.Movie;
import edu.pmdm.olmedo_lvaroimdbapp.models.MovieDetailsActivity;
import edu.pmdm.olmedo_lvaroimdbapp.sync.FavoritesSync;

public class Top10 extends Fragment {
    private RecyclerView recyclerView;
    private List<Movie> movieList = new ArrayList<>();
    private FavoritesSync favoritesSync;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView = root.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "usuario_sin_uid";

        favoritesSync = new FavoritesSync(requireContext(), uid);

        recyclerView.setAdapter(new RecyclerView.Adapter<MovieViewHolder>() {
            @NonNull
            @Override
            public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie, parent, false);
                return new MovieViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
                Movie movie = movieList.get(position);
                Glide.with(holder.imageView.getContext())
                        .load(movie.getImageUrl())
                        .into(holder.imageView);
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(getContext(), MovieDetailsActivity.class);
                    intent.putExtra("MOVIE_ID", movie.getTconst());
                    intent.putExtra("IMAGE_URL", movie.getImageUrl());
                    startActivity(intent);
                });
                holder.itemView.setOnLongClickListener(v -> {
                    if (movie == null) {
                        Toast.makeText(getContext(), "Película no disponible para insertar", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    FavoriteDBHelper dbHelper = new FavoriteDBHelper(getContext());
                    if (dbHelper.isFavorite(uid, movie.getTconst())) {
                        Toast.makeText(getContext(), "La película ya está en favoritos", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    boolean isInserted = dbHelper.insertFavorite(uid, movie.getTconst(), movie.getImageUrl(), movie.getTitle());
                    if (!isInserted) {
                        Toast.makeText(getContext(), "Error al insertar en favoritos", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    new Thread(() -> {
                        try {
                            String overviewResponse = IMDbApiService.getMovieOverview(movie.getTconst());
                            JSONObject overviewJson = new JSONObject(overviewResponse);

                            String releaseDate = overviewJson.optString("year", "");
                            Double rating = Double.valueOf(overviewJson.optString("rating", ""));
                            String overview = overviewJson.optString("plotSummary", "");

                            movie.setReleaseDate(releaseDate);
                            movie.setRating(rating);
                            movie.setDescription(overview);

                            favoritesSync.addMovieToCloud(movie);
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Añadido a favoritos", Toast.LENGTH_SHORT).show();
                            });

                        } catch (Exception e) {
                            Log.e("GalleryFragment", "Error al obtener detalles de la película", e);
                            favoritesSync.addMovieToCloud(movie);
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Añadido a favoritos (datos limitados)", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();

                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return movieList.size();
            }
        });

        fetchTopMeterMovies();
        return root;
    }

    // Realiza la solicitud a la API para obtener el Top Meter y actualiza el RecyclerView.
    private void fetchTopMeterMovies() {
        new Thread(() -> {
            IMDbApiService apiService = new IMDbApiService();
            try {
                String response = apiService.getTopMeterMovies(); // Usa la clase IMDbApiService para la petición
                parseAndPopulateMovies(response);
            } catch (IOException e) {
                Log.e("HomeFragment", "Error fetching top meter movies", e);
            }
        }).start();
    }

    // Parsea la respuesta de la API y actualiza la lista de películas.
    private void parseAndPopulateMovies(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject data = jsonObject.optJSONObject("data");
            if (data != null) {
                JSONArray edges = data.optJSONObject("topMeterTitles").optJSONArray("edges");
                if (edges != null) {
                    for (int i = 0; i < Math.min(edges.length(), 10); i++) { // Limitar al Top 10
                        JSONObject node = edges.getJSONObject(i).optJSONObject("node");
                        String movieId = node.optString("id", "");
                        if (!movieId.startsWith("tt")) {
                            Log.e("HomeFragment", "ID no válido: " + movieId);
                            continue;
                        }
                        String imageUrl = node.optJSONObject("primaryImage").optString("url", "");
                        if (imageUrl.isEmpty()) {
                            Log.e("HomeFragment", "URL de imagen no disponible para ID: " + movieId);
                            continue;
                        }
                        String title = node.optJSONObject("titleText").optString("text", "Sin título");

                        Movie movie = new Movie();
                        movie.setTconst(movieId);
                        movie.setImageUrl(imageUrl);
                        movie.setTitle(title);
                        movieList.add(movie);
                    }
                    requireActivity().runOnUiThread(() -> recyclerView.getAdapter().notifyDataSetChanged());
                } else {
                    Log.e("HomeFragment", "No se encontraron elementos 'edges' en el JSON");
                }
            } else {
                Log.e("HomeFragment", "No se encontró el objeto 'data' en el JSON");
            }
        } catch (Exception e) {
            Log.e("HomeFragment", "Error parsing JSON response", e);
        }
    }

    static class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.movie_poster);
        }
    }
}
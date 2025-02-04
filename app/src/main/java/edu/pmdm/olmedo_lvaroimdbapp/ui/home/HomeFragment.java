package edu.pmdm.olmedo_lvaroimdbapp.ui.home;

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

public class HomeFragment extends Fragment {

    private RecyclerView recyclerView;
    private List<Movie> movieList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView = root.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
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
                //Carga la imagen con Glide
                Glide.with(holder.imageView.getContext())
                        .load(movie.getImageUrl())
                        .into(holder.imageView);
                //Maneja el click en el ítem
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(getContext(), MovieDetailsActivity.class);
                    intent.putExtra("MOVIE_ID", movie.getTconst());
                    intent.putExtra("IMAGE_URL", movie.getImageUrl());
                    startActivity(intent);
                });
                holder.itemView.setOnLongClickListener(v -> {
                    //Verifica que la película no sea nula
                    if (movie == null) {
                        Toast.makeText(getContext(), "Película no disponible para insertar", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    //Obtiene UID de Firebase
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    String uid = (currentUser != null) ? currentUser.getUid() : "usuario_sin_uid";

                    //Crea una instancia del DBHelper
                    FavoriteDBHelper dbHelper = new FavoriteDBHelper(getContext());

                    //Verifica si ya está en favoritos
                    if (dbHelper.isFavorite(uid, movie.getTconst())) {
                        //Si ya está en favoritos, mostrar un mensaje
                        Toast.makeText(getContext(), "La película ya está en favoritos", Toast.LENGTH_SHORT).show();
                        Log.d("GalleryFragment", "Película ya en favoritos");
                    } else {
                        //Inserta en la tabla 'favorites'
                        boolean isInserted = dbHelper.insertFavorite(uid, movie.getTconst(), movie.getImageUrl(), movie.getTitle());
                        if (isInserted) {
                            Toast.makeText(getContext(), "Añadido a favoritos", Toast.LENGTH_SHORT).show();
                            Log.d("GalleryFragment", "Insert exitoso");
                        } else {
                            Toast.makeText(getContext(), "Error al insertar en favoritos", Toast.LENGTH_SHORT).show();
                            Log.e("GalleryFragment", "Fallo al insertar en la base de datos");
                        }
                    }
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

    //Realiza la solicitud a la API para obtener el Top Meter y actualiza el RecyclerView.
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

    //Parsea la respuesta de la API y actualizar la lista de películas.
    private void parseAndPopulateMovies(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject data = jsonObject.optJSONObject("data");
            if (data != null) {
                JSONArray edges = data.optJSONObject("topMeterTitles").optJSONArray("edges");
                if (edges != null) {
                    for (int i = 0; i < Math.min(edges.length(), 10); i++) { // Limitar al Top 10
                        JSONObject node = edges.getJSONObject(i).optJSONObject("node");
                        //Extrae el ID de la película
                        String movieId = node.optString("id", "");
                        if (!movieId.startsWith("tt")) {
                            Log.e("HomeFragment", "ID no válido: " + movieId);
                            continue;
                        }
                        //Extrae la URL de la imagen
                        String imageUrl = node.optJSONObject("primaryImage").optString("url", "");
                        if (imageUrl.isEmpty()) {
                            Log.e("HomeFragment", "URL de imagen no disponible para ID: " + movieId);
                            continue;
                        }
                        //Extrae el título de la película
                        String title = node.optJSONObject("titleText").optString("text", "Sin título");
                        //Crea objeto Movie y añade a la lista
                        Movie movie = new Movie();
                        movie.setTconst(movieId);
                        movie.setImageUrl(imageUrl);
                        movie.setTitle(title);
                        movieList.add(movie);
                    }
                    // Actualiza RecyclerView en el hilo principal
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
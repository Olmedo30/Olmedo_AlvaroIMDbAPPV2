package edu.pmdm.olmedo_lvaroimdbapp.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class IMDbApiClient {
    private static final String BASE_URL = "https://imdb-com.p.rapidapi.com/";
    private static IMDbApiService apiService;
    private static final ApiKeyManager apiKeyManager = new ApiKeyManager();

    public static IMDbApiService getApiService() {
        if (apiService == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(IMDbApiService.class);
        }
        return apiService;
    }

    public static String getApiKey() {
        return apiKeyManager.getCurrentKey();
    }

    public static void switchApiKey() {
        apiKeyManager.switchToNextKey();
    }
}

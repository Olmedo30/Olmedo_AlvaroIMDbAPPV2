package edu.pmdm.olmedo_lvaroimdbapp.api;

import java.util.ArrayList;
import java.util.List;

public class ApiKeyManager {
    private final List<String> apiKeys = new ArrayList<>();
    private int currentKeyIndex = 0;

    public ApiKeyManager() {
        // Añade tus claves de RapidAPI aquí
        apiKeys.add("73fdb42924msh6cf55593aaa18fdp13cc38jsnb9f18f9774b1");
        apiKeys.add("8099f3a016msh8e0cc7d1ce24bf7p1d06a3jsn80d118e4d8b4");
        apiKeys.add("3181d793ccmsh28b258514584729p1b14bajsnfafdd2c75bb5");
    }

    public String getCurrentKey() {
        return apiKeys.get(currentKeyIndex);
    }

    public void switchToNextKey() {
        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
    }
}

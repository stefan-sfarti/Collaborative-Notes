package com.collabnotes.CollabNotes.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("classpath:focus-poet-457511-n7-firebase-adminsdk-fbsvc-39626c7ba6.json")
    private Resource serviceAccountResource;
    private boolean firebaseInitialized = false;

    @PostConstruct
    public void initialize() {
        try {
            InputStream serviceAccount = serviceAccountResource.getInputStream();
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                firebaseInitialized = true;
                logger.info("Firebase initialized successfully.");
            }
        } catch (IOException e) {
            logger.error("Error initializing Firebase: ", e);
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }

    @Bean
    public Firestore firestore() {
        try {

            InputStream serviceAccount = serviceAccountResource.getInputStream();
            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);


            FirestoreOptions firestoreOptions = FirestoreOptions.newBuilder()
                    .setProjectId("focus-poet-457511-n7")
                    .setCredentials(credentials)
                    .build();


            return firestoreOptions.getService();
        } catch (IOException e) {
            logger.error("Failed to initialize Firestore: ", e);
            throw new RuntimeException("Firestore initialization failed", e);
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        if (!firebaseInitialized) {
            throw new IllegalStateException("Firebase not initialized");
        }
        return FirebaseAuth.getInstance(FirebaseApp.getInstance());
    }
}
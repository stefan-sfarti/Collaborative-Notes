package com.collabnotes.CollabNotes.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

@Service
public class PubSubPublisherService {

    @Value("classpath:focus-poet-457511-n7-firebase-adminsdk-fbsvc-39626c7ba6.json")
    private Resource serviceAccountResource;

    private static final String PROJECT_ID = "focus-poet-457511-n7";
    private static final String TOPIC_ID = "note-updates";
    private GoogleCredentials credentials;

    @PostConstruct
    public void init() {
        try (InputStream serviceAccount = serviceAccountResource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(serviceAccount);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Pub/Sub credentials", e);
        }
    }

    public void publishNoteUpdate(String noteId, String userId, String action) {
        Publisher publisher = null;
        try {
            publisher = Publisher.newBuilder(ProjectTopicName.of(PROJECT_ID, TOPIC_ID))
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            String message = String.format("{\"noteId\": \"%s\", \"userId\": \"%s\", \"action\": \"%s\", \"timestamp\": %d}",
                    noteId, userId, action, System.currentTimeMillis());

            ByteString data = ByteString.copyFromUtf8(message);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

            publisher.publish(pubsubMessage).get();
        } catch (IOException | InterruptedException | ExecutionException e) {
            System.err.println("Failed to publish to Pub/Sub: " + e.getMessage());
        } finally {
            if (publisher != null) {
                try {
                    publisher.shutdown();
                } catch (Exception ignore) {}
            }
        }
    }
}

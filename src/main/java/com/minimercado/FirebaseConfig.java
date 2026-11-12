package com.minimercado;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Abre a conexão com o Firestore usando a chave serviceAccountKey.json. */
@Configuration
public class FirebaseConfig {
    @Bean
    public Firestore firestore(@Value("${firebase.credentials}") String caminho) throws IOException {
        File arq = new File(caminho);
        if (!arq.exists())
            throw new IllegalStateException("Chave do Firebase não encontrada em: " + arq.getAbsolutePath()
                    + ". Coloque o serviceAccountKey.json na pasta do projeto, ao lado do pom.xml.");
        try (InputStream in = new FileInputStream(arq)) {
            if (FirebaseApp.getApps().isEmpty())
                FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build());
        }
        return FirestoreClient.getFirestore();
    }
}

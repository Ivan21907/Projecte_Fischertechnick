package org.example;

//llibreries de firebase
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

//llibreries varies
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

//llibreries de mqtt
import com.google.firebase.cloud.FirestoreClient;
import org.eclipse.paho.client.mqttv3.*;

import org.json.JSONObject;

public class Main implements MqttCallback {

    // Variable per la connexio a la base de dades de Firebase
    private Firestore db;
    // Variable per la connexio al broker MQTT
    private MqttClient client;
    // Variable per crear un document al firebase per guardar la hora de la ultima insercio a la base de dades
    private long lastInsertTime = System.currentTimeMillis();

    // Constructor
    public Main() {
        // Crea un nou client MQTT
        String broker = "tcp://192.168.0.10:1883";
        //identificador del client
        String clientId = "myClient";
        //crea una nova connexio amb el broker
        try {
            client = new MqttClient(broker, clientId);
            client.setCallback(this);
            // Connecta al broker
            client.connect();
            // Es subscribeix al tema de la temperatura
            String tempTopic = "i/bme680";
            client.subscribe(tempTopic);
        } catch (MqttException e) {
            e.printStackTrace();
        }

        // Connexio a la base de dades de Firebase
        FileInputStream serviceAccount = null;
        try {
            // Carrega el fitxer de credencials de Firebase
            serviceAccount = new FileInputStream("provesFirebase.json");

            // Inicialitza Firebase amb les credencials
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);

            // Obte la instancia de la base de dades
            db = FirestoreClient.getFirestore();
            //mostra per pantalla que s'ha connectat a la base de dades
            System.out.println("Connectat a la base de dades Firebase.");
        } catch (IOException e) {
            System.out.println("Error al carregar les credencials de firebase: " + e.getMessage());
        }
    }

    /**
     * Metode per si es perd la connexio al broker MQTT
     * @param cause the reason behind the loss of connection.
     */
    public void connectionLost(Throwable cause) {
        System.out.println("Conexión perdida al broker MQTT.");
    }

    /**
     * Metode per si es rep un missatge del broker MQTT
     * @param topic name of the topic on the message was published to
     * @param message the actual message.
     * @throws Exception
     */
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        //Es rep el missatge del broker MQTT
        String payload = new String(message.getPayload());

        // Es converteix el missatge a un objecte JSON
        JSONObject json = new JSONObject(payload);
        //Agafem les claus del missatge que ens interessen i les guardem en variables
        double temperatura = json.getDouble("t");
        double humitat = json.getDouble("h");
        String temps = json.getString("ts");

        // Verifiquem si han passat 30 segons des de la última insercio
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastInsertTime >= 5000) {
            lastInsertTime = currentTime;

            //Generem un nom de document unic basat en la marca de temps actual
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
            String document = "mqtt_" + dateFormat.format(new Date(currentTime));

            //Agafem la col·leccio de la base de dades i creem un document amb el nom generat anteriorment
            DocumentReference documentRef = db.collection("proves").document(document);

            //Fem un hashmap amb les dades que volem guardar a la base de dades
            Map<String, Object> data = new HashMap<>();
            data.put("Humitat", humitat);
            data.put("Temperatura", temperatura);
            //Obtenim el dia i la hora per separat
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            String dia = dayFormat.format(new Date(currentTime));
            String hora = timeFormat.format(new Date(currentTime));
            data.put("Dia", dia);
            data.put("Hora", hora);

            // Insertem el document a la base de dades
            ApiFuture<WriteResult> result = documentRef.set(data);

            //Esperem a que s'inserti el document
            WriteResult writeResult = result.get();
            System.out.println("Document creat amb ID: " + documentRef.getId());

            // Mostrem per consola les dades que s'han guardat a la base de dades
            System.out.println("Temperatura: " + temperatura + "ºC");
            System.out.println("Humitat: " + humitat + "%");

            System.out.println("Dia/Hora" + temps);
        }
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
        //Aquest metode es crida quan s'ha entregat un missatge publicat
    }

    public static void main(String[] args) {
        new Main();
    }
}

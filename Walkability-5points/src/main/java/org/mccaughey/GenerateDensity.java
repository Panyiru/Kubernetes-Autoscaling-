package org.mccaughey;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.MessageListener;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.geotools.data.DataUtilities;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geojson.feature.FeatureJSON;
import org.json.JSONException;
import org.json.JSONObject;
import org.mccaughey.ActiveMQ.Sender;
import org.mccaughey.connectivity.ConnectivityIndex;
import org.mccaughey.connectivity.NetworkBufferOMS;
import org.mccaughey.density.DwellingDensity;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.opengis.feature.simple.SimpleFeature;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class GenerateDensity implements MessageListener,Runnable {
    // URL of the JMS server
    private static String url = "tcp://115.146.85.235:61616";
    // default broker URL is : tcp://localhost:61616"

    // Name of the queue
    private static String subject_Receive = "PolygonDensity";
    private Connection connection_Receive;
    private static Sender sender;

    public static void main(String[] argv){

        GenerateDensity l = new GenerateDensity();
        Thread receiver = new Thread(l);
        receiver.start();

        sender = new Sender("densityZscore");
        Thread senderThread = new Thread(sender);
        senderThread.start();


    }

    public void run(){
        try{
            // Getting JMS connection from the server
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
            connection_Receive = connectionFactory.createConnection();
            connection_Receive.start();

            // Creating session for receiving messages
            Session session = connection_Receive.createSession(false,
                    Session.AUTO_ACKNOWLEDGE);

            // Getting the queue
            Destination destination = session.createQueue(subject_Receive + "?consumer.prefetchSize=1");

            // MessageConsumer is used for receiving (consuming) messages
            MessageConsumer consumer = session.createConsumer(destination);

            consumer.setMessageListener(this);
            System.out.println("Waiting for messages...");
        }
        catch(JMSException e){
            e.printStackTrace(System.out);
        }


    }

    @Override
    public void onMessage(Message message){
        // We will be using TestMessage in our example. MessageProducer sent us a TextMessage
        // so we must cast to it to get access to its .getText() method.
        try{
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                System.out.println("Received message '" + textMessage.getText() + "'");

                String string = textMessage.getText();
                int index = Integer.parseInt(string.split("--")[0]);
                InputStream inputStream = new ByteArrayInputStream(string.split("--")[1].getBytes(Charset.forName("UTF-8")));

                FeatureJSON fjson = new FeatureJSON();
                SimpleFeature region = fjson.readFeature(inputStream);

                URL landUseURL = new File("./src/main/java/org/mccaughey/MB_WA_2006_census_projected.shp").toURI().toURL();
                File landUseShapeFile = new File(landUseURL.toURI());
                FileDataStore landUseDataStore = FileDataStoreFinder.getDataStore(landUseShapeFile);

                SimpleFeature densityFeature = DwellingDensity.averageDensity(landUseDataStore.getFeatureSource(), region, "TURPOP2006");
                System.out.println("Density: " + densityFeature.getAttribute("AverageDensity"));
                Double density = (Double) densityFeature.getAttribute("AverageDensity");

                landUseDataStore.dispose();

                int currCounter;

                //Check the order
                BufferedReader br = new BufferedReader(new FileReader("./src/main/java/org/mccaughey/output/counter_density.json"));
                JSONObject counterObj = new JSONObject(br.readLine().trim());
                currCounter = counterObj.getInt("density_counter");


                while(currCounter < index-1){
                    br.close();
                    Thread.sleep(50);
                    //Check the order again
                    br = new BufferedReader(new FileReader("./src/main/java/org/mccaughey/output/counter_density.json"));
                    counterObj = new JSONObject(br.readLine().trim());
                    currCounter = counterObj.getInt("density_counter");
                }
                br.close();
                counterObj.put("density_counter", index);

                //Write the feature into the file
                File file_density = new File("./src/main/java/org/mccaughey/output/densityOMS.geojson");
                FileOutputStream fout_2 = new FileOutputStream(file_density, true);
                fout_2.write((fjson.toString(densityFeature)+",").getBytes());
                fout_2.flush();
                fout_2.close();

                sender.sendMessage("density: " + density.toString());

                //Change the counter value
                File file_counter = new File("./src/main/java/org/mccaughey/output/counter_density.json");
                FileOutputStream fout_00 = new FileOutputStream(file_counter, false);
                fout_00.write(counterObj.toString().getBytes());
                fout_00.flush();
                fout_00.close();

            }
            else{
                connection_Receive.close();
            }
        }
        catch(JMSException e){
            e.printStackTrace(System.out);
        }
        catch(IOException e){
            e.printStackTrace(System.out);
        }
        catch(URISyntaxException e1){
            System.out.println(e1.getMessage());
        }
        catch(JSONException e){
            System.out.println(e.toString());
        }
        catch(InterruptedException e){
            e.printStackTrace(System.out);
        }

    }

    public void close(){
        try {
            connection_Receive.close();
            sender.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

}


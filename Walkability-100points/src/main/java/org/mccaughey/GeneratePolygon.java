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
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geojson.feature.FeatureJSON;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.mccaughey.ActiveMQ.Sender;
import org.mccaughey.connectivity.NetworkBufferOMS;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.opengis.feature.simple.SimpleFeature;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class GeneratePolygon implements MessageListener,Runnable {
    // URL of the JMS server
    private static String url = "tcp://115.146.85.235:61616";
    // default broker URL is : tcp://localhost:61616"

    // Name of the queue
    private static String subject_Receive = "PointQueue";
    private Connection connection_Receive;
    private static Sender sender_1;
    private static Sender sender_2;
    private static Sender sender_3;

    public static void main(String[] argv) throws Exception {

        GeneratePolygon l = new GeneratePolygon();
        Thread receiver = new Thread(l);
        receiver.start();

        sender_1 = new Sender("PolygonConnectivity");
        Thread senderThread_1 = new Thread(sender_1);
        senderThread_1.start();

        sender_2 = new Sender("PolygonDensity");
        Thread senderThread_2 = new Thread(sender_2);
        senderThread_2.start();

        sender_3 = new Sender("PolygonLUM");
        Thread senderThread_3 = new Thread(sender_3);
        senderThread_3.start();

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
            connection_Receive.start();
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
                SimpleFeature point = fjson.readFeature(inputStream);

                SimpleFeature[] pointArray = {point};
                URL roadsUrl = new File("./src/main/java/org/mccaughey/psma_cut_projected.geojson.gz").toURI().toURL();

                 /*
                Step 1. Generate Polygons (Regions).
                * */

                NetworkBufferOMS networkBufferOMS = new NetworkBufferOMS();
                networkBufferOMS.network = DataUtilities.source(GeoJSONUtilities.readFeatures(roadsUrl));
                networkBufferOMS.points = DataUtilities.source(pointArray);
                networkBufferOMS.bufferSize = Config.BUFFER_SIZE;
                networkBufferOMS.distance = Config.DISTANCE;
                networkBufferOMS.run();

                //The region is a SimpleFeatureSource object
                SimpleFeatureSource regionSrc = networkBufferOMS.regions;


                /*
                Step 2. For each polygon, calculate its connectivity, density as well as Land Use Measure
                * */

                SimpleFeatureIterator features = regionSrc.getFeatures().features();
                JSONParser jsonParser = new JSONParser();
                while (features.hasNext()) {
                    SimpleFeature region = features.next();
                    FeatureJSON fjson_2 = new FeatureJSON();
                    String msg = fjson_2.toString(region);

                    int currCounter;

                    //Check the order
                    BufferedReader br = new BufferedReader(new FileReader("./src/main/java/org/mccaughey/output/counter_region.json"));
                    JSONObject counterObj = new JSONObject(br.readLine().trim());
                    currCounter = counterObj.getInt("region_counter");


                    while(currCounter < index-1){
                        br.close();
                        Thread.sleep(50);
                        //Check the order again
                        br = new BufferedReader(new FileReader("./src/main/java/org/mccaughey/output/counter_region.json"));
                        counterObj = new JSONObject(br.readLine().trim());
                        currCounter = counterObj.getInt("region_counter");
                    }
                    br.close();
                    counterObj.put("region_counter", index);

                    //Write the feature into the file
                    File file_region = new File("./src/main/java/org/mccaughey/output/regionOMS.geojson");
                    FileOutputStream fout_0 = new FileOutputStream(file_region, true);
                    fout_0.write((msg+",").getBytes());
                    fout_0.flush();
                    fout_0.close();

                    sender_1.sendMessage(index + "--" + msg);
                    sender_2.sendMessage(index + "--" + msg);
                    sender_3.sendMessage(index + "--" + msg);

                    //Change the counter value
                    File file_counter = new File("./src/main/java/org/mccaughey/output/counter_region.json");
                    FileOutputStream fout_00 = new FileOutputStream(file_counter, false);
                    fout_00.write(counterObj.toString().getBytes());
                    fout_00.flush();
                    fout_00.close();
                }

            }
            else{
                connection_Receive.close();
            }
        }
        catch(JMSException e){
            System.out.println(e.toString());
        }
        catch(IOException e){
            System.out.println(e.toString());
        }
        catch(JSONException e){
            System.out.println(e.toString());
        }
        catch(InterruptedException e){
            e.printStackTrace(System.out);
        }

    }

}

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
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geojson.feature.FeatureJSON;
import org.mccaughey.ActiveMQ.Sender;
import org.mccaughey.connectivity.ConnectivityIndex;
import org.mccaughey.connectivity.NetworkBufferOMS;
import org.mccaughey.density.DwellingDensity;
import org.mccaughey.landuse.LandUseMix;
import org.mccaughey.statistics.ZScoreOMS;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.opengis.feature.simple.SimpleFeature;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class GenerateZScore implements MessageListener,Runnable {
    // URL of the JMS server
    private static String url = "tcp://115.146.85.235:61616";
    // default broker URL is : tcp://localhost:61616"

    // Name of the queue
    private static String subject_Receive_1 = "connectivityZscore";
    private static String subject_Receive_2 = "densityZscore";
    private static String subject_Receive_3 = "LUMZscore";
    private static int counter = 0;

    private Connection connection_Receive;

    public static void main(String[] argv){

        GenerateZScore l = new GenerateZScore();
        l.run();
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

            Destination destination_1 = session.createQueue(subject_Receive_1 + "?consumer.prefetchSize=1");
            MessageConsumer consumer_1 = session.createConsumer(destination_1);
            consumer_1.setMessageListener(this);

            Destination destination_2 = session.createQueue(subject_Receive_2 + "?consumer.prefetchSize=1");
            MessageConsumer consumer_2 = session.createConsumer(destination_2);
            consumer_2.setMessageListener(this);

            Destination destination_3 = session.createQueue(subject_Receive_3 + "?consumer.prefetchSize=1");
            MessageConsumer consumer_3 = session.createConsumer(destination_3);
            consumer_3.setMessageListener(this);

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
                counter++;
                TextMessage textMessage = (TextMessage) message;
                System.out.println("Received message "+ counter +" '" + textMessage.getText() + "'");

                if(counter == Config.NUM_OF_POINTS * 3){
                    //Finalize the files
                    File file_region = new File("./src/main/java/org/mccaughey/output/regionOMS.geojson");
                    FileOutputStream fout_0 = new FileOutputStream(file_region, true);
                    FileChannel fileChannel_0 = fout_0.getChannel();
                    fileChannel_0.truncate(fileChannel_0.size() - 1); //Removes last character

                    fout_0.write("]}".getBytes());
                    fout_0.flush();
                    fileChannel_0.close();
                    fout_0.close();

                    File file_connectivity = new File("./src/main/java/org/mccaughey/output/connectivityOMS.geojson");
                    FileOutputStream fout_1 = new FileOutputStream(file_connectivity, true);
                    FileChannel fileChannel_1 = fout_1.getChannel();
                    fileChannel_1.truncate(fileChannel_1.size() - 1); //Removes last character

                    fout_1.write("]}".getBytes());
                    fout_1.flush();
                    fileChannel_1.close();
                    fout_1.close();

                    File file_density = new File("./src/main/java/org/mccaughey/output/densityOMS.geojson");
                    FileOutputStream fout_2 = new FileOutputStream(file_density, true);
                    FileChannel fileChannel_2 = fout_2.getChannel();
                    fileChannel_2.truncate(fileChannel_2.size() - 1); //Removes last character

                    fout_2.write("]}".getBytes());
                    fout_2.flush();
                    fileChannel_2.close();
                    fout_2.close();

                    File file_lum = new File("./src/main/java/org/mccaughey/output/lumOMS.geojson");
                    FileOutputStream fout_3 = new FileOutputStream(file_lum, true);
                    FileChannel fileChannel_3 = fout_3.getChannel();
                    fileChannel_3.truncate(fileChannel_3.size() - 1); //Removes last character

                    fout_3.write("]}".getBytes());
                    fout_3.flush();
                    fileChannel_3.close();
                    fout_3.close();

                    /* Step 6. Read in the results generated above (Connectivity, Density, LUM). Merge them into one feature file:
                    "src/main/resources/zScoreOMSTest.geojson"
                    * */
                    List<SimpleFeature> ZScoreFeatures = new ArrayList<SimpleFeature>();
                    URL connectivityUrl = file_connectivity.toURI().toURL();
                    URL densityUrl = file_density.toURI().toURL();
                    URL lumUrl = file_lum.toURI().toURL();
                    URL regionsUrl = file_region.toURI().toURL();

                    SimpleFeatureIterator connectivityIt =  DataUtilities.source(GeoJSONUtilities.readFeatures(connectivityUrl)).getFeatures().features();
                    SimpleFeatureIterator densityIt =  DataUtilities.source(GeoJSONUtilities.readFeatures(densityUrl)).getFeatures().features();
                    SimpleFeatureIterator lumIt =  DataUtilities.source(GeoJSONUtilities.readFeatures(lumUrl)).getFeatures().features();
                    SimpleFeatureIterator regionIt =  DataUtilities.source(GeoJSONUtilities.readFeatures(regionsUrl)).getFeatures().features();

                    while(regionIt.hasNext()){
                        SimpleFeature region = regionIt.next();
                        Double connectivity = (Double) connectivityIt.next().getAttribute("Connectivity");
                        Double density = (Double) densityIt.next().getAttribute("AverageDensity");
                        Double lum = (Double) lumIt.next().getAttribute("LandUseMixMeasure");
                        ZScoreFeatures.add(Config.buildFeature(region, connectivity,density,lum));
                    }

                    List<String> attributes = new ArrayList();
                    attributes.add("Connectivity");
                    attributes.add("Density");
                    attributes.add("LUM");

                    SimpleFeatureCollection ZScoreCollections = DataUtilities.collection(ZScoreFeatures);
                    ZScoreOMS zscoreOMS = new ZScoreOMS();
                    zscoreOMS.attributes = attributes;
                    zscoreOMS.regionsSource = DataUtilities.source(ZScoreCollections);
                    zscoreOMS.sumOfZScores();

                    GeoJSONUtilities.writeFeatures(zscoreOMS.resultsSource.getFeatures(),
                            new File("./src/main/java/org/mccaughey/output/ZScoreOMSTest.geojson").toURI().toURL());

                    regionIt.close();
                    lumIt.close();
                    densityIt.close();
                    connectivityIt.close();
                    connection_Receive.close();
                }
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

    }

    public void close(){
        try {
            connection_Receive.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

}




package org.mccaughey.ActiveMQ;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureCollectionIteration;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.feature.FeatureJSON.*;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.geometry.aggregate.MultiPoint;

import java.io.File;
import java.io.IOException;
import java.net.URL;


public class Sender implements Runnable{
    //URL of the JMS server. DEFAULT_BROKER_URL will just mean that JMS server is on localhost
    private static String url = "tcp://115.146.85.235:61616";

    // default broker URL is : tcp://localhost:61616"
    private String subject; // Queue Name.

    private MessageProducer producer;

    private Session session;

    private Connection connection;

    public Sender(String subject){
        this.subject = subject;
    }

    public void run(){
        try{
            // Getting JMS connection from the server and starting it
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
            connection = connectionFactory.createConnection();
            connection.start();

            //Creating a non transactional session to send/receive JMS message.
            session = connection.createSession(false,
                    Session.DUPS_OK_ACKNOWLEDGE);

            //Destination represents here our queue 'JCG_QUEUE' on the JMS server.
            //The queue will be created automatically on the server.
            Destination destination = session.createQueue(subject);

            // MessageProducer is used for sending messages to the queue.
            producer = session.createProducer(destination);

        }
        catch(JMSException e){
            e.printStackTrace();
        }
    }

    public void sendMessage(String s1){

        try {
            TextMessage message = session.createTextMessage(s1);
            producer.send(message);
            System.out.println("Sent msg: '" + message.getText() + "'");

        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    public void close(){
        try {
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }


}

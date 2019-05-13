package org.mccaughey;

import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.json.JSONException;
import org.json.JSONObject;
import org.mccaughey.ActiveMQ.Sender;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

public class SendPoint {
    public static void main(String[] args){
        Sender p = new Sender("PointQueue");
        p.run();

        try{

            //Create the counter_file for multiple containers to write data into files in order
            JSONObject counterJsonRegion = new JSONObject();
            counterJsonRegion.put("region_counter",0);
            JSONObject counterJsonConnectivity = new JSONObject();
            counterJsonConnectivity.put("connectivity_counter",0);
            JSONObject counterJsonDensity = new JSONObject();
            counterJsonDensity.put("density_counter",0);
            JSONObject counterJsonLum = new JSONObject();
            counterJsonLum.put("lum_counter",0);

            File file_counter_region = new File("./src/main/java/org/mccaughey/output/counter_region.json");
            //If the file has already existed, the content will be overwritten.
            if (!file_counter_region.exists()) {
                file_counter_region.createNewFile();
            }
            FileOutputStream fout_00 = new FileOutputStream(file_counter_region, false);
            fout_00.write(counterJsonRegion.toString().getBytes());
            fout_00.flush();
            fout_00.close();

            File file_counter_connectivity = new File("./src/main/java/org/mccaughey/output/counter_connectivity.json");
            //If the file has already existed, the content will be overwritten.
            if (!file_counter_connectivity.exists()) {
                file_counter_connectivity.createNewFile();
            }
            FileOutputStream fout_01 = new FileOutputStream(file_counter_connectivity, false);
            fout_01.write(counterJsonConnectivity.toString().getBytes());
            fout_01.flush();
            fout_01.close();

            File file_counter_density = new File("./src/main/java/org/mccaughey/output/counter_density.json");
            //If the file has already existed, the content will be overwritten.
            if (!file_counter_density.exists()) {
                file_counter_density.createNewFile();
            }
            FileOutputStream fout_02 = new FileOutputStream(file_counter_density, false);
            fout_02.write(counterJsonDensity.toString().getBytes());
            fout_02.flush();
            fout_02.close();

            File file_counter_lum = new File("./src/main/java/org/mccaughey/output/counter_lum.json");
            //If the file has already existed, the content will be overwritten.
            if (!file_counter_lum.exists()) {
                file_counter_lum.createNewFile();
            }
            FileOutputStream fout_03 = new FileOutputStream(file_counter_lum, false);
            fout_03.write(counterJsonLum.toString().getBytes());
            fout_03.flush();
            fout_03.close();


            File file_region = new File("./src/main/java/org/mccaughey/output/regionOMS.geojson");
            //If the file has already existed, the content will be overwritten.
            if (!file_region.exists()) {
                file_region.createNewFile();
            }
            FileOutputStream fout_0 = new FileOutputStream(file_region, false);
            fout_0.write("{\"type\":\"FeatureCollection\",\"features\":[".getBytes());
            fout_0.flush();
            fout_0.close();

            File file_connectivity = new File("./src/main/java/org/mccaughey/output/connectivityOMS.geojson");
            //If the file has already existed, the content will be overwritten.
            if (!file_connectivity.exists()) {
                file_connectivity.createNewFile();
            }
            FileOutputStream fout_1 = new FileOutputStream(file_connectivity, false);
            fout_1.write("{\"type\":\"FeatureCollection\",\"features\":[".getBytes());
            fout_1.flush();
            fout_1.close();

            File file_density = new File("./src/main/java/org/mccaughey/output/densityOMS.geojson");
            //If the file has already existed, the content will be overwritten.
            if (!file_density.exists()) {
                file_density.createNewFile();
            }
            FileOutputStream fout_2 = new FileOutputStream(file_density, false);
            fout_2.write("{\"type\":\"FeatureCollection\",\"features\":[".getBytes());
            fout_2.flush();
            fout_2.close();

            File file_lum = new File("./src/main/java/org/mccaughey/output/lumOMS.geojson");
            //If the file has already existed, the content will be overwritten.
            if (!file_lum.exists()) {
                file_lum.createNewFile();
            }
            FileOutputStream fout_3 = new FileOutputStream(file_lum, false);
            fout_3.write("{\"type\":\"FeatureCollection\",\"features\":[".getBytes());
            fout_3.flush();
            fout_3.close();

            URL pointsUrl = new File("./src/main/java/org/mccaughey/Rndm1000ptsProjected.json").toURI().toURL();
            SimpleFeatureIterator points = GeoJSONUtilities.readFeatures(pointsUrl).features();
            int counter = 0;
            while(points.hasNext()){
                counter++;
                SimpleFeature point = points.next();
                FeatureJSON fjson = new FeatureJSON();
                String output = fjson.toString(point);
                p.sendMessage(counter + "--" + output);
            }

        }
        catch(IOException e){
            e.printStackTrace();
        }
        catch(JSONException e){
            e.printStackTrace();
        }
        finally {
            p.close();
        }


    }
}

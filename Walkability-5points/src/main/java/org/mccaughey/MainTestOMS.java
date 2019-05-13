package org.mccaughey;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.geotools.data.DataUtilities;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.mccaughey.utilities.GeoJSONUtilities;
import org.mccaughey.utilities.ValidationUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.mccaughey.statistics.ZScore;
import org.mccaughey.statistics.ZScoreOMS;
import org.mccaughey.connectivity.NetworkBufferOMS;
import org.mccaughey.connectivity.ConnectivityIndexOMS;
import org.mccaughey.density.DwellingDensityOMS;
import org.mccaughey.landuse.LandUseMixOMS;
import org.opengis.feature.simple.SimpleFeatureType;

import au.org.aurin.types.AttributeSelector;


public class MainTestOMS extends TestCase{

    public MainTestOMS(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(MainTestOMS.class);
    }

    public void testMainOMS(){

        /* Start recording time
        * */
        long startTIme = System.currentTimeMillis();

        try{
            /* Step 1. Read in two input files: road LineFile and multi-point file
            * */

            URL roadsUrl = MainTestOMS.class.getClass().getResource("/psma_cut_projected.geojson.gz");
            URL pointsUrl = MainTestOMS.class.getClass().getResource("/RndmMultiPoint5ptsProjected.json");

            /* Step 2. Generate polygon regions. Output the file into "src/main/resources/networkBufferOMSTest.geojson"
            * */
            NetworkBufferOMS networkBufferOMS = new NetworkBufferOMS();
            networkBufferOMS.network = DataUtilities.source(GeoJSONUtilities.readFeatures(roadsUrl));
            networkBufferOMS.points = DataUtilities.source(GeoJSONUtilities.readFeatures(pointsUrl));
            networkBufferOMS.bufferSize = 100.0;
            networkBufferOMS.distance = 1600.0;
            networkBufferOMS.run();

            GeoJSONUtilities.writeFeatures(networkBufferOMS.regions.getFeatures(),
                    new File("src/main/resources/networkBufferOMSTest.geojson").toURI().toURL());

            /* Step 3. Read in the file generated in the last step. Calculate the Connectivity Index. Output the result file
            into "src/main/resources/connectivityOMSTest.geojson"
            * */

            URL regionsUrl = MainTest.class.getClass().getResource("/networkBufferOMSTest.geojson");
            ConnectivityIndexOMS connectivityOMS = new ConnectivityIndexOMS();
            connectivityOMS.network = DataUtilities.source(GeoJSONUtilities.readFeatures(roadsUrl));
            connectivityOMS.regions = DataUtilities.source(GeoJSONUtilities.readFeatures(regionsUrl));
            connectivityOMS.run();
            GeoJSONUtilities.writeFeatures(connectivityOMS.results.getFeatures(),
                    new File("src/main/resources/connectivityOMSTest.geojson").toURI().toURL());

            /* Step 4. Similarly, calculate Density Index. Output the results into "src/main/resources/densityOMSTest.geojson"
            * */

            URL landUseURL = MainTest.class.getClass().getResource("/MB_WA_2006_census_projected.shp");
            File landUseShapeFile = new File(landUseURL.toURI());
            File densityGeoJSON = File.createTempFile("density",".geojson");
            FileDataStore densityDataStore = FileDataStoreFinder.getDataStore(landUseShapeFile);
            GeoJSONUtilities.writeFeatures(densityDataStore.getFeatureSource().getFeatures(), densityGeoJSON.toURI().toURL());

            DwellingDensityOMS densityOMS = new DwellingDensityOMS();
            densityOMS.countAttribute = new AttributeSelector(null, "TDWELL2006");
            densityOMS.populationSource = DataUtilities.source(GeoJSONUtilities.readFeatures(densityGeoJSON.toURI().toURL()));
            densityOMS.regionsSource = DataUtilities.source(GeoJSONUtilities.readFeatures(regionsUrl));
            densityOMS.averageDensity();
            GeoJSONUtilities.writeFeatures(densityOMS.resultsSource.getFeatures(),
                    new File("src/main/resources/densityOMSTest.geojson").toURI().toURL());

            densityDataStore.dispose();
            densityGeoJSON.delete();

            /* Step 5. Calculate LandUseMix Index. Output the results into "src/main/resources/lumOMSTest.geojson"
             * */

            FileDataStore landUseDataStore = FileDataStoreFinder.getDataStore(landUseShapeFile);

            List<String> classifications = new ArrayList<String>();
            classifications.add("Parkland");
            classifications.add("Residential");
            classifications.add("Education");
            classifications.add("Commercial");
            classifications.add("Industrial");
            classifications.add("Hospital/Medical");

            LandUseMixOMS lumOMS = new LandUseMixOMS();
            lumOMS.landUseSource = landUseDataStore.getFeatureSource();
            lumOMS.regionsSource = DataUtilities.source(GeoJSONUtilities.readFeatures(regionsUrl));
            lumOMS.classificationAttribute = "CATEGORY";
            lumOMS.categories = classifications;
            lumOMS.validateInputs();
            lumOMS.landUseMixMeasure();

            GeoJSONUtilities.writeFeatures(lumOMS.resultsSource.getFeatures(),
                    new File("src/main/resources/lumOMSTest.geojson").toURI().toURL());
            landUseDataStore.dispose();


            /* Step 6. Read in the results generated above (Connectivity, Density, LUM). Merge them into one feature file:
            "src/main/resources/zScoreOMSTest.geojson"
            * */
            List<SimpleFeature> ZScoreFeatures = new ArrayList<SimpleFeature>();
            URL connectivityUrl = MainTest.class.getClass().getResource("/connectivityOMSTest.geojson");
            URL densityUrl = MainTest.class.getClass().getResource("/densityOMSTest.geojson");
            URL lumUrl = MainTest.class.getClass().getResource("/lumOMSTest.geojson");

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
            GeoJSONUtilities.writeFeatures(ZScoreCollections,
                    new File("src/main/resources/zScoreOMSTest.geojson").toURI().toURL());


            /* Step 7. Read in the data file above. Calculate the Sum-Zcore for each region. Output the results into "target/ZScoreOMSTest.geojson"
            * */

            URL zScoreUrl = MainTest.class.getClass().getResource("/zScoreOMSTest.geojson");
            ZScoreOMS zscoreOMS = new ZScoreOMS();
            zscoreOMS.attributes = attributes;
            zscoreOMS.regionsSource = DataUtilities.source(GeoJSONUtilities.readFeatures(zScoreUrl));
            zscoreOMS.sumOfZScores();

            GeoJSONUtilities.writeFeatures(zscoreOMS.resultsSource.getFeatures(),
                    new File("target/ZScoreOMSTest.geojson").toURI().toURL());
            assertTrue(true);
        }
        catch(URISyntaxException e1){
            System.out.println(e1.getMessage());
        }
        catch(IOException e2){
            System.out.println(e2.getMessage());
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Running Time: " + (double)(endTime-startTIme)/1000 + "s");
    }

}

package org.mccaughey;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.mccaughey.utilities.ValidationUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class Config {

    public static int NUM_OF_POINTS = 5;

    public static double BUFFER_SIZE = 50.0;

    public static double DISTANCE = 800.0;

    public static SimpleFeature buildFeature(SimpleFeature region, Double connectivity, Double density, Double lum) {

        SimpleFeatureType sft = (SimpleFeatureType) region.getType();
        SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
        stb.init(sft);
        stb.setName("ZScoreFeatureType");
        stb.add("Connectivity", Double.class);
        stb.add("Density", Double.class);
        stb.add("LUM", Double.class);
        SimpleFeatureType featureType = stb.buildFeatureType();
        SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(featureType);
        sfb.addAll(region.getAttributes());
        sfb.set("Connectivity",ValidationUtils.isValidDouble(connectivity) ? connectivity : null);
        sfb.set("Density", ValidationUtils.isValidDouble(density) ? density : null);
        sfb.set("LUM",ValidationUtils.isValidDouble(lum) ? lum : null);
        return sfb.buildFeature(region.getID());
    }
}

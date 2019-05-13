package org.mccaughey.priorityAllocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.Finalize;
import oms3.annotations.In;
import oms3.annotations.Name;
import oms3.annotations.Out;

import org.geotools.data.DataUtilities;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureIterator;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class performs the point in polygon priority allocation process, for allocating land use types to
 * cadastral/parcel data
 *
 * @author amacaulay
 *
 */
@Name("Priority Allocation")
@Description("The Point in Polygon Priority Allocation process creates a land use polygon data set based on point features,parcel features and a classification priority")
public class PointInPolygonPriorityAllocationOMS {
  static final Logger LOGGER = LoggerFactory.getLogger(PointInPolygonPriorityAllocationOMS.class);

  /**
   * Region(s) of Interest, this is used to filter down and avoid processing data which isn't needed
   */
  @In
  @Name("Regions")
  @Description("The extent of the neighbourhoods are used to limit the analysis extent")
  public SimpleFeatureSource regionsSource;

  /**
   * A land parcel type data set (eg. cadastre)
   */
  @In
  @Name("Parcels")
  @Description("Cadastral parcels used to provide an areal extent to resulting land use polygons")
  public SimpleFeatureSource parcels;

  /**
   * Point features which will be used to reallocate parcel land use types
   */
  @In
  @Name("Land Use Feature Points")
  @Description("Point feature data set with land use categories stored in an attribute")
  public SimpleFeatureSource pointFeatures;

  /**
   * Attribute in pointFeatures which represents land use type
   */
  @In
  @Name("Land Use Attribute")
  @Description("The land use attribute in the point data set which will be used to allocate land uses to the parcels")
  public String landUseAttribute;

  /**
   * The priority list of which land uses, to figure out which one to allocate
   */
  @In
  @Name("Priority Order")
  @Description("An ordered list of land use categories that will be used to allocate a single land use to parcels where multiple category types may exist within them.")
  public SimpleFeatureSource priorityOrderSource;

  /**
   * Name of key column in priority order dataset
   */
  @In
  @Name("Priority Order Key")
  @Description("Name of key column in priority order dataset")
  public String priorityKey;

  /**
   * Name of key column in priority order dataset
   */
  @In
  @Name("Priority Order Value")
  @Description("Name of value column in priority order dataset")
  public String priorityValue;

  /**
   * The resulting parcels with re-allocated land use types
   */
  @Out
  public SimpleFeatureSource resultParcels;

  /**
   * Reads in the population count layer and regions layer from given URLs, writes out average density results to
   * resultsURL
   *
   * @throws CQLException
   * @throws IOException
   */
  @Execute
  public void allocate() throws CQLException, IOException {
    LOGGER.info("Calculating Priority Allocation");

    Map<String, Integer> priorityOrder = priorityOrderMap(priorityOrderSource);
    try {
      FeatureIterator<SimpleFeature> regions = regionsSource.getFeatures().features();
      List<SimpleFeature> allocatedParcels = new ArrayList<SimpleFeature>();
      try {
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<List<SimpleFeature>>> futures = new ArrayList<Future<List<SimpleFeature>>>();
        while (regions.hasNext()) {
          SimpleFeature regionOfInterest = regions.next();
          Allocater ac = new Allocater(regionOfInterest, pointFeatures, priorityOrder, parcels, landUseAttribute);
          Future<List<SimpleFeature>> future = executorService.submit(ac);
          futures.add(future);
        }
        int remaining = futures.size();
        for (Future<List<SimpleFeature>> future : futures) {
          allocatedParcels.addAll(future.get());
          LOGGER.debug("Calculated priority allocation. Remaining processes: {} of {}", --remaining, futures.size());
        }
        resultParcels = DataUtilities.source(AllocationUtils.prioritiseOverlap(
            DataUtilities.collection(allocatedParcels), landUseAttribute, priorityOrder));
        LOGGER.info("Completed Priority Allocation");

      } catch (ExecutionException e) {
        LOGGER.error("Failed to complete process for all features; ExecutionException: {}", e);
        throw new IllegalStateException("Failed to complete process for all features", e.getCause());
      } catch (InterruptedException e) {
        throw new IllegalStateException("Failed to complete process for all features", e.getCause());
      } finally {
        regions.close();
      }
    } catch (IOException e) {
      LOGGER.error("Failed to read features");
    }

  }

  private Map<String, Integer> priorityOrderMap(SimpleFeatureSource source) throws IOException {
    SimpleFeatureIterator features = source.getFeatures().features();
    Map<String, Integer> priorityOrder = new HashMap();
    try {
      while (features.hasNext()) {
        SimpleFeature feature = features.next();
        Integer value;
        Object valueObj = feature.getAttribute(priorityValue);
        if (valueObj.getClass() == Integer.class) {
          value = (Integer) valueObj;
        } else if (valueObj.getClass() == String.class) {
          value = Integer.parseInt((String) (valueObj));
        } else if (valueObj.getClass() == Long.class) {
          value = ((Long) (valueObj)).intValue();
        } else {
          throw new IOException("Cannot parse value object " + valueObj.getClass().toString());
        }
        priorityOrder.put(feature.getAttribute(priorityKey).toString(), value);
      }
    } finally {
      features.close();
    }
    return priorityOrder;
  }

  /*
   * Validate outputs
   */
  @Finalize
  public void validateOutputs() throws IOException {

    if (resultParcels.getCount(new Query()) == 0) {
      throw new IllegalArgumentException("Cannot continue tool execution; no results were produced");
    }
  }

}

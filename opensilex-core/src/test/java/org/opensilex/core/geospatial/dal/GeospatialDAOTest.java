/*
 * *******************************************************************************
 *                     GeospatialDAOTest.java
 * OpenSILEX
 * Copyright © INRAE 2020
 * Creation date: September 28, 2020
 * Contact: vincent.migot@inrae.fr, anne.tireau@inrae.fr, pascal.neveu@inrae.fr
 * *******************************************************************************
 */
package org.opensilex.core.geospatial.dal;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.geojson.Geometry;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Polygon;
import com.mongodb.client.model.geojson.Position;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Test;
import org.opensilex.core.AbstractMongoIntegrationTest;
import org.opensilex.nosql.service.NoSQLService;
import org.opensilex.sparql.deserializer.SPARQLDeserializers;
import org.opensilex.utils.ListWithPagination;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Jean Philippe VERT
 */
public class GeospatialDAOTest extends AbstractMongoIntegrationTest {

    private static GeospatialDAO geospatialDAO = null;
    private static MongoClient mongoClient = null;
    private final URI type;

    public GeospatialDAOTest() throws URISyntaxException {
        this.type = new URI("http://www.opensilex.org/vocabulary/oeso#WindyArea");
        if (geospatialDAO == null) {
            NoSQLService service = getOpensilex().getServiceInstance("nosql", NoSQLService.class);
            mongoClient = service.getMongoDBClient();
            geospatialDAO = new GeospatialDAO(mongoClient);
        }
    }

    @AfterClass
    public static void closeMongoConnection() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    private void verificationOfCorrectInsertion(Geometry geometry, URI uri, URI type, URI graph) {
        GeospatialModel geometryByURI = geospatialDAO.getGeometryByURI(uri, graph);
        TestCase.assertTrue(SPARQLDeserializers.compareURIs(uri.toString(), geometryByURI.getUri().toString()));
        TestCase.assertTrue(SPARQLDeserializers.compareURIs(type.toString(), geometryByURI.getType().toString()));
        TestCase.assertTrue(SPARQLDeserializers.compareURIs(graph.toString(), geometryByURI.getGraph().toString()));
        TestCase.assertEquals(geometry, geometryByURI.getGeometry());
    }


    private GeospatialModel getGeospatialModel(String typeGeometry, URI uri, boolean otherGraph) throws URISyntaxException {
        Geometry geometry = null;
        // Valid longitude values are between -180 and 180, both inclusive.
        // Valid latitude values are between -90 and 90, both inclusive.
        if (typeGeometry.equals("Polygon")) {
            List<Position> list = new LinkedList<>();
            list.add(new Position(3.97167246, 43.61328981));
            list.add(new Position(3.97171243, 43.61332417));
            list.add(new Position(3.9717427, 43.61330558));
            list.add(new Position(3.97170272, 43.61327122));
            list.add(new Position(3.97167246, 43.61328981));
            list.add(new Position(3.97167246, 43.61328981));
            geometry = new Polygon(list);
        } else if (typeGeometry.equals("Point")) {
            geometry = new Point(new Position(3.97167246, 43.61328981));
        }

        URI type = new URI("vocabulary:WindyArea");
        URI graph;
        if (otherGraph) {
            graph = new URI("test-exp:mau17-pg/2");
        } else {
            graph = new URI("test-exp:ZA17");
        }

        GeospatialModel geospatial = new GeospatialModel();
        geospatial.setUri(uri);
        geospatial.setType(type);
        geospatial.setGraph(graph);
        geospatial.setGeometry(geometry);

        return geospatial;
    }

    @Test
    public void testCreate() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Point", new URI("http://opensilex/Geospatial/G_991"), false);

        geospatialDAO.create(geospatial);

        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());
    }

    @Test
    public void testGeometryByURI() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Polygon", new URI("http://opensilex/Geospatial/G_992"), false);

        geospatialDAO.create(geospatial);

        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());
    }

    @Test
    public void testGeometryURIShort() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Point", new URI("http://opensilex/Geospatial/G_993"), false);

        geospatialDAO.create(geospatial);

        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());
    }

    @Test
    public void testDelete() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Point", new URI("http://opensilex/Geospatial/G_994"), false);

        geospatialDAO.create(geospatial);
        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());

        geospatialDAO.delete(geospatial.getUri(), geospatial.getGraph());

        // we check that the geometries are not in the database
        GeospatialModel geometryDelete = geospatialDAO.getGeometryByURI(geospatial.getUri(), geospatial.getGraph());
        TestCase.assertNull(geometryDelete);
    }

    @Test
    public void testUpdate() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Polygon", new URI("http://opensilex/Geospatial/G_995"), false);

        geospatialDAO.create(geospatial);
        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());

        URI oldUri = geospatial.getUri();
        URI oldGraph = geospatial.getGraph();
        URI newUri = new URI("http://opensilex/Area/G_504");
        URI newGraph = new URI("test-exp:ZA17");

        geospatial.setUri(newUri);
        geospatial.setType(new URI("http://www.opensilex.org/vocabulary/oeso#PollutedArea"));
        geospatial.setGraph(newGraph);
        geospatial.setGeometry(new Point(new Position(4.9716721, 43.6)));

        geospatialDAO.update(geospatial, oldUri, oldGraph);
        verificationOfCorrectInsertion(geospatial.getGeometry(), newUri, geospatial.getType(), newGraph);

        // we check that the old geometries are not in the database
        TestCase.assertNull(geospatialDAO.getGeometryByURI(oldUri, oldGraph));
    }

    @Test(expected = Exception.class)
    public void testCreateWithExistingUri() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Point", new URI("http://opensilex/Geospatial/G_996"), false);

        geospatialDAO.create(geospatial);

        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());

        geospatialDAO.create(geospatial);
    }

    @Test
    public void testGetGeometryByUris() throws URISyntaxException {
        List<URI> objectsURI = new LinkedList<>();

        URI uri = new URI("http://opensilex/Geospatial/G_997");
        GeospatialModel geospatial = getGeospatialModel("Point", uri, false);
        geospatialDAO.create(geospatial);
        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());
        objectsURI.add(uri);

        // returns all geometries that respond to the filtering, returns a HashMap<String, Geometry>.
        HashMap<String, Geometry> geometryByUris = geospatialDAO.getGeometryByUris(null, objectsURI);
        TestCase.assertNotNull(geometryByUris);
    }

    @Test
    public void testGetGeometryByUrisWithoutUriExperience() throws URISyntaxException {
        List<URI> objectsURI = new LinkedList<>();

        URI uri = new URI("http://opensilex/Geospatial/G_998");
        GeospatialModel geospatial = getGeospatialModel("Point", uri, false);
        geospatialDAO.create(geospatial);
        verificationOfCorrectInsertion(geospatial.getGeometry(), geospatial.getUri(), type, geospatial.getGraph());
        objectsURI.add(uri);

        URI uri2 = new URI("http://opensilex/Geospatial/G_999");
        GeospatialModel geospatial2 = getGeospatialModel("Polygon", uri2, true);
        geospatialDAO.create(geospatial2);
        verificationOfCorrectInsertion(geospatial2.getGeometry(), geospatial2.getUri(), type, geospatial2.getGraph());
        objectsURI.add(uri2);

        // returns all geometries that match the uri list filtering, returns a HashMap<String, Geometry>.
        HashMap<String, Geometry> geometryByUrisWithoutUriExperience = geospatialDAO.getGeometryByUris(null, objectsURI);
        TestCase.assertEquals(geometryByUrisWithoutUriExperience.size(), 2);
    }

    @Test
    public void testSearchIntersects() {
        List<Position> list = new LinkedList<>();
        list.add(new Position(3.97167246, 43.61328981));
        list.add(new Position(3.97171243, 43.61332417));
        list.add(new Position(3.9717427, 43.61330558));
        list.add(new Position(3.97170272, 43.61327122));
        list.add(new Position(3.97167246, 43.61328981));
        list.add(new Position(3.97167246, 43.61328981));

        Polygon geometry = new Polygon(list);

        // Creates a filter that matches all documents containing a field with geospatial data that intersects with the specified shape.
        ListWithPagination<GeospatialModel> searchIntersects = geospatialDAO.searchIntersects(null, geometry, 1, 20);

        TestCase.assertNotNull(searchIntersects);
    }

    @Test
    public void testSearchIntersectsInstance() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Point", new URI(""), false);

        // Creates a filter that matches all documents containing a field with geospatial data that intersects with the specified shape.
        ListWithPagination<GeospatialModel> searchIntersects = geospatialDAO.searchIntersects(geospatial, 1, 20);

        TestCase.assertNotNull(searchIntersects);
    }

    @Test
    public void testSearchWithin() {
        List<Position> list = new LinkedList<>();
        list.add(new Position(3.97167246, 43.61328981));
        list.add(new Position(3.97171243, 43.61332417));
        list.add(new Position(3.9717427, 43.61330558));
        list.add(new Position(3.97170272, 43.61327122));
        list.add(new Position(3.97167246, 43.61328981));
        list.add(new Position(3.97167246, 43.61328981));

        Polygon geometry = new Polygon(list);

        // Creates a filter that matches all documents containing a field with geospatial data that within with the specified shape.
        ListWithPagination<GeospatialModel> searchWithin = geospatialDAO.searchWithin(null, geometry, 1, 20);

        TestCase.assertNotNull(searchWithin);
    }

    @Test
    public void testSearchWithinInstance() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Point", new URI(""), false);

        // Creates a filter that matches all documents containing a field with geospatial data that withing with the specified shape.
        ListWithPagination<GeospatialModel> searchWithin = geospatialDAO.searchWithin(geospatial, 1, 20);

        TestCase.assertNotNull(searchWithin);
    }

    @Test
    public void testSearchNear() {
        Point point = new Point(new Position(3.97, 43.61));

        // Creates a filter that returns all geospatial data within the distance radius provided.
        ListWithPagination<GeospatialModel> searchNear = geospatialDAO.searchNear(null, point, 390.0, null, 1, 20);

        TestCase.assertNotNull(searchNear);
    }

    @Test
    public void testSearchNearInstance() throws URISyntaxException {
        GeospatialModel geospatial = getGeospatialModel("Point", new URI(""), false);

        // Creates a filter that returns all geospatial data within the distance radius provided.
        ListWithPagination<GeospatialModel> searchNear = geospatialDAO.searchNear(geospatial, null, 20.0, 1, 20);

        TestCase.assertNotNull(searchNear);
    }
}

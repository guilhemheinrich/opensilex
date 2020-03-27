//******************************************************************************
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRA 2019
// Contact: vincent.migot@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//******************************************************************************
package org.opensilex.sparql.service;

import org.apache.jena.arq.querybuilder.AbstractQueryBuilder;
import org.apache.jena.arq.querybuilder.AskBuilder;
import org.apache.jena.arq.querybuilder.ConstructBuilder;
import org.apache.jena.arq.querybuilder.DescribeBuilder;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.arq.querybuilder.UpdateBuilder;
import org.apache.jena.arq.querybuilder.WhereBuilder;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.opensilex.service.Service;
import org.opensilex.sparql.deserializer.SPARQLDeserializer;
import org.opensilex.sparql.deserializer.SPARQLDeserializers;
import org.opensilex.sparql.exceptions.*;
import org.opensilex.sparql.mapping.SPARQLClassObjectMapper;
import org.opensilex.sparql.model.SPARQLResourceModel;
import org.opensilex.sparql.rdf4j.RDF4JConfig;
import org.opensilex.sparql.rdf4j.RDF4JConnection;
import org.opensilex.sparql.utils.Ontology;
import org.opensilex.sparql.utils.OrderBy;
import org.opensilex.sparql.utils.URIGenerator;
import org.opensilex.utils.ListWithPagination;
import org.opensilex.utils.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.function.Consumer;
import static org.apache.jena.arq.querybuilder.AbstractQueryBuilder.makeVar;
import org.apache.jena.arq.querybuilder.ExprFactory;
import org.opensilex.service.ServiceDefaultDefinition;
import org.opensilex.sparql.model.SPARQLTreeModel;
import org.opensilex.sparql.tree.ResourceTree;

/**
 * Implementation of SPARQLService
 */
@ServiceDefaultDefinition(
        connection = RDF4JConnection.class,
        connectionConfig = RDF4JConfig.class,
        connectionConfigID = "rdf4j"
)
public class SPARQLService implements SPARQLConnection, Service, AutoCloseable {

    private final static Logger LOGGER = LoggerFactory.getLogger(SPARQLService.class);

    public final static String DEFAULT_SPARQL_SERVICE = "sparql";

    private final SPARQLConnection connection;

    public SPARQLService(SPARQLConnection connection) {
        this.connection = connection;
    }

    @Override
    public void startup() throws Exception {
        connection.startup();
    }

    @Override
    public void shutdown() throws Exception {
        connection.shutdown();
    }

    @Override
    public void close() throws Exception {
        shutdown();
    }

    private static HashMap<String, String> getDefaultPrefixes() {
        return new HashMap<String, String>() {
            {
                put(RDFS.PREFIX, RDFS.NAMESPACE);
                put(FOAF.PREFIX, FOAF.NAMESPACE);
                put("dc", DCTerms.NS);
                put("oa", OA.NS);
                put(XMLSchema.PREFIX, XMLSchema.NAMESPACE);
            }
        };
    }

    private static HashMap<String, String> prefixes = getDefaultPrefixes();

    public static void addPrefix(String prefix, String namespace) {
        prefixes.put(prefix, namespace);
    }

    public static Map<String, String> getPrefixes() {
        return prefixes;
    }

    public static PrefixMapping getPrefixMapping() {
        return PrefixMapping.Factory.create().setNsPrefixes(prefixes);
    }

    private static void addPrefixes(UpdateBuilder builder) {
        builder.addPrefixes(getPrefixMapping());

    }

    private static void addPrefixes(AbstractQueryBuilder<?> builder) {
        builder.addPrefixes(getPrefixMapping());
    }

    public static void clearPrefixes() {
        prefixes = getDefaultPrefixes();
    }

    @Override
    public boolean executeAskQuery(AskBuilder ask) throws SPARQLException {
        addPrefixes(ask);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SPARQL ASK\n" + ask.buildString());
        }
        return connection.executeAskQuery(ask);
    }

    @Override
    public List<SPARQLStatement> executeDescribeQuery(DescribeBuilder describe) throws SPARQLException {
        addPrefixes(describe);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SPARQL DESCRIBE\n" + describe.buildString());
        }
        return connection.executeDescribeQuery(describe);
    }

    public List<SPARQLStatement> describe(Node graph, URI uri) throws SPARQLException {
        DescribeBuilder describe = new DescribeBuilder();
        Var uriVar = makeVar(SPARQLResourceModel.URI_FIELD);
        describe.addVar(uriVar);
        if (graph != null) {
            describe.from(graph.getURI());
        }
        describe.addBind(new ExprFactory().iri(uri), uriVar);
        return executeDescribeQuery(describe);
    }

    @Override
    public List<SPARQLStatement> getGraphStatement(URI graph) throws SPARQLException {
        LOGGER.debug("SPARQL GET GRAPH STATEMENTS FOR: " + graph);
        return connection.getGraphStatement(graph);
    }

    @Override
    public List<SPARQLStatement> executeConstructQuery(ConstructBuilder construct) throws SPARQLException {
        addPrefixes(construct);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SPARQL CONSTRUCT\n" + construct.buildString());
        }
        return connection.executeConstructQuery(construct);
    }

    @Override
    public List<SPARQLResult> executeSelectQuery(SelectBuilder select, Consumer<SPARQLResult> resultHandler) throws SPARQLException {
        addPrefixes(select);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SPARQL SELECT\n" + select.buildString());
        }
        return connection.executeSelectQuery(select, resultHandler);
    }

    @Override
    public void executeUpdateQuery(UpdateBuilder update) throws SPARQLException {
        addPrefixes(update);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SPARQL UPDATE\n" + update.build().toString());
        }
        connection.executeUpdateQuery(update);
    }

    @Override
    public void executeDeleteQuery(UpdateBuilder delete) throws SPARQLException {
        addPrefixes(delete);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SPARQL DELETE\n" + delete.buildRequest().toString());
        }
        connection.executeDeleteQuery(delete);
    }

    private int transactionLevel = 0;

    @Override
    public void startTransaction() throws SPARQLException {
        if (transactionLevel == 0) {
            LOGGER.debug("SPARQL TRANSACTION START");
            connection.startTransaction();
        }
        transactionLevel++;
    }

    @Override
    public void commitTransaction() throws SPARQLException {
        transactionLevel--;
        if (transactionLevel == 0) {
            LOGGER.debug("SPARQL TRANSACTION COMMIT");
            connection.commitTransaction();
        }
    }

    @Override
    public void rollbackTransaction(Exception ex) throws Exception {
        if (transactionLevel != 0) {
            LOGGER.error("SPARQL TRANSACTION ROLLBACK: ", ex);
            transactionLevel = 0;
            connection.rollbackTransaction(ex);
        }
    }

    public void rollbackTransaction() throws Exception {
        rollbackTransaction(null);
    }

    @Override
    public void clearGraph(URI graph) throws SPARQLException {
        LOGGER.debug("SPARQL CLEAR GRAPH: " + graph);
        connection.clearGraph(graph);
    }

    @Override
    public void renameGraph(URI oldGraphURI, URI newGraphURI) throws SPARQLException {
        disableSHACL();
        LOGGER.debug("MOVE GRAPH " + oldGraphURI + " TO " + newGraphURI);
        connection.renameGraph(oldGraphURI, newGraphURI);
        enableSHACL();
    }

    @Override
    public void clear() throws SPARQLException {
        LOGGER.debug("SPARQL CLEAR REPOSITORY");
        connection.clear();
    }

    public void loadOntology(URI graph, InputStream ontology, Lang format) throws SPARQLException {
        LOGGER.debug("SPARQL LOAD " + format.getName() + " FILE INTO GRAPH: " + graph.toString());
        connection.loadOntology(graph, ontology, format);
    }

    public <T extends SPARQLResourceModel> T getByURI(Class<T> objectClass, URI uri, String lang) throws Exception {
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
        T instance = sparqlObjectMapper.createInstance(uri, lang, this);
        return instance;
    }

    public <T extends SPARQLResourceModel> List<T> getListByURIs(Class<T> objectClass, List<URI> uris, String lang) throws Exception {
        List<T> instances;
        if (uris.size() > 0) {
            SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
            instances = sparqlObjectMapper.createInstanceList(uris, lang, this);
        } else {
            instances = new ArrayList<>();
        }
        return instances;
    }

    public <T extends SPARQLResourceModel> T loadByURI(Class<T> objectClass, URI uri, String lang) throws Exception {
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
        SelectBuilder select = sparqlObjectMapper.getSelectBuilder(lang);

        select.addValueVar(sparqlObjectMapper.getURIFieldExprVar(), SPARQLDeserializers.nodeURI(uri));

        List<SPARQLResult> results = executeSelectQuery(select);

        if (results.size() == 1) {
            return sparqlObjectMapper.createInstance(results.get(0), lang, this);
        } else if (results.size() > 1) {
            throw new SPARQLException("Multiple objects for the same URI: " + uri.toString());
        } else {
            return null;
        }
    }

    public <T extends SPARQLResourceModel> List<T> loadListByURIs(Class<T> objectClass, List<URI> uris, String lang) throws Exception {
        List<T> resultObjects = new ArrayList<>();

        if (uris.size() > 0) {
            SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
            SelectBuilder select = sparqlObjectMapper.getSelectBuilder(lang);

            List<Node> uriNodes = SPARQLDeserializers.nodeListURI(uris);

            select.addValueVar(sparqlObjectMapper.getURIFieldExprVar(), uriNodes.toArray());

            List<SPARQLResult> results = executeSelectQuery(select);

            for (SPARQLResult result : results) {
                resultObjects.add(sparqlObjectMapper.createInstance(result, lang, this));
            }
        }
        return resultObjects;
    }

    public <T extends SPARQLResourceModel> T getByUniquePropertyValue(Class<T> objectClass, String lang, Property property, Object propertyValue) throws Exception {
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
        SelectBuilder select = sparqlObjectMapper.getSelectBuilder(lang);
        Field field = sparqlObjectMapper.getFieldFromUniqueProperty(property);

        SPARQLDeserializer<?> deserializer = SPARQLDeserializers.getForClass(propertyValue.getClass());
        select.addWhereValueVar(field.getName(), deserializer.getNode(propertyValue));

        List<SPARQLResult> results = executeSelectQuery(select);

        if (results.isEmpty()) {
            return null;
        } else if (results.size() == 1) {
            return sparqlObjectMapper.createInstance(results.get(0), lang, this);
        } else {
            throw new SPARQLException("Multiple objects for some unique property");
        }
    }

    public <T extends SPARQLResourceModel> boolean existsByUniquePropertyValue(Class<T> objectClass, Property property, Object propertyValue) throws Exception {
        return existsByUniquePropertyValue(objectClass, property, propertyValue, null);
    }

    public <T extends SPARQLResourceModel> boolean existsByUniquePropertyValue(Class<T> objectClass, Property property, Object propertyValue, String lang) throws Exception {
        if (propertyValue == null) {
            return false;
        }
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
        AskBuilder ask = sparqlObjectMapper.getAskBuilder(lang);
        Field field = sparqlObjectMapper.getFieldFromUniqueProperty(property);
        SPARQLDeserializer<?> deserializer = SPARQLDeserializers.getForClass(propertyValue.getClass());
        ask.setVar(field.getName(), deserializer.getNode(propertyValue));

        return executeAskQuery(ask);
    }

    public <T extends SPARQLResourceModel> List<URI> searchURIs(Class<T> objectClass, String lang) throws Exception {
        return searchURIs(objectClass, lang, null);
    }

    public <T extends SPARQLResourceModel> List<URI> searchURIs(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler) throws Exception {
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
        SelectBuilder select = sparqlObjectMapper.getSelectBuilder(lang);

        if (filterHandler != null) {
            filterHandler.accept(select);
        }

        List<URI> resultList = new ArrayList<>();
        SPARQLDeserializer<URI> uriDeserializer = SPARQLDeserializers.getForClass(URI.class);
        executeSelectQuery(select, ThrowingConsumer.wrap((SPARQLResult result) -> {
            resultList.add(uriDeserializer.fromString((result.getStringValue(sparqlObjectMapper.getURIFieldName()))));
        }, Exception.class));

        return resultList;
    }

    public <T extends SPARQLTreeModel> ResourceTree<T> searchResourceTree(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler) throws Exception {
        List<T> list = search(objectClass, lang, filterHandler);

        ResourceTree<T> tree = new ResourceTree<T>(list);

        for (T item : list) {
            tree.addTree(item);
        }

        return tree;
    }

    public <T extends SPARQLResourceModel> List<T> search(Class<T> objectClass, String lang) throws Exception {
        return search(objectClass, lang, null, null, null, null);
    }

    public <T extends SPARQLResourceModel> List<T> search(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler) throws Exception {
        return search(objectClass, lang, filterHandler, null, null, null);
    }

    public <T extends SPARQLResourceModel> List<T> search(Class<T> objectClass, String lang, List<OrderBy> orderByList) throws Exception {
        return search(objectClass, lang, null, orderByList, null, null);
    }

    public <T extends SPARQLResourceModel> List<T> search(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler, List<OrderBy> orderByList) throws Exception {
        return search(objectClass, lang, filterHandler, orderByList, null, null);
    }

    public <T extends SPARQLResourceModel> List<T> search(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler, List<OrderBy> orderByList, Integer page, Integer pageSize) throws Exception {
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
        SelectBuilder select = sparqlObjectMapper.getSelectBuilder(lang);

        if (filterHandler != null) {
            filterHandler.accept(select);
        }

        if (orderByList != null) {
            orderByList.forEach(ThrowingConsumer.wrap((OrderBy orderBy) -> {
                Expr fieldOrderExpr = sparqlObjectMapper.getFieldOrderExpr(orderBy.getFieldName());
                select.addOrderBy(fieldOrderExpr, orderBy.getOrder());
            }, SPARQLUnknownFieldException.class));
        }

        if (page == null || page < 0) {
            page = 0;
        }

        if (pageSize != null && pageSize > 0) {
            select.setOffset(page * pageSize);
            select.setLimit(pageSize);
        }

        List<T> resultList = new ArrayList<>();
        executeSelectQuery(select, ThrowingConsumer.wrap((SPARQLResult result) -> {
            resultList.add(sparqlObjectMapper.createInstance(result, lang, this));
        }, Exception.class));

        return resultList;
    }

    public <T extends SPARQLResourceModel> int count(Class<T> objectClass) throws Exception {
        return count(objectClass, null, null);
    }

    public <T extends SPARQLResourceModel> int count(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler) throws Exception {
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
        SelectBuilder selectCount = sparqlObjectMapper.getCountBuilder("count", lang);

        if (filterHandler != null) {
            filterHandler.accept(selectCount);
        }

        List<SPARQLResult> resultSet = executeSelectQuery(selectCount);

        if (resultSet.size() == 1) {
            return Integer.valueOf(resultSet.get(0).getStringValue("count"));
        } else {
            throw new SPARQLException("Invalid count query");
        }
    }

    public <T extends SPARQLResourceModel> ListWithPagination<T> searchWithPagination(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler, Integer page, Integer pageSize) throws Exception {
        return searchWithPagination(objectClass, lang, filterHandler, null, page, pageSize);
    }

    public <T extends SPARQLResourceModel> ListWithPagination<T> searchWithPagination(Class<T> objectClass, String lang, ThrowingConsumer<SelectBuilder, Exception> filterHandler, List<OrderBy> orderByList, Integer page, Integer pageSize) throws Exception {
        int total = count(objectClass, lang, filterHandler);

        List<T> list;
        if (pageSize == null || pageSize == 0) {
            list = search(objectClass, lang, filterHandler, orderByList);
        } else if (total > 0 && (page * pageSize) < total) {
            list = search(objectClass, lang, filterHandler, orderByList, page, pageSize);
        } else {
            list = new ArrayList<>();
        }

        return new ListWithPagination<>(list, page, pageSize, total);
    }

    public <T extends SPARQLResourceModel> void create(T instance) throws Exception {
        create(instance, true, null);
    }

    public <T extends SPARQLResourceModel> void create(T instance, Resource rdfType) throws Exception {
        create(instance, new URI(rdfType.getURI()));
    }

    public <T extends SPARQLResourceModel> void create(T instance, URI rdfType) throws Exception {
        create(instance, true, rdfType);
    }

    public <T extends SPARQLResourceModel> void create(T instance, boolean checkUriExist) throws Exception {
        create(instance, checkUriExist, null);
    }

    private <T extends SPARQLResourceModel> void create(T instance, boolean checkUriExist, URI rdfType) throws Exception {
        SPARQLClassObjectMapper<T> mapper = SPARQLClassObjectMapper.getForClass(instance.getClass());
        if (rdfType == null) {
            instance.setType(new URI(mapper.getRDFType().getURI()));
        } else {
            instance.setType(rdfType);
        }
        generateUniqueUriIfNullOrValidateCurrent(mapper, instance, checkUriExist);

        create(mapper.getAllDependentResourcesToCreate(instance));
        UpdateBuilder create = mapper.getCreateBuilder(instance);
        executeUpdateQuery(create);
    }

    public <T extends SPARQLResourceModel> void create(List<T> instances) throws Exception {
        if (instances.size() > 0) {
            UpdateBuilder create = new UpdateBuilder();
            for (T instance : instances) {
                SPARQLClassObjectMapper<T> mapper = SPARQLClassObjectMapper.getForClass(instance.getClass());
                create(mapper.getAllDependentResourcesToCreate(instance));
                generateUniqueUriIfNullOrValidateCurrent(mapper, instance, true);
                mapper.addCreateBuilder(instance, create);
            }

            executeUpdateQuery(create);
        }
    }

    private <T extends SPARQLResourceModel> void generateUniqueUriIfNullOrValidateCurrent(SPARQLClassObjectMapper<T> sparqlObjectMapper, T instance, boolean checkUriExist) throws Exception {
        URIGenerator<T> uriGenerator = sparqlObjectMapper.getUriGenerator(instance);
        URI uri = sparqlObjectMapper.getURI(instance);
        if (uri == null) {

            int retry = 0;
            String graphPrefix = sparqlObjectMapper.getDefaultGraph().toString();
            uri = uriGenerator.generateURI(graphPrefix, instance, retry);
            while (uriExists(uri)) {
                uri = uriGenerator.generateURI(graphPrefix, instance, ++retry);
            }

            sparqlObjectMapper.setUri(instance, uri);
        } else if (checkUriExist && uriExists(uri)) {
            throw new SPARQLAlreadyExistingUriException(uri);
        }
    }

    public <T extends SPARQLResourceModel> void update(T instance) throws Exception {
        @SuppressWarnings("unchecked")
        Class<T> objectClass = (Class<T>) instance.getClass();
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);

        try {
            startTransaction();

            T oldInstance = loadByURI(objectClass, sparqlObjectMapper.getURI(instance), null);
            if (oldInstance == null) {
                throw new SPARQLInvalidURIException(instance.getUri());
            }

            delete(oldInstance.getClass(), oldInstance.getUri());
            create(instance, false);

            commitTransaction();
        } catch (Exception ex) {
            rollbackTransaction(ex);
            throw ex;
        }
    }

    public <T extends SPARQLResourceModel> void update(List<T> instances) throws Exception {
        try {
            startTransaction();
            if (instances.size() > 0) {
                for (T instance : instances) {
                    update(instance);
                }
            }
            commitTransaction();
        } catch (Exception ex) {
            rollbackTransaction(ex);
            throw ex;
        }
    }

    public <T extends SPARQLResourceModel> void delete(Class<T> objectClass, URI uri) throws Exception {
        try {
            startTransaction();
            if (!uriExists(uri)) {
                throw new SPARQLInvalidURIException(uri);
            }
            SPARQLClassObjectMapper<T> mapper = SPARQLClassObjectMapper.getForClass(objectClass);

            Map<Class<? extends SPARQLResourceModel>, Field> cascadeDeleteClassesProperties = mapper.getCascadeDeleteClassesField();
            for (Map.Entry<Class<? extends SPARQLResourceModel>, Field> cascadeDeleteClassField : cascadeDeleteClassesProperties.entrySet()) {
                deleteByObjectRelation(cascadeDeleteClassField.getKey(), cascadeDeleteClassField.getValue(), uri);
            }
            T instance = loadByURI(objectClass, uri, null);

            UpdateBuilder delete = mapper.getDeleteBuilder(instance);

            executeDeleteQuery(delete);

            UpdateBuilder deleteRelations = mapper.getDeleteRelationsBuilder(uri);
            if (deleteRelations != null) {
                executeDeleteQuery(deleteRelations);
            }
            commitTransaction();
        } catch (Exception ex) {
            rollbackTransaction(ex);
            throw ex;
        }
    }

    public <T extends SPARQLResourceModel> void delete(Class<T> objectClass, List<URI> uris) throws Exception {
        if (uris.size() > 0) {
            try {
                startTransaction();
                SPARQLClassObjectMapper<T> mapper = SPARQLClassObjectMapper.getForClass(objectClass);

                Map<Class<? extends SPARQLResourceModel>, Field> cascadeDeleteClassesProperties = mapper.getCascadeDeleteClassesField();
                for (Map.Entry<Class<? extends SPARQLResourceModel>, Field> cascadeDeleteClassField : cascadeDeleteClassesProperties.entrySet()) {
                    deleteByObjectRelation(cascadeDeleteClassField.getKey(), cascadeDeleteClassField.getValue(), uris);
                }

                List<T> instances = loadListByURIs(objectClass, uris, null);
                UpdateBuilder delete = new UpdateBuilder();
                UpdateBuilder deleteRelations = new UpdateBuilder();
                boolean hasRelations = false;
                for (T instance : instances) {
                    mapper.addDeleteBuilder(instance, delete);
                    if (mapper.addDeleteRelationsBuilder(instance.getUri(), deleteRelations)) {
                        hasRelations = true;
                    }
                }

                executeDeleteQuery(delete);

                if (hasRelations) {
                    executeDeleteQuery(deleteRelations);
                }
            } catch (Exception ex) {
                rollbackTransaction(ex);
                throw ex;
            }
        }
    }

    protected <T extends SPARQLResourceModel> void deleteByObjectRelation(Class<T> objectClass, Field relationField, URI objectURI) throws Exception {
        List<URI> relatedObjectURIs = this.searchURIs(objectClass, null, (select) -> {
            SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
            select.addValueVar(sparqlObjectMapper.getFieldExprVar(relationField.getName()), SPARQLDeserializers.nodeURI(objectURI));
        });
        this.delete(objectClass, relatedObjectURIs);
    }

    protected <T extends SPARQLResourceModel> void deleteByObjectRelation(Class<T> objectClass, Field relationField, List<URI> objectURIs) throws Exception {
        List<URI> relatedObjectURIs = this.searchURIs(objectClass, null, (select) -> {
            SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);
            select.addValueVar(sparqlObjectMapper.getFieldExprVar(relationField.getName()), SPARQLDeserializers.nodeListURI(objectURIs).toArray());
        });
        this.delete(objectClass, relatedObjectURIs);
    }

    public boolean uriExists(URI uri) throws SPARQLException {
        AskBuilder askQuery = new AskBuilder();
        Var s = makeVar("s");
        Var p = makeVar("p");
        Var o = makeVar("o");
        Node nodeUri = SPARQLDeserializers.nodeURI(uri);
        askQuery.addWhere(nodeUri, p, o);
        WhereBuilder reverseWhere = new WhereBuilder();
        reverseWhere.addWhere(s, p, nodeUri);
        askQuery.addUnion(reverseWhere);

        return executeAskQuery(askQuery);
    }

    public <T extends SPARQLResourceModel> boolean uriExists(Class<T> objectClass, URI uri) throws Exception {
        if (uri == null) {
            return false;
        }
        return executeAskQuery(getUriExistsQuery(objectClass, uri));
    }

    /**
     * @param rdfType the {@link RDF#type} to check
     * @param uri the {@link URI} to check
     * @return true if uri exists in the TripleStore and if it's an instance of
     * rdfType
     */
    public boolean uriExists(URI rdfType, URI uri) throws SPARQLException {
        Var typeVar = makeVar("type");
        return executeAskQuery(new AskBuilder()
                .addWhere(SPARQLDeserializers.nodeURI(uri), RDF.type, typeVar)
                .addWhere(typeVar, Ontology.subClassAny, SPARQLDeserializers.nodeURI(rdfType))
        );
    }

    public <T extends SPARQLResourceModel> AskBuilder getUriExistsQuery(Class<T> objectClass, URI uri) throws SPARQLException {
        SPARQLClassObjectMapper<T> sparqlObjectMapper = SPARQLClassObjectMapper.getForClass(objectClass);

        AskBuilder askQuery = new AskBuilder();
        Node nodeUri = SPARQLDeserializers.nodeURI(uri);

        Var fieldType = sparqlObjectMapper.getTypeFieldVar();
        askQuery.addWhere(nodeUri, RDF.type, fieldType);

        Resource typeDef = sparqlObjectMapper.getRDFType();

        askQuery.addWhere(fieldType, Ontology.subClassAny, typeDef);
        return askQuery;
    }

    /**
     * Insert a list of quad (graph,subject,property,object),
     * (graph,subject,property,object_1) ... (graph,subject,property,object_k)
     *
     * and remove any old quad (graph,subject,property,?object) in the SPARQL
     * graph
     *
     * @param graph the graph in which the triple(s) are present
     * @param subject the triple subject URI
     * @param property the triple property
     * @param objects the list of object values
     */
    public void updateObjectRelations(Node graph, URI subject, Property property, List<?> objects) throws Exception {

        UpdateBuilder updateBuilder = new UpdateBuilder();

        Node subjectNode = SPARQLDeserializers.nodeURI(subject);
        Node objectVariable = makeVar("o");
        Node propertyNode = property.asNode();

        // build a triple for each new object to insert
        Iterator<Triple> insertTriplesIt = objects.stream().map(object -> {
            try {
                return new Triple(subjectNode, propertyNode, SPARQLDeserializers.getForClass(object.getClass()).getNode(object));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).iterator();

        updateBuilder.addDelete(graph, subjectNode, property, objectVariable);
        updateBuilder.addInsert(graph, insertTriplesIt);
        updateBuilder.addOptional(subjectNode, property, objectVariable);

        executeUpdateQuery(updateBuilder);
    }

    /**
     * Insert a list of quad (graph,subject,property,object),
     * (graph,subject_1,property,object) ... (graph,subject_k,property,object)
     *
     * and remove any old quad (graph,?subject,property,object) in the SPARQL
     * graph
     *
     * @param graph the graph in which the triple(s) are present
     * @param subjects the list of subject URIS
     * @param property the triple property
     * @param object the triple(s) object
     */
    public void updateSubjectRelations(Node graph, List<URI> subjects, Property property, Object object) throws Exception {

        UpdateBuilder updateBuilder = new UpdateBuilder();

        Node objectNode = SPARQLDeserializers.getForClass(object.getClass()).getNode(object);
        Node subjectVariable = makeVar("s");
        Node propertyNode = property.asNode();

        // build a triple for each new subject to insert
        Iterator<Triple> insertTriplesIt = subjects.stream().map(subject -> {
            try {
                return new Triple(SPARQLDeserializers.nodeURI(subject), propertyNode, objectNode);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).iterator();

        updateBuilder.addDelete(graph, subjectVariable, property, objectNode);
        updateBuilder.addInsert(graph, insertTriplesIt);
        updateBuilder.addOptional(subjectVariable, property, objectNode);

        executeUpdateQuery(updateBuilder);
    }

    public Map<String, String> getOtherTranslations(Node graph, URI resourceURI, Property labelProperty, boolean reverseRelation, String lang) throws Exception {
        Map<String, String> translations = new HashMap<>();

        SelectBuilder select = new SelectBuilder();

        Var valueVar = makeVar("value");
        Var langVar = makeVar("lang");

        select.addVar(valueVar);
        select.addVar(SPARQLQueryHelper.getExprFactory().lang(valueVar), langVar);

        if (reverseRelation) {
            select.addWhere(valueVar, labelProperty, SPARQLDeserializers.nodeURI(resourceURI));
        } else {
            select.addWhere(SPARQLDeserializers.nodeURI(resourceURI), labelProperty, valueVar);
        }

        executeSelectQuery(select, (SPARQLResult result) -> {
            String value = result.getStringValue("value");
            String resultLang = result.getStringValue("lang");
            if (!resultLang.equals(lang)) {
                translations.put(resultLang, value);
            }
        });

        return translations;
    }

    public Map<String, String> getTranslations(Node graph, URI resourceURI, Property labelProperty, boolean reverseRelation) throws Exception {
        return getOtherTranslations(graph, resourceURI, labelProperty, reverseRelation, null);
    }

    @Override
    public void disableSHACL() throws SPARQLException {
        LOGGER.debug("DISABLE SHACL Validation");
        connection.disableSHACL();
    }

    @Override
    public void enableSHACL() throws SPARQLException {
        LOGGER.debug("ENABLE SHACL Validation");
        SPARQLClassObjectMapper.initialize();
        connection.enableSHACL();
    }

    @Deprecated
    public RepositoryConnection getRepositoryConnection() {
        RDF4JConnection cnt = (RDF4JConnection) this.connection;
        return cnt.getRepositoryConnectionImpl();
    }
}

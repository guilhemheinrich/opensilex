//******************************************************************************
//                          ExperimentDAO.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRAE 2020
// Contact: vincent.migot@inrae.fr, anne.tireau@inrae.fr, pascal.neveu@inrae.fr
//******************************************************************************
package org.opensilex.core.experiment.dal;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.arq.querybuilder.*;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.P_Seq;
import org.apache.jena.sparql.path.P_ZeroOrMore1;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.opensilex.core.exception.DuplicateNameException;
import org.opensilex.core.ontology.Oeso;
import org.opensilex.core.organisation.dal.InfrastructureDAO;
import org.opensilex.core.organisation.dal.InfrastructureFacilityModel;
import org.opensilex.core.organisation.dal.InfrastructureModel;
import org.opensilex.nosql.mongodb.MongoDBService;
import org.opensilex.security.authentication.ForbiddenURIAccessException;
import org.opensilex.security.authentication.NotFoundURIException;
import org.opensilex.security.authentication.SecurityOntology;
import org.opensilex.security.user.dal.UserModel;
import org.opensilex.sparql.deserializer.SPARQLDeserializers;
import org.opensilex.sparql.model.SPARQLResourceModel;
import org.opensilex.sparql.service.SPARQLQueryHelper;
import org.opensilex.sparql.service.SPARQLService;
import org.opensilex.sparql.utils.Ontology;
import org.opensilex.utils.ListWithPagination;
import org.opensilex.utils.OrderBy;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.opensilex.sparql.service.SPARQLQueryHelper.makeVar;

/**
 * @author Vincent MIGOT
 * @author Renaud COLIN
 */
public class ExperimentDAO {

    protected final SPARQLService sparql;
    protected final MongoDBService nosql;

    public ExperimentDAO(SPARQLService sparql, MongoDBService nosql) {
        this.sparql = sparql;
        this.nosql = nosql;
    }

    public ExperimentModel create(ExperimentModel instance) throws Exception {
        sparql.create(instance);
        return instance;
    }

    public ExperimentModel update(ExperimentModel instance, UserModel user) throws Exception {
        validateExperimentAccess(instance.getUri(), user);
        sparql.update(instance);
        return instance;
    }

    public void updateWithVariables(URI xpUri, List<URI> variablesUris, UserModel user) throws Exception {
        validateExperimentAccess(xpUri, user);
        sparql.updateObjectRelations(SPARQLDeserializers.nodeURI(xpUri), xpUri, Oeso.measures, variablesUris);
    }

    public void updateWithFactors(URI xpUri, List<URI> factorsUris, UserModel user) throws Exception {
        validateExperimentAccess(xpUri, user);
        sparql.updateSubjectRelations(SPARQLDeserializers.nodeURI(xpUri), factorsUris, Oeso.studyEffectOf, xpUri);
    }

    public void delete(URI xpUri, UserModel user) throws Exception {
        validateExperimentAccess(xpUri, user);
        sparql.delete(ExperimentModel.class, xpUri);
    }

    public void delete(List<URI> xpUris, UserModel user) throws Exception {
        for (URI xpUri : xpUris) {
            validateExperimentAccess(xpUri, user);
        }
        sparql.delete(ExperimentModel.class, xpUris);
    }

    public ExperimentModel get(URI xpUri, UserModel user) throws Exception {
        validateExperimentAccess(xpUri, user);
        ExperimentModel xp = sparql.getByURI(ExperimentModel.class, xpUri, user.getLanguage());
        return xp;
    }

    @Deprecated
    public ListWithPagination<ExperimentModel> search(
            URI uri,
            String name,
            Integer campaign,
            Boolean isEnded,
            List<URI> variables, List<OrderBy> orderByList, int page, int pageSize) throws Exception {

        ListWithPagination<ExperimentModel> xps = sparql.searchWithPagination(
                ExperimentModel.class,
                null,
                (SelectBuilder select) -> {
                    appendUriRegexFilter(select, uri);
                    appendRegexLabelFilter(select, name);
                    appendIsActiveFilter(select, isEnded);
                    appendVariablesListFilter(select, variables);
                },
                orderByList,
                page,
                pageSize
        );
        return xps;
    }

    @Deprecated
    public ListWithPagination<ExperimentModel> search(URI uri,
            Integer campaign,
            String name,
            List<URI> species,
            String startDate, String endDate,
            Boolean isEnded,
            List<URI> projects,
            Boolean isPublic,
            List<URI> groups, boolean admin,
            List<OrderBy> orderByList, int page, int pageSize) throws Exception {

        ListWithPagination<ExperimentModel> xps = sparql.searchWithPagination(
                ExperimentModel.class,
                null,
                (SelectBuilder select) -> {
                    appendUriRegexFilter(select, uri);
                    appendRegexLabelFilter(select, name);
                    appendSpeciesFilter(select, species);
                    appendGroupsListFilters(select, admin, isPublic, groups);
                    appendProjectListFilter(select, projects);
                },
                orderByList,
                page,
                pageSize
        );

        return xps;

    }

    public ListWithPagination<ExperimentModel> search(
            Integer year,
            String name,
            List<URI> species,
            List<URI> factorCategories,
            Boolean isEnded,
            List<URI> projects,
            Boolean isPublic,
            UserModel user,
            List<OrderBy> orderByList, int page, int pageSize) throws Exception {
        LocalDate startDate;
        LocalDate endDate;
        if (year != null) {
            String yearString = Integer.toString(year);
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        } else {
            startDate = null;
            endDate = null;
        }

        ListWithPagination<ExperimentModel> xps = sparql.searchWithPagination(
                ExperimentModel.class,
                null,
                (SelectBuilder select) -> {
                    appendRegexLabelFilter(select, name);
                    appendSpeciesFilter(select, species);
                    appendFactorFilter(select, factorCategories);
                    appendIsActiveFilter(select, isEnded);
                    appendDateFilter(select, startDate, endDate);
                    appendProjectListFilter(select, projects);
                    appendUserExperimentsFilter(select, user);
                    appendPublicFilter(select, isPublic);
                },
                orderByList,
                page,
                pageSize
        );

        return xps;

    }

    private void appendSpeciesFilter(SelectBuilder select, List<URI> species) throws Exception {
        if (species != null && !species.isEmpty()) {
            addWhere(select, ExperimentModel.URI_FIELD, Oeso.hasSpecies, ExperimentModel.SPECIES_FIELD);
            select.addFilter(SPARQLQueryHelper.inURIFilter(ExperimentModel.SPECIES_FIELD, species));
        }
    }

    private void appendFactorFilter(SelectBuilder select, List<URI> factorCategories) throws Exception {
        if (factorCategories != null && !factorCategories.isEmpty()) {
            Var factors = makeVar(ExperimentModel.FACTORS_FIELD);
            Var xpUri = makeVar(ExperimentModel.URI_FIELD);
            Var category = makeVar(ExperimentModel.FACTORS_CATEGORIES_FIELD);
            Var categories = makeVar("_categories");

            select.addWhere(factors, Oeso.studiedEffectIn,xpUri );
            select.addOptional(xpUri, Oeso.studyEffectOf, factors);
            select.addWhere(categories, Ontology.subClassAny, category);
            select.addWhere(factors, Oeso.hasCategory, categories);
            select.addFilter(SPARQLQueryHelper.inURIFilter(category, factorCategories));
        }
    }

    private void appendRegexLabelFilter(SelectBuilder select, String name) {
        if (!StringUtils.isEmpty(name)) {
            select.addFilter(SPARQLQueryHelper.regexFilter(ExperimentModel.NAME_FIELD, name));
        }
    }

    private void appendUriRegexFilter(SelectBuilder select, URI uri) {
        if (uri != null) {
            Var uriVar = makeVar(SPARQLResourceModel.URI_FIELD);
            Expr strUriExpr = SPARQLQueryHelper.getExprFactory().str(uriVar);
            select.addFilter(SPARQLQueryHelper.regexFilter(strUriExpr, uri.toString(), null));
        }
    }

    private void appendDateFilter(SelectBuilder select, LocalDate startDate, LocalDate endDate) throws Exception {

        if (startDate != null && endDate != null) {

            Expr dateRangeExpr = SPARQLQueryHelper.intervalDateRange(ExperimentModel.START_DATE_FIELD, startDate, ExperimentModel.END_DATE_FIELD, endDate);
            select.addFilter(dateRangeExpr);
        } else {
            if (startDate != null || endDate != null) {
                Expr dateRangeExpr = SPARQLQueryHelper.dateRange(ExperimentModel.START_DATE_FIELD, startDate, ExperimentModel.END_DATE_FIELD, endDate);
                select.addFilter(dateRangeExpr);

            }

        }

    }

    private void appendIsActiveFilter(SelectBuilder select, Boolean ended) throws Exception {
        if (ended != null) {
            Node endDateVar = NodeFactory.createVariable(ExperimentModel.END_DATE_FIELD);
            Node currentDateNode = SPARQLDeserializers.getForClass(LocalDate.class).getNode(LocalDate.now());

            // an experiment is ended if the end date is less than the the current date
            if (ended) {
                select.addFilter(SPARQLQueryHelper.getExprFactory().le(endDateVar, currentDateNode));
            } else {
                ExprFactory exprFactory = SPARQLQueryHelper.getExprFactory();
                Expr noEndDateFilter = exprFactory.not(exprFactory.bound(endDateVar));
                select.addFilter(exprFactory.or(noEndDateFilter, exprFactory.gt(endDateVar, currentDateNode)));
            }
        }

    }

    private void appendProjectListFilter(SelectBuilder select, List<URI> projects) throws Exception {

        if (projects != null && !projects.isEmpty()) {
            addWhere(select, ExperimentModel.URI_FIELD, Oeso.hasProject, ExperimentModel.PROJECT_URI_FIELD);
            select.addFilter(SPARQLQueryHelper.inURIFilter(ExperimentModel.PROJECT_URI_FIELD, projects));
        }
    }

    private static void addWhere(SelectBuilder select, String subjectVar, Property property, String objectVar) {
        select.getWhereHandler().getClause().addTriplePattern(new Triple(makeVar(subjectVar), property.asNode(), makeVar(objectVar)));
    }

    private void appendGroupsListFilters(SelectBuilder select, boolean admin, Boolean isPublic, List<URI> groups) {

        if (admin) {
            // add no filter on groups for the admin
            return;
        }
        Var groupVar = makeVar(ExperimentModel.GROUP_FIELD);
        Triple groupTriple = new Triple(makeVar(ExperimentModel.URI_FIELD), SecurityOntology.hasGroup.asNode(), groupVar);

        if (CollectionUtils.isEmpty(groups) || (isPublic != null && isPublic)) {
            // get experiment without any group
            select.addFilter(SPARQLQueryHelper.getExprFactory().notexists(new WhereBuilder().addWhere(groupTriple)));
        } else {
            ExprFactory exprFactory = SPARQLQueryHelper.getExprFactory();

            // get experiment with no group specified or in the given list
            ElementGroup rootFilteringElem = new ElementGroup();
            ElementGroup optionals = new ElementGroup();
            optionals.addTriplePattern(groupTriple);

            Expr boundExpr = exprFactory.not(exprFactory.bound(groupVar));
            Expr groupInUrisExpr = exprFactory.in(groupVar, groups.stream()
                    .map(uri -> NodeFactory.createURI(SPARQLDeserializers.getExpandedURI(uri.toString())))
                    .toArray());

            rootFilteringElem.addElement(new ElementOptional(optionals));
            rootFilteringElem.addElementFilter(new ElementFilter(SPARQLQueryHelper.or(boundExpr, groupInUrisExpr)));
            select.getWhereHandler().getClause().addElement(rootFilteringElem);
        }
    }

    private void appendVariablesListFilter(SelectBuilder select, List<URI> variables) throws Exception {
        if (variables != null && !variables.isEmpty()) {
            addWhere(select, ExperimentModel.URI_FIELD, Oeso.measures, ExperimentModel.VARIABLES_FIELD);
            SPARQLQueryHelper.addWhereValues(select, ExperimentModel.VARIABLES_FIELD, variables);
        }
    }

    private void appendPublicFilter(SelectBuilder select, Boolean isPublic) throws Exception {
        if (isPublic != null) {
            select.addFilter(SPARQLQueryHelper.eq(ExperimentModel.IS_PUBLIC_FIELD, isPublic));
        }
    }

    public Set<URI> getUserExperiments(UserModel user) throws Exception {
        String lang = user.getLanguage();
        Set<URI> userExperiments = new HashSet<>();
        List<URI> xps = sparql.searchURIs(ExperimentModel.class, lang, (SelectBuilder select) -> {
            appendUserExperimentsFilter(select, user);
        });

        userExperiments.addAll(xps);

        return userExperiments;
    }
    
    public static void appendUserExperimentsFilter(SelectBuilder select, UserModel user) throws Exception {
        if (user == null || user.isAdmin()) {
            return;
        }

        Var uriVar = makeVar(ExperimentModel.URI_FIELD);
        Var userProfileVar = makeVar("_userProfile");
        Var userVar = makeVar("_user");
        Var groupVar = makeVar(ExperimentModel.GROUP_FIELD);

        Node userNodeURI = SPARQLDeserializers.nodeURI(user.getUri());

        ElementGroup optionals = new ElementGroup();
        optionals.addTriplePattern(new Triple(uriVar, SecurityOntology.hasGroup.asNode(), groupVar));
        optionals.addTriplePattern(new Triple(groupVar, SecurityOntology.hasUserProfile.asNode(), userProfileVar));
        optionals.addTriplePattern(new Triple(userProfileVar, SecurityOntology.hasUser.asNode(), userVar));
        select.getWhereHandler().getClause().addElement(new ElementOptional(optionals));
        Expr inGroup = SPARQLQueryHelper.eq(userVar, userNodeURI);

        Var scientificSupervisorVar = makeVar(ExperimentModel.SCIENTIFIC_SUPERVISOR_FIELD);
        select.addOptional(new Triple(uriVar, Oeso.hasScientificSupervisor.asNode(), scientificSupervisorVar));
        Expr hasScientificSupervisor = SPARQLQueryHelper.eq(scientificSupervisorVar, userNodeURI);

        Var technicalSupervisorVar = makeVar(ExperimentModel.TECHNICAL_SUPERVISOR_FIELD);
        select.addOptional(new Triple(uriVar, Oeso.hasTechnicalSupervisor.asNode(), technicalSupervisorVar));
        Expr hasTechnicalSupervisor = SPARQLQueryHelper.eq(technicalSupervisorVar, userNodeURI);

        Var isPublicVar = makeVar(ExperimentModel.IS_PUBLIC_FIELD);
        select.addOptional(new Triple(uriVar, Oeso.isPublic.asNode(), isPublicVar));
        Expr isPublic = SPARQLQueryHelper.eq(isPublicVar, Boolean.TRUE);

        Var creatorVar = makeVar(ExperimentModel.CREATOR_FIELD);
        select.addOptional(new Triple(uriVar, DCTerms.creator.asNode(), creatorVar));
        Expr isCreator = SPARQLQueryHelper.eq(creatorVar, userNodeURI);

        select.addFilter(SPARQLQueryHelper.or(
                inGroup,
                hasScientificSupervisor,
                hasTechnicalSupervisor,
                isPublic,
                isCreator
        ));
    }

    public void validateExperimentAccess(URI experimentURI, UserModel user) throws Exception {

        if (!sparql.uriExists(ExperimentModel.class, experimentURI)) {
            throw new NotFoundURIException("Experiment URI not found: ", experimentURI);
        }

        if (user.isAdmin()) {
            return;
        }

        AskBuilder ask = sparql.getUriExistsQuery(ExperimentModel.class, experimentURI);

        Node uriVar = SPARQLDeserializers.nodeURI(experimentURI);
        Var userProfileVar = makeVar("_userProfile");
        Var userVar = makeVar("_user");
        Var groupVar = makeVar(ExperimentModel.GROUP_FIELD);

        Node userNodeURI = SPARQLDeserializers.nodeURI(user.getUri());

        ElementGroup optionals = new ElementGroup();
        optionals.addTriplePattern(new Triple(uriVar, SecurityOntology.hasGroup.asNode(), groupVar));
        optionals.addTriplePattern(new Triple(groupVar, SecurityOntology.hasUserProfile.asNode(), userProfileVar));
        optionals.addTriplePattern(new Triple(userProfileVar, SecurityOntology.hasUser.asNode(), userVar));
        ask.getWhereHandler().getClause().addElement(new ElementOptional(optionals));
        Expr inGroup = SPARQLQueryHelper.eq(userVar, userNodeURI);

        Var creatorVar = makeVar(ExperimentModel.CREATOR_FIELD);
        ask.addOptional(new Triple(uriVar, DCTerms.creator.asNode(), creatorVar));
        Expr isCreator = SPARQLQueryHelper.eq(creatorVar, userNodeURI);

        Var scientificSupervisorVar = makeVar(ExperimentModel.SCIENTIFIC_SUPERVISOR_FIELD);
        ask.addOptional(new Triple(uriVar, Oeso.hasScientificSupervisor.asNode(), scientificSupervisorVar));
        Expr hasScientificSupervisor = SPARQLQueryHelper.eq(scientificSupervisorVar, userNodeURI);

        Var technicalSupervisorVar = makeVar(ExperimentModel.TECHNICAL_SUPERVISOR_FIELD);
        ask.addOptional(new Triple(uriVar, Oeso.hasTechnicalSupervisor.asNode(), technicalSupervisorVar));
        Expr hasTechnicalSupervisor = SPARQLQueryHelper.eq(technicalSupervisorVar, userNodeURI);

        Var isPublicVar = makeVar(ExperimentModel.IS_PUBLIC_FIELD);
        ask.addOptional(new Triple(uriVar, Oeso.isPublic.asNode(), isPublicVar));
        Expr isPublic = SPARQLQueryHelper.eq(isPublicVar, Boolean.TRUE);

        ask.addFilter(
                SPARQLQueryHelper.or(
                        isCreator,
                        inGroup,
                        hasScientificSupervisor,
                        hasTechnicalSupervisor,
                        isPublic
                )
        );

        if (!sparql.executeAskQuery(ask)) {
            throw new ForbiddenURIAccessException(experimentURI);
        }
    }

    public List<InfrastructureFacilityModel> getAvailableFacilities(URI xpUri, UserModel user) throws Exception {
        validateExperimentAccess(xpUri, user);

        ExperimentModel xp = sparql.getByURI(ExperimentModel.class, xpUri, user.getLanguage());

        Collection<URI> organizationUriFilter = xp.getInfrastructures()
                .stream().map(SPARQLResourceModel::getUri)
                .collect(Collectors.toSet());

        InfrastructureDAO organizationDAO = new InfrastructureDAO(sparql, nosql);

        if (CollectionUtils.isEmpty(organizationUriFilter)) {
            return organizationDAO.getAllFacilities(user);
        } else {
            return organizationDAO.getAllFacilities(user, organizationUriFilter);
        }
    }

    public List<ExperimentModel> getByURIs(List<URI> uris, UserModel currentUser) throws Exception {
        return sparql.getListByURIs(ExperimentModel.class, uris, currentUser.getLanguage());
    }

    public ExperimentModel getByName(String name) throws Exception {
        //pageSize=2 in order to detect duplicated names
        ListWithPagination<ExperimentModel> results = sparql.searchWithPagination(
            ExperimentModel.class,
            null,
            (SelectBuilder select) -> {
                select.addFilter(SPARQLQueryHelper.eq(ExperimentModel.NAME_FIELD, name));
            },
            null,
            0,
            2
        );
        
        if (results.getList().isEmpty()) {
            return null;
        }
        
        if (results.getList().size() > 1) {
            throw new DuplicateNameException(name);
        }

        return results.getList().get(0);
    }

    /**
     * Update the experiment species from the germplasms of their scientific objects. The following request is used
     * to perform the update :
     *
     * <pre>
     * delete {
     *         graph <.../set/experiments> {
     *                 <__experimentUri__> vocabulary:hasSpecies ?oldSpecies.
     *         }
     * } insert {
     *         graph <.../set/experiments> {
     *                 <__experimentUri__> vocabulary:hasSpecies ?newSpecies.
     *         }
     * } where {
     *         optional {
     *                 graph <.../set/experiments> {
     *                         <__experimentUri__> vocabulary:hasSpecies ?oldSpecies.
     *                 }
     *         } optional {
     *                 graph <__experimentUri__> {
     *                         ?scientificObject a ?rdfType.
     *                         ?scientificObject vocabulary:hasGermplasm ?germplasm.
     *                 }
     *                         ?rdfType rdfs:subClassOf* vocabulary:ScientificObject.
     *                 {
     *                         ?germplasm a/rdfs:subClassOf* vocabulary:Species.
     *                         bind(?germplasm as ?newSpecies)
     *                 } union {
     *                         ?germplasm vocabulary:fromSpecies ?newSpecies.
     *                 }
     *         }
     * }
     * </pre>
     *
     * Note : I perform the operation within a single request. The downside is that the "where" section has two independent
     * optionals, the first corresponding to the "delete" statement, and the second corresponding to the "insert".
     * I guess this could cause performance issues, as a cartesian product is done. Maybe it would be better to do it in
     * two requests, even if this mean having more communication latency, but I'm not sure...
     * - Valentin Rigolle
     *
     * @param experimentUri
     * @throws Exception
     */
    public void updateExperimentSpeciesFromScientificObjects(URI experimentUri) throws Exception {
        // Vars
        Var oldSpeciesVar = makeVar("oldSpecies");
        Var newSpeciesVar = makeVar("newSpecies");
        Var scientificObjectVar = makeVar("scientificObject");
        Var germplasmVar = makeVar("germplasm");
        Var rdfTypeVar = makeVar("rdfType");

        // Uris
        Node experimentGraph = SPARQLDeserializers.nodeURI(sparql.getDefaultGraphURI(ExperimentModel.class));
        Node experimentUriNode = SPARQLDeserializers.nodeURI(experimentUri);

        // Useful paths to reuse
        Path subClassOf = new P_ZeroOrMore1(new P_Link(RDFS.subClassOf.asNode()));
        Path aSubClassOf = new P_Seq(new P_Link(RDF.type.asNode()), subClassOf);

        // Update statement building
        UpdateBuilder update = new UpdateBuilder();
        update.addDelete(experimentGraph, experimentUriNode, Oeso.hasSpecies.asNode(), oldSpeciesVar);
        update.addInsert(experimentGraph, experimentUriNode, Oeso.hasSpecies.asNode(), newSpeciesVar);

        // Where statement building
        WhereBuilder where = new WhereBuilder();

        // Old species
        WhereBuilder whereOldSpecies = new WhereBuilder();
        whereOldSpecies.addGraph(experimentGraph, experimentUriNode, Oeso.hasSpecies.asNode(), oldSpeciesVar);
        where.addOptional(whereOldSpecies);

        // New species
        WhereBuilder whereNewSpecies = new WhereBuilder();

        // Selection of the scientific object and its germplasm
        WhereBuilder whereInExperiment = new WhereBuilder();
        whereInExperiment.addWhere(scientificObjectVar, RDF.type.asNode(), rdfTypeVar);
        whereInExperiment.addWhere(scientificObjectVar, Oeso.hasGermplasm.asNode(), germplasmVar);
        whereNewSpecies.addGraph(experimentUriNode, whereInExperiment);
        whereNewSpecies.addWhere(rdfTypeVar, subClassOf, Oeso.ScientificObject.asNode());

        // The two cases for the species
        WhereBuilder whereIsSpecies = new WhereBuilder();
        WhereBuilder whereFromSpecies = new WhereBuilder();

        // First case : the germplasm is a species
        whereIsSpecies.addWhere(germplasmVar, aSubClassOf, Oeso.Species.asNode());
        whereIsSpecies.addBind(new ExprVar(germplasmVar), newSpeciesVar);

        // Second case : the germplasm is derived from a species
        whereFromSpecies.addWhere(germplasmVar, Oeso.fromSpecies.asNode(), newSpeciesVar);

        // Union
        whereNewSpecies.addWhere(
                whereIsSpecies.addUnion(whereFromSpecies)
        );

        where.addOptional(whereNewSpecies);

        update.addWhere(where);

        sparql.executeUpdateQuery(update);
    }
}

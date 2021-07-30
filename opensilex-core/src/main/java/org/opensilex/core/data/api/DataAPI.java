//******************************************************************************
//                          DataAPI.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRAE 2020
// Contact: anne.tireau@inrae.fr, pascal.neveu@inrae.fr
//******************************************************************************
package org.opensilex.core.data.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoCommandException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.result.DeleteResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.bson.Document;
import org.opensilex.core.data.dal.DataDAO;
import org.opensilex.core.data.dal.DataModel;
import org.opensilex.core.data.utils.DataValidateUtils;
import org.opensilex.core.device.api.DeviceAPI;
import org.opensilex.core.exception.DateMappingExceptionResponse;
import org.opensilex.core.provenance.dal.ProvenanceDAO;
import org.opensilex.core.variable.dal.VariableModel;
import org.opensilex.fs.service.FileStorageService;
import org.opensilex.core.exception.DataTypeException;
import org.opensilex.core.exception.DateValidationException;
import org.opensilex.core.exception.NoVariableDataTypeException;
import org.opensilex.core.exception.UnableToParseDateException;
import org.opensilex.core.experiment.api.ExperimentAPI;
import org.opensilex.core.experiment.dal.ExperimentModel;
import org.opensilex.core.provenance.api.ProvenanceGetDTO;
import org.opensilex.core.provenance.dal.ProvenanceModel;
import org.opensilex.core.scientificObject.dal.ScientificObjectModel;
import org.opensilex.core.variable.dal.VariableDAO;
import org.opensilex.nosql.exceptions.NoSQLInvalidURIException;
import org.opensilex.nosql.exceptions.NoSQLInvalidUriListException;
import org.opensilex.nosql.exceptions.NoSQLTooLargeSetException;
import org.opensilex.nosql.mongodb.MongoDBService;
import org.opensilex.security.authentication.ApiCredential;
import org.opensilex.security.authentication.ApiCredentialGroup;
import org.opensilex.security.authentication.ApiProtected;
import org.opensilex.security.authentication.NotFoundURIException;
import org.opensilex.security.authentication.injection.CurrentUser;
import org.opensilex.security.user.dal.UserModel;
import org.opensilex.server.exceptions.NotFoundException;
import org.opensilex.server.response.ErrorDTO;
import org.opensilex.server.response.ErrorResponse;
import org.opensilex.server.response.ObjectUriResponse;
import org.opensilex.server.response.PaginatedListResponse;
import org.opensilex.server.response.SingleObjectResponse;
import org.opensilex.server.rest.serialization.ObjectMapperContextResolver;
import org.opensilex.sparql.response.NamedResourceDTO;
import org.opensilex.sparql.service.SPARQLService;
import org.opensilex.utils.ListWithPagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opensilex.utils.OrderBy;

/**
 *
 * @author sammy
 */
@Api(DataAPI.CREDENTIAL_DATA_GROUP_ID)
@Path("/core/data")
@ApiCredentialGroup(
        groupId = DataAPI.CREDENTIAL_DATA_GROUP_ID,
        groupLabelKey = DataAPI.CREDENTIAL_DATA_GROUP_LABEL_KEY
)
public class DataAPI {
    protected final static Logger LOGGER = LoggerFactory.getLogger(DataAPI.class);

    public static final String CREDENTIAL_DATA_GROUP_ID = "Data";
    public static final String CREDENTIAL_DATA_GROUP_LABEL_KEY = "credential-groups.data";

    public static final String DATA_EXAMPLE_URI = "http://opensilex.dev/id/data/1598857852858";
    public static final String DATA_EXAMPLE_OBJECTURI = "http://opensilex.dev/opensilex/2020/o20000345";
    public static final String DATA_EXAMPLE_VARIABLEURI = "http://opensilex.dev/variable#variable.2020-08-21_11-21-23entity6_method6_quality6_unit6";
    public static final String DATA_EXAMPLE_CONFIDENCE = "0.5";
    public static final String DATA_EXAMPLE_CONFIDENCE_MAX = "1";
    public static final String DATA_EXAMPLE_PROVENANCEURI = "http://opensilex.dev/provenance/1598001689415";
    public static final String DATA_EXAMPLE_VALUE = "8.6";
    public static final String DATA_EXAMPLE_MINIMAL_DATE  = "2020-08-21T00:00:00+01:00";
    public static final String DATA_EXAMPLE_MAXIMAL_DATE = "2020-09-21T00:00:00+01:00";
    public static final String DATA_EXAMPLE_TIMEZONE = "Europe/Paris";
    public static final String DATA_EXAMPLE_METADATA = "{ \"LabelView\" : \"side90\",\n" +
                                                        "\"paramA\" : \"90\"}";;
    
    public static final String CREDENTIAL_DATA_MODIFICATION_ID = "data-modification";
    public static final String CREDENTIAL_DATA_MODIFICATION_LABEL_KEY = "credential.data.modification";

    public static final String CREDENTIAL_DATA_DELETE_ID = "data-delete";
    public static final String CREDENTIAL_DATA_DELETE_LABEL_KEY = "credential.data.delete";
    public static final int SIZE_MAX = 50000;
    


    @Inject
    private MongoDBService nosql;
    
    @Inject
    private SPARQLService sparql;
    
    @Inject
    private FileStorageService fs;

    @CurrentUser
    UserModel user;

    @POST
    @ApiProtected
    @ApiOperation("Add data")
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Add data", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Bad user request", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})
   public Response addListData(
            @ApiParam("Data description") @Valid List<DataCreationDTO> dtoList
    ) throws Exception {            
        DataDAO dao = new DataDAO(nosql, sparql, fs);        

        List<DataModel> dataList = new ArrayList();
        try {
            if (dtoList.size()> SIZE_MAX) {
                throw new NoSQLTooLargeSetException(SIZE_MAX, dtoList.size());
            }
            
            for(DataCreationDTO dto : dtoList ){            
                DataModel model = dto.newModel();
                dataList.add(model);
            }

            validData(dataList);

            dataList = (List<DataModel>) dao.createAll(dataList);
            List<URI> createdResources = new ArrayList<>();
            for (DataModel data : dataList){
                createdResources.add(data.getUri());
            }
            return new ObjectUriResponse(Response.Status.CREATED, createdResources).getResponse();

        } catch (NoSQLTooLargeSetException ex) {
            return new ErrorResponse(Response.Status.BAD_REQUEST, "DATA_SIZE_LIMIT",
                ex.getMessage()).getResponse();

        } catch (MongoBulkWriteException duplicateError) {
            List<DataCreationDTO> datas = new ArrayList();
            List<BulkWriteError> errors = duplicateError.getWriteErrors();
            for (int i=0 ; i < errors.size() ; i++) {
                int index = errors.get(i).getIndex();
                datas.add(dtoList.get(index));
            }                    
            ObjectMapper mapper = ObjectMapperContextResolver.getObjectMapper();
            String json = mapper.writeValueAsString(datas);

            return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", json)
            .getResponse();

        } catch (MongoCommandException e) {
            return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", e.getErrorMessage())
            .getResponse();
        } catch (DateValidationException e) {
            return new DateMappingExceptionResponse().toResponse(e);
        } catch (DataTypeException | NoVariableDataTypeException e) {
            return new ErrorResponse(Response.Status.BAD_REQUEST, "DATA_TYPE_ERROR", e.getMessage())
            .getResponse();
        } catch (NoSQLInvalidUriListException e) {
            throw new NotFoundException(e.getMessage());
        }        
    }
    
    @GET
    @Path("{uri}")
    @ApiOperation("Get data")
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Data retrieved", response = DataGetDTO.class),
        @ApiResponse(code = 404, message = "Data not found", response = ErrorResponse.class)})
    public Response getData(
            @ApiParam(value = "Data URI", /*example = "platform-data:irrigation",*/ required = true) @PathParam("uri") @NotNull URI uri)
            throws Exception {
        DataDAO dao = new DataDAO(nosql,sparql, fs);
        
        try {
            DataModel model = dao.get(uri);
            return new SingleObjectResponse<>(DataGetDTO.getDtoFromModel(model)).getResponse();
        } catch (NoSQLInvalidURIException e) {
            throw new NotFoundURIException("Invalid or unknown data URI ", uri);
        }
        
    }
    
    @GET
    @ApiOperation("Search data")
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Return data list", response = DataGetDTO.class, responseContainer = "List")
    })
    public Response searchDataList(
            @ApiParam(value = "Search by minimal date", example = DATA_EXAMPLE_MINIMAL_DATE) @QueryParam("start_date") String startDate,
            @ApiParam(value = "Search by maximal date", example = DATA_EXAMPLE_MAXIMAL_DATE) @QueryParam("end_date") String endDate,
            @ApiParam(value = "Precise the timezone corresponding to the given dates", example = DATA_EXAMPLE_TIMEZONE) @QueryParam("timezone") String timezone,
            @ApiParam(value = "Search by experiment uris", example = ExperimentAPI.EXPERIMENT_EXAMPLE_URI) @QueryParam("experiment") List<URI> experiments,
            @ApiParam(value = "Search by objects uris", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("scientific_objects") List<URI> objects,
            @ApiParam(value = "Search by variables uris", example = DATA_EXAMPLE_VARIABLEURI) @QueryParam("variables") List<URI> variables,
            @ApiParam(value = "Search by devices uris", example = DeviceAPI.DEVICE_EXAMPLE_URI) @QueryParam("devices") List<URI> devices,
            @ApiParam(value = "Search by minimal confidence index", example = DATA_EXAMPLE_CONFIDENCE) @QueryParam("min_confidence") @Min(0) @Max(1) Float confidenceMin,
            @ApiParam(value = "Search by maximal confidence index", example = DATA_EXAMPLE_CONFIDENCE_MAX) @QueryParam("max_confidence") @Min(0) @Max(1) Float confidenceMax,
            @ApiParam(value = "Search by provenances", example = DATA_EXAMPLE_PROVENANCEURI) @QueryParam("provenances") List<URI> provenances,
            @ApiParam(value = "Search by metadata", example = DATA_EXAMPLE_METADATA) @QueryParam("metadata") String metadata,
            @ApiParam(value = "List of fields to sort as an array of fieldName=asc|desc", example = "date=desc") @QueryParam("order_by") List<OrderBy> orderByList,
            @ApiParam(value = "Page number", example = "0") @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @ApiParam(value = "Page size", example = "20") @QueryParam("page_size") @DefaultValue("20") @Min(0) int pageSize
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        
        //convert dates
        Instant startInstant = null;
        Instant endInstant = null;
        
        if (startDate != null) {
            try  {
                startInstant = DataValidateUtils.getDateInstant(startDate, timezone, Boolean.FALSE);
            } catch (DateValidationException e) {
                return new DateMappingExceptionResponse().toResponse(e);
            }          
        }
        
        if (endDate != null) {
            try {
                endInstant = DataValidateUtils.getDateInstant(endDate, timezone, Boolean.TRUE);
            } catch (DateValidationException e) {
                return new DateMappingExceptionResponse().toResponse(e);
            }
        }
                    
        Document metadataFilter = null;
        if (metadata != null) {
            try {
                metadataFilter = Document.parse(metadata);
            } catch (Exception e) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "METADATA_PARAM_ERROR", "unable to parse metadata")
            .getResponse();
            }
        }        
        
        ListWithPagination<DataModel> resultList = dao.search(
                user,
                experiments,
                objects,
                variables,
                provenances,
                devices,
                startInstant,
                endInstant,
                confidenceMin,
                confidenceMax,
                metadataFilter,
                orderByList,
                page,
                pageSize
        );
               
        ListWithPagination<DataGetDTO> resultDTOList = resultList.convert(DataGetDTO.class, DataGetDTO::getDtoFromModel);

        return new PaginatedListResponse<>(resultDTOList).getResponse();
    }
    
    @GET
    @Path("/count")
    @ApiOperation("Count data")
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Return the number of data ", response = Integer.class)
    })
    public Response countData(
            @ApiParam(value = "Search by minimal date", example = DATA_EXAMPLE_MINIMAL_DATE) @QueryParam("start_date") String startDate,
            @ApiParam(value = "Search by maximal date", example = DATA_EXAMPLE_MAXIMAL_DATE) @QueryParam("end_date") String endDate,
            @ApiParam(value = "Precise the timezone corresponding to the given dates", example = DATA_EXAMPLE_TIMEZONE) @QueryParam("timezone") String timezone,
            @ApiParam(value = "Search by experiment uris", example = ExperimentAPI.EXPERIMENT_EXAMPLE_URI) @QueryParam("experiment") List<URI> experiments,
            @ApiParam(value = "Search by objects uris", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("scientific_objects") List<URI> objects,
            @ApiParam(value = "Search by variables uris", example = DATA_EXAMPLE_VARIABLEURI) @QueryParam("variables") List<URI> variables,
            @ApiParam(value = "Search by devices uris", example = DeviceAPI.DEVICE_EXAMPLE_URI) @QueryParam("devices") List<URI> devices,
            @ApiParam(value = "Search by minimal confidence index", example = DATA_EXAMPLE_CONFIDENCE) @QueryParam("min_confidence") @Min(0) @Max(1) Float confidenceMin,
            @ApiParam(value = "Search by maximal confidence index", example = DATA_EXAMPLE_CONFIDENCE_MAX) @QueryParam("max_confidence") @Min(0) @Max(1) Float confidenceMax,
            @ApiParam(value = "Search by provenances", example = DATA_EXAMPLE_PROVENANCEURI) @QueryParam("provenances") List<URI> provenances,
            @ApiParam(value = "Search by metadata", example = DATA_EXAMPLE_METADATA) @QueryParam("metadata") String metadata
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        
        //convert dates
        Instant startInstant = null;
        Instant endInstant = null;
        
        if (startDate != null) {
            try  {
                startInstant = DataValidateUtils.getDateInstant(startDate, timezone, Boolean.FALSE);
            } catch (DateValidationException e) {
                return new DateMappingExceptionResponse().toResponse(e);
            }          
        }
        
        if (endDate != null) {
            try {
                endInstant = DataValidateUtils.getDateInstant(endDate, timezone, Boolean.TRUE);
            } catch (DateValidationException e) {
                return new DateMappingExceptionResponse().toResponse(e);
            }
        }
                    
        Document metadataFilter = null;
        if (metadata != null) {
            try {
                metadataFilter = Document.parse(metadata);
            } catch (Exception e) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "METADATA_PARAM_ERROR", "unable to parse metadata")
            .getResponse();
            }
        }        
        
        int count = dao.count(
                user,
                experiments,
                objects,
                variables,
                provenances,
                devices,
                startInstant,
                endInstant,
                confidenceMin,
                confidenceMax,
                metadataFilter
        );
        return new SingleObjectResponse<>(count).getResponse();
    }
    
    @DELETE
    @Path("{uri}")
    @ApiOperation("Delete data")
    @ApiProtected
    @ApiCredential(credentialId = CREDENTIAL_DATA_DELETE_ID, credentialLabelKey = CREDENTIAL_DATA_DELETE_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Data deleted", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Invalid or unknown Data URI", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})
    public Response deleteData(
            @ApiParam(value = "Data URI", example = DATA_EXAMPLE_URI, required = true) @PathParam("uri") @NotNull URI uri) 
            throws Exception {
        try {
            DataDAO dao = new DataDAO(nosql,sparql, fs);        
            dao.delete(uri);
            return new ObjectUriResponse(Response.Status.OK, uri).getResponse(); 
        } catch (NoSQLInvalidURIException e){
            throw new NotFoundURIException("Invalid or unknown data URI ", uri);
        }
    }
    
    @PUT
    @Path("{uri}/confidence")
    @ApiProtected
    @ApiOperation("Update confidence index")
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Confidence update", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Bad user request", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})

    public Response updateConfidence(
            @ApiParam("Data description") @Valid DataConfidenceDTO dto,
            @ApiParam(value = "Data URI", required = true) @PathParam("uri") @NotNull URI uri
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);  
        try {
            DataModel data = dao.get(uri);
            data.setConfidence(dto.getConfidence());
            dao.update(data);
            return new ObjectUriResponse(Response.Status.OK, data.getUri()).getResponse();
        } catch (NoSQLInvalidURIException e){
            throw new NotFoundURIException("Invalid or unknown data URI ", uri);
        }
        
    }
    
    @PUT
    @ApiProtected
    @ApiOperation("Update data")
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Confidence update", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Bad user request", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})

    public Response update(
            @ApiParam("Data description") @Valid DataUpdateDTO dto
            //@ApiParam(value = "Data URI", required = true) @PathParam("uri") @NotNull URI uri
    ) throws Exception {
        
        DataDAO dao = new DataDAO(nosql, sparql, fs);            

        try {
            DataModel model = dto.newModel();
            validData(Arrays.asList(model));
            dao.update(model);
            return new SingleObjectResponse<>(DataGetDTO.getDtoFromModel(model)).getResponse();
        } catch (NoSQLInvalidURIException e){
            throw new NotFoundURIException("Invalid or unknown data URI ", dto.getUri());
        } catch (MongoBulkWriteException e) {                   
            return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", e.getMessage())
            .getResponse();            
        } catch (MongoCommandException e) {
            return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", e.getErrorMessage())
            .getResponse();
        } catch (DateValidationException e) {
           return new DateMappingExceptionResponse().toResponse(e);
        }
    }
 
    @DELETE
    @ApiOperation("Delete data on criteria")
    @ApiProtected
    @ApiCredential(credentialId = CREDENTIAL_DATA_DELETE_ID, credentialLabelKey = CREDENTIAL_DATA_DELETE_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Data deleted", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Invalid or unknown Data URI", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})
    public Response deleteDataOnSearch(
            @ApiParam(value = "Search by experiment uri", example = ExperimentAPI.EXPERIMENT_EXAMPLE_URI) @QueryParam("experiment") URI experimentUri,
            @ApiParam(value = "Search by object uri", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("scientific_object") URI objectUri,
            @ApiParam(value = "Search by variable uri", example = DATA_EXAMPLE_VARIABLEURI) @QueryParam("variable") URI variableUri,
            @ApiParam(value = "Search by provenance uri", example = DATA_EXAMPLE_PROVENANCEURI) @QueryParam("provenance") URI provenanceUri
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql,sparql, fs);
        DeleteResult result = dao.deleteWithFilter(user, experimentUri, objectUri, variableUri, provenanceUri);
        return new SingleObjectResponse(result).getResponse(); 
    }
    
    /**
     * Check one data type
     *
     * @param data
     * @throws Exception
     */
    public void checkVariableDataTypes(DataModel data) throws Exception {
        ArrayList<DataModel> dataList = new ArrayList();
        dataList.add(data);
        checkVariableDataTypes(dataList);
    }

    /**
     * Check data list type
     *
     * @param datas
     * @throws Exception
     */
    public void checkVariableDataTypes(List<DataModel> datas) throws Exception {
        VariableDAO dao = new VariableDAO(sparql);
        Map<URI, URI> variableTypes = new HashMap();
        int dataIndex = 0;
        for (DataModel data : datas) {
            checkVariableDatumTypes(dao, variableTypes, data, dataIndex);
            dataIndex++;
        }
    }

    private void checkVariableDatumTypes(VariableDAO dao, Map<URI, URI> variableTypes, DataModel data, int dataIndex) throws Exception {
        if (data.getValue() != null) {
            URI variableUri = data.getVariable();
            if (!variableTypes.containsKey(variableUri)) {
                VariableModel variable = dao.get(data.getVariable());
                if (variable.getDataType() == null) {
                    throw new NoVariableDataTypeException(variableUri);
                } else {
                    variableTypes.put(variableUri, variable.getDataType());
                }
            }
            URI dataType = variableTypes.get(variableUri);

            if (!DataValidateUtils.checkTypeCoherence(dataType, data.getValue())) {
                throw new DataTypeException(variableUri, data.getValue(), dataType, dataIndex);
            }
        }
    }
    
    private void validData(List<DataModel> dataList) throws Exception {
        VariableDAO dao = new VariableDAO(sparql);
        Map<URI, URI> variableTypes = new HashMap();
        Set<URI> variableURIs = new HashSet<>();
        Set<URI> notFoundedVariableURIs = new HashSet<>();
        Set<URI> objectURIs = new HashSet<>();
        Set<URI> notFoundedObjectURIs = new HashSet<>();
        Set<URI> provenanceURIs= new HashSet<>();
        Set<URI> notFoundedProvenanceURIs = new HashSet<>();
        Set<URI> expURIs= new HashSet<>();
        Set<URI> notFoundedExpURIs = new HashSet<>();
        
        int dataIndex = 0;
        for (DataModel data : dataList) {
            // check variable uri and datatype
            if (data.getVariable() != null) {
                if (!variableURIs.contains(data.getVariable())) {
                    variableURIs.add(data.getVariable());
                    VariableModel variable = dao.get(data.getVariable());

                    if (variable == null) {
                        notFoundedVariableURIs.add(data.getVariable());
                    } else {
                        checkVariableDatumTypes(dao, variableTypes, data, dataIndex);
                        dataIndex++;
                    }
                }
            }            
            
            //check objects uri
            if (data.getScientificObject() != null) {
                if (!objectURIs.contains(data.getScientificObject())) {
                    objectURIs.add(data.getScientificObject());
                    if (!sparql.uriExists(ScientificObjectModel.class, data.getScientificObject())) {
                        notFoundedObjectURIs.add(data.getScientificObject());
                    }
                }         
            }
        
            //check provenance uri
            ProvenanceDAO provDAO = new ProvenanceDAO(nosql, sparql);
            if (!provenanceURIs.contains(data.getProvenance().getUri())) {
                provenanceURIs.add(data.getProvenance().getUri());
                if (!provDAO.provenanceExists(data.getProvenance().getUri())) {
                    notFoundedProvenanceURIs.add(data.getProvenance().getUri());
                }
            }  
            
            // check experiments uri
            if (data.getProvenance().getExperiments() != null) {
                for (URI exp:data.getProvenance().getExperiments()) {
                    if (!expURIs.contains(exp)) {
                        expURIs.add(exp);
                        if (!sparql.uriExists(ExperimentModel.class, exp)) {
                            notFoundedExpURIs.add(exp);
                        }    
                    } 
                }
            }
            
        }      
        
        if (!notFoundedVariableURIs.isEmpty()) {
            throw new NoSQLInvalidUriListException("wrong variable uris: ", new ArrayList<>(notFoundedVariableURIs));
        }
        if (!notFoundedObjectURIs.isEmpty()) {
            throw new NoSQLInvalidUriListException("wrong scientific_object uris: ", new ArrayList<>(notFoundedObjectURIs));
        }
        if (!notFoundedProvenanceURIs.isEmpty()) {
            throw new NoSQLInvalidUriListException("wrong provenance uris: ", new ArrayList<>(notFoundedProvenanceURIs));
        }
        if (!notFoundedExpURIs.isEmpty()) {
            throw new NoSQLInvalidUriListException("wrong experiments uris: ", new ArrayList<>(notFoundedExpURIs));
        }

    }
    
      /**
     * @param startDate startDate
     * @param endDate endDate
     * @param timezone timezone
     * @param experiments experimentUris
     * @param objects objectUris
     * @param variables variableUris
     * @param devices
     * @param confidenceMin confidenceMin
     * @param confidenceMax confidenceMax
     * @param provenances provenanceUris
     * @param metadata metadata json filter
     * @param csvFormat long or wide format
     * @param withRawData export raw_data if true
     * @param orderByList orderByList
     * @param page page number
     * @param pageSize page size
     * @return
     * @throws Exception
     */
    @GET
    @Path("export")
    @ApiOperation("Export data")
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_PLAIN})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Return a csv file with data list results in wide or long format"),
        @ApiResponse(code = 400, message = "Invalid parameters", response = ErrorDTO.class)
    })
    public Response exportData(
            @ApiParam(value = "Search by minimal date", example = DATA_EXAMPLE_MINIMAL_DATE) @QueryParam("start_date") String startDate,
            @ApiParam(value = "Search by maximal date", example = DATA_EXAMPLE_MAXIMAL_DATE) @QueryParam("end_date") String endDate,
            @ApiParam(value = "Precise the timezone corresponding to the given dates", example = DATA_EXAMPLE_TIMEZONE) @QueryParam("timezone") String timezone,
            @ApiParam(value = "Search by experiment uris", example = ExperimentAPI.EXPERIMENT_EXAMPLE_URI) @QueryParam("experiments") List<URI> experiments,
            @ApiParam(value = "Search by objects", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("scientific_objects") List<URI> objects,
            @ApiParam(value = "Search by variables", example = DATA_EXAMPLE_VARIABLEURI) @QueryParam("variables") List<URI> variables,
            @ApiParam(value = "Search by devices uris", example = DeviceAPI.DEVICE_EXAMPLE_URI) @QueryParam("devices") List<URI> devices,
            @ApiParam(value = "Search by minimal confidence index", example = DATA_EXAMPLE_CONFIDENCE) @QueryParam("min_confidence") @Min(0) @Max(1) Float confidenceMin,
            @ApiParam(value = "Search by maximal confidence index", example = DATA_EXAMPLE_CONFIDENCE) @QueryParam("max_confidence") @Min(0) @Max(1) Float confidenceMax,
            @ApiParam(value = "Search by provenances", example = DATA_EXAMPLE_PROVENANCEURI) @QueryParam("provenances") List<URI> provenances,
            @ApiParam(value = "Search by metadata", example = DATA_EXAMPLE_METADATA) @QueryParam("metadata") String metadata,
            @ApiParam(value = "Format wide or long", example = "wide") @DefaultValue("wide") @QueryParam("mode") String csvFormat,
            @ApiParam(value = "Export also raw_data") @DefaultValue("false") @QueryParam("with_raw_data") boolean withRawData,
            @ApiParam(value = "List of fields to sort as an array of fieldName=asc|desc", example = "date=desc") @QueryParam("order_by") List<OrderBy> orderByList,
            @ApiParam(value = "Page number", example = "0") @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @ApiParam(value = "Page size", example = "20") @QueryParam("page_size") @DefaultValue("20") @Min(0) int pageSize
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        //convert dates
        Instant startInstant = null;
        Instant endInstant = null;

        if (startDate != null) {
            try {
                startInstant = DataValidateUtils.getDateInstant(startDate, timezone, Boolean.FALSE);
            } catch (UnableToParseDateException e) {
                return new ErrorResponse(e).getResponse();
            } catch (ZoneRulesException e) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "WRONG TIMEZONE PARAMETER", e.getMessage())
                        .getResponse();
            }
        }

        if (endDate != null) {
            try {
                endInstant = DataValidateUtils.getDateInstant(endDate, timezone, Boolean.TRUE);
            } catch (UnableToParseDateException e) {
                return new ErrorResponse(e).getResponse();
            } catch (ZoneRulesException e) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "WRONG TIMEZONE PARAMETER", e.getMessage())
                        .getResponse();
            }
        }

        Document metadataFilter = null;
        if (metadata != null) {
            try {
                metadataFilter = Document.parse(metadata);
            } catch (Exception e) {
                return new ErrorResponse(e).getResponse();
            }
        }
        
        Instant start = Instant.now();
        List<DataModel> resultList = dao.search(user, experiments, objects, variables, provenances, devices, startInstant, endInstant, confidenceMin, confidenceMax, metadataFilter, orderByList);
        Instant data = Instant.now();
        LOGGER.debug(resultList.size() + " observations retrieved " + Long.toString(Duration.between(start, data).toMillis()) + " milliseconds elapsed");

        Response prepareCSVExport = null;

        if (csvFormat.equals("long")) {
            prepareCSVExport = dao.prepareCSVLongExportResponse(resultList, user, withRawData);
        } else {
            prepareCSVExport = dao.prepareCSVWideExportResponse(resultList, user, withRawData);
        }

        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        LOGGER.debug("Export data " + Long.toString(timeElapsed) + " milliseconds elapsed");

        return prepareCSVExport;
    }
    
    @GET
    @Path("provenances")
    @ApiOperation("Get provenances linked to data")
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Return provenances list", response = ProvenanceGetDTO.class, responseContainer = "List")
    })
    public Response getUsedProvenances(
            @ApiParam(value = "Search by experiment uris", example = ExperimentAPI.EXPERIMENT_EXAMPLE_URI) @QueryParam("experiments") List<URI> experiments,
            @ApiParam(value = "Search by objects uris", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("scientific_objects") List<URI> objects,
            @ApiParam(value = "Search by variables uris", example = DATA_EXAMPLE_VARIABLEURI) @QueryParam("variables") List<URI> variables,
            @ApiParam(value = "Search by devices uris", example = DeviceAPI.DEVICE_EXAMPLE_URI) @QueryParam("devices") List<URI> devices
    ) throws Exception {
        
        DataDAO dataDAO = new DataDAO(nosql, sparql, null);
        Set<URI> provenanceURIs = dataDAO.getDataProvenances(user, experiments, objects, variables, devices);

        ProvenanceDAO provenanceDAO = new ProvenanceDAO(nosql, sparql);
        List<ProvenanceModel> resultList = provenanceDAO.getListByURIs(new ArrayList<>(provenanceURIs));
        List<ProvenanceGetDTO> resultDTOList = new ArrayList<>();
        
        resultList.forEach(result -> {
            resultDTOList.add(ProvenanceGetDTO.fromModel(result));
        });
        return new PaginatedListResponse<>(resultDTOList).getResponse();
    }
    
    @GET
    @Path("variables")
    @ApiOperation("Get variables linked to data")
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Return variables list", response = ProvenanceGetDTO.class, responseContainer = "List")
    })
    public Response getUsedVariables(
            @ApiParam(value = "Search by experiment uris", example = ExperimentAPI.EXPERIMENT_EXAMPLE_URI) @QueryParam("experiments") List<URI> experiments,
            @ApiParam(value = "Search by objects uris", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("scientific_objects") List<URI> objects,
            @ApiParam(value = "Search by provenance uris", example = DATA_EXAMPLE_VARIABLEURI) @QueryParam("provenances") List<URI> provenances
    ) throws Exception {
        
        DataDAO dataDAO = new DataDAO(nosql, sparql, null);
        List<VariableModel> variables = dataDAO.getUsedVariables(user, experiments, objects, provenances);
        List<NamedResourceDTO> dtoList = variables.stream().map(NamedResourceDTO::getDTOFromModel).collect(Collectors.toList());
        return new PaginatedListResponse<>(dtoList).getResponse();
    }

}

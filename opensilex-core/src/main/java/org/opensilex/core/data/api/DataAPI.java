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
import com.mongodb.MongoWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.opencsv.CSVReader;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.File;
import static java.lang.Double.NaN;
import java.io.InputStream;
import java.io.InputStreamReader;
import static java.lang.Double.NaN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import static org.opensilex.core.data.api.DataCreationDTO.NAN_VALUES;
import static org.opensilex.core.data.api.DataCreationDTO.NA_VALUES;
import org.opensilex.core.data.dal.DataCSVValidationModel;
import org.opensilex.core.data.dal.DataDAO;
import org.opensilex.core.data.dal.DataFileModel;
import org.opensilex.core.data.dal.DataModel;
import org.opensilex.core.data.dal.DataProvenanceModel;
import org.opensilex.core.data.utils.DataValidateUtils;
import org.opensilex.core.data.utils.ParsedDateTimeMongo;
import org.opensilex.core.exception.CSVDataTypeException;
import org.opensilex.core.experiment.dal.ExperimentDAO;
import org.opensilex.core.ontology.dal.CSVCell;
import org.opensilex.core.provenance.dal.ProvenanceDAO;
import org.opensilex.core.provenance.dal.ProvenanceModel;
import org.opensilex.core.scientificObject.dal.ExperimentalObjectModel;
import org.opensilex.core.scientificObject.dal.ScientificObjectDAO;
import org.opensilex.core.variable.dal.VariableModel;
import org.opensilex.fs.service.FileStorageService;
import org.opensilex.core.exception.DataTypeException;
import org.opensilex.core.exception.NoVariableDataTypeException;
import org.opensilex.core.scientificObject.dal.ScientificObjectModel;
import org.opensilex.core.variable.dal.VariableDAO;
import org.opensilex.nosql.exceptions.NoSQLInvalidURIException;
import org.opensilex.nosql.exceptions.NoSQLTooLargeSetException;
import org.opensilex.nosql.mongodb.MongoDBService;
import org.opensilex.security.authentication.ApiCredential;
import org.opensilex.security.authentication.ApiCredentialGroup;
import org.opensilex.security.authentication.ApiProtected;
import org.opensilex.security.authentication.NotFoundURIException;
import org.opensilex.security.authentication.injection.CurrentUser;
import org.opensilex.security.user.dal.UserModel;
import org.opensilex.server.response.ErrorDTO;
import org.opensilex.server.response.ErrorResponse;
import org.opensilex.server.response.ObjectUriResponse;
import org.opensilex.server.response.PaginatedListResponse;
import org.opensilex.server.response.SingleObjectResponse;
import org.opensilex.server.rest.validation.DateFormat;
import org.opensilex.sparql.deserializer.URIDeserializer;
import org.opensilex.sparql.exceptions.SPARQLException;
import org.opensilex.sparql.service.SPARQLService;
import org.opensilex.utils.ListWithPagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    Logger LOGGER = LoggerFactory.getLogger(DataAPI.class);


    public static final String CREDENTIAL_DATA_GROUP_ID = "Data";
    public static final String CREDENTIAL_DATA_GROUP_LABEL_KEY = "credential-groups.data";

    public static final String DATA_EXAMPLE_URI = "http://opensilex.dev/id/data/1598857852858";
    public static final String DATA_EXAMPLE_OBJECTURI = "http://opensilex.dev/opensilex/2020/o20000345";
    public static final String DATA_EXAMPLE_VARIABLEURI = "http://opensilex.dev/variable#variable.2020-08-21_11-21-23entity6_method6_quality6_unit6";
    public static final String DATA_EXAMPLE_PROVENANCEURI = "http://opensilex.dev/provenance/1598001689415";
    public static final String DATA_EXAMPLE_VALUE = "8.6";
    public static final String DATA_EXAMPLE_MINIMAL_DATE  = "2020-08-21T00:00:00+0100";
    public static final String DATA_EXAMPLE_MAXIMAL_DATE = "2020-09-21T00:00:00+0100";
    
    public static final String CREDENTIAL_DATA_MODIFICATION_ID = "data-modification";
    public static final String CREDENTIAL_DATA_MODIFICATION_LABEL_KEY = "credential.data.modification";

    public static final String CREDENTIAL_DATA_DELETE_ID = "data-delete";
    public static final String CREDENTIAL_DATA_DELETE_LABEL_KEY = "credential.data.delete";
    
    public static final String ERROR_SIZE_SET = "Too large set";
    public static final String ERROR_MAX_SIZE_SET = "Set are limited to";
    public static final int SIZE_MAX = 20000;
    
    public static final int CSV_NB_ERRORS_MAX = 100;


    @Inject
    private MongoDBService nosql;
    
    @Inject
    private SPARQLService sparql;
    
    @Inject
    private FileStorageService fs;

    @CurrentUser
    UserModel user;

    @POST
    @Path("create")
    @ApiProtected
    @ApiOperation("Add data")
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Add data", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Bad user request", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})

    public Response addData(
            @ApiParam("Data description") @Valid DataCreationDTO dto
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        DataModel model = dto.newModel();
        
        ErrorResponse error = valid(model);
                
        if (error != null) {
            return error.getResponse();
        } else {
            try {                
                checkVariableDataTypes(model);
                model = (DataModel) dao.create(model);
                return new ObjectUriResponse(Response.Status.CREATED, model.getUri()).getResponse();
            } catch (DataTypeException typeException) {
                String errorMessage = typeException.getMessage();
                if (typeException.getValue().equals("NaN")) {
                    errorMessage = errorMessage + "NaN can only be used for decimals";
                }
                return new ErrorResponse(Response.Status.BAD_REQUEST, "DATA_TYPE_ERROR" ,
                    errorMessage).getResponse();                            
            } catch (MongoWriteException duplicateKey) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", duplicateKey.getMessage())
                    .getResponse();
            }            
        }
    }
    
    @POST
    @Path("listcreate")
    @ApiProtected
    @ApiOperation("Add list of data")
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
        
        Set<URI> variablesURI = new HashSet<>();
        Set<URI> objectsURI = new HashSet<>();
        Set<URI> provenancesURI= new HashSet<>();
        for(DataCreationDTO dto : dtoList ){
            variablesURI.add(dto.getVariable());
            if (dto.getScientificObjects() != null) {
                objectsURI.addAll(dto.getScientificObjects());
            }            
            provenancesURI.add(dto.getProvenance().getUri());
        }
        
        ErrorResponse error = dao.validList(variablesURI, objectsURI, provenancesURI);
        if (error != null) {
            return error.getResponse();
        } else {
            List<DataModel> dataList = new ArrayList();
            for(DataCreationDTO dto : dtoList ){            
                DataModel model = dto.newModel();
                dataList.add(model);
            }

            try{
                checkVariableDataTypes(dataList);
                dataList = (List<DataModel>) dao.createAll(dataList);
                List<URI> createdResources = new ArrayList<>();
                for (DataModel data : dataList){
                    createdResources.add(data.getUri());
                }
                return new ObjectUriResponse(Response.Status.CREATED, createdResources).getResponse();

            } catch(NoSQLTooLargeSetException ex) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, ERROR_SIZE_SET,
                    ERROR_MAX_SIZE_SET + String.valueOf(SIZE_MAX)).getResponse();
                
            } catch (DataTypeException typeException) {
                String errorMessage = typeException.getMessage();
                if (typeException.getValue().toString().equals("NaN")) {
                    errorMessage = errorMessage + " (NaN can only be used for decimals)";
                }                    
                return new ErrorResponse(Response.Status.BAD_REQUEST, "DATA_TYPE_ERROR" ,
                    errorMessage).getResponse();   
                
            } catch (MongoBulkWriteException duplicateError) {
                List<DataCreationDTO> datas = new ArrayList();
                List<BulkWriteError> errors = duplicateError.getWriteErrors();
                for (int i=0 ; i < errors.size() ; i++) {
                    int index = errors.get(i).getIndex();
                    datas.add(dtoList.get(index));
                }                    
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(datas);

                return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", json)
                .getResponse();
            
            } catch (MongoCommandException e) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", e.getErrorMessage())
                .getResponse();
            }
        }
    }
        
    
    
    @GET
    @Path("get/{uri}")
    @ApiOperation("Get a data")
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
            return new SingleObjectResponse<>(DataGetDTO.fromModel(model)).getResponse();
        } catch (NoSQLInvalidURIException e) {
            return new ErrorResponse(Response.Status.NOT_FOUND, "Invalid or unknown Data URI", e.getMessage())
                    .getResponse();
        } catch (Exception e) {
            return new ErrorResponse(e).getResponse();
        }
        
    }
    
    @GET
    @Path("search")
    @ApiOperation("Search data")
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Return data list", response = DataGetDTO.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Invalid parameters", response = ErrorDTO.class)
    })
    public Response searchDataList(
            //@ApiParam(value = "Search by uri", example = DATA_EXAMPLE_URI) @QueryParam("uri") URI uri,
            @ApiParam(value = "Search by object uri", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("objectUri") URI objectUri,
            @ApiParam(value = "Search by variable uri", example = DATA_EXAMPLE_VARIABLEURI) @QueryParam("variableUri") URI variableUri,
            @ApiParam(value = "Search by provenance uri", example = DATA_EXAMPLE_PROVENANCEURI) @QueryParam("provenanceUri") URI provenanceUri,
            @ApiParam(value = "Search by minimal date", example = DATA_EXAMPLE_MINIMAL_DATE) @QueryParam("startDate") String startDate,
            @ApiParam(value = "Search by maximal date", example = DATA_EXAMPLE_MAXIMAL_DATE) @QueryParam("endDate") String endDate,
            @ApiParam(value = "Page number", example = "0") @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @ApiParam(value = "Page size", example = "20") @QueryParam("pageSize") @DefaultValue("20") @Min(0) int pageSize
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        DateFormat[] formats = {DateFormat.YMDTHMSZ, DateFormat.YMDTHMSMSZ};
        LocalDateTime utcStartDate = null;
        LocalDateTime utcEndDate = null;
        for (DateFormat dateCheckFormat : formats) {
            try { 
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateCheckFormat.toString());
                if (startDate != null) {
                    OffsetDateTime ost = OffsetDateTime.parse(startDate, dtf);
                    utcStartDate = ost.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                }
                if (endDate != null) {
                    OffsetDateTime ost = OffsetDateTime.parse(endDate, dtf);
                    utcEndDate = ost.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                }
                
                break;
            } catch (DateTimeParseException e) {
            }                    
        }
        
        List<URI> provenances = new ArrayList();
        if (provenanceUri != null) {
            provenances.add(provenanceUri);
        }
        
        ListWithPagination<DataModel> resultList = dao.search(
                user,
                //uri,
                objectUri,
                variableUri,
                provenances,
                utcStartDate,
                utcEndDate,
                page,
                pageSize
        );
               
        ListWithPagination<DataGetDTO> resultDTOList = resultList.convert(DataGetDTO.class, DataGetDTO::fromModel);

        return new PaginatedListResponse<>(resultDTOList).getResponse();
    }
    
    @DELETE
    @Path("delete/{uri}")
    @ApiOperation("Delete a data")
    @ApiProtected
    @ApiCredential(credentialId = CREDENTIAL_DATA_DELETE_ID, credentialLabelKey = CREDENTIAL_DATA_DELETE_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Data deleted", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Invalid or unknown Data URI", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})
    public Response deleteData(
            @ApiParam(value = "Data URI", example = DATA_EXAMPLE_URI, required = true) @PathParam("uri") @NotNull URI uri) {

        try {
            DataDAO dao = new DataDAO(nosql,sparql, fs);
            dao.delete(uri);
            return new ObjectUriResponse(uri).getResponse();

        } catch (NoSQLInvalidURIException e) {
            return new ErrorResponse(Response.Status.BAD_REQUEST, "Invalid or unknown Data URI", e.getMessage())
                    .getResponse();
        } catch (Exception e) {
            return new ErrorResponse(e).getResponse();
        }
    }
    
    @PUT
    @Path("{uri}/confidence")
    @ApiProtected
    @ApiOperation("Update confidence")
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Confidence update", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Bad user request", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})

    public Response confidence(
            @ApiParam("Data description") @Valid DataConfidenceDTO dto,
            @ApiParam(value = "Data URI", required = true) @PathParam("uri") @NotNull URI uri
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        DataModel model = dto.newModel();    
        try{        
            dao.update(model);
            return new ObjectUriResponse(Response.Status.OK, model.getUri()).getResponse();
        }catch (NoSQLInvalidURIException e){
            return new ErrorResponse(Response.Status.BAD_REQUEST, "Invalid or unknown Data URI", e.getMessage()).getResponse();
        }catch (Exception e) {
            return new ErrorResponse(e).getResponse();
        }
        
    }
    
    @PUT
    @Path("update")
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
        DataModel model = dto.newModel();
        ErrorResponse error = valid(model);

        if (error != null) {
            return error.getResponse();
        } else {
            try {
                dao.update(model);
                return new SingleObjectResponse<>(DataGetDTO.fromModel(model)).getResponse();
            } catch (NoSQLInvalidURIException e) {
                return new ErrorResponse(Response.Status.NOT_FOUND, "Invalid or unknown Data URI", e.getMessage())
                        .getResponse();
            }catch (Exception e) {
                return new ErrorResponse(e).getResponse();
            }
        }        
    }
    
    /**
     * Saves data file with its metadata and use MULTIPART_FORM_DATA for it.fileContentDisposition parameter is automatically created from submitted file.No example could be provided for this kind of MediaType
     *
     * @param dto
     * @param file
     * @param fileContentDisposition
     * @return the insertion result.
     * @throws java.lang.Exception
     */
    @POST
    @Path("file/create")
    @ApiOperation(value = "Post data file")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Data file and metadata saved", response = ObjectUriResponse.class)})
    @ApiProtected
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postDataFile(
            @ApiParam(value = "File description with metadata", required = true, type = "string") @NotNull @Valid @FormDataParam("description") DataFileCreationDTO dto,
            @ApiParam(value = "Data file", required = true, type = "file") @FormDataParam("file") File file,
            @FormDataParam("file") FormDataContentDisposition fileContentDisposition
    ) throws Exception {

        DataDAO dao = new DataDAO(nosql, sparql, fs);
        DataFileModel model = dto.newModel(); 

        ErrorResponse error = valid(model);
        //Boolean valid = true;
        if (error != null) { 
            return error.getResponse();
        } else {
            try {
                model.setFilename(fileContentDisposition.getFileName());
                dao.insertFile(model, file);
                return new ObjectUriResponse(Response.Status.CREATED, model.getUri()).getResponse();
            } catch (MongoWriteException duplicateKey) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "Duplicate Data", duplicateKey.getMessage())
                    .getResponse();
            }
        }
        
    }
    
    /**
     * Save the metadata of a file already stored in an accessible storage.The absolute path of the file will be ${ws.updir.doc property}/relativePath
 ${ws.updir.doc property} refers to the <ws.updir.doc> property defined in the config.properties file used for building the webservice.
     *
     * @param dtoList
     * @param context
     * @return the insertion result.
     * @throws java.lang.Exception
     * @example [ { "rdfType":
     * "http://www.opensilex.org/vocabulary/oeso#HemisphericalImage", "date":
     * "2017-06-15T10:51:00+0200", "provenanceUri":
     * "http://www.opensilex.org/opensilex/id/provenance/1551805521606",
     * "relativePath" : "4P/464/proc.txt", "concernedItems": [{ "uri":
     * "http://www.opensilex.org/demo/DMO2018-1", "typeURI":
     * "http://www.opensilex.org/vocabulary/oeso#Experiment" }], "metadata": {
     * "sensorUri": "http://www.phenome-fppn.fr/diaphen/2018/s18001" } } ]
     * @example {
     * "metadata": {
     * "pagination": null,
     * "status": [],
     * "datafiles": [
     * "http://www.opensilex.org/opensilex/id/dataFile/HemisphericalImage/ynckimhx54ejoppqewxw2o4aje44kdfvsaimdkptypznrzzbreoa45ae8ad4836741e0ad1a48838bb525bb"
     * ]
     * }
     * }
     */
    @POST
    @Path("filepaths/create")
    @ApiOperation(value = "Post data about existing files")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Data file(s) metadata(s) saved", response = ObjectUriResponse.class)})
    @ApiProtected
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postDataFilePaths(
            @ApiParam(value = "Metadata of the file", required = true) @NotNull @Valid List<DataFilePathCreationDTO> dtoList,
            @Context HttpServletRequest context
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        
        Set<URI> objectsURI = new HashSet<>();
        Set<URI> provenancesURI= new HashSet<>();
        for(DataFilePathCreationDTO dto : dtoList ){ 
            objectsURI.addAll(dto.getScientificObjects());
            provenancesURI.add(dto.getProvenance().getUri());                   
        }
        
        ErrorResponse error = dao.validList(new HashSet(), objectsURI, provenancesURI);
        if (error != null) {
            return error.getResponse();
        } else {
            List<DataFileModel> dataList = new ArrayList();
            for(DataFilePathCreationDTO dto : dtoList ){            
                DataFileModel model = dto.newModel();
                // get the the absolute file path according to the fileStorageDirectory
                java.nio.file.Path absoluteFilePath = fs.getAbsolutePath(Paths.get(model.getPath()));
                

                if (!fs.exist(absoluteFilePath)) {
                    return new ErrorResponse(
                                Response.Status.BAD_REQUEST,
                                "File not found",
                                absoluteFilePath.toString()
                    ).getResponse();
                }

                model.setPath(absoluteFilePath.toString());
                model.setFilename(absoluteFilePath.getFileName().toString());
                dataList.add(model);
            }
            
            try {
                dataList = (List<DataFileModel>) dao.createAllFiles(dataList);
                List<URI> createdResources = new ArrayList<>();
                for (DataModel data : dataList){
                    createdResources.add(data.getUri());
                }            
                return new ObjectUriResponse(Response.Status.CREATED, createdResources).getResponse();
                
            } catch(NoSQLTooLargeSetException ex) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, ERROR_SIZE_SET,
                    ERROR_MAX_SIZE_SET + String.valueOf(SIZE_MAX)).getResponse();
                
            } catch (DataTypeException typeException) {
                String errorMessage = typeException.getMessage();
                if (typeException.getValue().toString().equals("NaN")) {
                    errorMessage = errorMessage + " (NaN can only be used for decimals)";
                }                    
                return new ErrorResponse(Response.Status.BAD_REQUEST, "DATA_TYPE_ERROR" ,
                    errorMessage).getResponse();   
                
            } catch (MongoBulkWriteException duplicateError) {
                List<DataFilePathCreationDTO> datas = new ArrayList();
                List<BulkWriteError> errors = duplicateError.getWriteErrors();
                for (int i=0 ; i < errors.size() ; i++) {
                    int index = errors.get(i).getIndex();
                    datas.add(dtoList.get(index));
                }                    
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(datas);

                return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", json)
                .getResponse();
            
            } catch (MongoCommandException e) {
                return new ErrorResponse(Response.Status.BAD_REQUEST, "DUPLICATE_DATA_KEY", e.getErrorMessage())
                .getResponse();
            }                
            
        }     

    }
    
    /**
     * Returns the content of the file corresponding to the URI given.
     *
     * @param uri
     * @param response
     * @return The file content or null with a 404 status if it doesn't exists
     */
    @ApiProtected
    @GET
    @Path("file/get/{uri}")
    @ApiOperation(value = "Get data file")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieve file")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_OCTET_STREAM})
    public Response getDataFile(
            @ApiParam(value = "Search by fileUri", required = true) @PathParam("uri") @NotNull URI uri,
            @Context HttpServletResponse response
    ) {
        try {
            DataDAO dao = new DataDAO(nosql, sparql, fs);

            DataFileModel description = dao.getFile(uri);

            java.nio.file.Path filePath = Paths.get(description.getPath());
            byte[] fileContent = fs.readFileAsByteArray(filePath);

            if (ArrayUtils.isEmpty(fileContent)) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()).build();
            }
            return Response.ok(fileContent, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + filePath.getFileName().toString() + "\"") //optional
                    .build();
            
        } catch (NoSQLInvalidURIException e) {
            return new ErrorResponse(Response.Status.NOT_FOUND, "Invalid or unknown file URI", e.getMessage())
                    .getResponse();
            
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()).build();
        }
    }
    
    /**
     * This service returns the description of a file corresponding to the URI given.
     *
     * @param uri
     * @return the file description
     * @throws java.lang.Exception
     * @example {
     * "uri": "http://www.phenome-fppn.fr/diaphen/id/dataFile/RGBImage/55fjbbmtmr4m3kkizslzaddfkdt2ranum3ikz6cdiajqzfdc7yqa31d87b83efac4c358ceb5b0da6ed27ff",
     * "rdfType": "http://www.opensilex.org/vocabulary/oeso#RGBImage",
     * "date": "2017-06-15T10:51:00+0200",
     * "concernedItems": [{
     * "uri": "http://www.phenome-fppn.fr/diaphen/2018/o18001199",
     * "typeURI": "http://www.opensilex.org/vocabulary/oeso#Plot"
     * }],
     * "provenanceUri": "http://www.phenome-fppn.fr/diaphen/id/provenance/1552405256945",
     * "metadata": {
     * "sensor": "http://www.phenome-fppn.fr/diaphen/2018/s18035",
     * "position": "1"
     * }
     * }
     */
    @GET
    @Path("file/description/{uri}")
    @ApiOperation(value = "Get data file description")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieve file description", response = DataFileCreationDTO.class),})
    @ApiProtected
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDataFileDescription(
            @ApiParam(value = "Search by fileUri", required = true) @PathParam("uri") @NotNull URI uri
    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        
        try {
            DataFileModel description = dao.getFile(uri);
            return new SingleObjectResponse<>(DataFileGetDTO.fromModel(description)).getResponse();
        } catch (NoSQLInvalidURIException e) {
            return new ErrorResponse(Response.Status.NOT_FOUND, "Invalid or unknown file URI", e.getMessage())
                    .getResponse();
        }
    }
    
    /**
     * Returns a thumbnail based on the content of the file corresponding to the URI given.The given URI must link to a picture.
     *
     * @param uri      the {@link URI} of the file to download
     * @param scaledHeight the height of the thumbnail to return
     * @param scaledWidth  the width of the thumbnail to return
     * @param response
     * @return The file content or null with a 404 status if it doesn't exists
     * @throws java.lang.Exception
     */
    @ApiProtected
    @GET
    @Path("file/thumbnail/{uri}")
    @ApiOperation(value = "Get picture thumbnail")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieve thumbnail of a picture")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_OCTET_STREAM})
    public Response getPicturesThumbnails(
            @ApiParam(value = "Search by fileUri", required = true) @PathParam("uri") @NotNull URI uri,
            @ApiParam(value = "Thumbnail width") @QueryParam("scaledWidth") @Min(256) @Max(1920) @DefaultValue("640") Integer scaledWidth,
            @ApiParam(value = "Thumbnail height") @QueryParam("scaledHeight") @Min(144) @Max(1080) @DefaultValue("360") Integer scaledHeight,
            @Context HttpServletResponse response) throws Exception {

        DataDAO dao = new DataDAO(nosql, sparql, fs);
        
        try {
            DataFileModel description = dao.getFile(uri);
                    byte[] imageData = ImageResizer.getInstance().resize(
                fs.readFileAsByteArray(Paths.get(description.getPath())),
                scaledWidth,
                scaledHeight
            );

            return Response.ok(imageData, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + description.getFilename() + "\"") //optional
                    .build();
            
        } catch (NoSQLInvalidURIException e) {
            return new ErrorResponse(Response.Status.NOT_FOUND, "Invalid or unknown file URI", e.getMessage())
                    .getResponse();
        }
    }
    
    /**
     * This service searches for file descriptions according to the search parameters given.
     *
     * @param pageSize
     * @param objectUri
     * @param provenanceUri
     * @param page
     * @param rdfType
     * @param startDate
     * @param endDate
     * @return List of file description
     * @example [{
     * "uri": "http://www.phenome-fppn.fr/diaphen/id/dataFile/RGBImage/55fjbbmtmr4m3kkizslzaddfkdt2ranum3ikz6cdiajqzfdc7yqa31d87b83efac4c358ceb5b0da6ed27ff",
     * "rdfType": "http://www.opensilex.org/vocabulary/oeso#RGBImage",
     * "date": "2017-06-15T10:51:00+0200",
     * "concernedItems": [{
     * "uri": "http://www.phenome-fppn.fr/diaphen/2018/o18001199",
     * "typeURI": "http://www.opensilex.org/vocabulary/oeso#Plot"
     * }],
     * "provenanceUri": "http://www.phenome-fppn.fr/diaphen/id/provenance/1552405256945",
     * "metadata": {
     * "sensor": "http://www.phenome-fppn.fr/diaphen/2018/s18035",
     * "position": "1"
     * }
     * }]
     */
    @GET
    @Path("file/search")
    @ApiOperation(value = "Retrieve data file descriptions corresponding to the search parameters given.")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieve file descriptions", response = DataFileGetDTO.class, responseContainer = "List")
    })
    @ApiProtected
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDataFileDescriptionsBySearch(
            @ApiParam(value = "Search by rdf type uri") @QueryParam("rdfType") URI rdfType,
            @ApiParam(value = "Search by object uri", example = DATA_EXAMPLE_OBJECTURI) @QueryParam("objectUri") URI objectUri,
            @ApiParam(value = "Search by provenance uri", example = DATA_EXAMPLE_PROVENANCEURI) @QueryParam("provenanceUri") URI provenanceUri,
            @ApiParam(value = "Search by minimal date", example = DATA_EXAMPLE_MINIMAL_DATE) @QueryParam("startDate") String startDate,
            @ApiParam(value = "Search by maximal date", example = DATA_EXAMPLE_MAXIMAL_DATE) @QueryParam("endDate") String endDate,
            @ApiParam(value = "Page number", example = "0") @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @ApiParam(value = "Page size", example = "20") @QueryParam("pageSize") @DefaultValue("20") @Min(0) int pageSize

    ) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);
        
        DateFormat[] formats = {DateFormat.YMDTHMSZ, DateFormat.YMDTHMSMSZ};
        LocalDateTime utcStartDate = null;
        LocalDateTime utcEndDate = null;
        for (DateFormat dateCheckFormat : formats) {
            try { 
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateCheckFormat.toString());
                if (startDate != null) {
                    OffsetDateTime ost = OffsetDateTime.parse(startDate, dtf);
                    utcStartDate = ost.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                }
                if (endDate != null) {
                    OffsetDateTime ost = OffsetDateTime.parse(endDate, dtf);
                    utcEndDate = ost.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                }
                
                break;
            } catch (DateTimeParseException e) {
            }                    
        }
        
        ListWithPagination<DataFileModel> resultList = dao.searchFiles(
                user,
                //uri,
                objectUri,
                provenanceUri,
                utcStartDate,
                utcEndDate,
                page,
                pageSize
        );
        
        ListWithPagination<DataFileGetDTO> resultDTOList = resultList.convert(DataFileGetDTO.class, DataFileGetDTO::fromModel);

        return new PaginatedListResponse<>(resultDTOList).getResponse();

    }
    
    @PUT
    @Path("file/update")
    @ApiProtected
    @ApiOperation("Update data file description")
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "data file update", response = ObjectUriResponse.class),
        @ApiResponse(code = 400, message = "Bad user request", response = ErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorResponse.class)})

    public Response updateFile(
            @ApiParam("DataFile description") @Valid DataFileUpdateDTO dto
            //@ApiParam(value = "Data URI", required = true) @PathParam("uri") @NotNull URI uri
    ) throws Exception {
        
        DataDAO dao = new DataDAO(nosql, sparql, fs);
               
        DataFileModel model = dto.newModel();
        ErrorResponse error = valid(model);

        if (error != null) {
            return error.getResponse();
        } else {
            // get the the absolute file path according to the fileStorageDirectory
            java.nio.file.Path absoluteFilePath = fs.getAbsolutePath(Paths.get(model.getPath()));                

            if (!fs.exist(absoluteFilePath)) {
                return new ErrorResponse(
                            Response.Status.BAD_REQUEST,
                            "File not found",
                            absoluteFilePath.toString()
                ).getResponse();
            }

            model.setPath(absoluteFilePath.toString());
            model.setFilename(absoluteFilePath.getFileName().toString());   
            
            try {
                dao.updateFile(model);
                return new ObjectUriResponse(Response.Status.OK, model.getUri()).getResponse();            
            }catch (NoSQLInvalidURIException e){
                return new ErrorResponse(Response.Status.BAD_REQUEST, "Invalid or unknown File URI", e.getMessage()).getResponse();
            }catch (Exception e) {
                return new ErrorResponse(e).getResponse();
            }
        }
    }    

    @POST
    @Path("csv-import")
    @ApiOperation(value = "Import a CSV file for the given experiement URI and scientific object type.")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Data file and metadata saved", response = DataCSVValidationDTO.class)})
    @ApiProtected
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importCSV(
            @ApiParam(value = "File description with metadata", required = true, type = "string") @Valid @FormDataParam("description") DataDescriptionDTO description,
            @ApiParam(value = "Data file", required = true, type = "file") @NotNull @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileContentDisposition) throws Exception {
        DataDAO dao = new DataDAO(nosql, sparql, fs);

        // test exp
        ExperimentDAO xpDAO = new ExperimentDAO(sparql);

        xpDAO.validateExperimentAccess(description.getExperiment(), user);

        // test prov
        ProvenanceModel provenanceModel = null;

        ProvenanceDAO provDAO = new ProvenanceDAO(nosql);
        if (description.getProvenance() != null) {
            try {
                provenanceModel = provDAO.get(description.getProvenance());
            } catch (NoSQLInvalidURIException e) {
                throw new NotFoundURIException("Provenance URI not found: ", description.getProvenance());

            }
        } else {
            throw new NotFoundURIException("Provenance URI not found: ", description.getProvenance());
        }

        DataCSVValidationModel validation;
        validation = validateWholeCSV(description.getExperiment(), provenanceModel, file, user);

        DataCSVValidationDTO csvValidation = new DataCSVValidationDTO();

        validation.setInsertionStep(true);
        validation.setValidCSV(!validation.hasErrors()); 
        validation.setNbLinesToImport(validation.getData().size()); 

        if (validation.isValidCSV()) {
            Instant start = Instant.now();
            try {
                dao.createAll(validation.getData());
                validation.setNbLinesImported(validation.getData().size());
            } catch (NoSQLTooLargeSetException ex) {
                validation.setTooLargeDataset(true);

            } catch (MongoBulkWriteException duplicateError) {
                List<BulkWriteError> bulkErrors = duplicateError.getWriteErrors();
                for (int i = 0; i < bulkErrors.size(); i++) {
                    int index = bulkErrors.get(i).getIndex();
                    DataGetDTO fromModel = DataGetDTO.fromModel(validation.getData().get(index));
                    int variableIndex = validation.getHeaders().indexOf(fromModel.getVariable().toString());
                    String variableName = validation.getHeadersLabels().get(variableIndex) + '(' + validation.getHeaders().get(variableIndex) + ')';
                    CSVCell csvCell = new CSVCell(index, validation.getHeaders().indexOf(fromModel.getVariable().toString()), fromModel.getValue().toString(), variableName);
                    validation.addDuplicatedDataError(csvCell);
                }
            } catch (DataTypeException e) {
                int indexOfVariable = validation.getHeaders().indexOf(e.getVariable().toString());
                String variableName = validation.getHeadersLabels().get(indexOfVariable) + '(' + validation.getHeaders().get(indexOfVariable) + ')';
                validation.addInvalidDataTypeError(new CSVCell(e.getDataIndex(), indexOfVariable, e.getValue().toString(), variableName));
            }
            Instant finish = Instant.now();
            long timeElapsed = Duration.between(start, finish).toMillis();
            LOGGER.debug("Insertion " + Long.toString(timeElapsed) + " milliseconds elapsed");
            
            validation.setValidCSV(!validation.hasErrors());
            csvValidation.setDataErrors(validation); 
        }
        return new SingleObjectResponse<>(csvValidation).getResponse();
    }

    @POST
    @Path("validate-csv")
    @ApiOperation(value = "Import a CSV file for the given experiement URI and scientific object type.")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Data file and metadata saved", response = DataCSVValidationDTO.class)})
    @ApiProtected
    @ApiCredential(credentialId = CREDENTIAL_DATA_MODIFICATION_ID, credentialLabelKey = CREDENTIAL_DATA_MODIFICATION_LABEL_KEY)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validateCSV(
            @ApiParam(value = "File description with metadata", required = true, type = "string") @Valid @FormDataParam("description") DataDescriptionDTO description,
            @ApiParam(value = "Data file", required = true, type = "file") @NotNull @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileContentDisposition) throws Exception {
        // test exp
        ExperimentDAO xpDAO = new ExperimentDAO(sparql);
        xpDAO.validateExperimentAccess(description.getExperiment(), user);

        // test prov
        ProvenanceModel provenanceModel = null;

        ProvenanceDAO provDAO = new ProvenanceDAO(nosql);
        if (description.getProvenance() != null) {
            try {
                provenanceModel = provDAO.get(description.getProvenance());
            } catch (NoSQLInvalidURIException e) {
                throw new NotFoundURIException("Provenance URI not found: ", description.getProvenance());

            }
        } else {
            throw new NotFoundURIException("Provenance URI not found: ", description.getProvenance());
        }

        DataCSVValidationModel validation;

        Instant start = Instant.now();
        validation = validateWholeCSV(description.getExperiment(), provenanceModel, file, user);
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        LOGGER.debug("Validation " + Long.toString(timeElapsed) + " milliseconds elapsed");

        DataCSVValidationDTO csvValidation = new DataCSVValidationDTO(); 
        
        validation.setValidCSV(!validation.hasErrors());
        validation.setValidationStep(true);
        validation.setNbLinesToImport(validation.getData().size());
        csvValidation.setDataErrors(validation);
        
        return new SingleObjectResponse<>(csvValidation).getResponse();
    }
    
    

    private DataCSVValidationModel validateWholeCSV(URI experimentURI, ProvenanceModel provenance, InputStream file, UserModel currentUser) throws Exception {
        DataCSVValidationModel csvValidation = new DataCSVValidationModel();
        ScientificObjectDAO scientificObjectDAO = new ScientificObjectDAO(sparql);

        Map<String, ExperimentalObjectModel> nameURIScientificObjectsInXp = new HashMap<>();
        List<String> scientificObjectsNotInXp = new ArrayList<>();

        Map<Integer, String> headerByIndex = new HashMap<>();

        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file));) {
            // Line 1
            String[] ids = csvReader.readNext();

            // 1. check variables
            HashMap<URI, URI> mapVariableUriDataType = new HashMap<>();
 
            VariableDAO dao = new VariableDAO(sparql);
            if (ids != null) {

                for (int i = 0; i < ids.length; i++) {
                    if (i > 1) {
                        String header = ids[i];
                        try {
                            if (header == null || !URIDeserializer.validateURI(header)) {
                                csvValidation.addInvalidHeaderURI(i, header);
                            } else {
                                VariableModel var = dao.get(URI.create(header));
//                                boolean uriExists = sparql.uriExists(VariableModel.class, URI.create(header));
                                if (var == null) {
                                    csvValidation.addInvalidHeaderURI(i, header);
                                } else {
                                    mapVariableUriDataType.put(var.getUri(), var.getDataType());
                                    // TODO : Validate duplicate variable colonne
                                    headerByIndex.put(i, header);
                                }
                            }
                        } catch (URISyntaxException e) {
                            csvValidation.addInvalidHeaderURI(i, ids[i]);
                        }
                    }
                }

                // 1.1 return error variables
                if (csvValidation.hasErrors()) {
                    return csvValidation;
                }
                csvValidation.setHeadersFromArray(ids);

                int rowIndex = 1;
                String[] values;

                // Line 2
                String[] headersLabels = csvReader.readNext();
                csvValidation.setHeadersLabelsFromArray(headersLabels);

                // Line 3
                csvReader.readNext();
                // Line 4
                int nbError = 0;
                boolean validateCSVRow = false;
                while ((values = csvReader.readNext()) != null) {
                    try {
                        validateCSVRow = validateCSVRow(provenance, values, rowIndex, csvValidation, headerByIndex, experimentURI, scientificObjectDAO, nameURIScientificObjectsInXp, scientificObjectsNotInXp, mapVariableUriDataType);
                    } catch (CSVDataTypeException e) {
                        csvValidation.addInvalidDataTypeError(e.getCsvCell());
                    }
                    rowIndex++;
                    if (!validateCSVRow) {
                        nbError++;
                    }
                    if (nbError >= CSV_NB_ERRORS_MAX) {
                        break;
                    }
                }
            }
        }

        if (csvValidation.getData().size() > SIZE_MAX) {
            csvValidation.setTooLargeDataset(true);
        }

        return csvValidation;
    }

    private boolean validateCSVRow(ProvenanceModel provenance, String[] values, int rowIndex, DataCSVValidationModel csvValidation, Map<Integer, String> headerByIndex, URI experimentURI, ScientificObjectDAO scientificObjectDAO, Map<String, ExperimentalObjectModel> nameURIScientificObjects, List<String> scientificObjectsNotInXp, HashMap<URI, URI> mapVariableUriDataType) throws CSVDataTypeException {

        boolean validRow = true;
        ExperimentalObjectModel object = null;

        ParsedDateTimeMongo parsedDateTimeMongo = null;
        for (int colIndex = 0; colIndex < values.length; colIndex++) {
            if (colIndex == 0) {
                // test in uri list
                if (nameURIScientificObjects.containsKey(values[colIndex])) {
                    object = nameURIScientificObjects.get(values[colIndex]);
                } else {
                    // test not in uri list
                    if (!scientificObjectsNotInXp.contains(values[colIndex])) {
                        object = getObjectByNameOrURI(scientificObjectDAO, experimentURI, values[colIndex]);
                    }
                    if (object == null) {
                        scientificObjectsNotInXp.add(values[colIndex]);
                        CSVCell cell = new CSVCell(rowIndex, colIndex, values[colIndex], "OBJECT_ID");
                        csvValidation.addInvalidObjectError(cell);
                        validRow = false;
                        break;
                    } else {
                        nameURIScientificObjects.put(values[colIndex], object);
                    }
                }
            } else if (colIndex == 1) {
                // check date
                parsedDateTimeMongo = DataValidateUtils.validateDataDateTime(values[colIndex]);
                if (parsedDateTimeMongo == null) {
                    CSVCell cell = new CSVCell(rowIndex, colIndex, values[colIndex], "DATE");
                    csvValidation.addInvalidDateError(cell);
                    validRow = false;
                    break;
                }
            } else {
                DataModel dataModel = new DataModel();
                DataProvenanceModel provenanceModel = new DataProvenanceModel();
                provenanceModel.setUri(provenance.getUri());
                dataModel.setDate(parsedDateTimeMongo.getLocalDateTime());
                dataModel.setTimezone(parsedDateTimeMongo.getOffset());
                List<URI> listObject = new ArrayList<>();
                listObject.add(object.getUri());
                dataModel.setScientificObjects(listObject);
                dataModel.setProvenance(provenanceModel);
                URI varURI = URI.create(headerByIndex.get(colIndex));
                dataModel.setVariable(varURI);
                dataModel.setValue(returnTypeCoherenceObject(varURI, values[colIndex], mapVariableUriDataType.get(varURI), rowIndex, colIndex, csvValidation));
                csvValidation.addData(dataModel);
            }

        }
        return validRow;
    }

    public ErrorResponse valid(DataModel data) throws SPARQLException, Exception {
        if (data.getVariable() != null) {
            VariableDAO dao = new VariableDAO(sparql);
            VariableModel variable = dao.get(data.getVariable());

            if (variable == null) {
                return new ErrorResponse(
                        Response.Status.BAD_REQUEST,
                        "wrong variable uri",
                        "Variable " + data.getVariable() + " doesn't exist"
                );
            } else {
                URI dataType = variable.getDataType();
                if (!DataValidateUtils.checkTypeCoherence(dataType, data.getValue())) {
                    return new ErrorResponse(
                            Response.Status.BAD_REQUEST,
                            "wrong value format",
                            "Variable dataType: " + dataType.toString()
                    );
                }
            }

        }

        //check objects uri
        if (data.getScientificObjects() != null) {
            if (!data.getScientificObjects().isEmpty()) {
                if (!sparql.uriListExists(ScientificObjectModel.class, data.getScientificObjects())) {
                    return new ErrorResponse(
                            Response.Status.BAD_REQUEST,
                            "wrong object uri",
                            "At least one scientific object doesn't exist exist"
                    );
                }
            }
        }
        //check provenance uri
        ProvenanceDAO provDAO = new ProvenanceDAO(nosql);
        if (!provDAO.provenanceExists(data.getProvenance().getUri())) {
            return new ErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "wrong provenance uri",
                    "Provenance " + data.getProvenance().getUri() + " doesn't exist"
            );
        }

        return null;
    }

    private ExperimentalObjectModel getObjectByNameOrURI(ScientificObjectDAO scientificObjectDAO, URI contextUri, String nameOrUri) {
        ExperimentalObjectModel object = null;
        try {
            object = testNameOrURI(scientificObjectDAO, contextUri, nameOrUri);
        } catch (Exception ex) {
        }
        return object;
    }

    private ExperimentalObjectModel testNameOrURI(ScientificObjectDAO scientificObjectDAO, URI contextUri, String nameOrUri) throws Exception {
        ExperimentalObjectModel object;
        if (URIDeserializer.validateURI(nameOrUri)) {
            URI objectUri = URI.create(nameOrUri);

            object = scientificObjectDAO.getObjectByURI(objectUri, contextUri);
        } else {
            object = scientificObjectDAO.getByNameAndContext(contextUri, nameOrUri);
        }

        return object;
    }

    private Object returnTypeCoherenceObject(URI variable, Object value, URI dataType, int dataIndex, int colIndex, DataCSVValidationModel csvValidation) throws CSVDataTypeException {
        String variableName = csvValidation.getHeadersLabels().get(colIndex) + '(' + csvValidation.getHeaders().get(colIndex) + ')';

        if (Arrays.asList(NA_VALUES).contains(value)) {
            value = "NA";
        } else if (Arrays.asList(NAN_VALUES).contains(value)) {
            value = NaN;
        }

        if (dataType == null) {
            return value;

        } else {
            if (dataType.toString().equals("xsd:integer")) {

                try {
                    return Integer.valueOf(value.toString());
                } catch (NumberFormatException e) {
                    CSVCell errorCell = new CSVCell(dataIndex, colIndex, value.toString(), variableName);
                    throw new CSVDataTypeException(variable, value, dataType, dataIndex, errorCell);
                }
            }

        }
        if (dataType.toString().equals("xsd:decimal")) {
            try {
                return Double.valueOf(value.toString());
            } catch (NumberFormatException e) {
                CSVCell errorCell = new CSVCell(dataIndex, colIndex, value.toString(), variableName);
                throw new CSVDataTypeException(variable, value, dataType, dataIndex, errorCell);
            }
        }
        if (dataType.toString().equals("xsd:boolean")) {
            Boolean toBooleanObject = BooleanUtils.toBooleanObject(value.toString());
            if (Objects.equals(toBooleanObject, Boolean.FALSE) || Objects.equals(toBooleanObject, Boolean.TRUE)) {
                return toBooleanObject;
            } else {
                CSVCell errorCell = new CSVCell(dataIndex, colIndex, value.toString(), variableName);
                throw new CSVDataTypeException(variable, value, dataType, dataIndex, errorCell);
            }
        }

        if (dataType.toString().equals("xsd:date")) {
            if ((value instanceof String)) {
                return value;
            } else {
                CSVCell errorCell = new CSVCell(dataIndex, colIndex, value.toString(), variableName);
                throw new CSVDataTypeException(variable, value, dataType, dataIndex, errorCell);
            }
        }
        if (dataType.toString().equals("xsd:datetime")) {
            if ((value instanceof String)) {
                return value;
            } else {
                CSVCell errorCell = new CSVCell(dataIndex, colIndex, value.toString(), variableName);
                throw new CSVDataTypeException(variable, value, dataType, dataIndex, errorCell);
            }
        }
        if (dataType.toString().equals("xsd:string")) {
            if ((value instanceof String)) {
                return value;
            } else {
                CSVCell errorCell = new CSVCell(dataIndex, colIndex, value.toString(), variableName);
                throw new CSVDataTypeException(variable, value, dataType, dataIndex, errorCell);
            }
        }

        return value;
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
        if (data.getValue() != "NA") {
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
}

//******************************************************************************
//                          DataGetDTO.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRAE 2020
// Contact: anne.tireau@inrae.fr, pascal.neveu@inrae.fr
//******************************************************************************
package org.opensilex.core.data.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import javax.validation.constraints.NotNull;
import org.opensilex.core.data.dal.DataModel;
import org.opensilex.server.rest.validation.DateFormat;
import org.opensilex.server.rest.validation.ValidURI;

/**
 *
 * @author sammy
 */
public class DataGetDTO extends DataCreationDTO {
    
    @NotNull
    @ValidURI
    @ApiModelProperty(value = "data URI", example = DataAPI.DATA_EXAMPLE_URI)    
    @Override
    public URI getUri() {
        return uri;
    }        
    
    @JsonIgnore
    @Override
    public String getTimezone() {
        return timezone;
    }
        
    public void setDate(Instant instant, String offset, Boolean isDateTime) {
        if (isDateTime) {
            OffsetDateTime odt = instant.atOffset(ZoneOffset.of(offset));
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DateFormat.YMDTHMSMSX.toString());
            this.setDate(dtf.format(odt));
        } else {
            LocalDate date = ZonedDateTime.ofInstant(instant, ZoneId.of(offset)).toLocalDate();            
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DateFormat.YMD.toString());            ;
            this.setDate(dtf.format(date));
        }        
    }
    
    public void fromModel(DataModel model) {
        setUri(model.getUri());
        setScientificObject(model.getScientificObject());
        setVariable(model.getVariable());      
        setDate(model.getDate(), model.getOffset(), model.getIsDateTime());          
        setConfidence(model.getConfidence());
        setValue(model.getValue());
        setMetadata(model.getMetadata());   
        setProvenance(model.getProvenance());
        setRawData(model.getRawData());
    }
    
    public static DataGetDTO getDtoFromModel(DataModel model){
        DataGetDTO dto = new DataGetDTO();
        dto.fromModel(model);
        return dto;
    }
}

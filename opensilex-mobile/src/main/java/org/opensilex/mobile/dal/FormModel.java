//******************************************************************************
//                          FormModel.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRA 2021
// Contact: maximilian.hart@inrae.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//******************************************************************************
package org.opensilex.mobile.dal;
 
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.opensilex.nosql.mongodb.MongoModel;

/**
 * @author Maximilian Hart
 */
public class FormModel  extends MongoModel {  
    
    private String commitAddress;

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private Instant creationDate; 
    
    private Instant lastUpdateDate;
    
    private String offset;
    
    private URI type;
    
    private List<Map> formData;
    
    public List<Map> getFormData() {
        return formData;
    }

    public void setFormData(List<Map> formData) {
        this.formData = formData;
    }

    public URI getType() {
        return type;
    }

    public void setType(URI type) {
        this.type = type;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public Instant getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setCommitAddress(String commitAddress) {
        this.commitAddress = commitAddress;
    }

    public String getCommitAddress() {
        return commitAddress;
    }

    public void setLastUpdateDate(Instant lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }
    
    @Override
    public String[] getUriSegments(MongoModel instance) {
        return new String[]{
            creationDate.toString()
        };
    }
}

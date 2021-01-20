/*
 * *******************************************************************************
 *                     AreaCreationDTO.java
 * OpenSILEX
 * Copyright © INRAE 2020
 * Creation date: September 14, 2020
 * Contact: vincent.migot@inrae.fr, anne.tireau@inrae.fr, pascal.neveu@inrae.fr
 * *******************************************************************************
 */
package org.opensilex.core.area.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.geojson.GeoJsonObject;
import org.opensilex.core.area.dal.AreaModel;
import org.opensilex.server.rest.validation.Required;
import org.opensilex.server.rest.validation.ValidURI;

import javax.validation.constraints.NotNull;
import java.net.URI;

/**
 * DTO representing JSON for area shipping
 *
 * @author Jean Philippe VERT
 */
@ApiModel
@JsonPropertyOrder({"uri", "rdf_type", "name", "description", "geometry"})
public class AreaCreationDTO {
    /**
     * Area URI
     */
    @ValidURI
    @ApiModelProperty(value = "Area URI", example = "http://opensilex/set/area/Z_001")
    protected URI uri;

    /**
     * Area name
     */
    @Required
    @ApiModelProperty(value = "Area name", example = "Z_001", required = true)
    protected String name;

    /**
     * Area Type : Area, WindyArea, etc
     */
    @NotNull
    @JsonProperty("rdf_type")
    @ApiModelProperty(value = "Area rdf_type", required = true, name = "rdf_type", example = "vocabulary:WindyArea")
    protected URI rdfType;

    /**
     * geometry of the Area
     */
    @NotNull
    @ApiModelProperty(value = "The geographical coordinates of the area", required = true)
    protected GeoJsonObject geometry;

    /**
     * description
     */
    @ApiModelProperty(value = "Description of the area", example = "Protocol n°1289 - Amount of water 5 ml/Days.")
    protected String description;

    public URI getUri() {
        return uri;
    }

    public AreaCreationDTO setUri(URI uri) {
        this.uri = uri;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URI getRdfType() {
        return rdfType;
    }

    public void setRdfType(URI rdfType) {
        this.rdfType = rdfType;
    }

    public GeoJsonObject getGeometry() {
        return geometry;
    }

    public void setGeometry(GeoJsonObject geometry) {
        this.geometry = geometry;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AreaModel newModel() {
        AreaModel model = new AreaModel();

        if (uri != null) {
            model.setUri(uri);
        }

        if (name != null) {
            model.setName(name);
        }

        if (rdfType != null) {
            model.setType(rdfType);
        }

        if (geometry != null) {
            model.setGeometry(geometry);
        }

        if (description != null) {
            model.setDescription(description);
        }

        return model;
    }
}

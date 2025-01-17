//******************************************************************************
//                          SpeciesDAO.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRAE 2020
// Contact: renaud.colin@inrae.fr, anne.tireau@inrae.fr, pascal.neveu@inrae.fr
//******************************************************************************
package org.opensilex.core.species.dal;

import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.opensilex.core.ontology.Oeso;
import org.opensilex.sparql.deserializer.SPARQLDeserializers;
import org.opensilex.sparql.model.SPARQLResourceModel;
import org.opensilex.sparql.service.SPARQLQueryHelper;
import org.opensilex.sparql.service.SPARQLService;
import org.opensilex.utils.OrderBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.opensilex.sparql.service.SPARQLQueryHelper.makeVar;

/**
 * @author Renaud COLIN
 */
public class SpeciesDAO {

    protected final SPARQLService sparql;

    public SpeciesDAO(SPARQLService sparql) {
        this.sparql = sparql;
    }

    /**
     *
     * @param lang the {@link SpeciesModel} {@link org.apache.jena.vocabulary.RDFS#label} lang
     * @return the list of all {@link SpeciesModel} in the given lang
     */
    public List<SpeciesModel> getAll(String lang) throws Exception {

        List<OrderBy> orderByList = new ArrayList();
        orderByList.add(new OrderBy("label=asc"));

        return sparql.search(
                SpeciesModel.class,
                lang,
                (SelectBuilder select) -> {
                    select.addFilter(SPARQLQueryHelper.eq(SPARQLResourceModel.TYPE_FIELD, new URI(Oeso.Species.toString())));
                },
                orderByList
        );
    }

    public List<SpeciesModel> getByExperiment(URI xpUri, String lang) throws Exception {
        List<OrderBy> orderByList = new ArrayList();
        orderByList.add(new OrderBy("label=asc"));

        return sparql.search(
                SpeciesModel.class,
                lang,
                (SelectBuilder select) -> {
                    select.addFilter(SPARQLQueryHelper.eq(SPARQLResourceModel.TYPE_FIELD, new URI(Oeso.Species.toString())));
                    select.addWhere(SPARQLDeserializers.nodeURI(xpUri), Oeso.hasSpecies, makeVar(SpeciesModel.URI_FIELD));
                },
                orderByList
        );
    }
}

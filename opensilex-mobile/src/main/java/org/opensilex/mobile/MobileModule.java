//******************************************************************************
//                          MoblieModule.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRA 2021
// Contact: arnaud.charleroy@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//******************************************************************************
package org.opensilex.mobile;

import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.OA;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.opensilex.OpenSilexModule;
import org.opensilex.server.extensions.APIExtension;
import org.opensilex.sparql.extensions.OntologyFileDefinition;
import org.opensilex.sparql.extensions.SPARQLExtension;

import java.util.List;

/**
 *
 * @author Arnaud Charleroy
 */
public class MobileModule extends OpenSilexModule implements APIExtension, SPARQLExtension {

    private static final String ONTOLOGIES_DIRECTORY = "ontologies";

    @Override
    public List<OntologyFileDefinition> getOntologiesFiles() throws Exception {
        List<OntologyFileDefinition> list = SPARQLExtension.super.getOntologiesFiles();
        list.add(new OntologyFileDefinition(
                "http://www.opensilex.org/vocabulary/iado",
                ONTOLOGIES_DIRECTORY+"/iado.owl",
                Lang.RDFXML,
                "iado"
        ));

        return list;
    }
}

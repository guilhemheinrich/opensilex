//******************************************************************************
//                                       Sensor.java
//
// Author(s): Morgane Vidal <morgane.vidal@inra.fr>
// PHIS-SILEX version 1.0
// Copyright © - INRA - 2018
// Creation date: 14 mars 2018
// Contact: morgane.vidal@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
// Last modification date:  14 mars 2018
// Subject: Represents sensors view
//******************************************************************************
package phis2ws.service.view.model.phis;

import java.util.HashMap;

/**
 * Represents a sensor view
 * @author Morgane Vidal <morgane.vidal@inra.fr>
 */
public class Sensor extends Device {
    //variables mesured by the sensor
    protected HashMap<String, String>  variables;
    
    public HashMap<String, String>  getVariables() {
        return variables;
    }

    public void setVariables(HashMap<String, String>  variables) {
        this.variables = variables;
    }
}

/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.File;
import javax.ws.rs.core.Response;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Test;
import static com.graphhopper.http.util.TestUtils.clientTarget;

/**
 * Tests the DataFlagEncoder with the SpatialRuleLookup enabled
 *
 * @author Robin Boldt
 */
public class SpatialRulesTest {
    private static final String DIR = "./target/north-bayreuth-gh/";

    private static final GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();

    static {
        // The EncodedValue "country" requires the setting "spatial_rules.borders_directory" as "country" does not load via DefaultTagParserFactory
        // TODO should we automatically detect this somehow and include a default country file?
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car").
                putObject("graph.encoded_values", "country,road_environment,road_class,road_access,max_speed").
                putObject("spatial_rules.borders_directory", "../core/files/spatialrules").
                putObject("spatial_rules.max_bbox", "11.4,11.7,49.9,50.1").
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR);
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerTestConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testDetourToComplyWithSpatialRule() {
        final Response response = clientTarget(app, "route?" + "point=49.995933,11.54809&point=50.004871,11.517191").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.get("info").has("errors"));
        double distance = json.get("paths").get(0).get("distance").asDouble();
        // Makes sure that SpatialRules are enforced. Without SpatialRules we take a shortcut trough the forest
        // so the route would be only 3.31km
        assertTrue("distance wasn't correct:" + distance, distance > 7000);
        assertTrue("distance wasn't correct:" + distance, distance < 7500);
    }

}

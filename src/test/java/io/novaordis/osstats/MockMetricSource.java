/*
 * Copyright (c) 2016 Nova Ordis LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.novaordis.osstats;

import io.novaordis.events.core.event.Property;
import io.novaordis.events.metric.MetricCollectionException;
import io.novaordis.events.metric.MetricDefinition;
import io.novaordis.events.metric.source.MetricSource;
import io.novaordis.utilities.os.OS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ovidiu Feodorov <ovidiu@novaordis.com>
 * @since 8/5/16
 */
public class MockMetricSource implements MetricSource {

    // Constants -------------------------------------------------------------------------------------------------------

    // Static ----------------------------------------------------------------------------------------------------------

    // Attributes ------------------------------------------------------------------------------------------------------

    private Map<OS, Map<MetricDefinition, Property>> bulkReadingsForOs;
    private Map<MetricDefinition, Property> readingsForMetrics;

    private boolean breakOnCollect;

    // Constructors ----------------------------------------------------------------------------------------------------

    public MockMetricSource() {

        bulkReadingsForOs = new HashMap<>();
        readingsForMetrics = new HashMap<>();
    }

    // MetricSource implementation -------------------------------------------------------------------------------------

    @Override
    public List<Property> collectAllMetrics(OS os) throws MetricCollectionException {

        if (breakOnCollect) {
            throw new MetricCollectionException("SYNTHETIC");
        }

        Map<MetricDefinition, Property> props = bulkReadingsForOs.get(os);

        if (props == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(props.values());
    }

    @Override
    public List<Property> collectMetrics(List<MetricDefinition> metricDefinitions) throws MetricCollectionException {

        List<Property> result = new ArrayList<>();

        for(MetricDefinition d: metricDefinitions) {

            Property p = readingsForMetrics.get(d);

            if (p != null) {
                result.add(p);
            }
        }

        return result;
    }

    // Public ----------------------------------------------------------------------------------------------------------

    public void addBulkReading(OS os, Property p) {

        Map<MetricDefinition, Property> properties = bulkReadingsForOs.get(os);

        if (properties == null) {

            properties = new HashMap<>();
            bulkReadingsForOs.put(os, properties);
        }

        properties.put(new MockMetricDefinition("mock"), p);
    }

    public void addReadingForMetric(MetricDefinition d, Property p) {

        readingsForMetrics.put(d, p);
    }

    public void breakOnCollectMetrics() {

        breakOnCollect = true;
    }

    @Override
    public String toString() {

        return "Mock Metric Source";
    }

    // Package protected -----------------------------------------------------------------------------------------------

    // Protected -------------------------------------------------------------------------------------------------------

    // Private ---------------------------------------------------------------------------------------------------------

    // Inner classes ---------------------------------------------------------------------------------------------------

}
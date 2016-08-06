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

package io.novaordis.osstats.metric;

import io.novaordis.utilities.os.OS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Not thread-safe, access synchronization must be implemented externally.
 *
 * @author Ovidiu Feodorov <ovidiu@novaordis.com>
 * @since 8/3/16
 */
public abstract class MetricDefinitionBase implements MetricDefinition {

    // Constants -------------------------------------------------------------------------------------------------------

    // Static ----------------------------------------------------------------------------------------------------------

    // Attributes ------------------------------------------------------------------------------------------------------

    private Map<OS, List<MetricSource>> sources;

    // Constructors ----------------------------------------------------------------------------------------------------

    public MetricDefinitionBase() {
        this.sources = new HashMap<>();
    }

    // MetricDefinition implementation ---------------------------------------------------------------------------------

    /**
     * The implementation returns a copy of the internal list.
     */
    @Override
    public List<MetricSource> getSources(OS os) {

        if (os == null) {
            throw new IllegalArgumentException("null os");
        }

        List<MetricSource> sl = sources.get(os);

        if (sl == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(sl);
    }

    /**
     * Not thread safe.
     */
    @Override
    public boolean addSource(OS os, MetricSource source) {

        List<MetricSource> sl = sources.get(os);

        if (sl == null) {

            sl = new ArrayList<>();
            sources.put(os, sl);
        }

        if (sl.contains(source)) {
            return false;
        }

        sl.add(source);

        return true;
    }

    // Public ----------------------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return getName();
    }

    // Package protected -----------------------------------------------------------------------------------------------

    // Protected -------------------------------------------------------------------------------------------------------

    // Private ---------------------------------------------------------------------------------------------------------

    // Inner classes ---------------------------------------------------------------------------------------------------

}
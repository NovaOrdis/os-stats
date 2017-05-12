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

package io.novaordis.osstats.configuration;

import io.novaordis.events.api.metric.MetricDefinition;
import io.novaordis.utilities.UserErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * A configuration instance backed by a property file.
 *
 * @author Ovidiu Feodorov <ovidiu@novaordis.com>
 * @since 7/27/16
 */
public class PropertiesConfigurationFile implements Configuration {

    // Constants -------------------------------------------------------------------------------------------------------

    private static final Logger log = LoggerFactory.getLogger(PropertiesConfigurationFile.class);

    public static final String SAMPLING_INTERVAL_PROPERTY_NAME = "sampling.interval";
    public static final String OUTPUT_FILE_PROPERTY_NAME = "output.file";
    public static final String OUTPUT_FILE_APPEND_PROPERTY_NAME = "output.file.append";
    public static final String METRICS_PROPERTY_NAME = "metrics";

    // Static ----------------------------------------------------------------------------------------------------------

    // Attributes ------------------------------------------------------------------------------------------------------

    private int samplingInterval;
    private String outputFileName;
    private boolean outputFileAppend;
    private boolean foreground;
    private List<MetricDefinition> metrics;

    // Constructors ----------------------------------------------------------------------------------------------------

    public PropertiesConfigurationFile(String fileName, boolean foreground) throws UserErrorException {

        this();

        File f = new File(fileName);
        if (!f.isFile() || !f.canRead()) {
            throw new UserErrorException("configuration file " + fileName + " does not exist or cannot be read");
        }

        InputStream is = null;
        Properties props = new Properties();

        try {

            is = new FileInputStream(f);
            props.load(is);
        }
        catch(Exception e) {
            throw new UserErrorException("failure while reading configuration file " + f);
        }
        finally {

            if (is != null) {
                try {
                    is.close();
                }
                catch(Exception e) {
                    log.warn("failed to close input stream for " + f);
                }
            }
        }

        this.foreground = foreground;

        readConfiguration(props);
    }

    /**
     * Testing only.
     */
    PropertiesConfigurationFile() {

        this.samplingInterval = DEFAULT_SAMPLING_INTERVAL_SEC;
        this.outputFileName = DEFAULT_OUTPUT_FILE_NAME;
        this.outputFileAppend = true;
        this.foreground = false;
        this.metrics = new ArrayList<>();
    }

    // Configuration implementation ------------------------------------------------------------------------------------

    @Override
    public boolean isForeground() {

        return foreground;
    }

    @Override
    public int getSamplingIntervalSec() {

        return samplingInterval;
    }

    @Override
    public String getOutputFileName() {

        return outputFileName;
    }

    @Override
    public boolean isOutputFileAppend() {

        return outputFileAppend;
    }

    @Override
    public List<MetricDefinition> getMetrics() {

        return metrics;
    }

    // Public ----------------------------------------------------------------------------------------------------------

    // Package protected -----------------------------------------------------------------------------------------------

    protected void readConfiguration(Properties properties) throws UserErrorException {

        String s = properties.getProperty(SAMPLING_INTERVAL_PROPERTY_NAME);

        if (s != null) {

            //
            // if null, we rely on the built-in values, set in the constructor
            //

            try {
                samplingInterval = Integer.parseInt(s);
            }
            catch(Exception e) {
                throw new UserErrorException("invalid sampling interval value: \"" + s + "\"", e);
            }
        }

        s = properties.getProperty(OUTPUT_FILE_PROPERTY_NAME);

        if (s != null) {

            outputFileName = s;
        }

        s = properties.getProperty(OUTPUT_FILE_APPEND_PROPERTY_NAME);

        if (s != null) {

            String ls = s;
            ls = ls.trim().toLowerCase();

            if ("true".equals(ls) || "yes".equals(ls)) {
                this.outputFileAppend = true;
            }
            else if ("false".equals(ls) || "no".equals(ls)) {
                this.outputFileAppend = false;
            }
            else {
                throw new UserErrorException(
                        "invalid '" + OUTPUT_FILE_APPEND_PROPERTY_NAME + "' boolean value: \"" + s + "\"");
            }
        }

        s = properties.getProperty(METRICS_PROPERTY_NAME);

        if (s != null) {

            StringTokenizer st = new StringTokenizer(s, ", ");
            while(st.hasMoreTokens()) {
                String tok = st.nextToken();
                MetricDefinition md = MetricDefinition.getInstance(tok);
                metrics.add(md);
            }
        }
    }

    // Protected -------------------------------------------------------------------------------------------------------

    // Private ---------------------------------------------------------------------------------------------------------

    // Inner classes ---------------------------------------------------------------------------------------------------

}
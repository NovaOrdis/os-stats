/*
 * Copyright (c) 2017 Nova Ordis LLC
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

package io.novaordis.databot.configuration.yaml;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.novaordis.databot.DataConsumer;
import io.novaordis.databot.configuration.Configuration;
import io.novaordis.databot.configuration.ConfigurationTest;
import io.novaordis.databot.consumer.AsynchronousCsvLineWriter;
import io.novaordis.databot.consumer.MockDataConsumer;
import io.novaordis.events.api.event.PropertyFactory;
import io.novaordis.events.api.metric.MetricDefinition;
import io.novaordis.events.api.metric.MetricSourceDefinition;
import io.novaordis.events.api.metric.MetricSourceDefinitionImpl;
import io.novaordis.events.api.metric.MetricSourceType;
import io.novaordis.jboss.cli.model.JBossControllerAddress;
import io.novaordis.jmx.JmxAddress;
import io.novaordis.utilities.UserErrorException;
import io.novaordis.utilities.address.Address;
import io.novaordis.utilities.address.AddressImpl;
import io.novaordis.utilities.address.LocalOSAddress;
import io.novaordis.utilities.expressions.DuplicateDeclarationException;
import io.novaordis.utilities.expressions.Scope;
import io.novaordis.utilities.expressions.ScopeImpl;
import io.novaordis.utilities.expressions.Variable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ovidiu Feodorov <ovidiu@novaordis.com>
 * @since 7/27/16
 */
public class YamlConfigurationFileTest extends ConfigurationTest {

    // Constants -------------------------------------------------------------------------------------------------------

    // Static ----------------------------------------------------------------------------------------------------------

    // Attributes ------------------------------------------------------------------------------------------------------

    // Constructors ----------------------------------------------------------------------------------------------------

    // Public ----------------------------------------------------------------------------------------------------------

    // Tests -----------------------------------------------------------------------------------------------------------

    // load() ----------------------------------------------------------------------------------------------------------

    @Test
    public void load_EmptyConfigurationFile() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        String s = "";
        InputStream is = new ByteArrayInputStream(s.getBytes());

        try {

            c.load(is);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.equals("empty configuration file"));
        }
    }

    @Test
    public void load_InvalidSamplingInterval() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        String s = "sampling.interval: blah";
        InputStream is = new ByteArrayInputStream(s.getBytes());

        try {

            c.load(is);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.startsWith("invalid sampling interval value: \"blah\""));
        }
    }

    @Test
    public void load_MissingOutputFile() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        String s =
                "output:\n" +
                "  append: false\n";

        InputStream is = new ByteArrayInputStream(s.getBytes());

        try {

            c.load(is);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertEquals(
                    "missing '" + YamlConfigurationFile.OUTPUT_KEY + "." + YamlConfigurationFile.OUTPUT_FILE_KEY + "'",
                    msg);
        }
    }

    @Test
    public void load_MissingConsumers() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        String s = "something: something else\n";

        InputStream is = new ByteArrayInputStream(s.getBytes());

        try {

            c.load(is);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertEquals("no data consumer specified in configuration", msg);
        }
    }

    @Test
    public void load_AsynchronousCsvWriter() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        List<MetricSourceDefinition> sourceDefinitions = c.getMetricSourceDefinitions();
        assertTrue(sourceDefinitions.isEmpty());

        String s = "output:\n" +
                "  file: something\n" +
                "  append: false\n";

        InputStream is = new ByteArrayInputStream(s.getBytes());

        c.load(is);

        List<DataConsumer> dcs = c.getDataConsumers();
        assertEquals(1, dcs.size());

        AsynchronousCsvLineWriter w = (AsynchronousCsvLineWriter)dcs.get(0);
        assertEquals("something", w.getOutputFileName());
        assertEquals(false, w.isOutputFileAppend());
    }

    @Test
    public void load_MetricsNotAList() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        String s =
                "output:\n" +
                "  file: something.csv\n" +
                "metrics: something\n";

        InputStream is = new ByteArrayInputStream(s.getBytes());

        try {

            c.load(is);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertEquals("'" + YamlConfigurationFile.METRICS_KEY + "' not a list", msg);
        }
    }

    @Test
    public void load_Metrics() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        List<MetricSourceDefinition> sourceDefinitions = c.getMetricSourceDefinitions();
        assertTrue(sourceDefinitions.isEmpty());

        String s =
                "output:\n" +
                "  file: something.csv\n" +
                "metrics:\n" +
                "  - PhysicalMemoryTotal\n" +
                "  - CpuUserTime\n" +
                "  - LoadAverageLastMinute\n";

        InputStream is = new ByteArrayInputStream(s.getBytes());

        c.load(is);

        List<MetricDefinition> mds = c.getMetricDefinitions();
        assertEquals(3, mds.size());

        MetricDefinition md = mds.get(0);
        assertEquals("PhysicalMemoryTotal", md.getId());
        MetricDefinition md2 = mds.get(1);
        assertEquals("CpuUserTime", md2.getId());
        MetricDefinition md3 = mds.get(2);
        assertEquals("LoadAverageLastMinute", md3.getId());

        sourceDefinitions = c.getMetricSourceDefinitions();
        assertEquals(1, sourceDefinitions.size());
        assertTrue(sourceDefinitions.get(0).getAddress().equals(new LocalOSAddress()));
    }

    @Test
    public void load_MetricDefinitionRequiresAuthentication() throws Exception {

        YamlConfigurationFile c = new YamlConfigurationFile(true, null);

        String s =
                "output: stdout\n" +
                        "sources:\n" +
                        "  test:\n" +
                        "    type: jboss-controller\n" +
                        "    host: localhost\n" +
                        "    port: 9999\n" +
                        "    username: someuser\n" +
                        "    password: somepass\n" +
                        "metrics:\n" +
                        "  - jbosscli://someuser@localhost:9999/something=somethingelse/blah";

        InputStream is = new ByteArrayInputStream(s.getBytes());

        c.load(is);

        //
        // we should have a metric source definition and a metric definition
        //

        List<MetricSourceDefinition> sourceDefintions = c.getMetricSourceDefinitions();
        assertEquals(1, sourceDefintions.size());

        Address a = sourceDefintions.get(0).getAddress();

        List<MetricDefinition> mds = c.getMetricDefinitions();
        assertEquals(1, mds.size());

        Address a2 = mds.get(0).getMetricSourceAddress();

        assertEquals(a, a2);
    }

    // processSources() ------------------------------------------------------------------------------------------------

    @Test
    public void processSources_NoSources() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        f.processSources(null, rootScope);

        //
        // nothing happens
        //

        List<Variable> v = rootScope.getVariablesDeclaredInScope();
        assertTrue(v.isEmpty());
    }

    @Test
    public void processSources() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        String s =
                "sources:\n" +
                        "  some-source:\n" +
                        "    type: jboss-controller\n" +
                        "    host: localhost\n" +
                        "    port: 9999\n" +
                        "    classpath:\n" +
                        "      - $JBOSS_HOME/bin/client/jboss-cli-client.jar\n" +
                        "      - /some/other/file.jar\n" +
                        "  some-other-source:\n" +
                        "    type: jboss-controller\n" +
                        "    host: other-host\n" +
                        "    port: 10101\n" +
                        "    username: admin\n" +
                        "    password: blah\n" +
                        "    classpath:\n" +
                        "      - $JBOSS_HOME/bin/client/jboss-cli-client.jar\n" +
                        "      - /some/other/file.jar\n";


        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        f.processSources(o, rootScope);

        //
        // two metric source definitions and two variables
        //

        List<MetricSourceDefinition> msDefs = f.getMetricSourceDefinitions();
        assertEquals(2, msDefs.size());

        for (MetricSourceDefinition d: msDefs) {

            String name = d.getName();
            Address a = d.getAddress();

            Variable v = rootScope.getVariable(name);
            assertNotNull(v);

            assertEquals(a.getLiteral(), v.get());
        }
    }

    @Test
    public void processSources_DuplicateName_FirstValueIsDiscarded() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        String s =
                "sources:\n" +
                        "  something:\n" +
                        "    type: jboss-controller\n" +
                        "    host: host1\n" +
                        "    port: 8888\n" +
                        "  something:\n" +
                        "    type: jboss-controller\n" +
                        "    host: host2\n" +
                        "    port: 9999\n";


        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        f.processSources(o, rootScope);

        List<MetricSourceDefinition> d = f.getMetricSourceDefinitions();
        assertEquals(1, d.size());
        MetricSourceDefinition msd = d.get(0);
        assertEquals("something", msd.getName());
        assertEquals("jbosscli://host2:9999", msd.getAddress().getLiteral());
    }

    // parseSources() --------------------------------------------------------------------------------------------------

    @Test
    public void parseSources_Null() {

        String s = "sources:\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        try {

            YamlConfigurationFile.parseSources(o);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("invalid"));
            assertTrue(msg.contains(YamlConfigurationFile.SOURCES_KEY));
        }
    }

    @Test
    public void parseSources_Null2() {

        String s = "sources: \n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        try {

            YamlConfigurationFile.parseSources(o);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("invalid"));
            assertTrue(msg.contains(YamlConfigurationFile.SOURCES_KEY));
        }
    }

    @Test
    public void parseSources_NotAMap() {

        String s = "sources: 10\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        try {

            YamlConfigurationFile.parseSources(o);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("invalid"));
            assertTrue(msg.contains(YamlConfigurationFile.SOURCES_KEY));
        }
    }

    @Test
    public void parseSources_EmptySource() {

        String s = "sources:\n" +
                   "  source-1:\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        try {

            YamlConfigurationFile.parseSources(o);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("invalid empty metric source declaration"));
            assertTrue(msg.contains("source-1"));
        }
    }

    @Test
    public void parseSources_SourceNotAMapSource() {

        String s = "sources:\n" +
                   "  source-1: 20\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        try {

            YamlConfigurationFile.parseSources(o);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("invalid"));
            assertTrue(msg.contains("source-1"));
            assertTrue(msg.contains("metric source declaration"));
            assertTrue(msg.contains("not a map but a"));
        }
    }

    @Test
    public void parseSources() throws Exception {

        String s =
                "sources:\n" +
                "  some-source:\n" +
                "    type: jboss-controller\n" +
                "    host: localhost\n" +
                "    port: 9999\n" +
                "    classpath:\n" +
                "      - $JBOSS_HOME/bin/client/jboss-cli-client.jar\n" +
                "      - /some/other/file.jar\n" +
                "  some-other-source:\n" +
                "    type: jboss-controller\n" +
                "    host: other-host\n" +
                "    port: 10101\n" +
                "    username: admin\n" +
                "    password: blah\n" +
                "    classpath:\n" +
                "      - $JBOSS_HOME/bin/client/jboss-cli-client.jar\n" +
                "      - /some/other/file.jar\n";


        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.SOURCES_KEY);

        List<MetricSourceDefinition> sourceDefinitions = YamlConfigurationFile.parseSources(o);
        assertEquals(2, sourceDefinitions.size());

        MetricSourceDefinition d = sourceDefinitions.get(0);

        assertEquals("some-source", d.getName());
        assertEquals(MetricSourceType.JBOSS_CONTROLLER, d.getType());
        assertEquals(new JBossControllerAddress("jbosscli://localhost:9999/"), d.getAddress());

        MetricSourceDefinition d2 = sourceDefinitions.get(1);

        assertEquals("some-other-source", d2.getName());
        assertEquals(MetricSourceType.JBOSS_CONTROLLER, d2.getType());
        assertEquals(new JBossControllerAddress("admin", null, "other-host", 10101), d2.getAddress());
        char[] password = d2.getAddress().getPassword();
        assertEquals("blah", new String(password));
    }

    // toMetricDefinition() --------------------------------------------------------------------------------------------

    @Test
    public void toMetricDefinition_Null() throws Exception {

        Scope scope = new ScopeImpl();
        PropertyFactory pf = new PropertyFactory();

        try {

            YamlConfigurationFile.toMetricDefinition(pf, scope, null);
            fail("should have thrown exception");
        }
        catch(IllegalArgumentException e) {

            String msg = e.getMessage();
            assertTrue(msg.equals("null metric definition"));
        }
    }

    @Test
    public void toMetricDefinition() throws Exception {

        PropertyFactory pf = new PropertyFactory();
        Scope scope = new ScopeImpl();
        MetricDefinition md = YamlConfigurationFile.toMetricDefinition(pf, scope, "PhysicalMemoryTotal");
        assertEquals("PhysicalMemoryTotal", md.getId());
    }

    @Test
    public void toMetricDefinition_VariableDeclarationsAreCorrectlyResolved() throws Exception {

        Scope scope = new ScopeImpl();
        PropertyFactory pf = new PropertyFactory();

        scope.declare("some_var", "Memory");

        MetricDefinition md = YamlConfigurationFile.toMetricDefinition(pf, scope, "Physical${some_var}Total");
        assertEquals("PhysicalMemoryTotal", md.getId());
    }

    @Test
    public void toMetricDefinition_UnknownVariable() {

        PropertyFactory pf = new PropertyFactory();
        Scope scope = new ScopeImpl();

        try {

            YamlConfigurationFile.toMetricDefinition(pf, scope, "contains${some_var}");
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("no metric definition parser can understand"));
            assertTrue(msg.contains("${some_var}"));
        }
    }

    @Test
    public void toMetricDefinition_MissingVariablesStayUnresolved_NoScope() throws Exception {

        PropertyFactory pf = new PropertyFactory();

        try {

            YamlConfigurationFile.toMetricDefinition(pf, null, "contains${some_var}");
            fail("should have thrown exception");
        }
        catch(IllegalArgumentException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("null scope"));
        }
    }

    // setMetricSourceVariables() --------------------------------------------------------------------------------------

    @Test
    public void setMetricSourceVariables() throws Exception {

        Scope variables = new ScopeImpl();

        Address a = new AddressImpl("something://someuser@somehost:1000");
        Address a2 = new JmxAddress("jmx://someuser@somehost:1001");
        Address a3 = new JBossControllerAddress("jbosscli://someuser@somehost:1002");

        List<MetricSourceDefinition> definitions = Arrays.asList(
                new MetricSourceDefinitionImpl("test-metric-source-1", a),
                new MetricSourceDefinitionImpl("test-metric-source-2", a2),
                new MetricSourceDefinitionImpl("test-metric-source-3", a3),
                //
                // this metric source has a different name but the same Address as 1
                //
                new MetricSourceDefinitionImpl("test-metric-source-4", a));

        YamlConfigurationFile.setMetricSourceVariables(definitions, variables);

        Variable v = variables.getVariable("test-metric-source-1");
        Variable v2 = variables.getVariable("test-metric-source-2");
        Variable v3 = variables.getVariable("test-metric-source-3");
        Variable v4 = variables.getVariable("test-metric-source-4");

        assertEquals(a.getLiteral(), v.get());
        assertEquals(a2.getLiteral(), v2.get());
        assertEquals(a3.getLiteral(), v3.get());
        assertEquals(a.getLiteral(), v4.get());
    }

    @Test
    public void setMetricSourceVariables_DuplicateName() throws Exception {

        Scope variables = new ScopeImpl();

        Address a = new AddressImpl("something://someuser@somehost:1000");
        Address a2 = new JmxAddress("jmx://someuser@somehost:1001");

        List<MetricSourceDefinition> definitions = Arrays.asList(
                new MetricSourceDefinitionImpl("test-metric-source", a),
                new MetricSourceDefinitionImpl("test-metric-source", a2));

        try {

            YamlConfigurationFile.setMetricSourceVariables(definitions, variables);
            fail("should have thrown exceptions");
        }
        catch(DuplicateDeclarationException e) {

            String msg = e.getMessage();
            assertEquals("test-metric-source", msg);
        }
    }

    // processMetrics() ------------------------------------------------------------------------------------------------

    @Test
    public void processMetrics_Null() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        f.processMetrics(null, rootScope);

        //
        // nothing happens
        //
    }

    @Test
    public void processMetrics_NotAList() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        try {

            f.processMetrics("something that is not a List", rootScope);
            fail("should throw exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("not a list"));
        }
    }

    @Test
    public void processMetrics() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        String s =

                "metrics:\n" +
                        "  - CpuUserTime\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.METRICS_KEY);


        f.processMetrics(o, rootScope);

        List<MetricDefinition> mDefs = f.getMetricDefinitions();
        assertEquals(1, mDefs.size());

        MetricDefinition md = mDefs.get(0);

        assertEquals("CpuUserTime", md.getId());
    }

    @Test
    public void processMetrics_Variables_NotDeclared() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        String s =

                "metrics:\n" +
                        "  - ${some-ms}/something/something-else\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.METRICS_KEY);

        try {

            f.processMetrics(o, rootScope);
            fail("should throw exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("no metric definition parser can understand"));
            assertTrue(msg.contains("${some-ms}"));
        }
    }

    @Test
    public void processMetrics_Variables_Declared() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        Scope rootScope = new ScopeImpl();

        rootScope.declare("some-ms", "localOS://");

        String s =

                "metrics:\n" +
                        "  - ${some-ms}/CpuUserTime\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.METRICS_KEY);

        f.processMetrics(o, rootScope);

        List<MetricDefinition> mDefs = f.getMetricDefinitions();
        assertEquals(1, mDefs.size());

        MetricDefinition md = mDefs.get(0);
        String id = md.getId();
        assertEquals("CpuUserTime", id);
    }

    // processOutput() -------------------------------------------------------------------------------------------------

    @Test
    public void processOutput_Stdout() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "output: stdout\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.OUTPUT_KEY);

        f.processOutput(o);

        List<DataConsumer> c = f.getDataConsumers();
        assertEquals(1, c.size());
        AsynchronousCsvLineWriter a = (AsynchronousCsvLineWriter)c.get(0);
        assertEquals(System.out, a.getPrintStream());
    }

    @Test
    public void processOutput_UnknownOutputType() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "output: something\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.OUTPUT_KEY);

        try {

            f.processOutput(o);
            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("something"));
            assertTrue(msg.contains("unknown output type"));
        }
    }

    // processConsumers() ----------------------------------------------------------------------------------------------

    @Test
    public void processConsumers_NullList() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "blah:\n";

        Object o = ((Map)YamlConfigurationFile.fromYaml(
                new ByteArrayInputStream(s.getBytes()))).get(YamlConfigurationFile.CONSUMERS_KEY);

        // noop
        f.processConsumers(o);

        assertTrue(f.getDataConsumers().isEmpty());
    }

    @Test
    public void processConsumers_EmptyList() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "consumers:\n";

        Map m = (Map)YamlConfigurationFile.fromYaml(new ByteArrayInputStream(s.getBytes()));

        assertTrue(m.containsKey("consumers"));
        assertNull(m.get("consumers"));

        Object o = m.get(YamlConfigurationFile.CONSUMERS_KEY);
        assertNull(o);

        // noop
        //noinspection ConstantConditions
        f.processConsumers(o);

        assertTrue(f.getDataConsumers().isEmpty());
    }

    @Test
    public void processConsumers_NullElementList() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "consumers:\n  -\n";

        Map m = (Map)YamlConfigurationFile.fromYaml(new ByteArrayInputStream(s.getBytes()));

        List l = (List)m.get(YamlConfigurationFile.CONSUMERS_KEY);
        assertNotNull(l);

        // however, it's an empty list
        assertEquals(1, l.size());
        assertNull(l.get(0));

        try {

            f.processConsumers(l);

            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("empty"));
            assertTrue(msg.contains(YamlConfigurationFile.CONSUMERS_KEY));
        }
    }

    @Test
    public void processConsumers_ListElementAnInteger() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "consumers:\n  - 7\n";

        Map m = (Map)YamlConfigurationFile.fromYaml(new ByteArrayInputStream(s.getBytes()));
        List l = (List)m.get(YamlConfigurationFile.CONSUMERS_KEY);

        try {

            f.processConsumers(l);

            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("invalid"));
            assertTrue(msg.contains(YamlConfigurationFile.CONSUMERS_KEY));
            assertTrue(msg.contains("7"));
        }
    }

    @Test
    public void processConsumers_ClassNotFoundException() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "consumers:\n  - no.such.Class\n";

        Map m = (Map)YamlConfigurationFile.fromYaml(new ByteArrayInputStream(s.getBytes()));
        List l = (List)m.get(YamlConfigurationFile.CONSUMERS_KEY);

        try {

            f.processConsumers(l);

            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("consumer class"));
            assertTrue(msg.contains("no.such.Class"));
            assertTrue(msg.contains("not found in classpath"));
            assertTrue(e.getCause() instanceof ClassNotFoundException);
        }
    }

    @Test
    public void processConsumers_NotADataConsumerClass() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "consumers:\n  - java.lang.String\n";

        Map m = (Map)YamlConfigurationFile.fromYaml(new ByteArrayInputStream(s.getBytes()));
        List l = (List)m.get(YamlConfigurationFile.CONSUMERS_KEY);

        try {

            f.processConsumers(l);

            fail("should have thrown exception");
        }
        catch(UserErrorException e) {

            String msg = e.getMessage();
            assertTrue(msg.contains("java.lang.String"));
            assertTrue(msg.contains("not a DataConsumer class"));
        }
    }

    @Test
    public void processConsumers() throws Exception {

        YamlConfigurationFile f = new YamlConfigurationFile(false, null);

        String s = "consumers:\n  - " + MockDataConsumer.class.getName() + "\n";

        Map m = (Map)YamlConfigurationFile.fromYaml(new ByteArrayInputStream(s.getBytes()));
        List l = (List)m.get(YamlConfigurationFile.CONSUMERS_KEY);

        f.processConsumers(l);

        List<DataConsumer> c = f.getDataConsumers();
        assertEquals(1, c.size());
        MockDataConsumer mdc = (MockDataConsumer)c.get(0);
        assertNotNull(mdc);
    }

    // environment value resolution ------------------------------------------------------------------------------------

    @Test
    public void insureTheScopeSeesEnvironmentVariables() throws Exception {

        YamlConfigurationFile ycf = new YamlConfigurationFile(true, null);

        Scope s = ycf.getRootScope();

        //
        // test the availability of a common enviornment variable
        //

        String user = System.getenv("USER");
        assertNotNull(user);

        Variable v = s.getVariable("USER");

        assertNotNull(v);
        assertEquals(user, v.get());
    }

    // TODO B3ke3y
    // @Test
    public void insureEnvironmentVariablesAreResolved() throws Exception {

        File f = new File(System.getProperty("basedir"),
                "src/test/resources/data/configuration/configuration-with-environment-variables.yaml");

        assertTrue(f.isFile());

        YamlConfigurationFile c = new YamlConfigurationFile(true, f.getPath());

        int si = c.getSamplingIntervalSec();
        assertEquals(7, si);

        fail("TODO continue with testing all other configuration elements");
    }

    // Package protected -----------------------------------------------------------------------------------------------

    // Protected -------------------------------------------------------------------------------------------------------

    @Override
    protected Configuration getConfigurationToTest(boolean foreground, String fileName) throws Exception {

        return new YamlConfigurationFile(foreground, fileName);
    }

    @Override
    protected String getReferenceFileName() {

        File f = new File(System.getProperty("basedir"), "src/test/resources/data/configuration/reference-yaml.yaml");
        assertTrue(f.isFile());
        return f.getPath();
    }

    @Override
    protected String getConfigurationFileName(String basename) {

        File f = new File(System.getProperty("basedir"), "src/test/resources/data/configuration/" + basename + ".yaml");
        assertTrue(f.isFile());
        return f.getPath();
    }

    // Private ---------------------------------------------------------------------------------------------------------

    // Inner classes ---------------------------------------------------------------------------------------------------

}

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

package io.novaordis.databot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.novaordis.databot.configuration.Configuration;
import io.novaordis.databot.event.MultiSourceReadingEvent;
import io.novaordis.databot.failure.DataBotException;
import io.novaordis.databot.failure.EventQueueFullException;
import io.novaordis.databot.task.SourceQueryTask;
import io.novaordis.events.api.event.Event;
import io.novaordis.events.api.event.Property;
import io.novaordis.events.api.event.TimedEvent;
import io.novaordis.events.api.metric.MetricDefinition;
import io.novaordis.events.api.metric.MetricSource;
import io.novaordis.events.api.metric.MetricSourceDefinition;
import io.novaordis.utilities.address.Address;

/**
 * A timer task that insure the sources are started, starts them if they're not, collects the required metrics, wraps
 * them into a TimedEvent instance and puts the event in the event queue.
 *
 * @author Ovidiu Feodorov <ovidiu@novaordis.com>
 * @since 7/29/16
 */
public class DataCollectionTask extends TimerTask {

    // Constants -------------------------------------------------------------------------------------------------------

    private static final Logger log = LoggerFactory.getLogger(DataCollectionTask.class);
    private static final boolean debug = log.isDebugEnabled();

    //
    // counts how many executions were triggered since this task was created
    //
    private volatile long executionCount;
    private volatile long successfulExecutionCount;

    //
    // the number of executions after which this timer task exits. null means unlimited number of executions.
    //
    private volatile Long maxExecutions;

    //
    // we collect the last cause of data run failure
    //
    private volatile Throwable causeOfLastFailure;

    // Static ----------------------------------------------------------------------------------------------------------

    public static String toLogMessage(Throwable t) {

        if (t == null) {

            return null;
        }

        String msg = t.getMessage();

        if (msg == null) {

            return t.getClass().getSimpleName() + " with no message, see stack trace below for more details";
        }

        return msg + " (" + t.getClass().getSimpleName() + ")";

    }

    // Attributes ------------------------------------------------------------------------------------------------------

    private DataBot dataBot;

    // Constructors ----------------------------------------------------------------------------------------------------

    public DataCollectionTask(DataBot dataBot) {

        setDataBot(dataBot);
    }

    // TimerTask overrides ---------------------------------------------------------------------------------------------

    @Override
    public void run() {

        executionCount ++;

        long t0 = System.currentTimeMillis();

        try {

            log.info(this + " executing data collection run");

            dataCollectionRun();

            //
            // the completion of the data collection run is logged by the underlying layers, where we have un-cached
            // information about the metric sources that were queried
            //

            successfulExecutionCount ++;

        }
        catch (Throwable t) {

            long t1 = System.currentTimeMillis();

            causeOfLastFailure = t;

            //
            // no matter of what happens during a data collection run, do not exit - keep going until explicitely
            // stopped; report the errors, though. The exceptions must not bubble up because an unchecked exception
            // cancels the timer.
            //

            log.error("data collection run (" + (t1 - t0) + " ms) failed: " + toLogMessage(t), t);
        }

        if (maxExecutions != null && executionCount == maxExecutions) {

            //
            // we're done, notify the DataBot instance that we won't run anymore
            //

            log.debug(this + " completed " + executionCount + " executions, exiting ...");

            dataBot.collectionTaskDone();
        }
    }

    // Public ----------------------------------------------------------------------------------------------------------

    public DataBot getDataBot() {

        return dataBot;
    }

    /**
     * @return the cause of the last data run failure, if any. May return null if we did not experience any
     * failure so far.
     */
    public Throwable getCauseOfLastFailure() {

        return causeOfLastFailure;
    }

    public String toString() {

        return dataBot == null ? "UNINITIALIZED" : "" + dataBot.getId();
    }

    /**
     * @return the number of executions after which this timer task exits. May return null, which means the task will
     * be executed an unlimited number of times.
     */
    public Long getMaxExecutions() {

        return maxExecutions;
    }

    public void setMaxExecutions(Long l) {

        log.debug(this + " setting max executions to " + l + (l == null ? " (unlimited)" : ""));

        this.maxExecutions = l;
    }

    // Package protected -----------------------------------------------------------------------------------------------

    /**
     * The method collects all declared metrics, consolidates them in a TimeEvent and places the event on the internal
     * event queue. Even if the method throws unchecked exceptions, the calling layer will correctly handle those.
     *
     * @exception DataBotException exceptional conditions during the data collection run. The upper layer will
     *  handle appropriately.
     */
    void dataCollectionRun() throws DataBotException {

        TimedEvent event = collectMetrics();

        BlockingQueue<Event> eventQueue = dataBot.getEventQueue();

        log.debug("placing event '" + event + "' in " + Util.queueLogLabel(eventQueue));

        boolean sent = eventQueue.offer(event);

        if (!sent) {

            //
            // we will just drop the event and notify the upper layer
            //

            throw new EventQueueFullException();
        }
    }

    /**
     * Must strive to handle all exceptions internally and not attempt to bubble them up. If exceptions occur, they
     * should be properly logged as ERROR, at this level. The calling layer will handle runaway exceptions, though.
     *
     * @return a timed event where readings for each metric source are encapsulated in EventProperties, one
     * EventProperty for each metric source. The EventProperty instances are added to the top-level event in the order
     * in which they are specified in the configuration.
     */
    TimedEvent collectMetrics() {

        if (log.isTraceEnabled()) {

            log.trace(this + " collecting metrics ...");
        }

        Configuration configuration = dataBot.getConfiguration();

        Map<Address, Future<List<Property>>> addressToFuture = new HashMap<>();
        List<Address> orderedListOfSources = new ArrayList<>();

        //noinspection Convert2streamapi
        for(MetricSourceDefinition sd: configuration.getMetricSourceDefinitions()) {

            Address sourceAddress = sd.getAddress();
            addressToFuture.put(sourceAddress, null);
            orderedListOfSources.add(sourceAddress);
        }

        MultiSourceReadingEvent msre = new MultiSourceReadingEvent();

        long t0 = System.currentTimeMillis();

        for(Address a: addressToFuture.keySet()) {

            //
            // dispatch an internal thread per source to collect metrics
            //

            MetricSource ms = dataBot.getMetricSource(a);

            List<MetricDefinition> metricsForSource = configuration.getMetricDefinitions(a);

            SourceQueryTask q = new SourceQueryTask(ms, metricsForSource);

            log.debug(this + " submitting data collection task for " + a + " to a source-handling thread");

            Future<List<Property>> future = dataBot.getSourceExecutor().submit(q);

            addressToFuture.put(a, future);
        }

        //
        // wait for metric values or metric source failure
        //

        int countOfSourcesThatFailed = 0;

        for(Address a: orderedListOfSources) {

            List<Property> properties = null;
            Future<List<Property>> future = addressToFuture.get(a);

            try {

                properties = future.get();
            }
            catch (InterruptedException e) {

                log.warn(Thread.currentThread().getName() + " interrupted while waiting for metric collection results");
            }
            catch (ExecutionException e) {

                countOfSourcesThatFailed++;
                Throwable cause = e.getCause();
                log.error("source " + a + " collection failed: ", cause);
            }
            finally {

                //
                // add the properties, even if it is an empty list, on failure, to update the source list and
                // collection timestamps
                //
                properties = properties == null ? Collections.emptyList() : properties;
                msre.addSourceReading(a, properties);
            }
        }

        if (debug) {

            log.debug("collection for " + addressToFuture.size() + " source(s) completed in " +
                    (msre.getCollectionEndTimestamp() - t0) + " ms" +
                    (countOfSourcesThatFailed == 0 ?
                            "" : ", " + countOfSourcesThatFailed + " source(s) failed during collection") +
                    ", " + msre.getAllPropertiesCount() + " properties collected");
        }

        if (log.isTraceEnabled() && msre.getAllPropertiesCount() > 0) {

            log.trace("collected properties:\n" + displayProperties(msre));
        }

        log.info(this + " completed data collection from " +
                displayMetricSourceAddressesInOrder(addressToFuture.keySet()) + " in " +
                (System.currentTimeMillis() - t0) + " ms");

        return msre;
    }

    /**
     * @return the number of times the data collection run was executed since this instance was created. Not all
     *  runs are necessarily successful. To get the number of successful runs, use getSuccessfulExecutionCount()
     *
     *  @see DataCollectionTask#getSuccessfulExecutionCount()
     */
    long getExecutionCount() {

        return executionCount;
    }

    /**
     * @return the number of successful data collection runs since this instance was created.
     *
     *  @see DataCollectionTask#getExecutionCount()
     */
    long getSuccessfulExecutionCount() {

        return successfulExecutionCount;
    }

    void setDataBot(DataBot dataBot) {

        this.dataBot = dataBot;
    }

    // Protected -------------------------------------------------------------------------------------------------------

    // Private ---------------------------------------------------------------------------------------------------------

    private String displayProperties(MultiSourceReadingEvent msre) {

        List<Address> addresses = msre.getSourceAddresses();

        String s = "";
        int index = 0;

        for(Iterator<Address> ai = addresses.iterator(); ai.hasNext(); ) {

            Address a = ai.next();
            List<Property> props = msre.getPropertiesForSource(a);

            for (Iterator<Property> pi = props.iterator(); pi.hasNext(); index++) {

                Property p = pi.next();

                String addressLiteral = a.getLiteral();


                s += "  " + index + ": " + addressLiteral + (addressLiteral.endsWith("/") ? "" : "/") +
                        p.getName() + "(" + p.getType() + "): " + p.getValue();

                if (pi.hasNext()) {

                    s += "\n";
                }
            }

            if (ai.hasNext()) {

                s += "\n";
            }
        }

        return s;
    }

    private String displayMetricSourceAddressesInOrder(Set<Address> addresses) {

        String s = "";

        List<String> sortedAddresses = new ArrayList<>();

        //noinspection Convert2streamapi
        for(Address a: addresses) {

            sortedAddresses.add(a.getLiteral());
        }

        Collections.sort(sortedAddresses);

        if (sortedAddresses.isEmpty()) {

            return "zero sources";
        }

        for(Iterator i = sortedAddresses.iterator(); i.hasNext(); ) {

            s += i.next();

            if (i.hasNext()) {

                s += ", ";
            }
        }
        return s;
    }

    // Inner classes ---------------------------------------------------------------------------------------------------

}

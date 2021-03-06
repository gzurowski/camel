/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.Throttler;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;throttle/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "throttle")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThrottleDefinition extends ExpressionNode implements ExecutorServiceAwareDefinition<ThrottleDefinition> {
    // TODO: Camel 3.0 Should not support outputs

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private Long timePeriodMillis;
    @XmlAttribute
    private Boolean asyncDelayed;
    @XmlAttribute
    private Boolean callerRunsWhenRejected;
    @XmlAttribute
    private Boolean rejectExecution;
    
    public ThrottleDefinition() {
    }

    public ThrottleDefinition(Expression maximumRequestsPerPeriod) {
        super(maximumRequestsPerPeriod);
    }

    @Override
    public String toString() {
        return "Throttle[" + description() + " -> " + getOutputs() + "]";
    }
    
    protected String description() {
        return getExpression() + " request per " + getTimePeriodMillis() + " millis";
    }

    @Override
    public String getShortName() {
        return "throttle";
    }

    @Override
    public String getLabel() {
        return "throttle[" + description() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, isAsyncDelayed());
        ScheduledExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredScheduledExecutorService(routeContext, "Throttle", this, isAsyncDelayed());
        
        // should be default 1000 millis
        long period = getTimePeriodMillis() != null ? getTimePeriodMillis() : 1000L;

        // max requests per period is mandatory
        Expression maxRequestsExpression = createMaxRequestsPerPeriodExpression(routeContext);
        if (maxRequestsExpression == null) {
            throw new IllegalArgumentException("MaxRequestsPerPeriod expression must be provided on " + this);
        }

        Throttler answer = new Throttler(routeContext.getCamelContext(), childProcessor, maxRequestsExpression, period, threadPool, shutdownThreadPool, isRejectExecution());

        if (getAsyncDelayed() != null) {
            answer.setAsyncDelayed(getAsyncDelayed());
        }
        
        if (getCallerRunsWhenRejected() == null) {
            // should be true by default
            answer.setCallerRunsWhenRejected(true);
        } else {
            answer.setCallerRunsWhenRejected(getCallerRunsWhenRejected());
        }
        return answer;
    }

    private Expression createMaxRequestsPerPeriodExpression(RouteContext routeContext) {
        if (getExpression() != null) {
            if (ObjectHelper.isNotEmpty(getExpression().getExpression()) || getExpression().getExpressionValue() != null) {
                return getExpression().createExpression(routeContext);
            } 
        } 
        return null;
    }
    
    // Fluent API
    // -------------------------------------------------------------------------
    /**
     * Sets the time period during which the maximum request count is valid for
     *
     * @param timePeriodMillis  period in millis
     * @return the builder
     */
    public ThrottleDefinition timePeriodMillis(long timePeriodMillis) {
        setTimePeriodMillis(timePeriodMillis);
        return this;
    }
    
    /**
     * Sets the time period during which the maximum request count per period
     *
     * @param maximumRequestsPerPeriod  the maximum request count number per time period
     * @return the builder
     */
    public ThrottleDefinition maximumRequestsPerPeriod(Long maximumRequestsPerPeriod) {
        setExpression(ExpressionNodeHelper.toExpressionDefinition(ExpressionBuilder.constantExpression(maximumRequestsPerPeriod)));
        return this;
    }

    /**
     * Whether or not the caller should run the task when it was rejected by the thread pool.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param callerRunsWhenRejected whether or not the caller should run
     * @return the builder
     */
    public ThrottleDefinition callerRunsWhenRejected(boolean callerRunsWhenRejected) {
        setCallerRunsWhenRejected(callerRunsWhenRejected);
        return this;
    }

    /**
     * Enables asynchronous delay which means the thread will <b>noy</b> block while delaying.
     *
     * @return the builder
     */
    public ThrottleDefinition asyncDelayed() {
        setAsyncDelayed(true);
        return this;
    }
    
    /**
     * Whether or not throttler throws the RejectExceutionException when the exchange exceeds the request limit
     * <p/>
     * Is by default <tt>false</tt>
     *
     * @param throw the RejectExecutionException if the exchange exceeds the request limit 
     * @return the builder
     */
    public ThrottleDefinition rejectExecution(boolean rejectExecution) {
        setRejectExecution(rejectExecution);
        return this;
    }

    public ThrottleDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    public ThrottleDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }
    
    

    // Properties
    // -------------------------------------------------------------------------

    public Long getTimePeriodMillis() {
        return timePeriodMillis;
    }

    public void setTimePeriodMillis(Long timePeriodMillis) {
        this.timePeriodMillis = timePeriodMillis;
    }

    public Boolean getAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(Boolean asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    public boolean isAsyncDelayed() {
        return asyncDelayed != null && asyncDelayed;
    }

    public Boolean getCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(Boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }
    
    public boolean isRejectExecution() {
        return rejectExecution != null ? rejectExecution : false;
    }

    public void setRejectExecution(Boolean rejectExecution) {
        this.rejectExecution = rejectExecution;
    }
}

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
package org.apache.camel.maven;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.Log4JLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

/**
 * Base class for Api based code generation MOJOs.
 */
public abstract class AbstractGeneratorMojo extends AbstractMojo {

    protected static final String PREFIX = "org.apache.camel.";
    protected static final String OUT_PACKAGE = PREFIX + "component.internal";
    protected static final String COMPONENT_PACKAGE = PREFIX + "component";
    private static final String LOGGER_PREFIX = "log4j.logger";

    // used for velocity logging, to avoid creating velocity.log
    protected final Logger LOG = Logger.getLogger(this.getClass());

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/camel-component")
    protected File generatedSrcDir;

    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/camel-component")
    protected File generatedTestDir;

    @Parameter(defaultValue = OUT_PACKAGE)
    protected String outPackage;

    @Parameter(required = true, property = PREFIX + "scheme")
    protected String scheme;

    @Parameter(required = true, property = PREFIX + "componentName")
    protected String componentName;

    @Parameter(defaultValue = COMPONENT_PACKAGE)
    protected String componentPackage;

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    private VelocityEngine engine;
    private ClassLoader projectClassLoader;

    protected AbstractGeneratorMojo() {
        // configure Log4J from system properties
        for (String propertyName : System.getProperties().stringPropertyNames())
        {
            if (propertyName.startsWith(LOGGER_PREFIX)) {
                String loggerName = propertyName.substring(LOGGER_PREFIX.length());
                String levelName = System.getProperty(propertyName, "");
                Level level = Level.toLevel(levelName); // defaults to DEBUG
                if (!"".equals(levelName) && !levelName.toUpperCase().equals(level.toString())) {
                    LOG.warn("Skipping unrecognized log4j log level " + levelName + ": -D" + propertyName + "=" + levelName);
                    continue;
                }
                LOG.debug("Setting " + loggerName + " => " + level.toString());
                Logger.getLogger(loggerName).setLevel(level);
            }
        }
    }

    public VelocityEngine getEngine() {
        if (engine == null) {
            // initialize velocity to load resources from class loader and use Log4J
            Properties velocityProperties = new Properties();
            velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADER, "cloader");
            velocityProperties.setProperty("cloader.resource.loader.class", ClasspathResourceLoader.class.getName());
            velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, Log4JLogChute.class.getName());
            velocityProperties.setProperty(Log4JLogChute.RUNTIME_LOG_LOG4J_LOGGER, LOG.getName());
            engine = new VelocityEngine(velocityProperties);
            engine.init();
        }
        return engine;
    }

    protected ClassLoader getProjectClassLoader() throws MojoExecutionException {
        if (projectClassLoader == null)  {
            final List classpathElements;
            try {
                classpathElements = project.getTestClasspathElements();
            } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            final URL[] urls = new URL[classpathElements.size()];
            int i = 0;
            for (Iterator it = classpathElements.iterator(); it.hasNext(); i++) {
                try {
                    urls[i] = new File((String) it.next()).toURI().toURL();
                    LOG.debug("Adding project path " + urls[i]);
                } catch (MalformedURLException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
            final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            projectClassLoader = new URLClassLoader(urls, tccl != null ? tccl : getClass().getClassLoader());
        }
        return projectClassLoader;
    }

    protected void mergeTemplate(VelocityContext context, File outFile, String templateName) throws MojoExecutionException {
        // ensure parent directories exist
        outFile.getParentFile().mkdirs();

        // add generated date
        context.put("generatedDate", new Date().toString());
        // add output package
        context.put("packageName", outPackage);

        // load velocity template
        final Template template = getEngine().getTemplate(templateName, "UTF-8");

        // generate file
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outFile));
            template.merge(context, writer);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (VelocityException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) {}
            }
        }
    }
}

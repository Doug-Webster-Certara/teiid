/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.teiid.jboss;

import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.teiid.adminapi.Admin;
import org.teiid.adminapi.AdminException;
import org.teiid.adminapi.VDB.Status;
import org.teiid.adminapi.impl.VDBMetaData;
import org.teiid.adminapi.jboss.AdminFactory;
import org.teiid.core.util.PropertiesUtils;
import org.teiid.deployers.CompositeVDB;
import org.teiid.deployers.RestWarGenerator;
import org.teiid.deployers.VDBLifeCycleListener;
import org.teiid.deployers.VDBRepository;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class ResteasyEnabler implements VDBLifeCycleListener, Service {

    private static String VERSION_DELIM = PropertiesUtils.getHierarchicalProperty("org.teiid.rest.versionDelim", "_"); //$NON-NLS-1$ //$NON-NLS-2$

    protected final Supplier<ModelControllerClientFactory> controllerValue;
    protected final Supplier<Executor> executorInjector;
    final Supplier<VDBRepository> vdbRepoInjector;

    final private RestWarGenerator generator;

    public ResteasyEnabler(RestWarGenerator generator, Supplier<ModelControllerClientFactory> modelContDep, Supplier<Executor> exDep, Supplier<VDBRepository> repDep) {
        this.generator = generator;
        this.controllerValue = modelContDep;
        this.executorInjector = exDep;
        this.vdbRepoInjector = repDep;
    }

    @Override
    public void added(String name, CompositeVDB vdb) {
    }

    @Override
    public void beforeRemove(String name, CompositeVDB cvdb) {
        if (cvdb != null) {
            //TODO: remove the war
        }
    }

    @Override
    public void finishedDeployment(String name, CompositeVDB cvdb) {
        final VDBMetaData vdb = cvdb.getVDB();

        if (!vdb.getStatus().equals(Status.ACTIVE)) {
            return;
        }

        final String warName = buildName(name, cvdb.getVDB().getVersion());
        if (generator.hasRestMetadata(vdb)) {
            final Runnable job = new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] warContents = generator.getContent(vdb, warName);
                        if (!vdb.getStatus().equals(Status.ACTIVE)) {
                            return;
                        }
                        if (warContents != null) {
                            //make it a non-persistent deployment
                            getAdmin().deploy(warName, new ByteArrayInputStream(warContents), false);
                        }
                    } catch (IOException e) {
                        LogManager.logWarning(LogConstants.CTX_RUNTIME, e,
                                IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50109, warName));
                    } catch (AdminException e) {
                        LogManager.logWarning(LogConstants.CTX_RUNTIME, e,
                                IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50109, warName));
                    }
                }
            };
            getExecutor().execute(job);
        }
    }

    @Override
    public void removed(String name, CompositeVDB cvdb) {

    }

    private String buildName(String name, String version) {


        return name+VERSION_DELIM+version +".war"; //$NON-NLS-1$
    }

    @Override
    public void start(StartContext arg0) throws StartException {
        this.vdbRepoInjector.get().addListener(this);
    }

    @Override
    public void stop(StopContext arg0) {
    }

    Admin getAdmin() {
        return AdminFactory.getInstance()
        .createAdmin(controllerValue.get().createClient(executorInjector.get()));
    }

    Executor getExecutor() {
        return executorInjector.get();
    }
}

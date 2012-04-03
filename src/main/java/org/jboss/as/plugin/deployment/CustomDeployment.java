/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.plugin.deployment;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Date: 11.07.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class CustomDeployment {

    enum Status {
        FAILURE,
        SUCCESS
    }

    enum Type {
        DEPLOY,
        REDEPLOY,
        UNDEPLOY;

        static boolean isValid(final String type) {
            return (parse(type) != null);
        }

        static Type parse(final String type) {
            for (Type t : Type.values()) {
                if (t.name().equalsIgnoreCase(type)) {
                    return t;
                }
            }
            return null;
        }
    }

    private final String hostname;
    private final int port;
    private final File archive;
    private Type type;

    private final List<Throwable> errors = new LinkedList<Throwable>();

    CustomDeployment(final String hostname, final int port, final File archive, final Type type) {
        this.hostname = hostname;
        this.port = port;
        this.archive = archive;
        this.type = type;
    }

    static CustomDeployment of(final String hostname, final int port, final File archive, final Type type) {
        return new CustomDeployment(hostname, port, archive, type);
    }

    public Status execute() throws IOException {
        Status status = Status.SUCCESS;
        final ModelControllerClient client = ModelControllerClient.Factory.create(hostname, port);
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        final DeploymentPlanBuilder builder = manager.newDeploymentPlan();
        final DeploymentPlan plan;
        switch (type) {
            case DEPLOY:
                plan = builder.add(archive).deploy(archive.getName()).build();
                break;
            case REDEPLOY: {
                plan = builder.replace(archive).redeploy(archive.getName()).build();
                break;
            }
            case UNDEPLOY: {
                plan = builder.undeploy(archive.getName()).remove(archive.getName()).build();
                break;
            }
            default:
                plan = null;
                break;
        }
        if (plan == null) {
            throw new IllegalStateException("Invalid type: " + type);
        }

        if (plan.getDeploymentActions().size() > 0) {
            try {
                final ServerDeploymentPlanResult planResult = manager.execute(plan).get();
                // Check the results
                for (DeploymentAction action : plan.getDeploymentActions()) {
                    final ServerDeploymentActionResult actionResult = planResult.getDeploymentActionResult(action.getId());
                    final ServerUpdateActionResult.Result result = actionResult.getResult();
                    switch (result) {
                        case FAILED:
                        case NOT_EXECUTED:
                        case ROLLED_BACK: {
                            errors.add(actionResult.getDeploymentException());
                            status = Status.FAILURE;
                            break;
                        }
                        case CONFIGURATION_MODIFIED_REQUIRES_RESTART:
                            // Should show warning
                            break;
                        default:
                            break;
                    }
                }

            } catch (InterruptedException e) {
                errors.add(e);
                status = Status.FAILURE;
            } catch (ExecutionException e) {
                errors.add(e);
                status = Status.FAILURE;
            }
        }
        return status;
    }

    public Collection<Throwable> getErrors() {
        if (errors.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(errors);
    }

    public File getArchive() {
        return archive;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public Type getType() {
        return type;
    }

    void setType(final Type type) {
        this.type = type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final CustomDeployment that = (CustomDeployment) o;

        if (port != that.port) return false;
        if (archive != null ? !archive.equals(that.archive) : that.archive != null) return false;
        if (hostname != null ? !hostname.equals(that.hostname) : that.hostname != null) return false;
        return type == that.type;

    }

    @Override
    public int hashCode() {
        int result = hostname != null ? hostname.hashCode() : 0;
        result = 31 * result + port;
        result = 31 * result + (archive != null ? archive.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
}

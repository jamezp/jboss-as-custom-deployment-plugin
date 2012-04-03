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

import static org.jboss.as.plugin.deployment.CustomDeployment.Type;
import static org.jboss.as.plugin.deployment.CustomDeployment.Type.DEPLOY;

import java.io.File;
import java.io.IOException;

/**
 * Date: 11.07.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Main {
    private static final String PARAM_USAGE_FORMAT = "   %-20s - %s%n";

    public static void main(final String[] args) throws IOException {
        String hostname = "localhost";
        int port = 9999;
        File archive = null;
        Type type = DEPLOY;
        int iterations = 1;

        // At least one parameter must be passed
        if (args == null || args.length < 1) {
            printUsage();
            System.exit(1);
        }

        // Parse incoming parameters
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if ("-hostname".equalsIgnoreCase(arg)) {
                hostname = args[++i];
            } else if ("-port".equalsIgnoreCase(arg)) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    System.out.printf("Error: %s%n%n", e.getMessage());
                    printUsage();
                    System.exit(1);
                }
            } else if ("-type".equalsIgnoreCase(arg)) {
                final String s = args[++i];
                if (Type.isValid(s)) {
                    type = Type.parse(s);
                } else {
                    System.out.printf("Type %s is an invalid type.%n%n", s);
                    printUsage();
                    System.exit(1);
                }
            } else if ("-iterations".equalsIgnoreCase(arg)) {
                iterations = Integer.parseInt(args[++i]);
            } else {
                // arg should be a file and the file must exist
                final File file = new File(arg);
                if (file.exists()) {
                    archive = file;
                } else {
                    System.out.printf("File %s does not exist.%n%n", file.getAbsolutePath());
                    printUsage();
                    System.exit(1);
                }
            }
        }

        // Create the custom deployment
        final CustomDeployment deployment = new CustomDeployment(hostname, port, archive, type);
        for (int i = 0; i < iterations; i++) {
            switch (deployment.execute()) {
                case SUCCESS: {
                    System.out.printf("Deployment was successful for archive %s.%n", deployment.getArchive());
                    break;
                }
                case FAILURE: {
                    System.out.printf("Deployment failed for archive %s.%n", deployment.getArchive());
                    System.out.println("  Errors:");
                    for (Throwable t : deployment.getErrors()) {
                        System.out.printf("   %s%n", t.getMessage());
                    }
                    System.exit(1);
                }
                default: {
                    System.out.println("Invalid response from the deployment.");
                }
            }
            if (type == DEPLOY) {
                deployment.setType(Type.REDEPLOY);
            } else {
                break;
            }
        }
        System.exit(0);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.printf(PARAM_USAGE_FORMAT, "-hostname <hostname>", "The host name to connect to. Default is localhost.");
        System.out.printf(PARAM_USAGE_FORMAT, "-port <port>", "The port to connect to. Default is 9999.");
        System.out.printf(PARAM_USAGE_FORMAT, "-type <type>", "The type of the deployment. Valid values are: DEPLOY, REDEPLOY and UNDEPLOY. The default is DEPLOY");
        System.out.printf(PARAM_USAGE_FORMAT, "archiveFile", "The archive file to execute the deployment type on.");
    }
}

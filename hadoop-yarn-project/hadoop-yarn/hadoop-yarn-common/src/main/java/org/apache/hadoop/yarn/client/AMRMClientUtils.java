/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.client;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;

import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceAudience.Public;
import org.apache.hadoop.classification.InterfaceStability.Unstable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.security.SaslRpcServer;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for AMRMClient.
 */
@Private
public final class AMRMClientUtils {
  private static final Logger LOG =
      LoggerFactory.getLogger(AMRMClientUtils.class);

  public static final String APP_ALREADY_REGISTERED_MESSAGE =
      "Application Master is already registered : ";

  private AMRMClientUtils() {
  }

  /**
   * Create a proxy for the specified protocol.
   *
   * @param configuration Configuration to generate {@link ClientRMProxy}
   * @param protocol Protocol for the proxy
   * @param user the user on whose behalf the proxy is being created
   * @param token the auth token to use for connection
   * @param <T> Type information of the proxy
   * @return Proxy to the RM
   * @throws IOException on failure
   */
  @Public
  @Unstable
  public static <T> T createRMProxy(final Configuration configuration,
      final Class<T> protocol, UserGroupInformation user,
      final Token<? extends TokenIdentifier> token) throws IOException {
    try {
      String rmClusterId = configuration.get(YarnConfiguration.RM_CLUSTER_ID,
          "yarn_cluster");
      LOG.info("Creating RMProxy to RM {} for protocol {} for user {}",
          rmClusterId, protocol.getSimpleName(), user);
      if (token != null) {
        // preserve the token service sent by the RM when adding the token
        // to ensure we replace the previous token setup by the RM.
        // Afterwards we can update the service address for the RPC layer.
        // Same as YarnServerSecurityUtils.updateAMRMToken()
        user.addToken(token);
        token.setService(ClientRMProxy.getAMRMTokenService(configuration));
        setAuthModeInConf(configuration);
      }
      final T proxyConnection = user.doAs(new PrivilegedExceptionAction<T>() {
        @Override
        public T run() throws Exception {
          return ClientRMProxy.createRMProxy(configuration, protocol);
        }
      });
      return proxyConnection;

    } catch (InterruptedException e) {
      throw new YarnRuntimeException(e);
    }
  }

  private static void setAuthModeInConf(Configuration conf) {
    conf.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION,
        SaslRpcServer.AuthMethod.TOKEN.toString());
  }
}
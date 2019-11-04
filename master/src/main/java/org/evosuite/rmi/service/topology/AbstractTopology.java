/**
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.rmi.service.topology;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.evosuite.Properties;
import org.evosuite.rmi.service.ClientNodeRemote;
import org.evosuite.rmi.service.ClientState;
import org.evosuite.rmi.service.ClientStateInformation;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTopology implements Topology {

  private static Logger logger = LoggerFactory.getLogger(AbstractTopology.class);

  protected final Map<String, ClientNodeRemote> clients =
      new ConcurrentHashMap<String, ClientNodeRemote>();

  /**
   * It is important to keep track of client states for debugging reasons. For example, if client
   * crash, could be useful to know in which state it was. We cannot query the client directly in
   * those cases, because it is crashed... The "key" is the RMI identifier of the client
   */
  protected final Map<String, ClientState> clientStates =
      new ConcurrentHashMap<String, ClientState>();

  protected final Map<String, ClientStateInformation> clientStateInformation =
      new ConcurrentHashMap<String, ClientStateInformation>();

  //
  // Clients
  //

  public void registerClientNode(final String clientIdentifier, final ClientNodeRemote clientNode) {
    synchronized (clients) {
      clients.put(clientIdentifier, clientNode);
      clients.notifyAll();
    }
  }

  public ClientNodeRemote getClientNode(final String clientIdentifier) {
    return clients.get(clientIdentifier);
  }

  public void informChangeOfStateInClient(final String clientIdentifier,
      final ClientState clientState, final ClientStateInformation clientInformation) {
    clientStates.put(clientIdentifier, clientState);
    // To be on the safe side
    clientInformation.setState(clientState);
    clientStateInformation.put(clientIdentifier, clientInformation);
  }

  public Map<String, ClientNodeRemote> getClientsOnceAllConnected(long timeoutInMs)
      throws InterruptedException {
    long start = System.currentTimeMillis();

    int numberOfExpectedClients = Properties.NUM_PARALLEL_CLIENTS;

    synchronized (clients) {
      while (clients.size() != numberOfExpectedClients) {
        long elapsed = System.currentTimeMillis() - start;
        long timeRemained = timeoutInMs - elapsed;
        if (timeRemained <= 0) {
          return null;
        }
        clients.wait(timeRemained);
      }
      return Collections.unmodifiableMap(clients);
    }
  }

  public void cancelAllClients() {
    for (ClientNodeRemote client : clients.values()) {
      try {
        LoggingUtils.getEvoLogger().info("Trying to kill client " + client);
        client.cancelCurrentSearch();
      } catch (RemoteException e) {
        logger.warn("Error while trying to cancel client: " + e);
        e.printStackTrace();
      }
    }
  }

  //
  // States
  //

  public Collection<ClientState> getCurrentStates() {
    return clientStates.values();
  }

  public ClientState getCurrentState(final String clientIdentifier) {
    return clientStates.get(clientIdentifier);
  }

  public String getSummaryOfClientStatuses() {
    if (clientStates.isEmpty()) {
      return "No client has registered";
    }
    StringBuilder summary = new StringBuilder();
    for (String id : clientStates.keySet()) {
      ClientState state = clientStates.get(id);
      summary.append(id + ": " + state + "\n");
    }
    return summary.toString();
  }

  //
  // Information
  //

  public Collection<ClientStateInformation> getCurrentStateInformation() {
    return clientStateInformation.values();
  }
}

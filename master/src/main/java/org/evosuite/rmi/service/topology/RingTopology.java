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

import org.evosuite.Properties;
import org.evosuite.rmi.service.ClientNodeRemote;
import org.evosuite.rmi.service.ClientState;

/**
 * Implements a ring topology.
 */
public class RingTopology extends AbstractTopology {

  @Override
  public ClientNodeRemote selectReceiver(final String senderIdentifier) {
    int idSender = Integer.parseInt(senderIdentifier.replaceAll("[^0-9]", ""));
    int idNeighbour = (idSender + 1) % Properties.NUM_PARALLEL_CLIENTS;

    while (!ClientState.SEARCH.equals(clientStates.get("ClientNode" + idNeighbour))
        && idNeighbour != idSender) {
      idNeighbour = (idNeighbour + 1) % Properties.NUM_PARALLEL_CLIENTS;
    }

    if (idNeighbour != idSender) {
      ClientNodeRemote node = clients.get("ClientNode" + idNeighbour);
      return node;
    }

    return null;
  }

}

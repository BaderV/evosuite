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

import java.util.Map;
import org.evosuite.ClientProcess;
import org.evosuite.Properties;
import org.evosuite.rmi.service.ClientNodeRemote;
import org.evosuite.utils.Randomness;

/**
 * Implements a hypercube topology.
 * 
 * TODO: As of 19/Nov/2019 the hypercube topology only supports 2, 4, and 8 clients. Ideally, it
 * should support any number of clients, i.e., it should be able to represent any cube with
 * n = 2 ^ number of clients.
 */
public class HypercubeTopology extends AbstractTopology {

  private NVertex nVertex = null;

  public HypercubeTopology() {
    switch (Properties.NUM_PARALLEL_CLIENTS) {
      case 2:
        this.nVertex = new TwoVertex();
        break;
      case 4:
        this.nVertex = new FourVertex();
        break;
      case 8:
        this.nVertex = new EightVertex();
        break;
      default:
        throw new RuntimeException("Number of clients not supported by the Hypercube topology");
    }
  }

  @Override
  public synchronized void registerClientNode(final String clientIdentifier,
      final ClientNodeRemote clientNode) {
    super.registerClientNode(clientIdentifier, clientNode);
  }

  @Override
  public ClientNodeRemote selectReceiver(final String senderIdentifier) {
    return this.nVertex.selectReceiver(this.clients, senderIdentifier);
  }

  public interface NVertex {
    public ClientNodeRemote selectReceiver(final Map<String, ClientNodeRemote> clients,
        final String senderIdentifier);

    default String getId(String... ids) {
      int randomIndex = Randomness.nextInt(ids.length);
      assert randomIndex < ids.length;
      return ids[randomIndex];
    }
  }

  /*
   * Two Clients
   */
  public class TwoVertex implements NVertex {
    @Override
    public ClientNodeRemote selectReceiver(final Map<String, ClientNodeRemote> clients,
        final String senderIdentifier) {
      ClientNodeRemote receiver = null;
      switch (senderIdentifier) {
        case ClientProcess.CLIENT_PREFIX + "0":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + "1");
          break;
        case ClientProcess.CLIENT_PREFIX + "1":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + "0");
          break;
        default:
          throw new RuntimeException("Unexpected sender: " + senderIdentifier);
      }
      assert receiver != null;
      return receiver;
    }
  }

  /*
   * Four Clients
   */
  public class FourVertex implements NVertex {
    @Override
    public ClientNodeRemote selectReceiver(final Map<String, ClientNodeRemote> clients,
        final String senderIdentifier) {
      ClientNodeRemote receiver = null;
      switch (senderIdentifier) {
        case ClientProcess.CLIENT_PREFIX + "0":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2"));
          break;
        case ClientProcess.CLIENT_PREFIX + "1":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3"));
          break;
        case ClientProcess.CLIENT_PREFIX + "2":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3"));
          break;
        case ClientProcess.CLIENT_PREFIX + "3":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2"));
          break;
        default:
          throw new RuntimeException("Unexpected sender: " + senderIdentifier);
      }
      assert receiver != null;
      return receiver;
    }
  }

  /*
   * Eight Clients
   */
  public class EightVertex implements NVertex {
    @Override
    public ClientNodeRemote selectReceiver(final Map<String, ClientNodeRemote> clients,
        final String senderIdentifier) {
      ClientNodeRemote receiver = null;
      switch (senderIdentifier) {
        case ClientProcess.CLIENT_PREFIX + "0":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2", "4"));
          break;
        case ClientProcess.CLIENT_PREFIX + "1":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3", "5"));
          break;
        case ClientProcess.CLIENT_PREFIX + "2":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3", "6"));
          break;
        case ClientProcess.CLIENT_PREFIX + "3":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2", "7"));
          break;
        case ClientProcess.CLIENT_PREFIX + "4":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "5", "6"));
          break;
        case ClientProcess.CLIENT_PREFIX + "5":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "4", "7"));
          break;
        case ClientProcess.CLIENT_PREFIX + "6":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("2", "4", "7"));
          break;
        case ClientProcess.CLIENT_PREFIX + "7":
          receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("3", "5", "6"));
          break;
        default:
          throw new RuntimeException("Unexpected sender: " + senderIdentifier);
      }
      assert receiver != null;
      return receiver;
    }
  }
}

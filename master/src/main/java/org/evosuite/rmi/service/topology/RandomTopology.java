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

import java.util.ArrayList;
import java.util.List;
import org.evosuite.rmi.service.ClientNodeRemote;
import org.evosuite.utils.Randomness;

/**
 * Implements a random topology.
 */
public class RandomTopology extends AbstractTopology {

  @Override
  public ClientNodeRemote selectReceiver(final String senderIdentifier) {
    synchronized (clients) {
      List<String> keys = new ArrayList<String>();
      for (String key : clients.keySet()) {
        // to avoid sender == receiver
        if (!key.equals(senderIdentifier)) {
          keys.add(key);
        }
      }
      int randomIndex = Randomness.nextInt(keys.size());
      assert randomIndex < keys.size();
      return clients.get(keys.get(randomIndex));
    }
  }
}

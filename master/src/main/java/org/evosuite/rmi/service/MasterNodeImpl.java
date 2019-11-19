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
package org.evosuite.rmi.service;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.evosuite.ClientProcess;
import org.evosuite.Properties;
import org.evosuite.Properties.NoSuchParameterException;
import org.evosuite.ga.Chromosome;
import org.evosuite.result.TestGenerationResult;
import org.evosuite.rmi.service.topology.AbstractTopology;
import org.evosuite.rmi.service.topology.HypercubeTopology;
import org.evosuite.rmi.service.topology.RandomTopology;
import org.evosuite.rmi.service.topology.RingTopology;
import org.evosuite.statistics.SearchStatistics;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.utils.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MasterNodeImpl implements MasterNodeRemote, MasterNodeLocal {

	private static final long serialVersionUID = -6329473514791197464L;

	private static Logger logger = LoggerFactory.getLogger(MasterNodeImpl.class);

	private final Registry registry;

	protected final Collection<Listener<ClientStateInformation>> listeners = Collections.synchronizedList(new ArrayList<Listener<ClientStateInformation>>());

	private final AbstractTopology topology;

	public MasterNodeImpl(Registry registry) {
		this.registry = registry;

		switch (Properties.CLIENTS_TOPOLOGY) {
		  case RANDOM:
		    this.topology = new RandomTopology();
		    break;
		  case HYPERCUBE:
		    this.topology = new HypercubeTopology();
		    break;
		  case RING:
		  default:
		    this.topology = new RingTopology();
		    break;
		}
	}

	@Override
	public void evosuite_registerClientNode(String clientRmiIdentifier) throws RemoteException {

		/*
		 * The client should first register its node, and then inform MasterNode
		 * by calling this method
		 */

		ClientNodeRemote node = null;
		try {
			node = (ClientNodeRemote) registry.lookup(clientRmiIdentifier);
		} catch (Exception e) {
			logger.error("Error when client " + clientRmiIdentifier
			        + " tries to register to master", e);
			return;
		}
		this.topology.registerClientNode(clientRmiIdentifier, node);
	}

	@Override
	public void evosuite_informChangeOfStateInClient(String clientRmiIdentifier,
	        ClientState state, ClientStateInformation information) throws RemoteException {
		this.topology.informChangeOfStateInClient(clientRmiIdentifier, state, information);
		fireEvent(information);
	}

	@Override
	public Collection<ClientState> getCurrentState() {
		return this.topology.getCurrentStates();
	}

    @Override
    public ClientState getCurrentState(String clientId) {
        return this.topology.getCurrentState(clientId);
    }

    @Override
	public Collection<ClientStateInformation> getCurrentStateInformation() {
		return this.topology.getCurrentStateInformation();
	}

	@Override
	public String getSummaryOfClientStatuses() {
		return this.topology.getSummaryOfClientStatuses();
	}

	@Override
	public Map<String, ClientNodeRemote> getClientsOnceAllConnected(long timeoutInMs)
	        throws InterruptedException {
		return this.topology.getClientsOnceAllConnected(timeoutInMs);
	}

	@Override
	public void cancelAllClients() {
		this.topology.cancelAllClients();
	}

	@Override
	public void evosuite_collectStatistics(String clientRmiIdentifier, Chromosome individual) {
		SearchStatistics.getInstance(clientRmiIdentifier).currentIndividual(individual);
	}

	@Override
	public void evosuite_collectStatistics(String clientRmiIdentifier, RuntimeVariable variable, Object value)
	        throws RemoteException {
		SearchStatistics.getInstance(clientRmiIdentifier).setOutputVariable(variable, value);
	}

	@Override
	public void evosuite_collectTestGenerationResult(
			String clientRmiIdentifier, List<TestGenerationResult> results)
			throws RemoteException {
		SearchStatistics.getInstance(clientRmiIdentifier).addTestGenerationResult(results);
	}

	@Override
	public void evosuite_flushStatisticsForClassChange(String clientRmiIdentifier)
			throws RemoteException {
		SearchStatistics.getInstance(clientRmiIdentifier).writeStatisticsForAnalysis();
	}

	@Override
	public void evosuite_updateProperty(String clientRmiIdentifier, String propertyName, Object value)
			throws RemoteException, IllegalArgumentException, IllegalAccessException, NoSuchParameterException {
		Properties.getInstance().setValue(propertyName, value);
	}

    @Override
    public void evosuite_migrate(String clientRmiIdentifier, Set<? extends Chromosome> migrants)
            throws RemoteException {
        ClientNodeRemote receiver = this.topology.selectReceiver(clientRmiIdentifier);
        if (receiver != null) {
            receiver.immigrate(migrants);
        }
    }

    @Override
    public void evosuite_collectBestSolutions(String clientRmiIdentifier, Set<? extends Chromosome> solutions) {
        try {
            ClientNodeRemote node = this.topology.getClientNode(ClientProcess.DEFAULT_CLIENT_NAME);
            assert node != null;
            node.collectBestSolutions(solutions);
        } catch (RemoteException e) {
            logger.error(clientRmiIdentifier + " cannot send best solutions to " + ClientProcess.DEFAULT_CLIENT_NAME, e);
        }
    }

    @Override
	public void addListener(Listener<ClientStateInformation> listener) {
		listeners.add(listener);
	}

	@Override
	public void deleteListener(Listener<ClientStateInformation> listener) {
		listeners.remove(listener);
	}

	/**
	 * <p>
	 * fireEvent
	 * </p>
	 * 
	 * @param event
	 *            a T object.
	 */
	public void fireEvent(ClientStateInformation event) {
		for (Listener<ClientStateInformation> listener : listeners) {
			listener.receiveEvent(event);
		}
	}
}

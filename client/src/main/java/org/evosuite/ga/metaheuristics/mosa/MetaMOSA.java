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
package org.evosuite.ga.metaheuristics.mosa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.evosuite.ClientProcess;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.operators.selection.BestKSelection;
import org.evosuite.ga.operators.selection.RandomKSelection;
import org.evosuite.ga.operators.selection.RankSelection;
import org.evosuite.ga.operators.selection.SelectionFunction;
import org.evosuite.strategy.MOSuiteStrategy;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.utils.ArrayUtil;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MetaMOSA implementation that contains other MOSAs, and for each iteration each of the sub-MOSAs
 * is evolved one step.
 */
@SuppressWarnings("rawtypes")
public class MetaMOSA<T extends Chromosome> extends AbstractMOSA<T> {

  private static final long serialVersionUID = 332599290761536575L;

  private static final Logger logger = LoggerFactory.getLogger(MetaMOSA.class);

  private SelectionFunction<T> emigrantsSelection;

  private final AbstractTopology topology;

  private final MOSA[] mosas;

  private final Properties.Algorithm algorithmBackup;

  private final Properties.Criterion[] criterionBackup;
  private final Properties.Criterion[][] criterionPerClient;

  private int current_mosa_index = 0;

  public MetaMOSA(ChromosomeFactory<T> factory) {
    super(factory);

    switch (Properties.EMIGRANT_SELECTION_FUNCTION) {
      case RANK:
        this.emigrantsSelection = new RankSelection<>();
        break;
      case RANDOMK:
        this.emigrantsSelection = new RandomKSelection<>();
        break;
      default:
        this.emigrantsSelection = new BestKSelection<>();
    }

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

    // Backup algorithm
    this.algorithmBackup = Properties.ALGORITHM;
    Properties.ALGORITHM = Properties.Algorithm.MOSA;

    // Backup criterion
    this.criterionBackup = Properties.CRITERION.clone();
    this.criterionPerClient = new Properties.Criterion[Properties.NUM_SEQUENTIAL_CLIENTS][];
    if (Properties.NUM_SEQUENTIAL_CLIENTS > 1
        && Properties.DIFFERENT_FITNESS_FUNCTIONS_PER_CLIENT) {
      // Shuffle criterion, either the default or the ones passed by argument, i.e., -Dcriterion=...
      Randomness.shuffle(this.criterionBackup);

      // Split criterion by number of clients, i.e., Properties.NUM_SEQUENTIAL_CLIENTS
      assert this.criterionBackup.length >= Properties.NUM_SEQUENTIAL_CLIENTS;
      Object[][] objs =
          ArrayUtil.splitArray(this.criterionBackup, Properties.NUM_SEQUENTIAL_CLIENTS);
      for (int k = 0; k < objs.length; k++) {
        if (objs[k].length == 1 && objs[k][0] == Properties.Criterion.EXCEPTION) {
          // EXCEPTION itself is not able to guide the search through the search space,
          // in here we add BRANCH to the list of fitness function to optimize as a helper
          // fitness function
          this.criterionPerClient[k] = new Properties.Criterion[] {
              Properties.Criterion.BRANCH,
              Properties.Criterion.EXCEPTION
          };
        } else {
          this.criterionPerClient[k] = new Properties.Criterion[objs[k].length];
          for (int z = 0; z < objs[k].length; z++) {
            this.criterionPerClient[k][z] = (Properties.Criterion) objs[k][z];
          }
        }
      }
    } else {
      Arrays.fill(this.criterionPerClient, this.criterionBackup);
    }

    // Create N sub-MOSAs
    this.mosas = new MOSA[Properties.NUM_SEQUENTIAL_CLIENTS];
    for (int i = 0; i < Properties.NUM_SEQUENTIAL_CLIENTS; i++) {
      ClientProcess.setIdentifier(this.getSubMOSAid(i));

      if (Properties.NUM_SEQUENTIAL_CLIENTS > 1
          && Properties.DIFFERENT_FITNESS_FUNCTIONS_PER_CLIENT) {
        // Set different criteria per client, i.e., sub-MOSA
        Properties.CRITERION = this.criterionPerClient[i];
      }

      // Create a new sub-MOSA
      MOSuiteStrategy testGenerationStrategy = new MOSuiteStrategy();
      MOSA<TestSuiteChromosome> mosa =
          (MOSA<TestSuiteChromosome>) testGenerationStrategy.setUpAlgorithm();
      // Run pre-procedure for each sub-MOSA
      mosa.preGenerationProcedure();

      // Register this new sub-MOSA
      this.topology.registerClient(this.getSubMOSAid(i), mosa);

      this.mosas[i] = mosa;
    }

    this.restoreProperties();
  }

  private String getSubMOSAid(final int id) {
    return ClientProcess.CLIENT_PREFIX + id + "x";
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void evolve() {
    logger.info("Evolving individual MOSA");

    // sub-MOSA to evolve
    MOSA mosa = this.mosas[this.current_mosa_index];
    if (mosa.getPopulation().isEmpty() || mosa.getFitnessFunctions().isEmpty()) {
      // nothing to optimize
      return;
    }

    // Evolve sub-MOSA
    mosa.evolve();

    // Collect and send k individuals to its neighbour, whatever the neighbour is
    if (Properties.MIGRANTS_ITERATION_FREQUENCY > 0) {
      MOSA receiver = this.topology.selectReceiver(this.getSubMOSAid(this.current_mosa_index));
      if (receiver != null
          && ((this.currentIteration + 1) % Properties.MIGRANTS_ITERATION_FREQUENCY == 0)
          && !mosa.getPopulation().isEmpty()) {
        // Select k individuals
        List<T> emigrants = new ArrayList<T>(this.emigrantsSelection.select(mosa.getPopulation(),
            Properties.MIGRANTS_COMMUNICATION_RATE));
        // Put them in receiver's population
        receiver.addToPopulation(emigrants);
      }
    }

    this.current_mosa_index++;
    if (this.current_mosa_index >= this.mosas.length) {
      // Reset index
      this.current_mosa_index = 0;
    }

    this.currentIteration++;
  }

  private void restoreProperties() {
    // Restore algorithm
    Properties.ALGORITHM = this.algorithmBackup;
    // Restore criterion
    Properties.CRITERION = this.criterionBackup;
    // Restore client name
    ClientProcess.setIdentifier(ClientProcess.DEFAULT_CLIENT_NAME);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void generateSolution() {
    logger.info("Executing generateSolution function of MetaMOSA");

    // Keep track of all goals
    this.fitnessFunctions.forEach(this::addUncoveredGoal);
    // Create one population of solutions which may be used or discarded in the end. Note: the
    // system in place to check whether the GA should finish checks whether the population has
    // reached 100% coverage, but to do so a population must exist
    this.initializePopulation();

    while (!this.isFinished()) {
      // Set some properties to run a specific MOSA instance
      Properties.CRITERION = this.criterionPerClient[this.current_mosa_index];
      ClientProcess.setIdentifier(this.getSubMOSAid(this.current_mosa_index));

      this.evolve();

      // Restore properties
      this.restoreProperties();

      this.notifyIteration();
    }

    // Get all solutions from all sub-MOSAs
    for (MOSA mosa : this.mosas) {
      this.population.addAll(mosa.getPopulation());
    }

    // Make sure this MetaMOSA has evaluated all solutions in the population
    for (Chromosome t : this.population) {
      this.calculateFitness((T) t);
    }

    this.notifySearchFinished();
  }

  public interface Topology {
    public MOSA selectReceiver(final String senderID);
  }

  private abstract class AbstractTopology implements Topology {
    protected final Map<String, MOSA> clients = new LinkedHashMap<String, MOSA>();

    public void registerClient(final String clientID, final MOSA mosa) {
      this.clients.put(clientID, mosa);
    }
  }

  private class RandomTopology extends AbstractTopology {
    @Override
    public MOSA selectReceiver(final String senderID) {
      List<String> keys = new ArrayList<String>();
      for (String key : this.clients.keySet()) {
        // to avoid sender == receiver
        if (!key.equals(senderID)) {
          keys.add(key);
        }
      }
      int randomIndex = Randomness.nextInt(keys.size());
      assert randomIndex < keys.size();
      return clients.get(keys.get(randomIndex));
    }
  }

  public interface NVertex {
    public MOSA selectReceiver(final Map<String, MOSA> clients, final String senderIdentifier);

    default String getId(String... ids) {
      int randomIndex = Randomness.nextInt(ids.length);
      assert randomIndex < ids.length;
      return ids[randomIndex];
    }
  }

  private class HypercubeTopology extends AbstractTopology {

    private NVertex nVertex = null;

    public HypercubeTopology() {
      switch (Properties.NUM_SEQUENTIAL_CLIENTS) {
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
    public MOSA selectReceiver(final String senderIdentifier) {
      return this.nVertex.selectReceiver(this.clients, senderIdentifier);
    }

    /*
     * Two Clients
     */
    public class TwoVertex implements NVertex {
      @Override
      public MOSA selectReceiver(final Map<String, MOSA> clients, final String senderIdentifier) {
        MOSA receiver = null;
        switch (senderIdentifier) {
          case ClientProcess.CLIENT_PREFIX + "0" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + "1" + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "1" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + "0" + "x");
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
      public MOSA selectReceiver(final Map<String, MOSA> clients, final String senderIdentifier) {
        MOSA receiver = null;
        switch (senderIdentifier) {
          case ClientProcess.CLIENT_PREFIX + "0" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "1" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "2" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "3" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2") + "x");
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
      public MOSA selectReceiver(final Map<String, MOSA> clients, final String senderIdentifier) {
        MOSA receiver = null;
        switch (senderIdentifier) {
          case ClientProcess.CLIENT_PREFIX + "0" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2", "4") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "1" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3", "5") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "2" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "3", "6") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "3" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "2", "7") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "4" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("0", "5", "6") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "5" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("1", "4", "7") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "6" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("2", "4", "7") + "x");
            break;
          case ClientProcess.CLIENT_PREFIX + "7" + "x":
            receiver = clients.get(ClientProcess.CLIENT_PREFIX + this.getId("3", "5", "6") + "x");
            break;
          default:
            throw new RuntimeException("Unexpected sender: " + senderIdentifier);
        }
        assert receiver != null;
        return receiver;
      }
    }
  }

  private class RingTopology extends AbstractTopology {
    @Override
    public MOSA selectReceiver(final String senderIdentifier) {
      int idSender = Integer.parseInt(senderIdentifier.replaceAll("[^0-9]", ""));
      int idNeighbour = (idSender + 1) % Properties.NUM_SEQUENTIAL_CLIENTS;

      int numAttempts = Properties.NUM_SEQUENTIAL_CLIENTS;
      while (idNeighbour == idSender && numAttempts > 0) {
        idNeighbour = (idNeighbour + 1) % Properties.NUM_SEQUENTIAL_CLIENTS;
        numAttempts--;
      }

      if (idNeighbour != idSender) {
        MOSA node = clients.get(getSubMOSAid(idNeighbour));
        return node;
      }

      return null;
    }
  }
}

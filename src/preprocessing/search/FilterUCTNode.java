package preprocessing.search;

import config.JoinConfig;
import expressions.compilation.UnaryBoolEval;
import indexing.HashIndex;
import joining.uct.SelectionPolicy;

import java.util.*;

import static preprocessing.search.FilterSearchConfig.*;

public class FilterUCTNode {
    private enum NodeType {
        ROOT, LEAF, INDEX, BRANCHING, ROW_PARALLEL
    }

    private static final Random random = new Random();
    private static int numPredicates;
    private static Map<List<Integer>, UnaryBoolEval> cache;
    private static List<Integer> order = null;
    private static BudgetedFilter filterOp;
    private static List<HashIndex> indices;

    // Node common members
    private final long createdIn;
    private final NodeType type;
    private final int treeLevel, nrActions;
    private final int[] nrTries;
    private final double[] accumulatedReward;
    private int nrVisits;
    private final List<Integer> priorityActions;


    private final int actionToPredicate[];
    private final FilterUCTNode[] childNodes;

    private final List<Integer> chosenPreds;
    private final List<Integer> unchosenPreds;


    private final int indexActions;
    private final int branchingActions;

    private int minBatches = 0;

    public FilterUCTNode(BudgetedFilter filterOp,
                         Map<List<Integer>, UnaryBoolEval> cache,
                         long roundCtr,
                         int numPredicates, List<HashIndex> indices) {
        this.type = NodeType.ROOT;
        this.treeLevel = 0;
        this.createdIn = roundCtr;
        // PREAMBLE - DO NOT CHANGE

        this.cache = cache;
        this.filterOp = filterOp;
        this.numPredicates = numPredicates;
        this.indices = indices;

        int indexActions = 0;
        for (HashIndex index : indices) {
            if (index != null) {
                indexActions++;
            }
        }
        this.indexActions = indexActions;
        this.branchingActions = 1;
        this.nrActions = numPredicates + indexActions + branchingActions;
        this.childNodes = new FilterUCTNode[nrActions];
        this.chosenPreds = new ArrayList<>();
        this.unchosenPreds = new ArrayList<>(numPredicates);
        this.actionToPredicate = new int[nrActions];

        int pred = 0, action = numPredicates;
        for (HashIndex index : indices) {
            if (index != null) {
                actionToPredicate[action] = pred;
                action++;
            }
            pred++;
        }

        for (int i = 0; i < numPredicates; ++i) {
            unchosenPreds.add(i);
            actionToPredicate[i] = i;
        }

        // CONCLUSION - DO NOT CHANGE
        this.nrVisits = 0;
        this.nrTries = new int[nrActions];
        this.accumulatedReward = new double[nrActions];
        this.priorityActions = new ArrayList<>(nrActions);
        for (int i = 0; i < nrActions; i++) {
            nrTries[i] = 0;
            accumulatedReward[i] = 0;
            priorityActions.add(i);
        }
    }

    private FilterUCTNode(FilterUCTNode parent, long roundCtr, NodeType type) {
        assert type == NodeType.LEAF : type.name();
        this.type = type;
        this.treeLevel = parent.treeLevel + 1;
        this.createdIn = roundCtr;
        // PREAMBLE - DO NOT CHANGE

        this.indexActions = 0;
        this.nrActions = 0;
        this.childNodes = new FilterUCTNode[0];
        this.chosenPreds = new ArrayList<>();
        this.unchosenPreds = new ArrayList<>();
        this.actionToPredicate = new int[0];
        this.branchingActions = 0;

        // CONCLUSION - DO NOT CHANGE
        this.nrVisits = 0;
        this.nrTries = new int[nrActions];
        this.accumulatedReward = new double[nrActions];
        this.priorityActions = new ArrayList<>(nrActions);
        for (int i = 0; i < nrActions; i++) {
            nrTries[i] = 0;
            accumulatedReward[i] = 0;
            priorityActions.add(i);
        }
    }

    private FilterUCTNode(FilterUCTNode parent, long roundCtr, int arg,
                          NodeType type) {
        this.type = type;
        this.treeLevel = parent.treeLevel + 1;
        this.createdIn = roundCtr;
        // PREAMBLE - DO NOT CHANGE

        if (type == NodeType.BRANCHING || type == NodeType.INDEX) {
            int nextPred = arg;
            int actions;
            if (type == NodeType.BRANCHING || type == NodeType.INDEX &&
                    parent.type == NodeType.ROOT) {
                actions = numPredicates - 1;
            } else {
                actions = parent.nrActions - 1;
            }


            this.chosenPreds = new ArrayList<>();
            this.chosenPreds.addAll(parent.chosenPreds);
            this.chosenPreds.add(nextPred);

            this.unchosenPreds = new ArrayList<>();
            this.unchosenPreds.addAll(parent.unchosenPreds);
            int indexToRemove = unchosenPreds.indexOf(nextPred);
            this.unchosenPreds.remove(indexToRemove);

            if (this.type == NodeType.INDEX) {
                List<Integer> indexedPreds = new ArrayList<>();
                int indexActions = 0;
                if (type == NodeType.INDEX) {
                    for (int pred : unchosenPreds) {
                        HashIndex index = indices.get(pred);
                        if (index != null) {
                            indexActions++;
                            indexedPreds.add(pred);
                        }
                    }
                }
                this.indexActions = indexActions;
                this.nrActions = actions + this.indexActions;


                this.childNodes = new FilterUCTNode[nrActions];
                this.actionToPredicate = new int[nrActions];

                for (int a = unchosenPreds.size(), i = 0; a < nrActions; ++a) {
                    actionToPredicate[a] = indexedPreds.get(i++);
                }

            } else {
                this.indexActions = 0;
                this.nrActions = actions;

                this.childNodes = new FilterUCTNode[nrActions];
                this.actionToPredicate = new int[nrActions];
            }


            for (int a = 0; a < unchosenPreds.size(); ++a) {
                actionToPredicate[a] = unchosenPreds.get(a);
            }
        } else {
            assert type == NodeType.ROW_PARALLEL : type.name();
            this.minBatches = arg;
            this.nrActions = ROW_PARALLEL_ACTIONS;
            this.childNodes = new FilterUCTNode[nrActions];
            this.chosenPreds = new ArrayList<>();
            this.unchosenPreds = new ArrayList<>();
            this.actionToPredicate = new int[0];
            this.indexActions = 0;
        }

        this.branchingActions = 0;

        // CONCLUSION - DO NOT CHANGE
        this.nrVisits = 0;
        this.nrTries = new int[nrActions];
        this.accumulatedReward = new double[nrActions];
        this.priorityActions = new ArrayList<>(nrActions);
        for (int i = 0; i < nrActions; i++) {
            nrTries[i] = 0;
            accumulatedReward[i] = 0;
            priorityActions.add(i);
        }
    }

    public double sample(long roundCtr, FilterState state) {
        if (type == NodeType.LEAF) {
            if (state.parallelBatches > 0) {
                return filterOp.executeWithBudget(PARALLEL_ROWS_PER_TIMESTEP,
                        state);
            }

            return filterOp.executeWithBudget(ROWS_PER_TIMESTEP, state);
        }


        boolean canExpand = createdIn != roundCtr;
        int action = selectAction(SelectionPolicy.UCB1);

        switch (type) {
            case ROOT: {
                if (action == numPredicates + indexActions) {
                    state.avoidBranching = true;
                    if (childNodes[action] == null && canExpand) {
                        childNodes[action] = new FilterUCTNode(this, roundCtr,
                                NodeType.LEAF);
                    }
                } else {
                    int predicate = actionToPredicate[action];
                    state.order[treeLevel] = predicate;
                    order = new ArrayList<>();
                    order.add(predicate);

                    if (this.indexActions > 0 &&
                            action >= unchosenPreds.size() &&
                            action < unchosenPreds.size() + indexActions) {
                        state.indexedTil = treeLevel;
                        order = new ArrayList<>();
                        state.cachedTil = -1;
                        state.cachedEval = null;
                    }

                    if (childNodes[action] == null && canExpand) {
                        if (this.unchosenPreds.size() == 1) {
                            if (ENABLE_ROW_PARALLELISM) {
                                childNodes[action] = new FilterUCTNode(this,
                                        roundCtr, 0,
                                        NodeType.ROW_PARALLEL);
                            } else {
                                childNodes[action] = new FilterUCTNode(this,
                                        roundCtr, NodeType.LEAF);
                            }
                        } else if (state.indexedTil == treeLevel) {
                            childNodes[action] = new FilterUCTNode(this,
                                    roundCtr, predicate, NodeType.INDEX);
                        } else {
                            childNodes[action] = new FilterUCTNode(this,
                                    roundCtr, predicate, NodeType.BRANCHING);
                        }
                    }
                }

                break;
            }

            case INDEX:
            case BRANCHING: {
                int predicate = actionToPredicate[action];
                state.order[treeLevel] = predicate;
                order.add(predicate);
                if (cache.get(order) != null) {
                    state.cachedTil = treeLevel;
                    state.cachedEval = cache.get(order);
                }

                if (this.indexActions > 0 &&
                        action >= unchosenPreds.size() &&
                        action < unchosenPreds.size() + indexActions) {
                    state.indexedTil = treeLevel;
                    order = new ArrayList<>();
                    state.cachedTil = -1;
                    state.cachedEval = null;
                }

                if (childNodes[action] == null && canExpand) {
                    if (this.unchosenPreds.size() == 1) {
                        if (ENABLE_ROW_PARALLELISM) {
                            childNodes[action] = new FilterUCTNode(this,
                                    roundCtr, 0,
                                    NodeType.ROW_PARALLEL);
                        } else {
                            childNodes[action] = new FilterUCTNode(this,
                                    roundCtr, NodeType.LEAF);
                        }
                    } else {
                        if (state.indexedTil == treeLevel) {
                            childNodes[action] = new FilterUCTNode(this,
                                    roundCtr, predicate, NodeType.INDEX);
                        } else {
                            childNodes[action] = new FilterUCTNode(this,
                                    roundCtr, predicate, NodeType.BRANCHING);
                        }
                    }
                }
                break;
            }

            case ROW_PARALLEL: {
                state.parallelBatches =
                        minBatches + action * ROW_PARALLEL_DELTA;

                if (childNodes[action] == null && canExpand) {
                    childNodes[action] = new FilterUCTNode(this, roundCtr,
                            NodeType.LEAF);
                }
                break;
            }
        }

        FilterUCTNode child = childNodes[action];
        double reward = (child != null) ?
                child.sample(roundCtr, state) :
                playout(state);

        updateStatistics(action, reward);
        return reward;
    }

    private double playout(FilterState state) {
        switch (type) {
            case BRANCHING:
            case INDEX: {
                int lastPred = state.order[treeLevel];

                Collections.shuffle(unchosenPreds);
                Iterator<Integer> unchosenPredsIter = unchosenPreds.iterator();
                for (int i = treeLevel + 1; i < numPredicates; ++i) {
                    int nextPred = unchosenPredsIter.next();
                    while (nextPred == lastPred) {
                        nextPred = unchosenPredsIter.next();
                    }
                    state.order[i] = nextPred;
                }

                // Use playouts that have no parallel batches to avoid spending
                // a large time on bad orders
                state.parallelBatches = 0;
                return filterOp.executeWithBudget(ROWS_PER_TIMESTEP, state);
            }

            case ROW_PARALLEL: {
                return filterOp.executeWithBudget(PARALLEL_ROWS_PER_TIMESTEP,
                        state);
            }

            case LEAF:
                throw new RuntimeException("Not possible to playout from leaf");

            case ROOT:
                throw new RuntimeException("Not possible to playout from root");
        }
        return 0;
    }


    public void getTopNodesForCompilation(PriorityQueue<FilterUCTNode> compile,
                                          int compileSetSize) {
        if (this.type == NodeType.ROOT) {
            for (int a = 0; a < Math.min(nrActions, numPredicates); ++a) {
                if (this.childNodes[a] != null) {
                    this.childNodes[a].getTopNodesForCompilation(compile,
                            compileSetSize);
                }
            }
        } else {
            for (int a = 0; a < nrActions; ++a) {
                if (this.childNodes[a] != null) {
                    this.childNodes[a].getTopNodesForCompilation(compile,
                            compileSetSize);
                    if (compile.size() >= compileSetSize) {
                        compile.poll();
                    }
                }
            }
        }
    }


    // Common UCT functions
    private int selectAction(SelectionPolicy policy) {
        if (!priorityActions.isEmpty()) {
            int nrUntried = priorityActions.size();
            int actionIndex = random.nextInt(nrUntried);
            int action = priorityActions.get(actionIndex);
            // Remove from untried actions and return
            priorityActions.remove(actionIndex);
            // System.out.println("Untried action: " + action);
            return action;
        }

        int offset = random.nextInt(nrActions);
        int bestAction = -1;
        double bestQuality = -1;
        for (int actionCtr = 0; actionCtr < nrActions; ++actionCtr) {
            // Calculate index of current action
            int action = (offset + actionCtr) % nrActions;
            double meanReward = accumulatedReward[action] / nrTries[action];
            double exploration =
                    Math.sqrt(Math.log(nrVisits) / nrTries[action]);
            // Assess the quality of the action according to policy
            double quality = -1;
            switch (policy) {
                case UCB1:
                    quality = meanReward +
                            FilterSearchConfig.EXPLORATION_FACTOR * exploration;
                    break;
                case MAX_REWARD:
                case EPSILON_GREEDY:
                    quality = meanReward;
                    break;
                case RANDOM:
                    quality = random.nextDouble();
                    break;
                case RANDOM_UCB1:
                    if (treeLevel == 0) {
                        quality = random.nextDouble();
                    } else {
                        quality = meanReward +
                                FilterSearchConfig.EXPLORATION_FACTOR *
                                        exploration;
                    }
                    break;
            }

            if (quality > bestQuality) {
                bestAction = action;
                bestQuality = quality;
            }
        }
        // For epsilon greedy, return random action with
        // probability epsilon.
        if (policy.equals(SelectionPolicy.EPSILON_GREEDY)) {
            if (random.nextDouble() <= JoinConfig.EPSILON) {
                return random.nextInt(nrActions);
            }
        }
        // Otherwise: return best action.
        return bestAction;
    }

    private void updateStatistics(int selectedAction, double reward) {
        ++nrVisits;
        ++nrTries[selectedAction];
        accumulatedReward[selectedAction] += reward;
    }

    // Getters
    public int getNumVisits() {
        return nrVisits;
    }

    public List<Integer> getChosenPreds() {
        return chosenPreds;
    }

}

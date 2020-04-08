package config;

import joining.uct.ExplorationWeightPolicy;
import joining.uct.SelectionPolicy;
import joining.uct.TreeSearchPolicy;

/**
 * Configures reinforcement-learning based
 * search for optimal join orders.
 * 
 * @author immanueltrummer
 *
 */
public class JoinConfig {
	/**
	 * Choose default action selection strategy.
	 */
	public static final SelectionPolicy DEFAULT_SELECTION =
			SelectionPolicy.UCB1;
	/**
	 * Number of learning steps performed per episode.
	 */
	public static int LEARN_BUDGET_EPISODE = 500;
	/**
	 * Number of execution steps performed per episode.
	 */
	public static int EXECUTION_BUDGET_EPISODE = 10000;
	/**
	 * Execution budget increase delta
	 */
	public static int EXECUTION_BUDGET_INCREASE_DELTA = 1;
	/**
	 * How to weigh progress calculated based on the
	 * percentage of input processed when calculating
	 * reward.
	 */
	public static double INPUT_REWARD_WEIGHT = 0.5;
	/**
	 * How to weigh progress calculated based on the
	 * percentage of output generated when calculating
	 * reward.
	 */
	public static double OUTPUT_REWARD_WEIGHT = 0.5;

	public static double REWARD_SCALE = 0.01;
	/**
	 * Weight for UCT exploration term (used to select
	 * most interesting action to try next). This
	 * factor may be dynamically adapted.
	 */
	public static double EXPLORATION_WEIGHT = 1E-5;
	/**
	 * Determines how the weight for the exploration term
	 * of the UCT algorithm is updated over time.
	 */
	public static final ExplorationWeightPolicy EXPLORATION_POLICY =
			ExplorationWeightPolicy.STATIC;
	/**
	 * The epsilon term used for epsilon greedy selection
	 * (i.e., the probability that a random action is
	 * selected as opposed to the maximum reward action).
	 */
	public static final double EPSILON = 0.1;
	/**
	 * Whether to regularly forget everything that was
	 * learned about join orders so far. This is helpful
	 * since the reward distribution keeps changing due
	 * to table offsets etc. Also helps against 
	 * non-uniform data which may cause (too) early
	 * convergence to one specific join order.
	 */
	public static final boolean FORGET = true;
	/**
	 * Whether to heuristically restrict join orders to
	 * avoid Cartesian product joins if possible.
	 */
	public static final boolean AVOID_CARTESIANS = false;
	/**
	 * Whether to skip tables during backtracking
	 * for which changes are guaranteed not to lead 
	 * to valid result tuples.
	 */
	public static final boolean FAST_BACKTRACK = false;
	/**
	 * Whether to resolve anti-joins immediately (instead
	 * of interrupting the process once the tuple limit
	 * is reached).
	 */
	public static final boolean SIMPLE_ANTI_JOIN = false;
	/**
	 * number of learning per iteration
	 */
	public static int SAMPLE_PER_LEARN = 10;
	/**
	 * monte carlo tree search policy
	 */
	public static TreeSearchPolicy TREE_POLICY = TreeSearchPolicy.UCT;
}

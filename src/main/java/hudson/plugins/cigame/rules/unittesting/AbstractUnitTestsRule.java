package hudson.plugins.cigame.rules.unittesting;

import java.util.Collection;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.plugins.cigame.model.AggregatableRule;
import hudson.plugins.cigame.model.RuleResult;
import hudson.tasks.test.AbstractTestResultAction;

public abstract class AbstractUnitTestsRule implements AggregatableRule<Integer> {
	
	@SuppressWarnings("unchecked")
	static final AbstractTestResultAction ZERO_RESULT = new AbstractTestResultAction(null) {

		@Override
		public int getFailCount() {
			return 0;
		}

		@Override
		public Object getResult() {
			return null;
		}

		@Override
		public int getTotalCount() {
			return 0;
		}

		@Override
		public int getSkipCount() {
			return 0;
		}
	};
    
    /**
     * Returns the youngest build which is usable to compare the current test result against.
     * I.e. returns the youngest build which is better than NOT_BUILD and has test results.
     * FAILURE builds are returned immediately!
     *
     * @return the previous build or null if no such build was found
     */
    private AbstractBuild<?, ?> getPreviousBuildWithResults(AbstractBuild<?, ?> previousBuild) {
        while(previousBuild != null) {
        	if (previousBuild.getResult() != null) {
	            if (previousBuild.getResult().isBetterThan(Result.FAILURE)) {
	                // this is reached if the previous build is a UNSTABLE or SUCCESS
	                @SuppressWarnings("unchecked")
	                AbstractTestResultAction action = previousBuild.getTestResultAction();
	                if (action != null) {
	                    return previousBuild;
	                }
	                // fall through
	            } else if (previousBuild.getResult().isWorseOrEqualTo(Result.NOT_BUILT)) { 
	                // this is reached if the previous build is a NOT_BUILT or ABORTED
	                // fall through
	            } else {
	                // this is reached if the previous build is a FAILURE
	                return previousBuild;
	            }
        	}
            previousBuild = previousBuild.getPreviousBuild();
        }
        
        return null;
    }
    
    @SuppressWarnings("unchecked")
	public final RuleResult<Integer> evaluate(AbstractBuild<?, ?> previousBuild,
			AbstractBuild<?, ?> build) {

        previousBuild = getPreviousBuildWithResults(previousBuild);

        AbstractTestResultAction action; 
        AbstractTestResultAction prevAction;
        Result result;
        Result prevResult;
        
        if (previousBuild == null && build == null) {
        	return null;
        }

        if (build == null) {
        	action = null;
        	result = Result.SUCCESS;
        } else {
        	action = build.getTestResultAction();
        	result = build.getResult();
        }
        
        if (previousBuild == null) {
        	prevAction = ZERO_RESULT;
        	prevResult = Result.SUCCESS;
        } else {
        	prevAction = previousBuild.getTestResultAction();
        	prevResult = previousBuild.getResult();
        }
        
        prevAction = prevAction != null ? prevAction : ZERO_RESULT;
        
        // sometimes (when a build is aborted?) result can be null
        prevResult = prevResult != null ? prevResult : Result.ABORTED;
        result = result != null ? result : Result.ABORTED;
        
        // if the current action is null, let's assume as a ZERO result
        action = action != null ? action : ZERO_RESULT;
                
        if ((prevResult.isBetterThan(Result.FAILURE)) && (result.isBetterThan(Result.FAILURE))) {
            return evaluate(action, prevAction);
        } else if (isEqualToFailure(prevResult)) {
            return evaluateWhenPreviousIsFailure(action, previousBuild);
        } else {
        	return null;
        }
	}
    
    /**
     * When previous build was a failure, loop through the previous ones of that
     * to reach a better one than failure with test results. Then invoke
     * evaluate against that build.
     * 
     * @param testResult current test results
     * @param previousBuild
     * @return
     */
    private RuleResult<Integer> evaluateWhenPreviousIsFailure(AbstractTestResultAction<?> testResult,
                                                              AbstractBuild<?, ?> previousBuild) {

        AbstractBuild<?, ?> previousOfPreviousBuild = getPreviousBetterThanFailureBuildWithTestResults(previousBuild);

        if (previousOfPreviousBuild != null && previousOfPreviousBuild.getTestResultAction() != null) {
            System.out.println("Evaluating Prev buid id =" + previousBuild.getId() + ", " + previousBuild.getNumber());
            return evaluate(testResult, previousOfPreviousBuild.getTestResultAction());
        }

        return null;
    }

    /**
     * Checks if the result is a FAILURE (there is no method to compare for
     * equality of Results).
     * 
     * @param prevResult result
     * @return true if result is FAILURE
     */
    private boolean isEqualToFailure(Result prevResult) {
        return prevResult.isBetterOrEqualTo(Result.FAILURE) && prevResult.isWorseOrEqualTo(Result.FAILURE);
    }

    /**
     * Returns the youngest build of status UNSTABLE or SUCCESS which contained
     * test result.
     * 
     * @return the previous build or null if no such build was found
     */
    private AbstractBuild<?, ?> getPreviousBetterThanFailureBuildWithTestResults(AbstractBuild<?, ?> previousBuild) {
        while (previousBuild != null) {
            System.out.println("Prev buid id =" + previousBuild.getId() + ", " + previousBuild.getNumber());
            if (previousBuild.getResult() != null && previousBuild.getResult().isBetterThan(Result.FAILURE)) {
                // this is reached if the previous build is a UNSTABLE or SUCCESS
                if (previousBuild.getTestResultAction() != null) {
                    return previousBuild;
                }
            }
            previousBuild = previousBuild.getPreviousBuild();
        }

        return null;
    }
    
	@Override
	public final RuleResult<?> aggregate(Collection<RuleResult<Integer>> results) {
		double score = 0.0;
        int testDiff = 0;
        for (RuleResult<Integer> result : results) {
            if (result != null) {
                score += result.getPoints();
                testDiff += result.getAdditionalData();
            }
        }
        
        if (score != 0.0) {
            return new RuleResult<Void>(score, 
                    getResultDescription(testDiff)); 
        }
        return null;
	}
    
	public final RuleResult<Integer> evaluate(AbstractBuild<?, ?> build) {
		throw new UnsupportedOperationException();
	}
	
	protected abstract String getResultDescription(Integer testDiff);
    
    protected abstract RuleResult<Integer> evaluate(
    		AbstractTestResultAction<?> testResult, AbstractTestResultAction<?> previousTestResult);
}

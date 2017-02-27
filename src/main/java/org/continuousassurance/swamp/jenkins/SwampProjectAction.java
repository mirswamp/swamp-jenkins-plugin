package org.continuousassurance.swamp.jenkins;

import java.util.List;

import com.google.common.collect.Lists;

import hudson.model.Job;
import hudson.plugins.analysis.core.AbstractProjectAction;
import hudson.plugins.analysis.core.ResultAction;
import hudson.plugins.analysis.graph.BuildResultGraph;
import org.continuousassurance.swamp.Messages;

/**
 * Entry point to visualize the FindBugs trend graph in the project screen.
 * Drawing of the graph is delegated to the associated {@link ResultAction}.
 *
 * @author Ulli Hafner
 */
public class SwampProjectAction extends AbstractProjectAction<ResultAction<SwampResult>> {
    /**
     * Instantiates a new {@link SwampProjectAction}.
     *
     * @param job
     *            the job that owns this action
     */
    public SwampProjectAction(final Job<?, ?> job) {
        this(job, SwampResultAction.class);
    }

    /**
     * Instantiates a new {@link SwampProjectAction}.
     *
     * @param job
     *            the job that owns this action
     * @param type
     *            the result action type
     */
    public SwampProjectAction(final Job<?, ?> job,
            final Class<? extends ResultAction<SwampResult>> type) {
        super(job, type, Messages._FindBugs_ProjectAction_Name(), Messages._FindBugs_Trend_Name(),
                DescriptorImpl.PLUGIN_ID, DescriptorImpl.ICON_URL, DescriptorImpl.RESULT_URL);
    }

    @Override
    protected List<BuildResultGraph> getAvailableGraphs() {
        List<BuildResultGraph> list = Lists.newArrayList();
        list.addAll(super.getAvailableGraphs());
        list.add(new SwampEvaluationsGraph());
        return list;
    }
}


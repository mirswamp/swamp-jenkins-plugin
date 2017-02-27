package org.continuousassurance.swamp.jenkins;

import java.util.Collection;

import hudson.model.Action;
import hudson.model.Run;

import hudson.plugins.analysis.core.HealthDescriptor;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.plugins.analysis.core.AbstractResultAction;
import org.continuousassurance.swamp.Messages;

/**
 * Controls the live cycle of the FindBugs results. This action persists the
 * results of the FindBugs analysis of a build and displays the results on the
 * build page. The actual visualization of the results is defined in the
 * matching <code>summary.jelly</code> file.
 * <p>
 * Moreover, this class renders the FindBugs result trend.
 * </p>
 *
 * @author Ulli Hafner
 */
public class SwampResultAction extends AbstractResultAction<SwampResult> {
    /**
     * Creates a new instance of {@link SwampResultAction}.
     *
     * @param owner
     *            the associated build of this action
     * @param healthDescriptor
     *            health descriptor to use
     * @param result
     *            the result in this build
     */
    public SwampResultAction(final Run<?, ?> owner, final HealthDescriptor healthDescriptor, final SwampResult result) {
        super(owner, new SwampHealthDescriptor(healthDescriptor), result);
    }

    @Override
    public String getDisplayName() {
        return Messages.FindBugs_ProjectAction_Name();
    }

    @Override
    protected PluginDescriptor getDescriptor() {
        return new DescriptorImpl();
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return asSet(new SwampProjectAction(getJob()));
    }
}

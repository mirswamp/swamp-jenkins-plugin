package org.continuousassurance.swamp.jenkins;

import hudson.Launcher;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.plugins.analysis.core.AnnotationsAggregator;
import hudson.plugins.analysis.core.HealthDescriptor;
import hudson.plugins.analysis.core.ParserResult;

/**
 * Aggregates {@link SwampResultAction}s of {@link MatrixRun}s into
 * {@link MatrixBuild}.
 *
 * @author Ulli Hafner
 */
public class SwampAnnotationsAggregator extends AnnotationsAggregator {
    /**
     * Creates a new instance of {@link SwampAnnotationsAggregator}.
     *
     * @param build
     *            the matrix build
     * @param launcher
     *            the launcher
     * @param listener
     *            the build listener
     * @param healthDescriptor
     *            health descriptor
     * @param defaultEncoding
     *            the default encoding to be used when reading and parsing files
     * @param usePreviousBuildAsReference
     *            determines whether the previous build should be used as the
     *            reference build
     * @param useStableBuildAsReference
     *            determines whether only stable builds should be used as
     *            reference builds or not
     */
    public SwampAnnotationsAggregator(final MatrixBuild build, final Launcher launcher,
            final BuildListener listener, final HealthDescriptor healthDescriptor, final String defaultEncoding,
            final boolean usePreviousBuildAsReference, final boolean useStableBuildAsReference) {
        super(build, launcher, listener, healthDescriptor, defaultEncoding,
                usePreviousBuildAsReference, useStableBuildAsReference);
    }

    @Override
    protected Action createAction(final HealthDescriptor healthDescriptor, final String defaultEncoding, final ParserResult aggregatedResult) {
        return new SwampResultAction(build, healthDescriptor,
                new SwampResult(build, defaultEncoding, aggregatedResult, usePreviousBuildAsReference(),
                        useOnlyStableBuildsAsReference()));
    }

    @Override
    protected boolean hasResult(final MatrixRun run) {
        return getAction(run) != null;
    }

    @Override
    protected SwampResult getResult(final MatrixRun run) {
        return getAction(run).getResult();
    }

    private SwampResultAction getAction(final MatrixRun run) {
        return run.getAction(SwampResultAction.class);
    }
}


package org.continuousassurance.swamp.jenkins;

import hudson.plugins.analysis.core.AbstractHealthDescriptor;
import hudson.plugins.analysis.core.HealthDescriptor;
import hudson.plugins.analysis.util.model.AnnotationProvider;

import org.jvnet.localizer.Localizable;
import org.continuousassurance.swamp.Messages;

/**
 * A health descriptor for FindBugs build results.
 *
 * @author Ulli Hafner
 */
public class SwampHealthDescriptor extends AbstractHealthDescriptor {
    private static final long serialVersionUID = -3404826986876607396L;

    /**
     * Creates a new instance of {@link SwampHealthDescriptor} based on the
     * values of the specified descriptor.
     *
     * @param healthDescriptor the descriptor to copy the values from
     */
    public SwampHealthDescriptor(final HealthDescriptor healthDescriptor) {
        super(healthDescriptor);
    }

    @Override
    protected Localizable createDescription(final AnnotationProvider result) {
        if (result.getNumberOfAnnotations() == 0) {
            return Messages._FindBugs_ResultAction_HealthReportNoItem();
        }
        else if (result.getNumberOfAnnotations() == 1) {
            return Messages._FindBugs_ResultAction_HealthReportSingleItem();
        }
        else {
            return Messages._FindBugs_ResultAction_HealthReportMultipleItem(result.getNumberOfAnnotations());
        }
    }
}


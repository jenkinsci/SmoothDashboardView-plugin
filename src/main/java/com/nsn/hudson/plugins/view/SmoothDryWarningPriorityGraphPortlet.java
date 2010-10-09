package com.nsn.hudson.plugins.view;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.analysis.graph.BuildResultGraph;
import hudson.plugins.dry.Messages;
import hudson.plugins.dry.dashboard.WarningsPriorityGraphPortlet;
import hudson.plugins.view.dashboard.DashboardPortlet;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author tang
 */

public class SmoothDryWarningPriorityGraphPortlet extends
		WarningsPriorityGraphPortlet {

	@DataBoundConstructor
	public SmoothDryWarningPriorityGraphPortlet(String name, String width,
			String height, String dayCountString) {
		super(name, width, height, dayCountString);
	}

    /** {@inheritDoc} */
    protected BuildResultGraph getGraphType() {
    	return new SmoothPrioritGraph();
    }

	/**
	 * Extension point registration.
	 * 
	 * @author tang
	 */
	@Extension(optional = true)
	public static class WarningsGraphDescriptor extends
			Descriptor<DashboardPortlet> {
		@Override
		public String getDisplayName() {
			return "Smooth "+ Messages.Portlet_WarningsPriorityGraph();
		}
	}
}

package com.nsn.hudson.plugins.view;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.analysis.graph.BuildResultGraph;
import hudson.plugins.view.dashboard.DashboardPortlet;
import hudson.plugins.warnings.Messages;

import org.kohsuke.stapler.DataBoundConstructor;

public class SmoothWarningsPriorityGraphPortlet extends
		hudson.plugins.warnings.dashboard.WarningsPriorityGraphPortlet {
	
	
	@DataBoundConstructor
	public SmoothWarningsPriorityGraphPortlet(String name, String width,
			String height, String dayCountString) {
		super(name, width, height, dayCountString);
	}
	
	 /**
     * Extension point registration.
     *
     * @author tang
     */
    @Extension(optional = true)
    public static class WarningsGraphDescriptor extends Descriptor<DashboardPortlet> {
        @Override
        public String getDisplayName() {
            return "Smooth "+Messages.Portlet_WarningsPriorityGraph();
        }
    }
    
    /** {@inheritDoc} */
    protected BuildResultGraph getGraphType() {
    	return new SmoothPrioritGraph();
    }
    

    

}

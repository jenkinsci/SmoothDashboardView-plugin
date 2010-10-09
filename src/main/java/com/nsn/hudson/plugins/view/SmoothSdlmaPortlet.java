package com.nsn.hudson.plugins.view;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.view.dashboard.DashboardPortlet;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.joda.time.LocalDate;
import org.kohsuke.stapler.DataBoundConstructor;

import com.nsn.hudson.plugins.sdlmahud.SdlmaBuildAction;
import com.nsn.hudson.plugins.sdlmahud.SdlmaReport;
import com.nsn.hudson.plugins.sdlmahud.SdlmaResult;
import com.nsn.hudson.plugins.sdlmahud.SdlmaResultSummary;

public class SmoothSdlmaPortlet extends DashboardPortlet {

	@Extension
	public static class SmoothDescriptor extends Descriptor<DashboardPortlet> {
		@Override
		public String getDisplayName() {
			return "Smooth SDLMA dashboard view";
		}
	}

	@DataBoundConstructor
	public SmoothSdlmaPortlet(String name) {
		super(name);
	}

	public Graph getSummaryGraph() throws IOException {

		java.util.List<SdlmaJobInfo> jobInfos = new ArrayList<SdlmaJobInfo>();

		final Map<LocalDate, SdlmaResultSummary> summaries = new HashMap<LocalDate, SdlmaResultSummary>();

		for (Job job : getDashboard().getJobs()) {

			if (job.getFirstBuild() == null) {
				continue;
			}

			SdlmaJobInfo jobInfo = new SdlmaJobInfo(job);
			jobInfo.setStartRunTime(new LocalDate(job.getFirstBuild()
					.getTimestamp()));
			jobInfo.setEndRunTime(new LocalDate(job.getLastBuild()
					.getTimestamp()));
			jobInfos.add(jobInfo);
		}

		// get the latest endRunDay of jobs
		LocalDate endRunDay = new LocalDate(2000, 1, 1);
		for (SdlmaJobInfo jobInfo : jobInfos) {
			if (endRunDay.isBefore(jobInfo.getEndRunTime())) {
				endRunDay = jobInfo.getEndRunTime();
			}
		}

		for (SdlmaJobInfo jobInfo : jobInfos) {
			// summarize the job from first run day of this job to end run day
			// of this job
			for (LocalDate startRunDay = jobInfo.getStartRunTime(); (startRunDay
					.isBefore(jobInfo.getEndRunTime()) || startRunDay
					.isEqual(jobInfo.getEndRunTime())); startRunDay = startRunDay
					.plusDays(1)) {

				Run run = jobInfo.getJob().getFirstBuild();

				SdlmaBuildAction action = null;
				SdlmaBuildAction lastaction = null;

				while (run != null) {
					Run nextRun = run.getNextBuild();

					LocalDate runDay = new LocalDate(run.getTimestamp());

					if (startRunDay.isEqual(runDay)) {
						action = run.getAction(SdlmaBuildAction.class);
					}
					if (startRunDay.isBefore(runDay)) {
						if (run.getPreviousBuild() != null)
							lastaction = getPreviousBuildWithTestResultAction(run);
						break;
					}

					run = nextRun;
				}
				if (action != null) {
					summarize(summaries, startRunDay, action);
					action = null;
				} else if (lastaction != null) {
					summarize(summaries, startRunDay, lastaction);
					lastaction = null;
				}
			}

			// makeup to summarize the job statics data from lastRunDay + 1 of
			// this job to latest endRunDay of all jobs
			for (LocalDate startRunDay = jobInfo.getEndRunTime().plusDays(1); startRunDay
					.isBefore(endRunDay)
					|| startRunDay.isEqual(endRunDay); startRunDay = startRunDay
					.plusDays(1)) {
				summarize(summaries, startRunDay, jobInfo.getJob()
						.getLastBuild().getAction(SdlmaBuildAction.class));
			}

		}

		return new Graph(-1, 400, 300) {
			@Override
			protected JFreeChart createGraph() {

				JFreeChart chart = ChartFactory.createStackedAreaChart(null, // chart
						// title
						null, // unused
						"Blocks", // range axis label
						buildDataSet(summaries), // data
						PlotOrientation.VERTICAL, // orientation
						true, // include legend?
						false, // tooltips?
						true // urls?
						);

				chart.setBackgroundPaint(Color.WHITE);

				CategoryPlot plot = chart.getCategoryPlot();
				plot.setBackgroundPaint(Color.WHITE);
				plot.setDomainGridlinePaint(Color.BLACK);
				plot.setRangeGridlinePaint(Color.BLACK);
				plot.setForegroundAlpha(0.8f);

				CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
				plot.setDomainAxis(domainAxis);
				domainAxis
						.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
				domainAxis.setLowerMargin(0.0);
				domainAxis.setUpperMargin(0.0);
				domainAxis.setCategoryMargin(0.0);

				StackedAreaRenderer ar = new StackedAreaRenderer2();
				plot.setRenderer(ar);
				ar.setSeriesPaint(0, Color.RED);
				ar.setSeriesPaint(1, Color.GREEN);
				return chart;
			}

		};

	}

	private SdlmaBuildAction getPreviousBuildWithTestResultAction(Run run) {
		if (run == null) {
			return null;
		} else if (run.getAction(SdlmaBuildAction.class) != null) {
			return run.getAction(SdlmaBuildAction.class);
		} else {
			return getPreviousBuildWithTestResultAction(run.getPreviousBuild());
		}
	}

	private void recordNoBuildData(
			final Map<LocalDate, SdlmaResultSummary> summaries, Run run,
			LocalDate lastRunDay, LocalDate runDay) {
		// if there is no build in some day, it will fill with last run build
		if (run.getPreviousBuild() != null) {
			SdlmaBuildAction action = run.getAction(SdlmaBuildAction.class);
			summarize(summaries, lastRunDay, action);
		}
		while (true) {
			if (lastRunDay.plusDays(1).isEqual(runDay)) {
				lastRunDay = lastRunDay.plusDays(1);
				break;
			}
			lastRunDay = lastRunDay.plusDays(1);
			if (run.getPreviousBuild() != null) {
				SdlmaBuildAction action = run.getAction(SdlmaBuildAction.class);
				summarize(summaries, lastRunDay, action);
			}
		}
		// add today's data
		SdlmaBuildAction action = run.getAction(SdlmaBuildAction.class);
		summarize(summaries, runDay, action);
	}

	private static void summarize(Map<LocalDate, SdlmaResultSummary> summaries,
			LocalDate day, SdlmaBuildAction lastAction) {

		SdlmaBuildAction action = lastAction;

		if (action == null) {
			return;
		}

		// do {
		SdlmaResult result = action.getResult();
		if (result != null) {
			SdlmaReport report = result.getReport();
			NumberOnlyBuildLabel buildLabel = new NumberOnlyBuildLabel(action
					.getBuild());

			SdlmaResultSummary sdlmaResultSummary = summaries.get(day);
			if (sdlmaResultSummary == null) {
				sdlmaResultSummary = new SdlmaResultSummary();
				summaries.put(day, sdlmaResultSummary);
			}
			sdlmaResultSummary.addData(report.getComplexityOverN(), report
					.getBlockCount()
					- report.getComplexityOverN());

		}

	}

	private CategoryDataset buildDataSet(
			Map<LocalDate, SdlmaResultSummary> summaries) {
		DataSetBuilder<String, LocalDate> dsb = new DataSetBuilder<String, LocalDate>();
		for (Entry<LocalDate, SdlmaResultSummary> entry : summaries.entrySet()) {
			dsb.add(entry.getValue().getComplexity(),
					"Number of blocks with high complexity", entry.getKey());
			dsb.add(entry.getValue().getBlockCount(), "Total number of blocks",
					entry.getKey());
		}
		return dsb.build();
	}

	public class SdlmaJobInfo {

		public final Job job;

		public LocalDate startRunTime;

		public LocalDate endRunTime;

		public SdlmaJobInfo(Job job) {
			this.job = job;
		}

		public Job getJob() {
			return job;
		}

		public LocalDate getStartRunTime() {
			return startRunTime;
		}

		public void setStartRunTime(LocalDate startRunTime) {
			this.startRunTime = startRunTime;
		}

		public LocalDate getEndRunTime() {
			return endRunTime;
		}

		public void setEndRunTime(LocalDate endRunTime) {
			this.endRunTime = endRunTime;
		}

	}

}

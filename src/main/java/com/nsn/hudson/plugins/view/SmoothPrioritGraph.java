package com.nsn.hudson.plugins.view;

import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.data.category.CategoryDataset;
import org.joda.time.LocalDate;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import hudson.model.AbstractBuild;
import hudson.plugins.analysis.core.BuildResult;
import hudson.plugins.analysis.core.ResultAction;
import hudson.plugins.analysis.graph.GraphConfiguration;
import hudson.plugins.analysis.graph.PriorityGraph;
import hudson.plugins.analysis.util.ToolTipProvider;
import hudson.plugins.warnings.Messages;
import hudson.util.DataSetBuilder;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;

/***
 * extends the PriorityGraph of Warning plugin
 * 
 * @author tang
 * 
 */

public class SmoothPrioritGraph extends PriorityGraph {

	@Override
	public String getId() {
		return "PRIORITY";
	}

	@Override
	public String getLabel() {
		return "Smooth " + Messages.Portlet_WarningsPriorityGraph();
	}
	
	/**
     * Creates a PNG image trend graph with clickable map.
     *
     * @param configuration
     *            the configuration parameters
     * @param resultActions
     *            the result actions to start the graph computation from
     * @param pluginName
     *            the name of the plug-in
     * @return the graph
     */
    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("WMI")
    public JFreeChart createAggregation(final GraphConfiguration configuration,
            final Collection<ResultAction<? extends BuildResult>> resultActions, final String pluginName) {
    	
    	LocalDate latestBuildDate = getLatestBuildDate(resultActions);
    	
        Multimap<LocalDate, List<Integer>> total = HashMultimap.create();
        
        for (ResultAction<? extends BuildResult> resultAction : resultActions) {

        	Map<LocalDate, List<Integer>> lastByDate = lastbuildByDate(
                    createSeriesPerBuild(configuration, resultAction.getResult()));
        	Map<LocalDate, List<Integer>> fullDataByDate = fillBuildDataByDate(
					latestBuildDate, lastByDate);
            
            for (LocalDate buildDate : fullDataByDate.keySet()) {
                total.put(buildDate, fullDataByDate.get(buildDate));
            }
        }
        Map<LocalDate, List<Integer>> seriesPerDay = createSeriesPerDay(total);
        JFreeChart chart = createChart(createDatasetPerDay(seriesPerDay));

        attachRenderers(configuration, pluginName, chart, resultActions.iterator().next().getToolTipProvider());

        return chart;
    }

    /**
     * fill the build result form start date of the job to end date of all jobs.
     *      if no data in one date,copy data from previous date's data
     * 
     * @param latestBuildDate end date of all jobs.
     * @param lastByDate
     * @return
     */
	private Map<LocalDate, List<Integer>> fillBuildDataByDate(
			LocalDate latestBuildDate, Map<LocalDate, List<Integer>> lastByDate) {
		Map<LocalDate, List<Integer>> fullDataByDate = Maps.newHashMap(lastByDate);
		List<LocalDate> dates = Lists.newArrayList(lastByDate.keySet());
		Collections.sort(dates);
		LocalDate firstDate = dates.get(0);
		LocalDate endDate = latestBuildDate;
		for(LocalDate date=firstDate;date.isBefore(endDate)||date.isEqual(endDate);date = date.plusDays(1)){
			List<Integer> list = lastByDate.get(date);
			if(list==null){
				list = fullDataByDate.get(date.minusDays(1));
			}
			fullDataByDate.put(date, list);
		}
		return fullDataByDate;
	}

    /**
     * get the latest date in all the jobs.
     * @param resultActions
     * @return
     */
	private LocalDate getLatestBuildDate(
			final Collection<ResultAction<? extends BuildResult>> resultActions) {
		LocalDate latestBuildDate = null;
    	for (ResultAction<? extends BuildResult> resultAction : resultActions) {
    		AbstractBuild<?, ?> build = resultAction.getBuild();
    		LocalDate date = new LocalDate(build.getTimestamp());
    		if (latestBuildDate == null || date.isAfter(latestBuildDate)) {
    			latestBuildDate = date;
			}
    	}
		return latestBuildDate;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	@Override
	public JFreeChart create(final GraphConfiguration configuration,
			final ResultAction<? extends BuildResult> resultAction,
			final String pluginName) {
		JFreeChart chart = createChart(configuration, resultAction);

		attachRenderers(configuration, pluginName, chart, resultAction
				.getToolTipProvider());

		return chart;
	}
	
	
	/**
     * Creates the chart by iterating through all available actions.
     *
     * @param configuration
     *            the configuration parameters
     * @param action
     *            the action to start with
     * @return the created chart
     */
    protected JFreeChart createChart(final GraphConfiguration configuration, final ResultAction<? extends BuildResult> action) {
        CategoryDataset dataSet;
        if (configuration.useBuildDateAsDomain()) {
            Map<LocalDate, List<Integer>> averagePerDay = lastbuildByDate(createSeriesPerBuild(configuration, action.getResult()));
            dataSet = createDatasetPerDay(averagePerDay);
        	
        }
        else {
            dataSet = createDatasetPerBuildNumber(createSeriesPerBuild(configuration, action.getResult()));
        }
        return createChart(dataSet);
    }
    
    /**
     * Aggregates the series per build to a series per date.
     *
     * @param valuesPerBuild
     *            the series per build
     * @return the series per date
     */
    @SuppressWarnings("rawtypes")
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("WMI")
    private Map<LocalDate, List<Integer>> lastbuildByDate(
            final Map<AbstractBuild, List<Integer>> valuesPerBuild) {
    	Map<LocalDate, List<Integer>> valuesPerDate = Maps.newHashMap();
        for (AbstractBuild<?, ?> build : valuesPerBuild.keySet()) {
            valuesPerDate.put(new LocalDate(build.getTimestamp()), valuesPerBuild.get(build));
        }
        return valuesPerDate;
    }
    
    /**
     * Attach the renderers to the created graph.
     *
     * @param configuration
     *            the configuration parameters
     * @param pluginName
     *            the name of the plug-in
     * @param chart
     *            the graph to attach the renderer to
     * @param toolTipProvider the tooltip provider for the graph
     */
    private void attachRenderers(final GraphConfiguration configuration, final String pluginName, final JFreeChart chart,
            final ToolTipProvider toolTipProvider) {
        CategoryItemRenderer renderer = createRenderer(configuration, pluginName, toolTipProvider);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setRenderer(renderer);
        setColors(chart, getColors());
    }
    
    /**
     * Creates a data set that contains a series per build number.
     *
     * @param valuesPerBuild
     *            the collected values
     * @return a data set
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private CategoryDataset createDatasetPerBuildNumber(final Map<AbstractBuild, List<Integer>> valuesPerBuild) {
        DataSetBuilder<String, NumberOnlyBuildLabel> builder = new DataSetBuilder<String, NumberOnlyBuildLabel>();
        List<AbstractBuild> builds = Lists.newArrayList(valuesPerBuild.keySet());
        Collections.sort(builds);
        for (AbstractBuild<?, ?> build : builds) {
            List<Integer> series = valuesPerBuild.get(build);
            int level = 0;
            for (Integer integer : series) {
                builder.add(integer, getRowId(level), new NumberOnlyBuildLabel(build));
                level++;
            }
        }
        return builder.build();
    }
    
    /**
     * Creates a series of values per build.
     *
     * @param configuration
     *            the configuration
     * @param lastBuildResult
     *            the build result to start with
     * @return a series of values per build
     */
    @SuppressWarnings("rawtypes")
    private Map<AbstractBuild, List<Integer>> createSeriesPerBuild(
            final GraphConfiguration configuration, final BuildResult lastBuildResult) {
        BuildResult current = lastBuildResult;
        Calendar buildTime = current.getOwner().getTimestamp();

        int buildCount = 0;
        Map<AbstractBuild, List<Integer>> valuesPerBuild = Maps.newHashMap();
        while (true) {
            valuesPerBuild.put(current.getOwner(), computeSeries(current));

            if (current.hasPreviousResult()) {
                current = current.getPreviousResult();
                if (current == null) {
                    break; // see: HUDSON-6613
                }
            }
            else {
                break;
            }

            if (configuration.isBuildCountDefined()) {
                buildCount++;
                if (buildCount >= configuration.getBuildCount()) {
                    break;
                }
            }

            if (configuration.isDayCountDefined()) {
                Calendar oldBuildTime = current.getOwner().getTimestamp();
                if (computeDayDelta(buildTime, oldBuildTime) >= configuration.getDayCount()) {
                    break;
                }
            }
        }
        return valuesPerBuild;
    }
    
    /**
     * Aggregates multiple series per day to one single series per day by
     * computing the average value.
     *
     * @param multiSeriesPerDate
     *            the values given as multiple series per day
     * @return the values as one series per day (average)
     */
    private Map<LocalDate, List<Integer>> createSeriesPerDay(
            final Multimap<LocalDate, List<Integer>> multiSeriesPerDate) {
        Map<LocalDate, List<Integer>> seriesPerDate = Maps.newHashMap();

        //TODO: 1. get total value rather than get the average value
        for (LocalDate date : multiSeriesPerDate.keySet()) {
            Iterator<List<Integer>> perDayIterator = multiSeriesPerDate.get(date).iterator();
            List<Integer> total = perDayIterator.next();
            int seriesCount = 1;
            while (perDayIterator.hasNext()) {
                List<Integer> additional = perDayIterator.next();
                seriesCount++;

                List<Integer> sum = Lists.newArrayList();
                for (int i = 0; i < total.size(); i++) {
                    sum.add(total.get(i) + additional.get(i));
                }

                total = sum;
            }
            List<Integer> series = Lists.newArrayList();
            for (Integer totalValue : total) {
                series.add(totalValue);
            }
            seriesPerDate.put(date, series);
        }
        return seriesPerDate;
    }
    
    /**
     * Creates a data set that contains one series of values per day.
     *
     * @param averagePerDay
     *            the collected values averaged by day
     * @return a data set
     */
    @SuppressWarnings("unchecked")
    private CategoryDataset createDatasetPerDay(final Map<LocalDate, List<Integer>> averagePerDay) {
        List<LocalDate> buildDates = Lists.newArrayList(averagePerDay.keySet());
        Collections.sort(buildDates);

        DataSetBuilder<String, String> builder = new DataSetBuilder<String, String>();
        for (LocalDate date : buildDates) {
            int level = 0;
            for (Integer average : averagePerDay.get(date)) {
                builder.add(average, getRowId(level), date.toString("MM-dd"));
                level++;
            }
        }
        return builder.build();
    }

}

package hr.kapsch.integration;

import hr.kapsch.scheduling.quartz.PollingEndpointQuartzBridgeJob;
import org.quartz.*;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.quartz.JobDetailAwareTrigger;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public class PollingEndpointQuartzBridgeTrigger implements BeanNameAware, InitializingBean, Trigger {

	private static final Pattern TRIGGER_BEAN_NAME_PATTERN = Pattern.compile("(\\S+)Trigger");

	private Scheduler scheduler;
	private ScheduleBuilder scheduleBuilder;
	private String pollingEndpointBeanName;
	private String triggerBeanName;
	private boolean done;

	public PollingEndpointQuartzBridgeTrigger(Scheduler scheduler, ScheduleBuilder scheduleBuilder) {
		this.scheduler = requireNonNull(scheduler);
		this.scheduleBuilder = requireNonNull(scheduleBuilder);
	}

	@Override
	public void setBeanName(String name) {
		Matcher triggerBeanNameMatcher = TRIGGER_BEAN_NAME_PATTERN.matcher(name);
		if (!triggerBeanNameMatcher.matches()) {
			throw new IllegalArgumentException(getClass().getName() + " instance name must match " + TRIGGER_BEAN_NAME_PATTERN.pattern());
		}
		this.pollingEndpointBeanName = triggerBeanNameMatcher.group(1);
		this.triggerBeanName = name;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		JobKey jobKey = new JobKey(pollingEndpointBeanName);

		if (!scheduler.checkExists(jobKey)) {
			JobDetail jobDetail = JobBuilder.newJob(PollingEndpointQuartzBridgeJob.class)
					.withIdentity(jobKey)
					.storeDurably()
					.build();

			JobDataMap jobDataMap = new JobDataMap();
			jobDataMap.put(JobDetailAwareTrigger.JOB_DETAIL_KEY, jobDetail);
			jobDataMap.put(PollingEndpointQuartzBridgeJob.POLLING_ENDPOINT_BEAN_NAME_KEY, pollingEndpointBeanName);
			jobDataMap.put(PollingEndpointQuartzBridgeJob.TRIGGER_BEAN_NAME_KEY, triggerBeanName);

			org.quartz.Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity(pollingEndpointBeanName)
					.withSchedule(scheduleBuilder)
					.forJob(jobDetail)
					.usingJobData(jobDataMap)
					.build();

			scheduler.scheduleJob(jobDetail, trigger);
		}
	}

	@Override
	public Date nextExecutionTime(TriggerContext triggerContext) {
		if (done) {
			return null;
		}
		done = true;
		return new Date();
	}

	public void reset() {
		done = false;
	}

}

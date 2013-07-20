/*
 * Copyright (c) 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.quartz;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.ApplicationHolder;
import org.quartz.*;
import org.quartz.impl.JobDetailImpl;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import static org.quartz.impl.matchers.EverythingMatcher.allJobs;

/**
 * Simplified version of Spring's <a href='http://static.springframework.org/spring/docs/2.5.x/api/org/springframework/scheduling/quartz/MethodInvokingJobDetailFactoryBean.html'>MethodInvokingJobDetailFactoryBean</a>
 * that avoids issues with non-serializable classes (for JDBC storage).
 *
 * @author <a href='mailto:beckwithb@studentsonly.com'>Burt Beckwith</a>
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 * @since 0.3.2
 */
public class JobDetailFactoryBean implements FactoryBean<JobDetail>, InitializingBean {
    public static final transient String JOB_NAME_PARAMETER = "org.grails.plugins.quartz.grailsJobName";

    private Log log = LogFactory.getLog(JobDetailFactoryBean.class);

    // Properties
    private String name;
    private String group;
    private boolean concurrent;
    private boolean durability;
    private boolean requestsRecovery;
    private String[] jobListenerNames;

    // Returned object
    private JobDetail jobDetail;

    /**
     * Set the name of the job.
     * <p>Default is the bean name of this FactoryBean.
     *
     * @param name name of the job
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Set the group of the job.
     * <p>Default is the default group of the Scheduler.
     *
     * @param group group name of the job
     * @see org.quartz.Scheduler#DEFAULT_GROUP
     */
    public void setGroup(final String group) {
        this.group = group;
    }

    /**
     * Set a list of JobListener names for this job, referring to
     * JobListeners registered with the Scheduler.
     * <p>A JobListener name always refers to the name returned
     * by the JobListener implementation.
     *
     * @param names array of job listener names which should be applied to the job
     * @see SchedulerFactoryBean#setJobListeners
     * @see org.quartz.JobListener#getName
     */
    public void setJobListenerNames(final String[] names) {
        this.jobListenerNames = names;
    }

    @Required
    public void setConcurrent(final boolean concurrent) {
        this.concurrent = concurrent;
    }

    @Required
    public void setDurability(boolean durability) {
        this.durability = durability;
    }

    @Required
    public void setRequestsRecovery(boolean requestsRecovery) {
        this.requestsRecovery = requestsRecovery;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() {

        if (name == null) {
            throw new IllegalStateException("name is required");
        }

        if (group == null) {
            throw new IllegalStateException("group is required");
        }

        // Build JobDetail instance.
        GrailsJobDetail jd = new GrailsJobDetail();
        jd.setJobClass(GrailsJobFactory.GrailsJob.class);
        // Consider the concurrent flag to choose between stateful and stateless job.
        jd.setConcurrent(concurrent);
        jd.setDurability(durability);
        jd.setKey(new JobKey(name, group));
        jd.setRequestsRecovery(requestsRecovery);
        jd.getJobDataMap().put(JOB_NAME_PARAMETER, name);
        jobDetail = jd;

        // TODO: Move from here.
        // Register job listener names.
        if (jobListenerNames != null) {
            ApplicationContext ctx =
                    (ApplicationContext) ApplicationHolder.getApplication().getMainContext();
            Scheduler quartzScheduler = (Scheduler)ctx.getBean("quartzScheduler");
            try {
                ListenerManager manager =  quartzScheduler.getListenerManager();
                for (String jobListenerName : jobListenerNames) {

                    // no matcher == match all jobs

                    manager.addJobListener(manager.getJobListener(jobListenerName),allJobs());

                }
            } catch (SchedulerException e) {
                log.error("Error adding job listener to scheduler:",e);
            }
        }
    }

    private static class GrailsJobDetail extends JobDetailImpl{
        private boolean concurrent;

        @Override
        public boolean isPersistJobDataAfterExecution() {
            return !concurrent;
        }

        @Override
        public boolean isConcurrentExectionDisallowed() {
            return !concurrent;
        }

        private void setConcurrent(boolean concurrent) {
            this.concurrent = concurrent;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.FactoryBean#getObject()
     */
    @Override
    public JobDetail getObject() {
        return jobDetail;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.FactoryBean#getObjectType()
     */
    @Override
    public Class<JobDetail> getObjectType() {
        return JobDetail.class;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.FactoryBean#isSingleton()
     */
    @Override
    public boolean isSingleton() {
        return true;
    }
}

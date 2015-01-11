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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.UnableToInterruptJobException;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.AdaptableJobFactory;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Retrieves Job instances from ApplicationContext.
 * <p/>
 * Used by the Quartz scheduler to create an instance of the job class for executing.
 *
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 * @since 0.3.2
 */
public class GrailsJobFactory extends AdaptableJobFactory implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        String grailsJobName = (String) bundle.getJobDetail().getJobDataMap().get(JobDetailFactoryBean.JOB_NAME_PARAMETER);
        if (grailsJobName != null) {
            return new GrailsJob(applicationContext.getBean(grailsJobName));
        }

        return super.createJobInstance(bundle);
    }

    /**
     * Invokes execute() on the application's job class.
     */
    public static class GrailsJob implements InterruptableJob {

        private static final Class<?>[] NO_ARGS = null;

        private Object job;
        private Method executeMethod;
        private Method interruptMethod;
        boolean passExecutionContext;

        public GrailsJob(Object job) {
            this.job = job;

            // Finds an execute method with zero or one parameter.
            executeMethod = ReflectionUtils.findMethod(job.getClass(), GrailsJobClassConstants.EXECUTE, NO_ARGS);
            Assert.notNull(executeMethod, job.getClass().getName() + " must declare #" + GrailsJobClassConstants.EXECUTE + "() method");

            switch (executeMethod.getParameterTypes().length) {
                case 0: passExecutionContext = false; break;
                case 1: passExecutionContext = true;  break;
                default: throw new IllegalArgumentException(job.getClass().getName() + "#" + GrailsJobClassConstants.EXECUTE +
                               "() method must take either no arguments or one argument of type JobExecutionContext");
            }

            interruptMethod = ReflectionUtils.findMethod(job.getClass(), GrailsJobClassConstants.INTERRUPT);
        }

        public void execute(final JobExecutionContext context) throws JobExecutionException {
            try {
                if (passExecutionContext) {
                    executeMethod.invoke(job, context);
                }
                else {
                    executeMethod.invoke(job);
                }
            }
            catch (InvocationTargetException ite) {
                Throwable targetException = ite.getTargetException();
                if (targetException instanceof JobExecutionException) {
                    throw (JobExecutionException) targetException;
                }

                throw new JobExecutionException(targetException);
            }
            catch (IllegalAccessException iae) {
                JobExecutionException criticalError = new JobExecutionException(
                        "Cannot invoke " + job.getClass().getName() + "#" + executeMethod.getName() + "() method", iae);
                criticalError.setUnscheduleAllTriggers(true);
                throw criticalError;
            }
        }

        public void interrupt() throws UnableToInterruptJobException {
            if (interruptMethod == null) {
                throw new UnableToInterruptJobException(job.getClass().getName() + " doesn't support interruption");
            }

            try {
                interruptMethod.invoke(job);
            }
            catch (Throwable e) {
                throw new UnableToInterruptJobException(e);
            }
        }

        /**
         * For the quartz-monitor plugin.
         *
         * @return the GrailsJobClass object.
         */
        public Object getJob() {
            return job;
        }
    }

    /**
     * Quartz checks whether or not jobs are stateful and if so,
     * won't let jobs interfere with each other.
     */
    @PersistJobDataAfterExecution
    @DisallowConcurrentExecution
    public static class StatefulGrailsJob extends GrailsJob {
        public StatefulGrailsJob(Object job) {
            super(job);
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

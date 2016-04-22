/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.camunda.bpm.engine.test.api.multitenancy.suspensionstate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.TimerActivateJobDefinitionHandler;
import org.camunda.bpm.engine.impl.jobexecutor.TimerSuspendJobDefinitionHandler;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.management.JobDefinitionQuery;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.JobQuery;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class MultiTenancyJobDefinitionSuspensionStateTest extends PluggableProcessEngineTestCase {

  protected static final String TENANT_ONE = "tenant1";
  protected static final String TENANT_TWO = "tenant2";

  protected static final String PROCESS_DEFINITION_KEY = "testProcess";

  protected static final BpmnModelInstance PROCESS = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .userTask()
        .camundaAsyncBefore()
      .endEvent()
      .done();

  @Override
  protected void setUp() throws Exception {

    deploymentForTenant(TENANT_ONE, PROCESS);
    deploymentForTenant(TENANT_TWO, PROCESS);
    deployment(PROCESS);

    runtimeService.createProcessInstanceByKey(PROCESS_DEFINITION_KEY).processDefinitionTenantId(TENANT_ONE).execute();
    runtimeService.createProcessInstanceByKey(PROCESS_DEFINITION_KEY).processDefinitionTenantId(TENANT_TWO).execute();
    runtimeService.createProcessInstanceByKey(PROCESS_DEFINITION_KEY).processDefinitionWithoutTenantId().execute();
  }

  public void testSuspendAndActivateJobDefinitionsForAllTenants() {
    // given activated job definitions
    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // first suspend
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));

    // then activate
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .activate();

    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));
  }

  public void testSuspendJobDefinitionForTenant() {
    // given activated job definitions
    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testSuspendJobDefinitionForNonTenant() {
    // given activated job definitions
    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
  }

  public void testActivateJobDefinitionForTenant() {
    // given suspend job definitions
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testJobProcessDefinitionForNonTenant() {
    // given suspend job definitions
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().withoutTenantId().count(), is(1L));
  }

  public void testSuspendAndActivateJobDefinitionsIncludingJobsForAllTenants() {
    // given activated job definitions
    JobQuery query = managementService.createJobQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // first suspend
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .includeJobs(true)
      .suspend();

    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));

    // then activate
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .includeJobs(true)
      .activate();

    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));
  }

  public void testSuspendJobDefinitionIncludingJobsForTenant() {
    // given activated job definitions
    JobQuery query = managementService.createJobQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .includeJobs(true)
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testSuspendJobDefinitionIncludingJobsForNonTenant() {
    // given activated job definitions
    JobQuery query = managementService.createJobQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .includeJobs(true)
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
  }

  public void testActivateJobDefinitionIncludingJobsForTenant() {
    // given suspend job definitions
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .includeJobs(true)
      .suspend();

    JobQuery query = managementService.createJobQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .includeJobs(true)
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testActivateJobDefinitionIncludingJobsForNonTenant() {
    // given suspend job definitions
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .includeJobs(true)
      .suspend();

    JobQuery query = managementService.createJobQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .includeJobs(true)
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().withoutTenantId().count(), is(1L));
  }

  public void testDelayedSuspendJobDefinitionsForAllTenants() {
    // given activated job definitions

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .executionDate(tomorrow())
      .suspend();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // when execute the job to suspend the job definitions
    Job job = managementService.createJobQuery().timers().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));
  }

  public void testDelayedSuspendJobDefinitionsForTenant() {
    // given activated job definitions

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .executionDate(tomorrow())
      .suspend();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // when execute the job to suspend the job definitions
    Job job = managementService.createJobQuery().timers().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testDelayedSuspendJobDefinitionsForNonTenant() {
    // given activated job definitions

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .executionDate(tomorrow())
      .suspend();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // when execute the job to suspend the job definitions
    Job job = managementService.createJobQuery().timers().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
  }

  public void testDelayedActivateJobDefinitionsForAllTenants() {
    // given suspend job definitions
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .executionDate(tomorrow())
      .activate();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));

    // when execute the job to activate the job definitions
    Job job = managementService.createJobQuery().timers().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.suspended().count(), is(0L));
    assertThat(query.active().count(), is(3L));
  }

  public void testDelayedActivateJobDefinitionsForTenant() {
    // given suspend job definitions
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .executionDate(tomorrow())
      .activate();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));

    // when execute the job to activate the job definitions
    Job job = managementService.createJobQuery().timers().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testDelayedActivateJobDefinitionsForNonTenant() {
    // given suspend job definitions
    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .executionDate(tomorrow())
      .activate();

    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));

    // when execute the job to activate the job definitions
    Job job = managementService.createJobQuery().timers().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().withoutTenantId().count(), is(1L));
  }

  public void testSuspendJobDefinitionNoAuthenticatedTenants() {
    // given activated job definitions
    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    identityService.setAuthentication("user", null, null);

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    identityService.clearAuthentication();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
  }

  public void testSuspendJobDefinitionWithAuthenticatedTenant() {
    // given activated job definitions
    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    identityService.setAuthentication("user", null, Arrays.asList(TENANT_ONE));

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    identityService.clearAuthentication();

    assertThat(query.active().count(), is(1L));
    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().tenantIdIn(TENANT_TWO).count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testSuspendJobDefinitionDisabledTenantCheck() {
    // given activated job definitions
    JobDefinitionQuery query = managementService.createJobDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    processEngineConfiguration.setTenantCheckEnabled(false);
    identityService.setAuthentication("user", null, null);

    managementService
      .updateJobDefinitionSuspensionState()
      .byProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .suspend();

    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE, TENANT_TWO).includeJobDefinitionsWithoutTenantId().count(), is(3L));
  }

  protected Date tomorrow() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_YEAR, 1);
    return calendar.getTime();
  }

  @Override
  public void tearDown() throws Exception {
    CommandExecutor commandExecutor = processEngineConfiguration.getCommandExecutorTxRequired();
    commandExecutor.execute(new Command<Object>() {
      public Object execute(CommandContext commandContext) {
        commandContext.getHistoricJobLogManager().deleteHistoricJobLogsByHandlerType(TimerActivateJobDefinitionHandler.TYPE);
        commandContext.getHistoricJobLogManager().deleteHistoricJobLogsByHandlerType(TimerSuspendJobDefinitionHandler.TYPE);
        return null;
      }
    });
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.cmd.batch;

import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.authorization.BatchPermissions;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.impl.HistoricProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.ProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.batch.BatchConfiguration;
import org.camunda.bpm.engine.impl.batch.BatchConfiguration.BatchElementConfiguration;
import org.camunda.bpm.engine.impl.batch.builder.BatchBuilder;
import org.camunda.bpm.engine.impl.batch.deletion.DeleteProcessInstanceBatchConfiguration;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.PropertyChange;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;

/**
 * @author Askar Akhmerov
 */
public class DeleteProcessInstanceBatchCmd implements Command<Batch> {

  protected final String deleteReason;
  protected List<String> processInstanceIds;
  protected ProcessInstanceQuery processInstanceQuery;
  protected HistoricProcessInstanceQuery historicProcessInstanceQuery;
  protected boolean skipCustomListeners;
  protected boolean skipSubprocesses;

  public DeleteProcessInstanceBatchCmd(List<String> processInstances,
                                       ProcessInstanceQuery processInstanceQuery,
                                       HistoricProcessInstanceQuery historicProcessInstanceQuery,
                                       String deleteReason,
                                       boolean skipCustomListeners,
                                       boolean skipSubprocesses) {
    super();
    this.processInstanceIds = processInstances;
    this.processInstanceQuery = processInstanceQuery;
    this.historicProcessInstanceQuery = historicProcessInstanceQuery;
    this.deleteReason = deleteReason;
    this.skipCustomListeners = skipCustomListeners;
    this.skipSubprocesses = skipSubprocesses;
  }

  @Override
  public Batch execute(CommandContext commandContext) {
    BatchElementConfiguration elementConfiguration = collectProcessInstanceIds();

    ensureNotEmpty(BadUserRequestException.class, "processInstanceIds", elementConfiguration.getIds());

    return new BatchBuilder(commandContext)
        .type(Batch.TYPE_PROCESS_INSTANCE_DELETION)
        .config(getConfiguration(elementConfiguration))
        .permission(BatchPermissions.CREATE_BATCH_DELETE_RUNNING_PROCESS_INSTANCES)
        .operationLogHandler(this::writeUserOperationLog)
        .build();
  }

  protected BatchElementConfiguration collectProcessInstanceIds() {
    BatchElementConfiguration elementConfiguration = new BatchElementConfiguration();

    List<String> processInstanceIds = this.getProcessInstanceIds();
    if (processInstanceIds != null && !processInstanceIds.isEmpty()) {
      // TODO do we really want to do this? we practically did this previously
      // because we ran the queries without authorization in the batch handlers
      Context.getCommandContext().runWithoutAuthorization(() -> {
        ProcessInstanceQueryImpl query = new ProcessInstanceQueryImpl();
        query.processInstanceIds(new HashSet<>(processInstanceIds));
        elementConfiguration.addDeploymentMappings(query.listDeploymentIdMappings());
        return null;
      });
    }

    ProcessInstanceQueryImpl processInstanceQuery = (ProcessInstanceQueryImpl) this.processInstanceQuery;
    if (processInstanceQuery != null) {
      elementConfiguration.addDeploymentMappings(processInstanceQuery.listDeploymentIdMappings());
    }

    HistoricProcessInstanceQueryImpl historicProcessInstanceQuery = (HistoricProcessInstanceQueryImpl) this.historicProcessInstanceQuery;
    if (historicProcessInstanceQuery != null) {
      elementConfiguration.addDeploymentMappings(historicProcessInstanceQuery.listDeploymentIdMappings());
    }

    return elementConfiguration;
  }

  protected static List<String> getInstancesForDeploymentId(CommandContext commandContext, Collection<String> processIds, String deploymentId) {
    final ProcessInstanceQueryImpl processInstanceQueryToBeProcess = new ProcessInstanceQueryImpl();
    processInstanceQueryToBeProcess.processInstanceIds(new HashSet<>(processIds)).deploymentId(deploymentId);
    return commandContext.getExecutionManager().findProcessInstancesIdsByQueryCriteria(processInstanceQueryToBeProcess);
  }

  public List<String> getProcessInstanceIds() {
    return processInstanceIds;
  }

  protected void writeUserOperationLog(CommandContext commandContext, int numInstances) {

    List<PropertyChange> propertyChanges = new ArrayList<>();
    propertyChanges.add(new PropertyChange("nrOfInstances", null, numInstances));
    propertyChanges.add(new PropertyChange("async", null, true));
    propertyChanges.add(new PropertyChange("deleteReason", null, deleteReason));

    commandContext.getOperationLogManager()
        .logProcessInstanceOperation(UserOperationLogEntry.OPERATION_TYPE_DELETE,
            null,
            null,
            null,
            propertyChanges);
  }

  public BatchConfiguration getConfiguration(BatchElementConfiguration elementConfiguration) {
    return new DeleteProcessInstanceBatchConfiguration(elementConfiguration.getIds(), elementConfiguration.getMappings(),
        deleteReason, skipCustomListeners, skipSubprocesses, false);
  }

}

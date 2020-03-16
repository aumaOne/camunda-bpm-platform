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
package org.camunda.bpm.engine.impl.cmd;

import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotContainsNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.impl.ExternalTaskQueryImpl;
import org.camunda.bpm.engine.impl.HistoricProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.ProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.batch.BatchConfiguration.DeploymentMapping;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExternalTaskEntity;
import org.camunda.bpm.engine.impl.persistence.entity.PropertyChange;

public abstract class AbstractSetExternalTaskRetriesCmd<T> implements Command<T> {

  protected UpdateExternalTaskRetriesBuilderImpl builder;

  public AbstractSetExternalTaskRetriesCmd(UpdateExternalTaskRetriesBuilderImpl builder) {
    this.builder = builder;
  }

  protected List<String> collectProcessInstanceIds() {

    Set<String> collectedProcessInstanceIds = new HashSet<>();

    List<String> processInstanceIds = builder.getProcessInstanceIds();
    if (processInstanceIds != null && !processInstanceIds.isEmpty()) {
      collectedProcessInstanceIds.addAll(processInstanceIds);
    }

    ProcessInstanceQueryImpl processInstanceQuery = (ProcessInstanceQueryImpl) builder.getProcessInstanceQuery();
    if (processInstanceQuery != null) {
      collectedProcessInstanceIds.addAll(processInstanceQuery.listIds());
    }

    HistoricProcessInstanceQueryImpl historicProcessInstanceQuery = (HistoricProcessInstanceQueryImpl) builder.getHistoricProcessInstanceQuery();
    if (historicProcessInstanceQuery != null) {
      collectedProcessInstanceIds.addAll(historicProcessInstanceQuery.listIds());
    }

    return new ArrayList<>(collectedProcessInstanceIds);
  }

  protected List<String> collectExternalTaskIds(List<DeploymentMapping> mappings) {

    final Set<String> collectedIds = new HashSet<>();

    List<String> externalTaskIds = builder.getExternalTaskIds();
    if (externalTaskIds != null) {
      ensureNotContainsNull(BadUserRequestException.class, "External task id cannot be null", "externalTaskIds", externalTaskIds);
      collectedIds.addAll(externalTaskIds);
    }

    ExternalTaskQueryImpl externalTaskQuery = (ExternalTaskQueryImpl) builder.getExternalTaskQuery();
    if (externalTaskQuery != null) {
      collectedIds.addAll(externalTaskQuery.listIds());
    }

    CommandContext commandContext = Context.getCommandContext();
    final List<String> collectedProcessInstanceIds = collectProcessInstanceIds();
    if (!collectedProcessInstanceIds.isEmpty()) {

      commandContext.runWithoutAuthorization((Callable<Void>) () -> {
        ExternalTaskQueryImpl query = new ExternalTaskQueryImpl();
        query.processInstanceIdIn(collectedProcessInstanceIds.toArray(new String[0]));
        collectedIds.addAll(query.listIds());
        return null;
      });
    }

    List<String> ids = new ArrayList<>(collectedIds);
    if (mappings != null) {
      groupByDeploymentId(ids, commandContext.getExternalTaskManager()::findExternalTaskById, e -> getDeploymentId(commandContext, e), ExternalTaskEntity::getId)
        .entrySet().stream()
        .map(e -> new DeploymentMapping(e.getKey(), e.getValue().size()))
        .forEach(mappings::add);
    }

    return ids;
  }

  protected String getDeploymentId(CommandContext commandContext, ExternalTaskEntity externalTask) {
    List<String> ids = commandContext.getDeploymentManager().findDeploymentIdsByProcessInstances(
        Arrays.asList(externalTask.getProcessInstanceId()));
    return ids.isEmpty() ? null : ids.get(0);
  }

  protected <S extends DbEntity> Map<String, List<String>> groupByDeploymentId(List<String> ids, Function<String, S> idMapperFunction,
      Function<? super S, ? extends String> deploymentIdFunction, Function<? super S, ? extends String> entityIdFunction) {
    return ids.stream().map(idMapperFunction)
        .filter(Objects::nonNull)
        .filter(e -> deploymentIdFunction.apply(e) != null)
        .collect(Collectors.groupingBy(deploymentIdFunction,
            Collectors.mapping(entityIdFunction, Collectors.toList())));
  }

  protected void writeUserOperationLog(CommandContext commandContext, int numInstances,
                                       boolean async) {

    List<PropertyChange> propertyChanges = new ArrayList<>();
    propertyChanges.add(new PropertyChange("nrOfInstances", null, numInstances));
    propertyChanges.add(new PropertyChange("async", null, async));
    propertyChanges.add(new PropertyChange("retries", null, builder.getRetries()));

    commandContext.getOperationLogManager().logExternalTaskOperation(
        UserOperationLogEntry.OPERATION_TYPE_SET_EXTERNAL_TASK_RETRIES, null, propertyChanges);
  }

  protected void writeUserOperationLogAsync(CommandContext commandContext, int numInstances) {
    writeUserOperationLog(commandContext, numInstances, true);
  }

}

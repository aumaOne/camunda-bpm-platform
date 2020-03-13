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
package org.camunda.bpm.engine.impl;

import java.util.List;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.impl.batch.AbstractBatchJobHandler;
import org.camunda.bpm.engine.impl.batch.BatchJobConfiguration;
import org.camunda.bpm.engine.impl.batch.BatchJobContext;
import org.camunda.bpm.engine.impl.batch.BatchJobDeclaration;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobDeclaration;
import org.camunda.bpm.engine.impl.persistence.entity.ByteArrayEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.MessageEntity;

/**
 *
 * @author Anna Pazola
 *
 */
public class RestartProcessInstancesJobHandler extends AbstractBatchJobHandler<RestartProcessInstancesBatchConfiguration>{

  public static final BatchJobDeclaration JOB_DECLARATION = new BatchJobDeclaration(Batch.TYPE_PROCESS_INSTANCE_RESTART);

  @Override
  public String getType() {
    return Batch.TYPE_PROCESS_INSTANCE_RESTART;
  }

  @Override
  public void execute(BatchJobConfiguration configuration, ExecutionEntity execution, CommandContext commandContext, String tenantId) {
    ByteArrayEntity configurationEntity = commandContext
        .getDbEntityManager()
        .selectById(ByteArrayEntity.class, configuration.getConfigurationByteArrayId());

    RestartProcessInstancesBatchConfiguration batchConfiguration = readConfiguration(configurationEntity.getBytes());

    boolean initialLegacyRestrictions = commandContext.isRestrictUserOperationLogToAuthenticatedUsers();
    commandContext.disableUserOperationLog();
    commandContext.setRestrictUserOperationLogToAuthenticatedUsers(true);
    try {

      RestartProcessInstanceBuilderImpl builder = (RestartProcessInstanceBuilderImpl) commandContext.getProcessEngineConfiguration()
          .getRuntimeService()
          .restartProcessInstances(batchConfiguration.getProcessDefinitionId())
          .processInstanceIds(batchConfiguration.getIds());

      builder.setInstructions(batchConfiguration.getInstructions());

      if (batchConfiguration.isInitialVariables()) {
        builder.initialSetOfVariables();
      }

      if (batchConfiguration.isSkipCustomListeners()) {
        builder.skipCustomListeners();
      }

      if (batchConfiguration.isWithoutBusinessKey()) {
        builder.withoutBusinessKey();
      }

      if (batchConfiguration.isSkipIoMappings()) {
        builder.skipIoMappings();
      }

      builder.execute(false);

    } finally {
      commandContext.enableUserOperationLog();
      commandContext.setRestrictUserOperationLogToAuthenticatedUsers(initialLegacyRestrictions);
    }

    commandContext.getByteArrayManager().delete(configurationEntity);

  }

  @Override
  public JobDeclaration<BatchJobContext, MessageEntity> getJobDeclaration() {
    return JOB_DECLARATION;
  }

  @Override
  protected RestartProcessInstancesBatchConfiguration createJobConfiguration(RestartProcessInstancesBatchConfiguration configuration,
      List<String> processIdsForJob) {
    return new RestartProcessInstancesBatchConfiguration(processIdsForJob, null, configuration.getInstructions(), configuration.getProcessDefinitionId(),
        configuration.isInitialVariables(), configuration.isSkipCustomListeners(), configuration.isSkipIoMappings(), configuration.isWithoutBusinessKey());
  }

  @Override
  protected RestartProcessInstancesBatchConfigurationJsonConverter getJsonConverterInstance() {
    return RestartProcessInstancesBatchConfigurationJsonConverter.INSTANCE;
  }

}

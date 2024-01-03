/*
 * Copyright 2010-2020 Alfresco Software, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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


package org.activiti.engine.impl.interceptor;

import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 命令上下文拦截器，负责初始化命令上下文和关闭
 */
public class CommandContextInterceptor extends AbstractCommandInterceptor {

  private static final Logger log = LoggerFactory.getLogger(CommandContextInterceptor.class);

  protected CommandContextFactory commandContextFactory;
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  public CommandContextInterceptor() {
  }

  public CommandContextInterceptor(CommandContextFactory commandContextFactory, ProcessEngineConfigurationImpl processEngineConfiguration) {
    this.commandContextFactory = commandContextFactory;
    this.processEngineConfiguration = processEngineConfiguration;
  }

  public <T> T execute(CommandConfig config, Command<T> command) {
    CommandContext context = Context.getCommandContext();

    // 记录上下文是否重用的标志，如果后面检查是false，说明没有重用之前的上下文，而是在这里创建的，要负责把它关闭
    boolean contextReused = false;
    // We need to check the exception, because the transaction can be in a
    // rollback state, and some other command is being fired to compensate (eg. decrementing job retries)
      // 命令配置上下文不可重用 || 当前上下文为空 || 当前命令上下文发生过异常
    if (!config.isContextReusePossible() || context == null || context.getException() != null) {
      context = commandContextFactory.createCommandContext(command);
    } else {
      log.debug("Valid context found. Reusing it for the current command '{}'", command.getClass().getCanonicalName());
      contextReused = true;
      context.setReused(true);
    }

    try {

      // Push on stack
      Context.setCommandContext(context);
      Context.setProcessEngineConfiguration(processEngineConfiguration);
      return next.execute(config, command);

    } catch (Throwable e) {

      context.exception(e);

    } finally {
      try {
        if (!contextReused) {
          context.close();
        }
      } finally {

        // Pop from stack
        Context.removeCommandContext();
        Context.removeProcessEngineConfiguration();
        Context.removeBpmnOverrideContext();
      }
    }

    return null;
  }

  public CommandContextFactory getCommandContextFactory() {
    return commandContextFactory;
  }

  public void setCommandContextFactory(CommandContextFactory commandContextFactory) {
    this.commandContextFactory = commandContextFactory;
  }

  public ProcessEngineConfigurationImpl getProcessEngineConfiguration() {
    return processEngineConfiguration;
  }

  public void setProcessEngineContext(ProcessEngineConfigurationImpl processEngineContext) {
    this.processEngineConfiguration = processEngineContext;
  }
}
